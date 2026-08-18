import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../core/errors/api-error';
import { toAppError } from '../../../core/errors/api-error.util';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import { TitleDetailResponse } from '../../models/title-detail-response';
import { CatalogueApiService } from '../../services/catalogue-api.service';

const INVALID_TITLE_ID_ERROR: AppError = { message: 'Identifiant de titre invalide.', fieldErrors: [] };

/**
 * Détail public d'un Title (DEV-06.8, `GET /api/v1/titles/{id}`, surface
 * `permitAll`). Le 404 `TITLE_NOT_FOUND` est traité comme une erreur
 * ordinaire (`ErrorState`), jamais comme un état vide : le backend masque
 * volontairement un Title inexistant et un Title `WITHDRAWN` derrière le
 * même 404 (DEV-06.4) — cette distinction n'a pas à être reconstruite côté
 * frontend. Aucun `Copy`/disponibilité affiché : `CopyApiService` reste
 * staff-only (`COPY_READ`/`COPY_MANAGE`), aucun contrat public n'existe.
 */
@Component({
  selector: 'app-title-detail-page',
  imports: [TagModule, LoadingState, ErrorState],
  templateUrl: './title-detail-page.html',
  styleUrl: './title-detail-page.scss',
})
export class TitleDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly catalogueApiService = inject(CatalogueApiService);

  readonly title = signal<TitleDetailResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<AppError | null>(null);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = parseTitleId(params.get('id'));
      if (id === null) {
        this.title.set(null);
        this.loading.set(false);
        this.error.set(INVALID_TITLE_ID_ERROR);
        return;
      }
      this.loadTitle(id);
    });
  }

  authorNames(detail: TitleDetailResponse): string {
    return detail.authors.map((author) => author.fullName).join(', ');
  }

  private loadTitle(id: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.catalogueApiService.getTitleById(id).subscribe({
      next: (value) => {
        this.title.set(value);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}

/**
 * `null` si l'identifiant de route est absent ou n'est pas un entier —
 * n'appelle alors jamais une API avec `NaN` (même précédent StaffUserDetailPage).
 */
function parseTitleId(rawId: string | null): number | null {
  if (rawId === null) {
    return null;
  }
  const id = Number(rawId);
  return Number.isInteger(id) ? id : null;
}
