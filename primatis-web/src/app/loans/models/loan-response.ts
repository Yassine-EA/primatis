import { LoanBorrowerResponse } from './loan-borrower-response';
import { LoanCopyResponse } from './loan-copy-response';
import { LoanStatus } from './loan-status';

/**
 * Voir `be.primatis.loan.dto.LoanResponse` côté backend. `loanDate` est un
 * instant (`Instant`, ISO avec heure) ; `dueDate`/`returnDate` sont des
 * dates métier sans heure (`LocalDate`, ISO `yyyy-MM-dd`). `returnDate`
 * reste `null` tant que le Loan est ouvert (ACTIVE/OVERDUE) — reflète
 * strictement l'état persisté, aucune dérivation frontend.
 */
export interface LoanResponse {
  id: number;
  borrower: LoanBorrowerResponse;
  copy: LoanCopyResponse;
  loanDate: string;
  dueDate: string;
  returnDate: string | null;
  loanStatus: LoanStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}
