/**
 * Voir `be.primatis.catalogue.dto.GenreResponse` côté backend. Aucun
 * `GenreType` — `code`/`label` sont `UNIQUE` côté backend.
 */
export interface GenreResponse {
  id: number;
  code: string;
  label: string;
  description: string | null;
}
