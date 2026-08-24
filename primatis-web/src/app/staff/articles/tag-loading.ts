import { Observable, forkJoin, map, of, switchMap } from 'rxjs';

import { TagResponse } from '../../articles/models/tag-response';
import { StaffTagApiService } from '../../articles/services/staff-tag-api.service';

const TAG_PAGE_SIZE = 100;

/**
 * Charge l'intégralité des Tags, page par page, de façon déterministe —
 * même précédent exact que `loadAllGenres` (staff/catalogue/genre-loading.ts).
 * `GET /api/v1/staff/tags` est paginé (DEV-11.9) sans endpoint `all` ; pour
 * un contrôle de sélection (`TagPicker`), il faut néanmoins la liste
 * complète. `TAG_PAGE_SIZE` (100, le maximum accepté par le backend) n'est
 * qu'une taille de lot de récupération, jamais une troncature : toutes les
 * pages annoncées par `totalPages` sont récupérées, quel que soit leur
 * nombre — aucune page suivante n'est jamais cachée silencieusement
 * (mission DEV-11.12 §40).
 */
export function loadAllTags(staffTagApiService: StaffTagApiService): Observable<TagResponse[]> {
  return staffTagApiService.listTags(0, TAG_PAGE_SIZE).pipe(
    switchMap((firstPage) => {
      if (firstPage.totalPages <= 1) {
        return of(firstPage.content);
      }
      const remainingPageRequests = Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
        staffTagApiService.listTags(index + 1, TAG_PAGE_SIZE),
      );
      return forkJoin(remainingPageRequests).pipe(
        map((remainingPages) => [...firstPage.content, ...remainingPages.flatMap((page) => page.content)]),
      );
    }),
  );
}
