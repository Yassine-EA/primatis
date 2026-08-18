import { Observable, forkJoin, map, of, switchMap } from 'rxjs';

import { GenreResponse } from '../../catalogue/models/genre-response';
import { StaffCatalogueApiService } from '../../catalogue/services/staff-catalogue-api.service';

const GENRE_PAGE_SIZE = 100;

/**
 * Charge l'intégralité des Genres, page par page, de façon déterministe.
 *
 * `GET /api/v1/staff/genres` est paginé (contrat backend réel, DEV-06.5.1) —
 * pour un contrôle de sélection (`MultiSelect`), il faut néanmoins la liste
 * complète. `GENRE_PAGE_SIZE` (100, le maximum accepté par le backend)
 * n'est qu'une taille de lot de récupération, jamais une troncature : toutes
 * les pages annoncées par `totalPages` sont récupérées, quel que soit leur
 * nombre. Aucune limite arbitraire, aucune boucle non bornée (le nombre de
 * requêtes est fixé dès la première réponse par `totalPages`).
 */
export function loadAllGenres(staffCatalogueApiService: StaffCatalogueApiService): Observable<GenreResponse[]> {
  return staffCatalogueApiService.searchGenres({ page: 0, size: GENRE_PAGE_SIZE }).pipe(
    switchMap((firstPage) => {
      if (firstPage.totalPages <= 1) {
        return of(firstPage.content);
      }
      const remainingPageRequests = Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
        staffCatalogueApiService.searchGenres({ page: index + 1, size: GENRE_PAGE_SIZE }),
      );
      return forkJoin(remainingPageRequests).pipe(
        map((remainingPages) => [...firstPage.content, ...remainingPages.flatMap((page) => page.content)]),
      );
    }),
  );
}
