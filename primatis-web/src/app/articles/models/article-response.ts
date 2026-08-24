import { ArticleStatus } from './article-status';
import { ArticleUserResponse } from './article-user-response';
import { TagResponse } from './tag-response';

/**
 * Voir `be.primatis.article.dto.ArticleResponse` côté backend — contrat de
 * détail, réutilisé à la fois par la consultation publique (`GET
 * /api/v1/articles/{slug}`, toujours `PUBLISHED`) et par les mutations
 * staff (`POST`/`PATCH .../articles(/{id})`, tout statut). `publishedAt` :
 * `null` tant que l'Article reste `DRAFT` (contrairement à
 * `ArticleSummaryResponse`, structurellement `PUBLISHED`-only). `content`
 * est du HTML déjà sanitisé backend — jamais rendu ici (DEV-11.11).
 */
export interface ArticleResponse {
  id: number;
  title: string;
  content: string;
  summary: string | null;
  slug: string;
  articleStatus: ArticleStatus;
  author: ArticleUserResponse;
  lastModifiedBy: ArticleUserResponse | null;
  tags: TagResponse[];
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
