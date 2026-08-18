/**
 * Voir `be.primatis.loan.dto.CreateLoanRequest` côté backend. Validation
 * structurelle uniquement (`borrowerUserId`/`copyId` requis et positifs) :
 * aucune règle métier ici (éligibilité, disponibilité), autorité exclusive
 * du backend.
 */
export interface CreateLoanRequest {
  borrowerUserId: number;
  copyId: number;
}
