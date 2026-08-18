import { Language } from './language';
import { TitleStatus } from './title-status';

/**
 * Paramètres de `GET /api/v1/titles` (public). `page` : 0 est une valeur
 * valide et distincte de "absent" — traité explicitement comme tel dans
 * `CatalogueApiService`.
 */
export interface TitleSearchParams {
  q?: string;
  authorId?: number;
  genreCode?: string;
  language?: Language;
  page?: number;
  size?: number;
}

/**
 * Paramètres de `GET /api/v1/staff/titles`. Même contrat que
 * `TitleSearchParams`, avec `titleStatus` supplémentaire (réservé au staff).
 */
export interface StaffTitleSearchParams extends TitleSearchParams {
  titleStatus?: TitleStatus;
}
