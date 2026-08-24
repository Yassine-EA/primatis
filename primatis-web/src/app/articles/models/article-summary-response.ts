import { ArticleUserResponse } from './article-user-response';

/**
 * Voir `be.primatis.article.dto.ArticleSummaryResponse` côté backend —
 * contrat allégé pour `GET /api/v1/articles` (liste publique). Volontairement
 * absent par rapport à `ArticleResponse` : `content`, `lastModifiedBy`,
 * `tags`, `articleStatus`/`createdAt`/`updatedAt` (cette liste n'expose
 * jamais que des Articles `PUBLISHED`, voir la Javadoc backend).
 *
 * `publishedAt` est typé non nullable ici (contrairement à `ArticleResponse`) :
 * cet endpoint n'expose structurellement que des Articles `PUBLISHED`, et
 * `ck_article_published_at_consistency` (V001) garantit `published_at NOT
 * NULL` pour ce statut — jamais un simple raccourci de typage.
 */
export interface ArticleSummaryResponse {
  id: number;
  title: string;
  summary: string | null;
  slug: string;
  author: ArticleUserResponse;
  publishedAt: string;
}
