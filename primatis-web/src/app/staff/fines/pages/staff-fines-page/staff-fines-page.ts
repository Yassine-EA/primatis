import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { AuthService } from '../../../../auth/services/auth.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { FineResponse } from '../../../../fines/models/fine-response';
import { FineStatus } from '../../../../fines/models/fine-status';
import { FineApiService } from '../../../../fines/services/fine-api.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';

const DEFAULT_PAGE_SIZE = 20;

/**
 * Consultation staff paginée de toutes les amendes (DEV-09.13,
 * `FINE_READ`, `GET /api/v1/fines`), confirmation du paiement externe
 * (`FINE_MANAGE`, `POST /api/v1/fines/{id}/payment-confirmation`) et
 * annulation (`FINE_MANAGE`, `POST /api/v1/fines/{id}/cancel`). Même
 * pattern principal que `StaffReservationsPage` pour la liste (`p-table`
 * lazy server-side, permission double lecture/gestion) et pour le
 * workflow confirmation/toast/remplacement de ligne
 * (`ConfirmationService`/`MessageService` globaux) ; précédent
 * `StaffLoansPage.confirmReturn` pour la confirmation systématique avant
 * mutation, même une action positive (DEV-09.10 §16). `FINE_READ`
 * conditionne déjà l'accès à la route entière (`permissionGuard`) ;
 * `FINE_MANAGE` est vérifiée ici uniquement pour révéler/masquer les
 * actions Confirmer paiement/Annuler — UX seulement, le backend reste
 * l'autorité (`@PreAuthorize` sur `FineService.confirmExternalPayment`/
 * `cancelFine`).
 *
 * <p>Contrairement à `MemberFinesPage`, le membre débiteur
 * ({@code fine.borrower}) est affiché : l'agent consulte les amendes de
 * tous les membres, cette information n'est ici jamais redondante. Aucun
 * appel complémentaire `User`/`Loan`/`Copy` : les résumés compacts déjà
 * imbriqués dans `FineResponse` suffisent.
 *
 * <p>Aucune création de Fine (DEV-DEC-0048, créée automatiquement au
 * retour tardif) : contrairement à `StaffLoansPage`/`StaffReservationsPage`,
 * cette page n'a aucun dialog de création ni bouton « Nouvelle amende ».
 * Deux actions indépendantes par ligne, visibles uniquement pour
 * `fineStatus === 'UNPAID'` (`isActionable`) et `canManageFines` :
 * `payingFineId`/`cancellingFineId` désactivent uniquement les deux
 * boutons de la ligne concernée pendant la requête en cours (protection
 * anti-double-clic UI, jamais un verrou global — la concurrence réelle
 * reste protégée côté backend, `PESSIMISTIC_WRITE` + revalidation post-lock,
 * DEV-09.7/09.8). Après succès, la ligne est remplacée par le
 * `FineResponse` exact renvoyé — jamais reconstruite localement.
 */
@Component({
  selector: 'app-staff-fines-page',
  imports: [TableModule, TagModule, ButtonModule, CurrencyPipe, LoadingState, EmptyState, ErrorState],
  templateUrl: './staff-fines-page.html',
  styleUrl: './staff-fines-page.scss',
})
export class StaffFinesPage {
  private readonly fineApiService = inject(FineApiService);
  private readonly authService = inject(AuthService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);

  readonly rows = signal<FineResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — même principe que StaffReservationsPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly payingFineId = signal<number | null>(null);
  readonly cancellingFineId = signal<number | null>(null);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    this.load(0, DEFAULT_PAGE_SIZE);
  }

  get canManageFines(): boolean {
    return this.authService.hasPermission('FINE_MANAGE');
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

  fineStatusSeverity(status: FineStatus): 'danger' | 'success' | 'secondary' {
    switch (status) {
      case 'UNPAID':
        return 'danger';
      case 'PAID':
        return 'success';
      case 'CANCELLED':
        return 'secondary';
    }
  }

  fineStatusLabel(status: FineStatus): string {
    switch (status) {
      case 'UNPAID':
        return 'Impayée';
      case 'PAID':
        return 'Payée';
      case 'CANCELLED':
        return 'Annulée';
    }
  }

  /**
   * Règle de visibilité UI, pas une règle métier : le statut affiché est
   * celui déjà reçu du backend, jamais recalculé.
   */
  isActionable(fine: FineResponse): boolean {
    return fine.fineStatus === 'UNPAID';
  }

  /** Anti-double-clic UI uniquement, propre à cette ligne. */
  isBusy(fine: FineResponse): boolean {
    return this.payingFineId() === fine.id || this.cancellingFineId() === fine.id;
  }

  confirmPaymentPrompt(fine: FineResponse): void {
    this.confirmationService.confirm({
      header: 'Confirmation du paiement',
      message: `Confirmer le paiement de l'amende de ${fine.borrower.firstName} ${fine.borrower.lastName} ?`,
      accept: () => this.performConfirmPayment(fine),
    });
  }

  confirmCancelPrompt(fine: FineResponse): void {
    this.confirmationService.confirm({
      header: 'Annulation',
      message: `Confirmer l'annulation de l'amende de ${fine.borrower.firstName} ${fine.borrower.lastName} ?`,
      accept: () => this.performCancel(fine),
    });
  }

  private performConfirmPayment(fine: FineResponse): void {
    this.payingFineId.set(fine.id);
    this.fineApiService.confirmPayment(fine.id).subscribe({
      next: (updated) => {
        this.payingFineId.set(null);
        this.rows.set(this.rows().map((row) => (row.id === updated.id ? updated : row)));
        this.messageService.add({
          severity: 'success',
          summary: 'Paiement confirmé',
          detail: `Le paiement de l'amende de ${updated.borrower.firstName} ${updated.borrower.lastName} a été confirmé.`,
        });
      },
      error: (err: unknown) => {
        this.payingFineId.set(null);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  private performCancel(fine: FineResponse): void {
    this.cancellingFineId.set(fine.id);
    this.fineApiService.cancelFine(fine.id).subscribe({
      next: (updated) => {
        this.cancellingFineId.set(null);
        this.rows.set(this.rows().map((row) => (row.id === updated.id ? updated : row)));
        this.messageService.add({
          severity: 'success',
          summary: 'Amende annulée',
          detail: `L'amende de ${updated.borrower.firstName} ${updated.borrower.lastName} a été annulée.`,
        });
      },
      error: (err: unknown) => {
        this.cancellingFineId.set(null);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.fineApiService.listFines(page, size).subscribe({
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
