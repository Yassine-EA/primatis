import { Component, inject, signal } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { ReservationResponse } from '../../../../reservations/models/reservation-response';
import { ReservationStatus } from '../../../../reservations/models/reservation-status';
import { ReservationApiService } from '../../../../reservations/services/reservation-api.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { ReservationCreateDialog } from '../../components/reservation-create-dialog/reservation-create-dialog';

const DEFAULT_PAGE_SIZE = 20;
const CANCELLABLE_STATUSES: readonly ReservationStatus[] = ['WAITING', 'READY'];

/**
 * Réservations de l'utilisateur authentifié (DEV-08.10, `ROLE_MEMBER` via
 * `roleGuard` porté par la zone `/member` — restriction UX uniquement,
 * même précédent que `MemberLoansPage`). Consultation via
 * `ReservationApiService.listOwnReservations` (`GET /api/v1/me/reservations`,
 * ownership structurelle backend, aucun identifiant utilisateur envoyé) —
 * jamais `listReservations` (staff, `RESERVATION_READ`). N'affiche jamais
 * son propre nom (`reservation.member`) : redondant sur sa propre
 * consultation, même principe que `MemberLoansPage` qui n'affiche pas
 * `borrower`.
 *
 * <p>Composition de deux patterns existants (DEV-08.10, mission §2) :
 * consultation self-service paginée (`MemberLoansPage` — loading/empty/
 * error/table lazy) + action de ligne avec confirmation et toast
 * (`StaffLoansPage.confirmReturn`/`performReturn`, adapté au self-service
 * — pas de vérification de permission, l'ownership vient du endpoint
 * `/me/**` lui-même). Premier écran self-service du codebase combinant
 * ces deux dimensions.
 *
 * <p>Bouton Annuler visible uniquement pour `WAITING`/`READY`
 * ({@link #isCancellable}) : règle de visibilité UI basée sur le statut
 * serveur déjà reçu, jamais un recalcul métier — le backend reste seul
 * juge (`RESERVATION_NOT_CANCELLABLE` possible même si le bouton était
 * visible, en cas de changement d'état concurrent entre l'affichage et
 * l'appel). Après annulation, la ligne est remplacée par le
 * `ReservationResponse` exact renvoyé par `cancelOwnReservation` — jamais
 * reconstruite localement (`assignedCopy`/`expirationDate` conservés tels
 * quels par le backend sur `READY → CANCELLED`, DEV-DEC-0038 : la ligne
 * affichée doit refléter cette conservation, pas la masquer).
 *
 * <p><b>Création</b> (DEV-08.11, DEV-DEC-0045/OD-DEV08-FE-06 — incrément
 * séparé de la consultation/annulation ci-dessus) : intégration minimale
 * de {@link ReservationCreateDialog}, seul composant responsable du
 * formulaire/de la recherche Title/du toast succès (un seul responsable
 * du feedback succès, mission §12 — cette page ne duplique jamais le
 * toast déjà émis par le dialog). Après création réussie
 * ({@link #onReservationCreated}), rechargement **page 0** (pas la page
 * courante) : le tri serveur (`reservationDate DESC, id DESC`,
 * DEV-DEC-0036) place systématiquement la nouvelle réservation en tête
 * du dataset, donc sur la première page — revenir à la page 0 la rend
 * immédiatement visible à l'utilisateur qui vient de la créer, cohérent
 * avec l'intention de l'action (contrairement à `StaffLoansPage.onLoanCreated`,
 * qui recharge la page couramment affichée : un membre qui vient de
 * créer une réservation attend de la voir tout de suite, alors qu'un
 * agent créant un prêt pour un tiers ne consulte pas nécessairement la
 * page 0 ensuite — choix UX local, pas une règle métier, aucun DEV-DEC
 * nécessaire). Aucune insertion locale devinée : le contenu affiché
 * provient exclusivement de la réponse serveur de `listOwnReservations`.
 *
 * <p>Aucun countdown ni calcul d'expiration (DEV-DEC-0044/OD-DEV08-FE-05) :
 * `expirationDate`/`reservationDate` affichées telles que reçues.
 */
@Component({
  selector: 'app-member-reservations-page',
  imports: [TableModule, TagModule, ButtonModule, LoadingState, EmptyState, ErrorState, ReservationCreateDialog],
  templateUrl: './member-reservations-page.html',
  styleUrl: './member-reservations-page.scss',
})
export class MemberReservationsPage {
  private readonly reservationApiService = inject(ReservationApiService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);

  readonly rows = signal<ReservationResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — même principe que MemberLoansPage/StaffLoansPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly cancellingReservationId = signal<number | null>(null);
  readonly createDialogVisible = signal(false);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    this.load(0, DEFAULT_PAGE_SIZE);
  }

  /**
   * `event.first`/`event.rows` sont optionnels dans le typage PrimeNG :
   * toujours retomber sur des valeurs par défaut explicites plutôt que de
   * propager `undefined` vers le backend.
   */
  onLazyLoad(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  reservationStatusSeverity(status: ReservationStatus): 'info' | 'success' | 'secondary' | 'danger' | 'warn' {
    switch (status) {
      case 'WAITING':
        return 'info';
      case 'READY':
        return 'success';
      case 'FULFILLED':
        return 'secondary';
      case 'CANCELLED':
        return 'danger';
      case 'EXPIRED':
        return 'warn';
    }
  }

  /**
   * Règle de visibilité UI, pas une règle métier : le statut affiché est
   * celui déjà reçu du backend, jamais recalculé (mission §8).
   */
  isCancellable(reservation: ReservationResponse): boolean {
    return CANCELLABLE_STATUSES.includes(reservation.reservationStatus);
  }

  openCreateDialog(): void {
    this.createDialogVisible.set(true);
  }

  closeCreateDialog(): void {
    this.createDialogVisible.set(false);
  }

  /**
   * `reservation` (le `ReservationResponse` exact renvoyé par
   * `createOwnReservation`) n'est pas inséré localement : la page revient
   * à la page 0 et se recharge depuis le serveur, cohérente avec le tri
   * `reservationDate DESC` (voir Javadoc de la classe).
   */
  onReservationCreated(reservation: ReservationResponse): void {
    this.createDialogVisible.set(false);
    this.load(0, this.lastSize);
  }

  confirmCancel(reservation: ReservationResponse): void {
    this.confirmationService.confirm({
      header: 'Annulation',
      message: `Confirmer l'annulation de la réservation de « ${reservation.title.title} » ?`,
      accept: () => this.performCancel(reservation),
    });
  }

  private performCancel(reservation: ReservationResponse): void {
    this.cancellingReservationId.set(reservation.id);
    this.reservationApiService.cancelOwnReservation(reservation.id).subscribe({
      next: (updated) => {
        this.cancellingReservationId.set(null);
        this.rows.set(this.rows().map((row) => (row.id === updated.id ? updated : row)));
        this.messageService.add({
          severity: 'success',
          summary: 'Réservation annulée',
          detail: `La réservation de « ${updated.title.title} » a été annulée.`,
        });
      },
      error: (err: unknown) => {
        this.cancellingReservationId.set(null);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.reservationApiService.listOwnReservations(page, size).subscribe({
      next: (response) => {
        this.rows.set(response.content);
        this.totalRecords.set(response.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
