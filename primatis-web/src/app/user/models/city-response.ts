import { CountryResponse } from './country-response';

/**
 * Voir `be.primatis.user.web.CityResponse` côté backend.
 */
export interface CityResponse {
  id: number;
  name: string;
  postalCode: string;
  country: CountryResponse;
}
