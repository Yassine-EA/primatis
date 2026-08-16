import { AddressResponse } from './address-response';

/**
 * Voir `be.primatis.user.web.ResidenceResponse` côté backend. `endDate`
 * `null` désigne la résidence courante. Ressource distincte de
 * `UserResponse`/`MeProfileResponse` — jamais imbriquée dans l'un ou
 * l'autre.
 */
export interface ResidenceResponse {
  id: number;
  address: AddressResponse;
  startDate: string;
  endDate: string | null;
}
