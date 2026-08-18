import { Component, inject, signal } from '@angular/core';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { LoanResponse } from '../../../../loans/models/loan-response';
import { LoanStatus } from '../../../../loans/models/loan-status';
import { LoanApiService } from '../../../../loans/services/loan-api.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';

const DEFAULT_PAGE_SIZE = 20;

/**
 * Prêts de l'utilisateur authentifié (DEV-07.8, `ROLE_MEMBER` via
 * `roleGuard` porté par la zone `/member` — restriction UX uniquement,
 * cf. `MemberProfilePage`). Consultation strictement en lecture seule via
 * `LoanApiService.listOwnLoans` (`GET /api/v1/me/loans`, ownership
 * structurelle backend, aucun identifiant utilisateur envoyé) — jamais
 * `listLoans` (staff, `LOAN_READ`), jamais `registerLoan`/`registerReturn`
 * (`LOAN_MANAGE`), aucun bouton d'action. `loanStatus` affiché tel quel :
 * aucune dérivation `OVERDUE` côté frontend, le backend reste l'autorité.
 * N'affiche ni `borrower` (redondant sur `/me/loans`), ni le titre de
 * l'ouvrage (absent de `LoanCopyResponse`, aucun lookup catalogue N+1
 * improvisé — `GAP-07.8-01`).
 */
@Component({
  selector: 'app-member-loans-page',
  imports: [TableModule, TagModule, LoadingState, EmptyState, ErrorState],
  templateUrl: './member-loans-page.html',
  styleUrl: './member-loans-page.scss',
})
export class MemberLoansPage {
  private readonly loanApiService = inject(LoanApiService);

  readonly rows = signal<LoanResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — le tableau n'est monté qu'une fois des données ou
  // une erreur disponibles (cf. template), il ne peut donc pas déclencher
  // lui-même ce premier appel. Même principe que StaffUsersPage.
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

  loanStatusSeverity(status: LoanStatus): 'success' | 'danger' | 'secondary' {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'OVERDUE':
        return 'danger';
      case 'RETURNED':
        return 'secondary';
    }
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.loanApiService.listOwnLoans(page, size).subscribe({
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
