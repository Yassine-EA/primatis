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
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { CreateReservationRequest } from '../../../../reservations/models/create-reservation-request';
import { ReservationResponse } from '../../../../reservations/models/reservation-response';
import { ReservationApiService } from '../../../../reservations/services/reservation-api.service';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';

const SEARCH_DEBOUNCE_MS = 300;
const MEMBER_SEARCH_PAGE_SIZE = 20;
const TITLE_SEARCH_PAGE_SIZE = 20;

/**
 * Dialog de création staff d'une Reservation (DEV-08.13,
 * `RESERVATION_MANAGE`), consommé uniquement par `StaffReservationsPage`.
 * Le formulaire ne produit strictement que `{ userId, titleId }`
 * (`CreateReservationRequest`) — jamais de saisie numérique brute : le
 * membre et le Title sont exclusivement choisis via recherche/sélection,
 * même pattern exact que `staff/loans/components/loan-create-dialog/`
 * (dont les volets emprunteur et Title sont repris à l'identique ici,
 * moins le volet Copy — Reservation cible un Title, jamais un Copy,
 * business-rules.md §4.1).
 *
 * <p><b>Membre</b> : recherche débouncée sur
 * `UserApiService.listUsers(0, 20, q)` (`GET /api/v1/users?q=`,
 * même service/pattern exact que `LoanCreateDialog`). `q` est générique
 * et peut retourner des utilisateurs non adhérents
 * (`memberNumber === null`) : ces résultats restent visibles (jamais
 * filtrés côté client) mais non sélectionnables, marqués « Non adhérent »
 * — même comportement exact que `LoanCreateDialog`. Aucune règle
 * d'éligibilité (adhésion active, doublon actif, limite de réservations
 * actives, disponibilité immédiate d'un Copy) n'est recalculée ici :
 * `ReservationService.createReservation` reste seul juge, ses refus
 * remontent tels quels via `toAppError`.
 *
 * <p><b>Title : `StaffCatalogueApiService`, pas `CatalogueApiService`</b>
 * — choix opposé à `member/reservations/components/reservation-create-dialog/`
 * (DEV-08.11), vérifié explicitement avant implémentation (mission
 * DEV-08.13 §2/§3), jamais supposé par symétrie avec le dialog membre.
 * Audit RBAC réel (`V002__bootstrap_initial_rbac.sql`) : les deux seuls
 * rôles jamais dotés de `RESERVATION_MANAGE` (`ROLE_LIBRARIAN`,
 * `ROLE_ADMIN`) sont, dans ce même bootstrap, également et
 * inconditionnellement dotés de `CATALOGUE_MANAGE` — aucune interface
 * d'administration RBAC dynamique n'existe en V1 pour dissocier ces deux
 * permissions (`admin/roles` absent du frontend, aucun endpoint de
 * mutation `role_permission` côté backend). Un agent capable d'ouvrir ce
 * dialog (donc déjà titulaire de `RESERVATION_MANAGE`, vérifié par
 * `StaffReservationsPage`) possède donc structurellement toujours
 * `CATALOGUE_MANAGE` dans la baseline V1 — `StaffCatalogueApiService.searchTitles`
 * (`GET /api/v1/staff/titles`) n'ajoute ainsi aucune dépendance
 * fonctionnelle réellement absente, contrairement au dialog membre
 * self-service (DEV-08.11) où `CATALOGUE_MANAGE` n'est jamais garantie.
 *
 * <p>Aucun Copy, aucune disponibilité consultée ou recalculée, aucune
 * queue position, aucune Fine, aucune vérification de permission interne
 * au formulaire (la permission d'accès au dialog appartient exclusivement
 * à `StaffReservationsPage`).
 */
@Component({
  selector: 'app-staff-reservation-create-dialog',
  imports: [FormsModule, ButtonModule, DialogModule, InputTextModule, MessageModule, ErrorState],
  templateUrl: './staff-reservation-create-dialog.html',
  styleUrl: './staff-reservation-create-dialog.scss',
})
export class StaffReservationCreateDialog {
  private readonly userApiService = inject(UserApiService);
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);
  private readonly reservationApiService = inject(ReservationApiService);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();

  readonly closed = output<void>();
  readonly saved = output<ReservationResponse>();

  readonly memberSearchTerm = signal('');
  readonly memberSearchResults = signal<UserResponse[]>([]);
  readonly memberSearching = signal(false);
  readonly memberSearchError = signal<AppError | null>(null);
  readonly selectedMember = signal<UserResponse | null>(null);

  readonly titleSearchTerm = signal('');
  readonly titleSearchResults = signal<TitleResponse[]>([]);
  readonly titleSearching = signal(false);
  readonly titleSearchError = signal<AppError | null>(null);
  readonly selectedTitle = signal<TitleResponse | null>(null);

  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  private readonly memberSearchInput$ = new Subject<string>();
  private readonly titleSearchInput$ = new Subject<string>();

  constructor() {
    this.memberSearchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.runMemberSearch(value));

    this.titleSearchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.runTitleSearch(value));

    // Réinitialise systématiquement à chaque (ré)ouverture : jamais de
    // sélection obsolète d'une session précédente (même principe que
    // LoanCreateDialog).
    effect(() => {
      if (this.visible()) {
        this.resetState();
      }
    });
  }

  get canSubmit(): boolean {
    return this.selectedMember() !== null && this.selectedTitle() !== null && !this.submitting();
  }

  onMemberSearchInput(value: string): void {
    this.memberSearchTerm.set(value);
    this.memberSearchInput$.next(value);
  }

  /**
   * Un résultat sans `memberNumber` n'est jamais présenté comme un membre
   * valide : `GET /api/v1/users?q=` est générique et peut retourner des
   * non-adhérents.
   */
  selectMember(member: UserResponse): void {
    if (member.memberNumber === null) {
      return;
    }
    this.selectedMember.set(member);
  }

  changeMember(): void {
    this.selectedMember.set(null);
    this.memberSearchTerm.set('');
    this.memberSearchResults.set([]);
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

  cancel(): void {
    this.closed.emit();
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    const member = this.selectedMember();
    const title = this.selectedTitle();
    if (member === null || title === null) {
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);

    const request: CreateReservationRequest = { userId: member.id, titleId: title.id };
    this.reservationApiService.createReservation(request).subscribe({
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

  private runMemberSearch(value: string): void {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      this.memberSearchResults.set([]);
      this.memberSearching.set(false);
      this.memberSearchError.set(null);
      return;
    }
    this.memberSearching.set(true);
    this.memberSearchError.set(null);
    this.userApiService.listUsers(0, MEMBER_SEARCH_PAGE_SIZE, trimmed).subscribe({
      next: (response) => {
        this.memberSearchResults.set(response.content);
        this.memberSearching.set(false);
      },
      error: (err: unknown) => {
        this.memberSearching.set(false);
        this.memberSearchError.set(toAppError(err));
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

  private resetState(): void {
    this.memberSearchTerm.set('');
    this.memberSearchResults.set([]);
    this.memberSearching.set(false);
    this.memberSearchError.set(null);
    this.selectedMember.set(null);

    this.titleSearchTerm.set('');
    this.titleSearchResults.set([]);
    this.titleSearching.set(false);
    this.titleSearchError.set(null);
    this.selectedTitle.set(null);

    this.submitting.set(false);
    this.submitError.set(null);
  }
}
