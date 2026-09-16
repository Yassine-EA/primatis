import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { PaginatorModule } from 'primeng/paginator';
import { PaginatorState } from 'primeng/types/paginator';

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
 * toute recherche/filtre par Tag public en V1. Tri (`publishedAt DESC, id
 * DESC`) imposé côté backend : aucun contrôle de tri ici, aucun paramètre
 * envoyé. Même précédent structurel que `CataloguePage` (DESIGN-V2-B2)
 * pour la pagination server-side (`p-paginator`, plus de `p-table`).
 *
 * Vignette (DEV-ARTICLES-MEDIA-THUMBNAIL) : carte vedette et cartes de
 * grille utilisent `imageUrl` (première image réelle du contenu, calculée
 * backend) quand elle existe (`cardImageSrc`/`cardImageAlt`), sinon le
 * fallback décoratif `articleVisual` inchangé. La liste « Derniers
 * articles » de la barre latérale reste volontairement sur ce même
 * fallback décoratif uniquement — hors périmètre explicite de ce
 * correctif (mission DEV-ARTICLES-MEDIA-THUMBNAIL §3, qui n'énumère que
 * la carte vedette et les cartes de grille).
 *
 * Article vedette (DESIGN-V2-B4 §8) : le premier élément de la **première
 * page uniquement** — le tri backend étant déjà `publishedAt DESC`, ce
 * premier élément est réellement "le plus récent", jamais un statut
 * FEATURED fabriqué. Sur une page suivante, aucune mise en avant n'a de
 * sens ("le plus récent parmi ceux-ci" ne serait pas honnête) : la grille
 * affiche alors tous les éléments de la page sans vedette.
 */
@Component({
  selector: 'app-article-list-page',
  imports: [RouterLink, DatePipe, PaginatorModule, LoadingState, EmptyState, ErrorState],
  templateUrl: './article-list-page.html',
  styleUrl: './article-list-page.scss',
})
export class ArticleListPage {
  private readonly articleApiService = inject(ArticleApiService);
  private readonly titleService = inject(Title);

  readonly rows = signal<ArticleSummaryResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), même précédent que CataloguePage/StaffUsersPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  // `computed` plutôt qu'un getter : réévalué automatiquement quand `rows`
  // change, sans dépendre d'un cycle de détection déclenché ailleurs.
  private readonly onFirstPage = signal(true);

  readonly featured = computed<ArticleSummaryResponse | null>(() => {
    const list = this.rows();
    return this.onFirstPage() && list.length > 0 ? list[0] : null;
  });

  readonly gridRows = computed<ArticleSummaryResponse[]>(() => {
    const list = this.rows();
    return this.onFirstPage() ? list.slice(1) : list;
  });

  readonly latestArticles = computed<ArticleSummaryResponse[]>(() =>
    this.onFirstPage() ? this.rows().slice(1, 5) : [],
  );

  constructor() {
    this.titleService.setTitle('Articles — PRIMATIS');
    this.load(0, DEFAULT_PAGE_SIZE);
  }

  onPageChange(event: PaginatorState): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  currentFirst(): number {
    return this.lastPage * this.lastSize;
  }

  currentRows(): number {
    return this.lastSize;
  }

  authorName(author: ArticleSummaryResponse['author']): string {
    return `${author.firstName} ${author.lastName}`;
  }

  articleVisual(article: ArticleSummaryResponse, size: '640' | '960'): string {
    const variants = ['cosmos', 'library', 'observatory'] as const;
    const index = Math.abs(article.id) % variants.length;
    return `/assets/public/articles/cards/article-fallback-${variants[index]}-${size}.webp`;
  }

  /**
   * Vignette de carte (DEV-ARTICLES-MEDIA-THUMBNAIL) : la première image
   * réellement insérée dans `content` (`imageUrl`, calculée backend) si
   * elle existe, sinon le fallback décoratif actuel inchangé
   * (`articleVisual`). Jamais de parsing HTML côté Angular — `imageUrl` est
   * utilisée telle quelle.
   */
  cardImageSrc(article: ArticleSummaryResponse, size: '640' | '960'): string {
    return article.imageUrl ?? this.articleVisual(article, size);
  }

  /**
   * Une image réelle d'article est informative (`alt` cohérent, jamais
   * masquée aux technologies d'assistance) ; le fallback décoratif reste
   * `alt=""` (voir gabarit, `aria-hidden="true"` conditionnel).
   */
  cardImageAlt(article: ArticleSummaryResponse): string {
    return article.imageUrl ? `Illustration de l’article « ${article.title} »` : '';
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
        this.onFirstPage.set(page === 0);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
