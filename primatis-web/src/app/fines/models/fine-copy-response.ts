/**
 * Voir `be.primatis.fine.dto.FineCopyResponse` côté backend. Même forme
 * exacte que `LoanCopyResponse`/`ReservationCopyResponse` : `id`,
 * `inventoryCode`, `titleId` seul — jamais un `Title`/résumé Title imbriqué.
 */
export interface FineCopyResponse {
  id: number;
  inventoryCode: string;
  titleId: number;
}
