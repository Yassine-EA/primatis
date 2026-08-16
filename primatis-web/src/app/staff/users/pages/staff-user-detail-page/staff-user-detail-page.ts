import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { ResidenceResponse } from '../../../../user/models/residence-response';
import { UserResponse } from '../../../../user/models/user-response';
import { ResidenceApiService } from '../../../../user/services/residence-api.service';
import { UserApiService } from '../../../../user/services/user-api.service';

const CURRENT_RESIDENCE_NOT_FOUND_CODE = 'CURRENT_RESIDENCE_NOT_FOUND';
const INVALID_USER_ID_ERROR: AppError = { message: "Identifiant d'utilisateur invalide.", fieldErrors: [] };

/**
 * Détail Staff en lecture seule (DEV-05.11, `USER_READ`). Aucun contrôle
 * d'édition : toute modification (identité, rôles, `accountStatus`,
 * Membership) exige `USER_MANAGE`, réservé à DEV-05.12 (Admin). Résidence
 * affichée en lecture seule uniquement (édition différée, GAP-05.11-01 :
 * aucun référentiel City/Country consultable).
 */
@Component({
  selector: 'app-staff-user-detail-page',
  imports: [TagModule, LoadingState, EmptyState, ErrorState],
  templateUrl: './staff-user-detail-page.html',
  styleUrl: './staff-user-detail-page.scss',
})
export class StaffUserDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly userApiService = inject(UserApiService);
  private readonly residenceApiService = inject(ResidenceApiService);

  readonly user = signal<UserResponse | null>(null);
  readonly userLoading = signal(false);
  readonly userError = signal<AppError | null>(null);

  readonly currentResidence = signal<ResidenceResponse | null>(null);
  readonly residenceLoading = signal(false);
  readonly residenceError = signal<AppError | null>(null);

  readonly residenceHistory = signal<ResidenceResponse[]>([]);
  readonly historyLoading = signal(false);
  readonly historyError = signal<AppError | null>(null);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = parseUserId(params.get('id'));
      if (id === null) {
        this.user.set(null);
        this.userLoading.set(false);
        this.userError.set(INVALID_USER_ID_ERROR);
        return;
      }
      this.loadUser(id);
      this.loadCurrentResidence(id);
      this.loadResidenceHistory(id);
    });
  }

  private loadUser(id: number): void {
    this.userLoading.set(true);
    this.userError.set(null);
    this.userApiService.getUser(id).subscribe({
      next: (value) => {
        this.user.set(value);
        this.userLoading.set(false);
      },
      error: (err: unknown) => {
        this.userLoading.set(false);
        this.userError.set(toAppError(err));
      },
    });
  }

  private loadCurrentResidence(id: number): void {
    this.residenceLoading.set(true);
    this.residenceError.set(null);
    this.currentResidence.set(null);
    this.residenceApiService.getResidence(id).subscribe({
      next: (value) => {
        this.currentResidence.set(value);
        this.residenceLoading.set(false);
      },
      error: (err: unknown) => {
        this.residenceLoading.set(false);
        const appError = toAppError(err);
        if (appError.code === CURRENT_RESIDENCE_NOT_FOUND_CODE) {
          // État normal (DEV-05.11-DEC-05/06) : pas de résidence courante,
          // jamais un ErrorState pour ce cas précis.
          this.currentResidence.set(null);
          return;
        }
        this.residenceError.set(appError);
      },
    });
  }

  private loadResidenceHistory(id: number): void {
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.residenceApiService.getResidenceHistory(id).subscribe({
      next: (value) => {
        // Ordre backend conservé tel quel (startDate DESC) — jamais retrié.
        this.residenceHistory.set(value);
        this.historyLoading.set(false);
      },
      error: (err: unknown) => {
        this.historyLoading.set(false);
        this.historyError.set(toAppError(err));
      },
    });
  }
}

/**
 * `null` si l'identifiant de route est absent ou n'est pas un entier —
 * n'appelle alors jamais une API avec `NaN`.
 */
function parseUserId(rawId: string | null): number | null {
  if (rawId === null) {
    return null;
  }
  const id = Number(rawId);
  return Number.isInteger(id) ? id : null;
}
