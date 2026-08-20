import { FineCopyResponse } from './fine-copy-response';

/**
 * Voir `be.primatis.fine.dto.FineLoanResponse` côté backend. `loanDate` est
 * un `Instant` (ISO avec heure), `dueDate`/`returnDate` sont des
 * `LocalDate` (date seule) — mélange de granularités reflété tel quel côté
 * backend, jamais uniformisé ici : les trois restent des chaînes ISO
 * (aucune conversion `Date`), seul le format exact diffère selon le champ.
 */
export interface FineLoanResponse {
  id: number;
  loanDate: string;
  dueDate: string;
  returnDate: string | null;
  copy: FineCopyResponse;
}
