/**
 * Voir `be.primatis.catalogue.dto.CreateGenreRequest` côté backend.
 * `code`/`label` : unicité vérifiée par le backend (`uq_genre_code`/
 * `uq_genre_label`) — jamais revalidée ici.
 */
export interface CreateGenreRequest {
  code: string;
  label: string;
  description?: string;
}
