import { Component, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { TitleResponse } from '../../../../catalogue/models/title-response';
import { CatalogueApiService } from '../../../../catalogue/services/catalogue-api.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { CreateOwnReservationRequest } from '../../../../reservations/models/create-own-reservation-request';
import { ReservationResponse } from '../../../../reservations/models/reservation-response';
import { ReservationApiService } from '../../../../reservations/services/reservation-api.service';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';

const SEARCH_DEBOUNCE_MS = 300;
const TITLE_SEARCH_PAGE_SIZE = 20;

/**
 * Dialog de création self-service d'une Reservation (DEV-08.11,
 * DEV-DEC-0040/OD-DEV08-FE-01), consommé uniquement par
 * `MemberReservationsPage`. Le formulaire ne produit strictement que
 * `{ titleId }` (`CreateOwnReservationRequest`) — jamais de saisie
 * numérique brute : le Title est exclusivement choisi via recherche/
 * sélection, même principe que le volet Title de
 * `staff/loans/components/loan-create-dialog/` (recherche débouncée
 * 300ms, réinitialisation à l'ouverture via `effect()`).
 *
 * <p><b>Recherche Title : `CatalogueApiService`, pas
 * `StaffCatalogueApiService`</b> — déviation délibérée et nécessaire par
 * rapport au service utilisé par `LoanCreateDialog` (mission DEV-08.11
 * §5/§17 : « toute nécessité de sortie du précédent exact : STOP avant
 * modification » — documentée ici plutôt que silencieusement appliquée).
 * `StaffCatalogueApiService.searchTitles` cible `GET /api/v1/staff/titles`,
 * protégé par `CATALOGUE_MANAGE` côté backend (`StaffCatalogueApiService`,
 * Javadoc) : un membre self-service recevrait systématiquement `403`.
 * `CatalogueApiService.searchTitles` (`GET /api/v1/titles`, `permitAll`,
 * déjà utilisé par le catalogue public) partage exactement le même
 * contrat `q`/`page`/`size` → `PageResponse<TitleResponse>` et convient
 * structurellement à ce contexte self-service — même pattern UX exact
 * (debounce, résultats, sélection), service réellement accessible au
 * membre authentifié.
 *
 * <p>Reservation cible un Title, jamais un Copy (business-rules.md
 * §4.1) : contrairement à `LoanCreateDialog`, aucune recherche
 * emprunteur, aucune liste de Copy, aucun `availabilityStatus` consulté
 * ou recalculé. Aucune règle d'éligibilité (adhésion active, doublon
 * actif, limite de réservations actives, disponibilité immédiate d'un
 * Copy) n'est anticipée ici : `ReservationService.createOwnReservation`
 * reste seul juge, ses refus (`RESERVATION_COPY_AVAILABLE`/
 * `RESERVATION_ALREADY_ACTIVE`/`RESERVATION_LIMIT_REACHED`/
 * `NOT_A_MEMBER`/`MEMBER_BLOCKED`/`MEMBER_EXPIRED`/`TITLE_NOT_FOUND`)
 * remontent tels quels via `toAppError`, jamais prédits — le dialog
 * reste ouvert après une erreur, permettant une nouvelle tentative
 * (même comportement exact que `LoanCreateDialog.submit`).
 */
@Component({
  selector: 'app-reservation-create-dialog',
  imports: [FormsModule, ButtonModule, DialogModule, InputTextModule, MessageModule, ErrorState],
  templateUrl: './reservation-create-dialog.html',
  styleUrl: './reservation-create-dialog.scss',
})
export class ReservationCreateDialog {
  private readonly catalogueApiService = inject(CatalogueApiService);
  private readonly reservationApiService = inject(ReservationApiService);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();

  readonly closed = output<void>();
  readonly saved = output<ReservationResponse>();

  readonly titleSearchTerm = signal('');
  readonly titleSearchResults = signal<TitleResponse[]>([]);
  readonly titleSearching = signal(false);
  readonly titleSearchError = signal<AppError | null>(null);
  readonly selectedTitle = signal<TitleResponse | null>(null);

  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  private readonly titleSearchInput$ = new Subject<string>();

  constructor() {
    this.titleSearchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.runTitleSearch(value));

    // Réinitialise systématiquement à chaque (ré)ouverture : jamais de
    // sélection obsolète d'une session précédente (même principe que
    // LoanCreateDialog/AuthorFormDialog).
    effect(() => {
      if (this.visible()) {
        this.resetState();
      }
    });
  }

  get canSubmit(): boolean {
    return this.selectedTitle() !== null && !this.submitting();
  }

  onTitleSearchInput(value: string): void {
    this.titleSearchTerm.set(value);
    this.titleSearchInput$.next(value);
  }

  selectTitle(title: TitleResponse): void {
    this.selectedTitle.set(title);
  }

  changeTitle(): void {
    this.selectedTitle.set(null);
    this.titleSearchTerm.set('');
    this.titleSearchResults.set([]);
  }

  retryTitleSearch(): void {
    this.runTitleSearch(this.titleSearchTerm());
  }

  cancel(): void {
    this.closed.emit();
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    const title = this.selectedTitle();
    if (title === null) {
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);

    const request: CreateOwnReservationRequest = { titleId: title.id };
    this.reservationApiService.createOwnReservation(request).subscribe({
      next: (reservation) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Réservation créée',
          detail: `La réservation de « ${reservation.title.title} » a été créée.`,
        });
        this.saved.emit(reservation);
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
    this.catalogueApiService.searchTitles({ q: trimmed, page: 0, size: TITLE_SEARCH_PAGE_SIZE }).subscribe({
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

  private resetState(): void {
    this.titleSearchTerm.set('');
    this.titleSearchResults.set([]);
    this.titleSearching.set(false);
    this.titleSearchError.set(null);
    this.selectedTitle.set(null);

    this.submitting.set(false);
    this.submitError.set(null);
  }
}
