/**
 * Voir `be.primatis.catalogue.dto.CreateAuthorRequest` côté backend.
 * `fullName` n'est jamais une clé métier unique : l'homonymie entre deux
 * Authors distincts est autorisée, aucune validation d'unicité ici.
 */
export interface CreateAuthorRequest {
  fullName: string;
  birthDate?: string;
  deathDate?: string;
  nationality?: string;
  biography?: string;
}
