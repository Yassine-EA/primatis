import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';

import { AppError } from '../../../core/errors/api-error';
import { toAppError } from '../../../core/errors/api-error.util';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import { ArticleSummaryResponse } from '../../models/article-summary-response';
import { ArticleApiService } from '../../services/article-api.service';

const DEFAULT_PAGE_SIZE = 20;

/**
 * Liste publique des Articles (DEV-11.11, `GET /api/v1/articles`, surface
 * `permitAll`) : uniquement les Articles `PUBLISHED` (imposé par le
 * backend, jamais reconstruit ici). Aucun filtre — DEV-DEC-0061 interdit
 * toute recherche/filtre par Tag public en V1 (contrairement à
 * `CataloguePage`, aucun `filtersForm`). Tri (`publishedAt DESC, id DESC`)
 * imposé côté backend : aucun contrôle de tri ici. Même précédent
 * structurel exact que `CataloguePage` (DEV-06.8) pour le state management
 * (Signals) et la pagination server-side (`p-table` lazy).
 */
@Component({
  selector: 'app-article-list-page',
  imports: [RouterLink, TableModule, LoadingState, EmptyState, ErrorState],
  templateUrl: './article-list-page.html',
  styleUrl: './article-list-page.scss',
})
export class ArticleListPage {
  private readonly articleApiService = inject(ArticleApiService);

  readonly rows = signal<ArticleSummaryResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), même précédent que CataloguePage/StaffUsersPage.
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
   * propager `undefined` vers le backend (même précédent CataloguePage).
   */
  onLazyLoad(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  authorName(author: ArticleSummaryResponse['author']): string {
    return `${author.firstName} ${author.lastName}`;
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.articleApiService.listPublishedArticles(page, size).subscribe({
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
