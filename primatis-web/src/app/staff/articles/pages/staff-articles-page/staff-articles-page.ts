import { Component, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import {
  articleStatusSeverity as sharedArticleStatusSeverity,
  StatusTagSeverity,
} from '../../../../shared/status/status-severity';
import { ArticleStatus } from '../../../../articles/models/article-status';
import { StaffArticleSummaryResponse } from '../../../../articles/models/staff-article-summary-response';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';

const DEFAULT_PAGE_SIZE = 20;

/**
 * Liste staff paginée server-side des Articles (DEV-11.12, `ARTICLE_MANAGE`,
 * `GET /api/v1/staff/articles`, DEV-11.12A) — tous statuts confondus
 * (`DRAFT`/`PUBLISHED`/`ARCHIVED`), contrairement à `ArticleListPage`
 * (public, `PUBLISHED` uniquement). Aucun filtre (le backend n'en expose
 * aucun, tri fixe `updatedAt DESC, id DESC`) — même précédent structurel que
 * `StaffLoansPage`/`StaffReservationsPage` (liste sans `filtersForm`),
 * contrairement à `StaffCataloguePage` qui en a un.
 */
@Component({
  selector: 'app-staff-articles-page',
  imports: [RouterLink, ButtonModule, TableModule, TagModule, LoadingState, EmptyState, ErrorState],
  templateUrl: './staff-articles-page.html',
  styleUrl: './staff-articles-page.scss',
})
export class StaffArticlesPage {
  private readonly staffArticleApiService = inject(StaffArticleApiService);
  private readonly titleService = inject(Title);

  readonly rows = signal<StaffArticleSummaryResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    this.titleService.setTitle('Articles — PRIMATIS');
    this.load(0, DEFAULT_PAGE_SIZE);
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  authorName(author: StaffArticleSummaryResponse['author']): string {
    return `${author.firstName} ${author.lastName}`;
  }

  articleStatusSeverity(status: ArticleStatus): StatusTagSeverity {
    return sharedArticleStatusSeverity(status);
  }

  articleStatusLabel(status: ArticleStatus): string {
    switch (status) {
      case 'DRAFT':
        return 'Brouillon';
      case 'PUBLISHED':
        return 'Publié';
      case 'ARCHIVED':
        return 'Archivé';
    }
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.staffArticleApiService.listStaffArticles(page, size).subscribe({
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
