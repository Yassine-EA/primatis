/**
 * Voir `be.primatis.catalogue.dto.UpdateGenreRequest` côté backend. Même
 * mécanisme de PATCH sparse à trois états que `UpdateTitleRequest`.
 *
 * - `code`/`label` : `null`/vide explicite est rejeté par le backend
 *   (colonnes `NOT NULL`/`UNIQUE`) — pas de `| null` ici. Unicité
 *   revérifiée par le backend en cas de changement réel de valeur.
 * - `description` : `null` est accepté et efface la valeur — `| null`
 *   légitime.
 */
export interface UpdateGenreRequest {
  code?: string;
  label?: string;
  description?: string | null;
}
