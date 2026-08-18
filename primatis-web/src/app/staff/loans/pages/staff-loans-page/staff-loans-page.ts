import { Component, inject, signal } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { AuthService } from '../../../../auth/services/auth.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { LoanResponse } from '../../../../loans/models/loan-response';
import { LoanStatus } from '../../../../loans/models/loan-status';
import { LoanApiService } from '../../../../loans/services/loan-api.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';

const DEFAULT_PAGE_SIZE = 20;
const OPEN_LOAN_STATUSES: readonly LoanStatus[] = ['ACTIVE', 'OVERDUE'];

/**
 * Consultation staff paginée de tous les prêts (DEV-07.9, `LOAN_READ`,
 * `GET /api/v1/loans`) + retour d'un prêt ouvert (`LOAN_MANAGE`,
 * `POST /api/v1/loans/{id}/return`). Même pattern que `StaffUsersPage`
 * pour la liste (`p-table` lazy server-side) et que
 * `AdminUserDetailPage` pour le workflow confirmation/retour
 * (`ConfirmationService` global + `MessageService` global, aucun Dialog
 * local). `LOAN_READ` conditionne déjà l'accès à la route entière
 * (`permissionGuard`) ; `LOAN_MANAGE` est vérifiée ici uniquement pour
 * révéler/masquer l'action Retour — UX seulement, le backend reste
 * l'autorité (`@PreAuthorize` sur `LoanService.registerReturn`).
 *
 * **Aucune création de prêt** : DEV-07.9 a confirmé qu'aucune API
 * frontend/backend réelle ne permet de rechercher un emprunteur
 * (`UserApiService.listUsers`/`GET /api/v1/users` : pagination brute
 * `page`/`size` uniquement, aucun paramètre de recherche) — un input
 * brut de `borrowerUserId`, un chargement arbitraire de tous les
 * membres, ou un faux autocomplete sur une seule page sont tous
 * explicitement interdits. `GAP-07.9-01` documenté dans le log DEV-07.9.
 *
 * Aucune règle métier Loan recalculée ici : `loanStatus`, `dueDate`,
 * `returnDate` proviennent exclusivement de la réponse backend — après
 * un retour réussi, la ligne est remplacée par le `LoanResponse` exact
 * renvoyé par `registerReturn`, jamais recalculée côté frontend.
 */
@Component({
  selector: 'app-staff-loans-page',
  imports: [TableModule, TagModule, ButtonModule, LoadingState, EmptyState, ErrorState],
  templateUrl: './staff-loans-page.html',
  styleUrl: './staff-loans-page.scss',
})
export class StaffLoansPage {
  private readonly loanApiService = inject(LoanApiService);
  private readonly authService = inject(AuthService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);

  readonly rows = signal<LoanResponse[]>([]);
  readonly totalRecords = signal(0);
  // Même principe que StaffUsersPage/MemberLoansPage : premier chargement
  // déclenché explicitement dans le constructeur, jamais par le montage
  // automatique PrimeNG (le tableau n'est monté qu'une fois des données ou
  // une erreur disponibles, cf. template).
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly returningLoanId = signal<number | null>(null);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    this.load(0, DEFAULT_PAGE_SIZE);
  }

  get canManageLoans(): boolean {
    return this.authService.hasPermission('LOAN_MANAGE');
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

  isReturnable(loan: LoanResponse): boolean {
    return OPEN_LOAN_STATUSES.includes(loan.loanStatus);
  }

  confirmReturn(loan: LoanResponse): void {
    this.confirmationService.confirm({
      header: 'Retour',
      message: `Confirmer le retour de l'exemplaire ${loan.copy.inventoryCode} ?`,
      accept: () => this.performReturn(loan),
    });
  }

  private performReturn(loan: LoanResponse): void {
    this.returningLoanId.set(loan.id);
    this.loanApiService.registerReturn(loan.id).subscribe({
      next: (updated) => {
        this.returningLoanId.set(null);
        this.rows.set(this.rows().map((candidate) => (candidate.id === updated.id ? updated : candidate)));
        this.messageService.add({
          severity: 'success',
          summary: 'Prêt retourné',
          detail: `Le retour de l'exemplaire ${updated.copy.inventoryCode} a été enregistré.`,
        });
      },
      error: (err: unknown) => {
        this.returningLoanId.set(null);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.loanApiService.listLoans(page, size).subscribe({
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
