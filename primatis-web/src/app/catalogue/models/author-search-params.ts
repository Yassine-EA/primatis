/**
 * Paramètres de `GET /api/v1/staff/authors`. `page` : 0 est une valeur
 * valide et distincte de "absent".
 */
export interface AuthorSearchParams {
  q?: string;
  page?: number;
  size?: number;
}
