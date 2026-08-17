/**
 * Voir `be.primatis.catalogue.dto.UpdateAuthorRequest` côté backend. Même
 * mécanisme de PATCH sparse à trois états que `UpdateTitleRequest`.
 *
 * - `fullName` : `null` explicite est rejeté par le backend (colonne
 *   `NOT NULL`) — jamais une action valide, pas de `| null` ici. Une
 *   modification vers un nom déjà porté par un autre Author reste
 *   autorisée (pas de clé métier unique).
 * - `birthDate`/`deathDate`/`nationality`/`biography` : `null` est accepté
 *   et efface la valeur — `| null` légitime.
 */
export interface UpdateAuthorRequest {
  fullName?: string;
  birthDate?: string | null;
  deathDate?: string | null;
  nationality?: string | null;
  biography?: string | null;
}
