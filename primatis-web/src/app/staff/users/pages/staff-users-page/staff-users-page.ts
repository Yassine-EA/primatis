import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { MemberStatus } from '../../../../user/models/member-status';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';

const DEFAULT_PAGE_SIZE = 20;

/**
 * Liste Staff paginée server-side (DEV-05.11, `USER_READ`). Aucune
 * recherche/filtre/tri : le backend n'en expose pas (`page`/`size`
 * uniquement) — ne jamais simuler ces capacités sur la seule page chargée.
 * Lecture seule stricte : aucune action `USER_MANAGE`.
 */
@Component({
  selector: 'app-staff-users-page',
  imports: [RouterLink, TableModule, TagModule, LoadingState, EmptyState, ErrorState],
  templateUrl: './staff-users-page.html',
  styleUrl: './staff-users-page.scss',
})
export class StaffUsersPage {
  private readonly userApiService = inject(UserApiService);

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

  memberStatusSeverity(status: MemberStatus): 'success' | 'danger' | 'warn' {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'BLOCKED':
        return 'danger';
      case 'EXPIRED':
        return 'warn';
    }
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
