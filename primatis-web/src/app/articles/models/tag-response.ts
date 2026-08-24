/**
 * Voir `be.primatis.article.dto.TagResponse` côté backend. Mapping 1:1,
 * mêmes quatre champs exposés, aucune transformation.
 */
export interface TagResponse {
  id: number;
  code: string;
  label: string;
  description: string | null;
}
