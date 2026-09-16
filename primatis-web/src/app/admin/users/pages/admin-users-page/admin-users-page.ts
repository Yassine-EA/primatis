import { Component, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import {
  accountStatusSeverity as sharedAccountStatusSeverity,
  memberStatusSeverity as sharedMemberStatusSeverity,
  StatusTagSeverity,
} from '../../../../shared/status/status-severity';
import { AccountStatus } from '../../../../user/models/account-status';
import { MemberStatus } from '../../../../user/models/member-status';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';

const DEFAULT_PAGE_SIZE = 20;

/**
 * Liste Admin paginée server-side (DEV-05.12, `USER_MANAGE`). Même pattern
 * que {@code StaffUsersPage} (DEV-05.11) : aucune recherche/filtre/tri (le
 * backend n'expose que `page`/`size`), aucune colonne Roles (Décision 1 —
 * les rôles ne sont consultables/éditables que sur le détail). Seule action
 * de table : accès au détail, où vivent toutes les actions d'écriture.
 */
@Component({
  selector: 'app-admin-users-page',
  imports: [RouterLink, ButtonModule, TableModule, TagModule, LoadingState, EmptyState, ErrorState],
  templateUrl: './admin-users-page.html',
  styleUrl: './admin-users-page.scss',
})
export class AdminUsersPage {
  private readonly userApiService = inject(UserApiService);
  private readonly titleService = inject(Title);

  readonly rows = signal<UserResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), jamais par le déclenchement automatique
  // PrimeNG au montage — le tableau n'est monté qu'une fois des données ou
  // une erreur disponibles (cf. template), il ne peut donc pas déclencher
  // lui-même ce premier appel.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    this.titleService.setTitle('Administration des utilisateurs — PRIMATIS');
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

  memberStatusSeverity(status: MemberStatus): StatusTagSeverity {
    return sharedMemberStatusSeverity(status);
  }

  accountStatusSeverity(status: AccountStatus): StatusTagSeverity {
    return sharedAccountStatusSeverity(status);
  }

  // Même précédent exact que StaffUsersPage/MemberProfilePage (DEV-15.6/DEV-15.8).
  memberStatusLabel(status: MemberStatus): string {
    switch (status) {
      case 'ACTIVE':
        return 'Actif';
      case 'BLOCKED':
        return 'Bloqué';
      case 'EXPIRED':
        return 'Expiré';
    }
  }

  accountStatusLabel(status: AccountStatus): string {
    return status === 'ACTIVE' ? 'Actif' : 'Désactivé';
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.userApiService.listUsers(page, size).subscribe({
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
