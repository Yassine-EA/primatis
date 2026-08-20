/**
 * Voir `be.primatis.reservation.dto.ReservationCopyResponse` côté
 * backend. `titleId` seul (jamais un `Title`/`ReservationTitleResponse`
 * imbriqué, pas de graphe de DTO récursif). Distinct de
 * `catalogue.models.CopyResponse` : n'expose ni `location`, ni
 * `copyCondition`, ni `availabilityStatus` — ces informations restent du
 * ressort du contrat `CopyResponse` (staff, `COPY_READ`/`COPY_MANAGE`).
 */
export interface ReservationCopyResponse {
  id: number;
  inventoryCode: string;
  titleId: number;
}
