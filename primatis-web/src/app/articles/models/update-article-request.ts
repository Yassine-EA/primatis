/**
 * Voir `be.primatis.article.dto.UpdateArticleRequest` côté backend. PATCH
 * sparse à trois états (absent = inchangé, présent+valeur = remplace,
 * présent+`null` = efface) — même mécanisme exact que `UpdateTitleRequest`/
 * `UpdateGenreRequest`. Applicable à un Article `DRAFT` ou `PUBLISHED`
 * (jamais `ARCHIVED`) sans jamais le faire repasser en `DRAFT`.
 *
 * - `title`/`content` : `null` explicite est rejeté par le backend (colonnes
 *   `NOT NULL`) — jamais une action valide, donc pas de `| null` ici.
 * - `summary` (nullable en base) : `null` efface — `| null` légitime.
 *
 * Volontairement absent : `slug` (stable, jamais régénéré), `articleStatus`
 * (actions dédiées `publish`/`archive`), `author`/`lastModifiedBy`/
 * `publishedAt`/`createdAt`/`updatedAt`.
 */
export interface UpdateArticleRequest {
  title?: string;
  content?: string;
  summary?: string | null;
}
