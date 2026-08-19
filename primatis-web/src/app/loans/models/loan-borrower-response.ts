/**
 * Voir `be.primatis.loan.dto.LoanBorrowerResponse` côté backend.
 * Représentation compacte de l'emprunteur, embarquée dans `LoanResponse`.
 * N'inclut ni `email`, ni `phoneNumber`, ni `AccountStatus`/`MemberStatus`,
 * ni dates d'adhésion, ni rôles/permissions — ces données appartiennent au
 * contrat `UserResponse`, pas à un résumé Loan.
 */
export interface LoanBorrowerResponse {
  id: number;
  memberNumber: string;
  firstName: string;
  lastName: string;
}
