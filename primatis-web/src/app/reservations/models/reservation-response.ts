import { ReservationCopyResponse } from './reservation-copy-response';
import { ReservationMemberResponse } from './reservation-member-response';
import { ReservationStatus } from './reservation-status';
import { ReservationTitleResponse } from './reservation-title-response';

/**
 * Voir `be.primatis.reservation.dto.ReservationResponse` côté backend.
 * `reservationDate`/`expirationDate`/`createdAt`/`updatedAt` sont des
 * instants (`Instant`, ISO avec heure) — jamais convertis en `Date` ici
 * (DEV-DEC-0044, OD-DEV08-FE-05) : le backend reste l'autorité, aucun
 * countdown ni dérivation d'expiration côté frontend. `expirationDate`
 * reste `null` tant que la Reservation n'est jamais passée par `READY`.
 * `assignedCopy` reflète strictement l'état persisté, sans
 * réinterprétation : une Reservation `FULFILLED`/`CANCELLED`/`EXPIRED`
 * peut légitimement conserver un `assignedCopy` non `null` (trace
 * historique, jamais nettoyée par le backend). Aucun champ calculé
 * (`isExpired`/`isCancellable`/`queuePosition`/`timeRemaining`) : le DTO
 * reflète l'état persistant, jamais une décision UI.
 */
export interface ReservationResponse {
  id: number;
  member: ReservationMemberResponse;
  title: ReservationTitleResponse;
  assignedCopy: ReservationCopyResponse | null;
  fulfilledByLoanId: number | null;
  reservationDate: string;
  expirationDate: string | null;
  reservationStatus: ReservationStatus;
  createdAt: string;
  updatedAt: string;
}
