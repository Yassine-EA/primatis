/**
 * Voir `be.primatis.reservation.dto.CreateReservationRequest` côté
 * backend. Distinct de `CreateOwnReservationRequest` : `userId` est
 * fourni explicitement par le staff, jamais déduit du JWT de l'appelant.
 */
export interface CreateReservationRequest {
  userId: number;
  titleId: number;
}
