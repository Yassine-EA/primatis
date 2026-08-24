/**
 * Voir `be.primatis.article.dto.UpdateTagRequest` côté backend. Même
 * mécanisme de PATCH sparse que `UpdateArticleRequest`/`UpdateGenreRequest`.
 *
 * Volontairement absent : `code` — contrairement à `UpdateGenreRequest`
 * (qui autorise un changement de `code`), `Tag.code` est structurellement
 * immuable après création (business-rules.md §7.13 : « stable ») ; ce
 * contrat ne porte donc aucun champ correspondant.
 *
 * - `label` : `null`/vide explicite est rejeté par le backend (colonne
 *   `NOT NULL`) — pas de `| null` ici.
 * - `description` (nullable en base) : `null` efface — `| null` légitime.
 */
export interface UpdateTagRequest {
  label?: string;
  description?: string | null;
}
