/**
 * Voir `be.primatis.reservation.dto.CreateOwnReservationRequest` côté
 * backend. Aucun identifiant utilisateur ici — l'identité du membre est
 * dérivée exclusivement du JWT côté backend (ownership), jamais du corps
 * de la requête.
 */
export interface CreateOwnReservationRequest {
  titleId: number;
}
