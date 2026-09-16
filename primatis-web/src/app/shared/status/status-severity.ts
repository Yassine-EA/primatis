import { AccountStatus } from '../../user/models/account-status';
import { ArticleStatus } from '../../articles/models/article-status';
import { AvailabilityStatus } from '../../catalogue/models/availability-status';
import { CopyCondition } from '../../catalogue/models/copy-condition';
import { FineStatus } from '../../fines/models/fine-status';
import { LoanStatus } from '../../loans/models/loan-status';
import { MemberStatus } from '../../user/models/member-status';
import { ReservationStatus } from '../../reservations/models/reservation-status';
import { TitleStatus } from '../../catalogue/models/title-status';

/**
 * Sévérités `p-tag`/`p-message` (DEV-15.2, ID-06/ID-09 de l'audit DEV-15.1) :
 * même statut métier = même langage visuel partout dans l'application.
 * Frontend uniquement — aucune de ces fonctions n'altère la sémantique
 * métier des enums backend, elles se contentent d'un mapping d'affichage.
 */
export type StatusTagSeverity = 'success' | 'secondary' | 'info' | 'warn' | 'danger' | 'contrast';

export function loanStatusSeverity(status: LoanStatus): StatusTagSeverity {
  switch (status) {
    case 'ACTIVE':
      return 'success';
    case 'OVERDUE':
      return 'danger';
    case 'RETURNED':
      return 'secondary';
  }
}

export function reservationStatusSeverity(status: ReservationStatus): StatusTagSeverity {
  switch (status) {
    case 'WAITING':
      return 'info';
    case 'READY':
      return 'success';
    case 'FULFILLED':
      return 'secondary';
    case 'CANCELLED':
      return 'danger';
    case 'EXPIRED':
      return 'warn';
  }
}

export function fineStatusSeverity(status: FineStatus): StatusTagSeverity {
  switch (status) {
    case 'UNPAID':
      return 'danger';
    case 'PAID':
      return 'success';
    case 'CANCELLED':
      return 'secondary';
  }
}

export function memberStatusSeverity(status: MemberStatus): StatusTagSeverity {
  switch (status) {
    case 'ACTIVE':
      return 'success';
    case 'BLOCKED':
      return 'danger';
    case 'EXPIRED':
      return 'warn';
  }
}

export function accountStatusSeverity(status: AccountStatus): StatusTagSeverity {
  return status === 'ACTIVE' ? 'success' : 'danger';
}

export function articleStatusSeverity(status: ArticleStatus): StatusTagSeverity {
  if (status === 'PUBLISHED') {
    return 'success';
  }
  if (status === 'DRAFT') {
    return 'warn';
  }
  return 'secondary';
}

export function titleStatusSeverity(status: TitleStatus): StatusTagSeverity {
  return status === 'ACTIVE' ? 'success' : 'warn';
}

/**
 * Centralisé depuis `LoanCreateDialog` (DEV-15.7 → DEV-15.8, ID-19) : sens
 * réel vérifié dans le backend (`CopyService`, `be.primatis.catalogue.
 * AvailabilityStatus`) — `ON_LOAN`/`RESERVED` restent des états métier
 * normaux (jamais une erreur), seul `UNAVAILABLE` (imposé par
 * `CopyCondition.LOST`/`OUT_OF_SERVICE`) est réellement négatif. Couleur
 * uniquement, jamais une traduction du texte affiché — `AvailabilityStatus`
 * reste l'enum brut à l'écran partout où ce mapping est consommé
 * (`e2e/fixtures/loan-helpers.ts` vérifie littéralement "AVAILABLE").
 */
export function copyAvailabilityStatusSeverity(status: AvailabilityStatus): StatusTagSeverity {
  switch (status) {
    case 'AVAILABLE':
      return 'success';
    case 'ON_LOAN':
    case 'RESERVED':
      return 'warn';
    case 'UNAVAILABLE':
      return 'danger';
  }
}

/**
 * Centralisé depuis `LoanCreateDialog` (DEV-15.7 → DEV-15.8, ID-19) :
 * `DAMAGED` n'implique jamais `UNAVAILABLE` côté backend (`CopyService`) —
 * un exemplaire endommagé peut rester prêtable, `database.md` interdit
 * explicitement cette implication. Couleur uniquement, jamais une
 * traduction du texte affiché.
 */
export function copyConditionSeverity(condition: CopyCondition): StatusTagSeverity {
  switch (condition) {
    case 'GOOD':
      return 'success';
    case 'DAMAGED':
      return 'warn';
    case 'LOST':
    case 'OUT_OF_SERVICE':
      return 'danger';
  }
}
