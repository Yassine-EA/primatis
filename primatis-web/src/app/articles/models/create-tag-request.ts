/**
 * Voir `be.primatis.article.dto.CreateTagRequest` côté backend. `code` :
 * identifiant métier stable et unique (`uq_tag_code`) — jamais régénéré
 * depuis `label`, unicité vérifiée par le backend.
 */
export interface CreateTagRequest {
  code: string;
  label: string;
  description?: string;
}
