import { Component, inject, signal } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { AuthService } from '../../../../auth/services/auth.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { ReservationResponse } from '../../../../reservations/models/reservation-response';
import { ReservationStatus } from '../../../../reservations/models/reservation-status';
import { ReservationApiService } from '../../../../reservations/services/reservation-api.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { StaffReservationCreateDialog } from '../../components/staff-reservation-create-dialog/staff-reservation-create-dialog';

const DEFAULT_PAGE_SIZE = 20;
const CANCELLABLE_STATUSES: readonly ReservationStatus[] = ['WAITING', 'READY'];

/**
 * Consultation staff paginée de toutes les réservations (DEV-08.12,
 * `RESERVATION_READ`, `GET /api/v1/reservations`) et annulation
 * (`RESERVATION_MANAGE`, `POST /api/v1/reservations/{id}/cancel`). Même
 * pattern principal que `StaffLoansPage` pour la liste (`p-table` lazy
 * server-side, permission double lecture/gestion) et pour le workflow
 * confirmation/toast/remplacement de ligne
 * (`ConfirmationService`/`MessageService` globaux) ; contrat Reservation
 * repris tel quel de `MemberReservationsPage` (DEV-08.10). `RESERVATION_READ`
 * conditionnera l'accès à la route entière une fois branchée (DEV-08.14,
 * `permissionGuard`) ; `RESERVATION_MANAGE` est vérifiée ici uniquement
 * pour révéler/masquer l'action Annuler — UX seulement, le backend reste
 * l'autorité (`@PreAuthorize` sur `ReservationService.cancelReservation`).
 *
 * <p>Contrairement à `MemberReservationsPage`, le membre demandeur
 * ({@code reservation.member}) est affiché : l'agent consulte les
 * réservations de tous les membres, cette information n'est ici jamais
 * redondante. Aucun appel complémentaire `User`/`Title`/`Copy` : les
 * résumés compacts déjà imbriqués dans `ReservationResponse` suffisent.
 *
 * <p>Annulation : `ReservationApiService.cancelReservation` uniquement
 * (jamais `cancelOwnReservation`, self-service). Bouton Annuler visible
 * uniquement si {@code canManageReservations} **et**
 * {@code reservationStatus} vaut `WAITING`/`READY` — règle de visibilité
 * UI basée sur le statut serveur déjà reçu, jamais un recalcul métier
 * (`RESERVATION_NOT_CANCELLABLE` reste possible côté backend en cas de
 * changement d'état concurrent). Après succès, la ligne est remplacée
 * par le `ReservationResponse` exact renvoyé — jamais reconstruite
 * localement (`assignedCopy`/`expirationDate` conservés sur
 * `READY → CANCELLED`, DEV-DEC-0038, même principe exact que
 * `MemberReservationsPage`).
 *
 * <p><b>Création</b> (DEV-08.13) : intégration minimale de
 * {@link StaffReservationCreateDialog}, visible uniquement avec
 * {@code canManageReservations} (même garde que l'action Annuler). Après
 * création réussie ({@link #onReservationCreated}), la page **courante**
 * est rechargée — jamais la page 0 — même choix exact que
 * `StaffLoansPage.onLoanCreated` : contrairement au membre self-service
 * (DEV-08.11, qui vient de créer sa propre réservation et attend de la
 * voir immédiatement), un agent staff créant une réservation pour un
 * tiers ne consulte pas nécessairement la première page ensuite — aucun
 * motif réel de dévier du précédent `StaffLoansPage` déjà établi. Aucune
 * insertion locale devinée : le contenu affiché provient exclusivement
 * de la réponse serveur de `listReservations`.
 *
 * <p>Aucune recherche membre/Title dans cette page (portée exclusivement
 * par le dialog), aucun countdown ni calcul d'expiration (DEV-DEC-0044) :
 * `expirationDate`/`reservationDate` affichées telles que reçues.
 */
@Component({
  selector: 'app-staff-reservations-page',
  imports: [
    TableModule,
    TagModule,
    ButtonModule,
    LoadingState,
    EmptyState,
    ErrorState,
    StaffReservationCreateDialog,
  ],
  templateUrl: './staff-reservations-page.html',
  styleUrl: './staff-reservations-page.scss',
})
export class StaffReservationsPage {
  private readonly reservationApiService = inject(ReservationApiService);
  private readonly authService = inject(AuthService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);

  readonly rows = signal<ReservationResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — même principe que StaffLoansPage/MemberReservationsPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly cancellingReservationId = signal<number | null>(null);
  readonly createDialogVisible = signal(false);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    this.load(0, DEFAULT_PAGE_SIZE);
  }

  get canManageReservations(): boolean {
    return this.authService.hasPermission('RESERVATION_MANAGE');
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
   * celui déjà reçu du backend, jamais recalculé.
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
   * `createReservation`) n'est pas inséré localement : la page courante
   * est rechargée — voir Javadoc de la classe pour la justification du
   * choix « page courante » plutôt que « page 0 ».
   */
  onReservationCreated(reservation: ReservationResponse): void {
    this.createDialogVisible.set(false);
    this.load(this.lastPage, this.lastSize);
  }

  confirmCancel(reservation: ReservationResponse): void {
    this.confirmationService.confirm({
      header: 'Annulation',
      message: `Confirmer l'annulation de la réservation de ${reservation.member.firstName} ${reservation.member.lastName} pour « ${reservation.title.title} » ?`,
      accept: () => this.performCancel(reservation),
    });
  }

  private performCancel(reservation: ReservationResponse): void {
    this.cancellingReservationId.set(reservation.id);
    this.reservationApiService.cancelReservation(reservation.id).subscribe({
      next: (updated) => {
        this.cancellingReservationId.set(null);
        this.rows.set(this.rows().map((row) => (row.id === updated.id ? updated : row)));
        this.messageService.add({
          severity: 'success',
          summary: 'Réservation annulée',
          detail: `La réservation de ${updated.member.firstName} ${updated.member.lastName} pour « ${updated.title.title} » a été annulée.`,
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

    this.reservationApiService.listReservations(page, size).subscribe({
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
