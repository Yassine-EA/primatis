/**
 * Voir `be.primatis.catalogue.dto.AuthorResponse` côté backend. `fullName`
 * n'est pas une clé métier unique — deux Authors distincts peuvent porter le
 * même nom.
 */
export interface AuthorResponse {
  id: number;
  fullName: string;
  birthDate: string | null;
  deathDate: string | null;
  nationality: string | null;
  biography: string | null;
}
