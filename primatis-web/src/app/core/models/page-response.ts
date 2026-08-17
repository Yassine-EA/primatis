/**
 * Contrat REST générique de pagination PRIMATIS (voir
 * `be.primatis.web.PageResponse<T>` côté backend). Réutilisable par tout
 * endpoint de liste paginée, pas uniquement Users.
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
