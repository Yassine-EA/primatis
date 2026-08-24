import { ArticleStatus } from './article-status';
import { ArticleUserResponse } from './article-user-response';

/**
 * Voir `be.primatis.article.dto.StaffArticleSummaryResponse` côté backend
 * (DEV-11.12A) — contrat allégé pour `GET /api/v1/staff/articles`
 * (`ARTICLE_MANAGE`), tous statuts confondus. Distinct d'`ArticleSummaryResponse`
 * (public, structurellement `PUBLISHED`-only) : ici `articleStatus`/`updatedAt`
 * sont exposés (informatifs pour une gestion staff), et `publishedAt` reste
 * nullable (`null` pour un `DRAFT`). Volontairement absent par rapport à
 * `ArticleResponse` : `content`, `lastModifiedBy`, `tags` (aucun besoin
 * démontré pour une ligne de liste, disponibles via le détail).
 */
export interface StaffArticleSummaryResponse {
  id: number;
  title: string;
  summary: string | null;
  slug: string;
  articleStatus: ArticleStatus;
  author: ArticleUserResponse;
  publishedAt: string | null;
  updatedAt: string;
}
