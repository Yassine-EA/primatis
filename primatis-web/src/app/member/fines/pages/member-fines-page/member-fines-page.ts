import { Component, computed, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
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
import {
  fineStatusSeverity as sharedFineStatusSeverity,
  StatusTagSeverity,
} from '../../../../shared/status/status-severity';

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
  imports: [RouterLink, TableModule, TagModule, LoadingState, EmptyState, ErrorState],
  templateUrl: './member-fines-page.html',
  styleUrl: './member-fines-page.scss',
})
export class MemberFinesPage {
  private readonly fineApiService = inject(FineApiService);
  private readonly titleService = inject(Title);

  readonly rows = signal<FineResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — même principe que MemberLoansPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly unpaidCount = computed(
    () => this.rows().filter((fine) => fine.fineStatus === 'UNPAID').length,
  );
  readonly paidCount = computed(
    () => this.rows().filter((fine) => fine.fineStatus === 'PAID').length,
  );
  readonly cancelledCount = computed(
    () => this.rows().filter((fine) => fine.fineStatus === 'CANCELLED').length,
  );
  readonly unpaidAmount = computed(() =>
    this.rows()
      .filter((fine) => fine.fineStatus === 'UNPAID')
      .reduce((total, fine) => total + fine.amount, 0),
  );

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    this.titleService.setTitle('Mes amendes — PRIMATIS');
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

  fineStatusSeverity(status: FineStatus): StatusTagSeverity {
    return sharedFineStatusSeverity(status);
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

  formatAmount(value: number): string {
    return new Intl.NumberFormat('fr-BE', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }

  formatDate(value: string | null): string {
    if (!value) {
      return '—';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat('fr-BE', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC',
    })
      .format(date)
      .replace('.', '');
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
