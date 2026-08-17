/**
 * Paramètres de `GET /api/v1/staff/genres`. Pas de `q` — pas de recherche
 * textuelle sur ce endpoint (`StaffGenreController.listGenres`). `page` :
 * 0 est une valeur valide et distincte de "absent".
 */
export interface GenreListParams {
  page?: number;
  size?: number;
}
