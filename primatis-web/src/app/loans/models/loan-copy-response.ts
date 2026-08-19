/**
 * Voir `be.primatis.loan.dto.LoanCopyResponse` côté backend. `titleId`
 * seul (jamais un `Title`/`TitleResponse` imbriqué). Distinct de
 * `catalogue.models.CopyResponse` : n'expose ni `location`, ni
 * `copyCondition`, ni `availabilityStatus` — ces informations restent du
 * ressort du contrat `CopyResponse` (staff, `COPY_READ`/`COPY_MANAGE`).
 */
export interface LoanCopyResponse {
  id: number;
  inventoryCode: string;
  titleId: number;
}
