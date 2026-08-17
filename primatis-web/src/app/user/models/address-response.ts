import { CityResponse } from './city-response';

/**
 * Voir `be.primatis.user.web.AddressResponse` côté backend.
 */
export interface AddressResponse {
  id: number;
  street: string;
  streetNumber: string;
  boxNumber: string | null;
  additionalInfo: string | null;
  city: CityResponse;
}
