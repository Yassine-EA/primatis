import { Component, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { CopyResponse } from '../../../../catalogue/models/copy-response';
import { TitleResponse } from '../../../../catalogue/models/title-response';
import { CopyApiService } from '../../../../catalogue/services/copy-api.service';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { CreateLoanRequest } from '../../../../loans/models/create-loan-request';
import { LoanResponse } from '../../../../loans/models/loan-response';
import { LoanApiService } from '../../../../loans/services/loan-api.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';

const SEARCH_DEBOUNCE_MS = 300;
const BORROWER_SEARCH_PAGE_SIZE = 20;
const TITLE_SEARCH_PAGE_SIZE = 20;

/**
 * Dialog de création d'un Loan (DEV-07.9.2, `LOAN_MANAGE`), consommé
 * uniquement par `StaffLoansPage`. Le formulaire ne produit strictement
 * que `{ borrowerUserId, copyId }` (`CreateLoanRequest`) — jamais de
 * saisie numérique brute : l'emprunteur et l'exemplaire sont
 * exclusivement choisis via recherche/sélection.
 *
 * Emprunteur : recherche débouncée sur `UserApiService.listUsers(0, 20, q)`
 * (`GET /api/v1/users?q=`, DEV-07.9.1 — même pattern debounce que
 * `AuthorPicker`). `q` est générique et peut retourner des utilisateurs
 * non adhérents (`memberNumber === null`) : ces résultats restent
 * visibles (jamais filtrés côté client — la recherche backend resterait
 * sinon faussement perçue comme non exhaustive) mais non sélectionnables,
 * marqués « Non adhérent ». Aucune règle d'éligibilité dynamique
 * (`AccountStatus`, `MemberStatus`, expiration, Fine impayée, max 5
 * prêts) n'est recalculée ici : `LoanService.registerLoan` reste seul
 * juge, ses refus remontent tels quels via `toAppError`.
 *
 * Exemplaire : recherche Title existante (`StaffCatalogueApiService.searchTitles`)
 * puis `CopyApiService.listCopies(titleId)` (déjà utilisé ainsi par
 * `StaffTitleDetailPage`, sans pagination — liste bornée par Title).
 * Tous les Copies retournés restent affichés et sélectionnables, y
 * compris `RESERVED`/`ON_LOAN`/etc. : un Copy `RESERVED` peut être
 * légalement empruntable par le bénéficiaire correspondant, décision
 * qui appartient exclusivement à `LoanService.registerLoan`
 * (`COPY_ALREADY_ON_LOAN`/`COPY_NOT_LENDABLE`/
 * `COPY_RESERVED_FOR_ANOTHER_MEMBER` remontent en erreur métier si
 * refusé). Changer de Title réinitialise systématiquement le Copy choisi.
 */
@Component({
  selector: 'app-loan-create-dialog',
  imports: [
    FormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    MessageModule,
    TagModule,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './loan-create-dialog.html',
  styleUrl: './loan-create-dialog.scss',
})
export class LoanCreateDialog {
  private readonly userApiService = inject(UserApiService);
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);
  private readonly copyApiService = inject(CopyApiService);
  private readonly loanApiService = inject(LoanApiService);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();

  readonly closed = output<void>();
  readonly saved = output<LoanResponse>();

  readonly borrowerSearchTerm = signal('');
  readonly borrowerSearchResults = signal<UserResponse[]>([]);
  readonly borrowerSearching = signal(false);
  readonly borrowerSearchError = signal<AppError | null>(null);
  readonly selectedBorrower = signal<UserResponse | null>(null);

  readonly titleSearchTerm = signal('');
  readonly titleSearchResults = signal<TitleResponse[]>([]);
  readonly titleSearching = signal(false);
  readonly titleSearchError = signal<AppError | null>(null);
  readonly selectedTitle = signal<TitleResponse | null>(null);

  readonly copies = signal<CopyResponse[]>([]);
  readonly copiesLoading = signal(false);
  readonly copiesError = signal<AppError | null>(null);
  readonly selectedCopy = signal<CopyResponse | null>(null);

  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  private readonly borrowerSearchInput$ = new Subject<string>();
  private readonly titleSearchInput$ = new Subject<string>();

  constructor() {
    this.borrowerSearchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.runBorrowerSearch(value));

    this.titleSearchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.runTitleSearch(value));

    // Réinitialise systématiquement à chaque (ré)ouverture : jamais de
    // sélection obsolète d'une session précédente (même principe que
    // AuthorFormDialog).
    effect(() => {
      if (this.visible()) {
        this.resetState();
      }
    });
  }

  get canSubmit(): boolean {
    return this.selectedBorrower() !== null && this.selectedCopy() !== null && !this.submitting();
  }

  onBorrowerSearchInput(value: string): void {
    this.borrowerSearchTerm.set(value);
    this.borrowerSearchInput$.next(value);
  }

  /**
   * Un résultat sans `memberNumber` n'est jamais présenté comme un
   * emprunteur valide : `GET /api/v1/users?q=` est générique et peut
   * retourner des non-adhérents.
   */
  selectBorrower(borrower: UserResponse): void {
    if (borrower.memberNumber === null) {
      return;
    }
    this.selectedBorrower.set(borrower);
  }

  changeBorrower(): void {
    this.selectedBorrower.set(null);
    this.borrowerSearchTerm.set('');
    this.borrowerSearchResults.set([]);
  }

  onTitleSearchInput(value: string): void {
    this.titleSearchTerm.set(value);
    this.titleSearchInput$.next(value);
  }

  selectTitle(title: TitleResponse): void {
    this.selectedTitle.set(title);
    this.selectedCopy.set(null);
    this.loadCopies(title.id);
  }

  changeTitle(): void {
    this.selectedTitle.set(null);
    this.selectedCopy.set(null);
    this.titleSearchTerm.set('');
    this.titleSearchResults.set([]);
    this.copies.set([]);
    this.copiesError.set(null);
  }

  selectCopy(copy: CopyResponse): void {
    this.selectedCopy.set(copy);
  }

  retryCopies(): void {
    const title = this.selectedTitle();
    if (title !== null) {
      this.loadCopies(title.id);
    }
  }

  cancel(): void {
    this.closed.emit();
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    const borrower = this.selectedBorrower();
    const copy = this.selectedCopy();
    if (borrower === null || copy === null) {
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);

    const request: CreateLoanRequest = { borrowerUserId: borrower.id, copyId: copy.id };
    this.loanApiService.registerLoan(request).subscribe({
      next: (loan) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Prêt enregistré',
          detail: `Le prêt de l'exemplaire ${loan.copy.inventoryCode} a été enregistré.`,
        });
        this.saved.emit(loan);
        this.resetState();
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const appError = toAppError(err);
        this.submitError.set(appError.message);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: appError.message });
      },
    });
  }

  private runBorrowerSearch(value: string): void {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      this.borrowerSearchResults.set([]);
      this.borrowerSearching.set(false);
      this.borrowerSearchError.set(null);
      return;
    }
    this.borrowerSearching.set(true);
    this.borrowerSearchError.set(null);
    this.userApiService.listUsers(0, BORROWER_SEARCH_PAGE_SIZE, trimmed).subscribe({
      next: (response) => {
        this.borrowerSearchResults.set(response.content);
        this.borrowerSearching.set(false);
      },
      error: (err: unknown) => {
        this.borrowerSearching.set(false);
        this.borrowerSearchError.set(toAppError(err));
      },
    });
  }

  private runTitleSearch(value: string): void {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      this.titleSearchResults.set([]);
      this.titleSearching.set(false);
      this.titleSearchError.set(null);
      return;
    }
    this.titleSearching.set(true);
    this.titleSearchError.set(null);
    this.staffCatalogueApiService.searchTitles({ q: trimmed, page: 0, size: TITLE_SEARCH_PAGE_SIZE }).subscribe({
      next: (response) => {
        this.titleSearchResults.set(response.content);
        this.titleSearching.set(false);
      },
      error: (err: unknown) => {
        this.titleSearching.set(false);
        this.titleSearchError.set(toAppError(err));
      },
    });
  }

  private loadCopies(titleId: number): void {
    this.copiesLoading.set(true);
    this.copiesError.set(null);
    this.copyApiService.listCopies(titleId).subscribe({
      next: (copies) => {
        this.copies.set(copies);
        this.copiesLoading.set(false);
      },
      error: (err: unknown) => {
        this.copiesLoading.set(false);
        this.copiesError.set(toAppError(err));
      },
    });
  }

  private resetState(): void {
    this.borrowerSearchTerm.set('');
    this.borrowerSearchResults.set([]);
    this.borrowerSearching.set(false);
    this.borrowerSearchError.set(null);
    this.selectedBorrower.set(null);

    this.titleSearchTerm.set('');
    this.titleSearchResults.set([]);
    this.titleSearching.set(false);
    this.titleSearchError.set(null);
    this.selectedTitle.set(null);

    this.copies.set([]);
    this.copiesLoading.set(false);
    this.copiesError.set(null);
    this.selectedCopy.set(null);

    this.submitting.set(false);
    this.submitError.set(null);
  }
}
