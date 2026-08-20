import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

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
 * Amendes de l'utilisateur authentifié (DEV-09.12, `ROLE_MEMBER` via
 * `roleGuard` porté par la zone `/member` — restriction UX uniquement,
 * cf. `MemberLoansPage`/`MemberProfilePage`). Consultation strictement en
 * lecture seule via `FineApiService.listOwnFines` (`GET /api/v1/me/fines`,
 * ownership structurelle backend, aucun identifiant utilisateur envoyé) —
 * jamais `listFines` (staff, `FINE_READ`), aucun bouton d'action (aucune
 * Fine n'est jamais créée/payée/annulée par le membre lui-même,
 * `payment-confirmation`/`cancel` requièrent tous deux `FINE_MANAGE`,
 * DEV-09.10 §16). `fineStatus` affiché tel quel (`p-tag`), aucune
 * dérivation métier côté frontend, le backend reste l'autorité. N'affiche
 * pas `borrower` (redondant sur `/me/fines`, même raisonnement que
 * `MemberLoansPage` pour `LoanResponse.borrower`), historique complet
 * (`UNPAID`/`PAID`/`CANCELLED`) toujours visible, jamais filtré.
 */
@Component({
  selector: 'app-member-fines-page',
  imports: [TableModule, TagModule, CurrencyPipe, LoadingState, EmptyState, ErrorState],
  templateUrl: './member-fines-page.html',
  styleUrl: './member-fines-page.scss',
})
export class MemberFinesPage {
  private readonly fineApiService = inject(FineApiService);

  readonly rows = signal<FineResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — même principe que MemberLoansPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);

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

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.fineApiService.listOwnFines(page, size).subscribe({
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
