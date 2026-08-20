/**
 * Voir `be.primatis.fine.dto.FineBorrowerResponse` côté backend.
 * `memberNumber` non nullable ici (pas `string | null`) malgré la colonne
 * `app_user.member_number` nullable en base : une `Fine` est toujours
 * rattachée au `Loan` d'un adhérent (l'éligibilité au prêt exige déjà
 * `memberNumber != null`, `LoanService.requireEligibleBorrower`) — même
 * convention exacte que `LoanBorrowerResponse`/`ReservationMemberResponse`,
 * qui typent déjà ce champ en `string` pour la même raison.
 */
export interface FineBorrowerResponse {
  id: number;
  memberNumber: string;
  firstName: string;
  lastName: string;
}
