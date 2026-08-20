/**
 * Voir `be.primatis.reservation.dto.ReservationMemberResponse` côté
 * backend. Représentation compacte du membre demandeur, embarquée dans
 * `ReservationResponse`. N'inclut ni `email`, ni `phoneNumber`, ni
 * `AccountStatus`/`MemberStatus`, ni dates d'adhésion — ces données
 * appartiennent au contrat `UserResponse`, pas à un résumé Reservation.
 */
export interface ReservationMemberResponse {
  id: number;
  memberNumber: string;
  firstName: string;
  lastName: string;
}
