import { FineBorrowerResponse } from './fine-borrower-response';
import { FineLoanResponse } from './fine-loan-response';
import { FineStatus } from './fine-status';

/**
 * Voir `be.primatis.fine.dto.FineResponse` côté backend. Un seul contrat
 * réutilisé tel quel entre `/me/fines` (self-service) et `/fines` (staff)
 * — même précédent exact que `LoanResponse`/`ReservationResponse`.
 * `amount` (`BigDecimal` côté backend) est sérialisé en nombre JSON par
 * Jackson : `number` ici, comme tout autre champ numérique du contrat,
 * aucune bibliothèque décimale introduite. `issuedAt`/`paidAt`/
 * `cancelledAt` sont des instants ISO (jamais convertis en `Date`), `paidAt`
 * et `cancelledAt` ne sont jamais tous deux non `null` en même temps
 * (`ck_fine_status_consistency`, backend). Aucun champ calculé
 * (`isPayable`/`isCancellable`) : le DTO reflète l'état persistant, jamais
 * une décision UI, même principe que `ReservationResponse`.
 */
export interface FineResponse {
  id: number;
  borrower: FineBorrowerResponse;
  loan: FineLoanResponse;
  amount: number;
  reason: string;
  issuedAt: string;
  fineStatus: FineStatus;
  paidAt: string | null;
  cancelledAt: string | null;
}
