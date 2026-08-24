/**
 * Voir `be.primatis.article.dto.CreateArticleRequest` côté backend.
 * Volontairement absent : `id`, `slug` (généré backend), `articleStatus`
 * (toujours `DRAFT` à la création), `authorUser`/`lastModifiedBy` (dérivés
 * de l'identité authentifiée backend), `publishedAt`/`createdAt`/`updatedAt`,
 * `tagIds` (aucune association de Tag à la création — DEV-DEC-0060,
 * association traitée séparément via `updateArticleTags`).
 */
export interface CreateArticleRequest {
  title: string;
  content: string;
  summary?: string;
}
