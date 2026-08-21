/**
 * Voir `be.primatis.notification.NotificationType` côté backend. Les 11
 * valeurs sont exactement celles de l'enum Java, y compris
 * `ARTICLE_PUBLISHED` — non encore émise par un workflow réel (DEV-11
 * différé) mais structurellement présente dans le contrat.
 */
export type NotificationType =
  | 'LOAN_DUE_SOON'
  | 'LOAN_OVERDUE'
  | 'LOAN_RETURNED'
  | 'RESERVATION_CREATED'
  | 'RESERVATION_READY'
  | 'RESERVATION_EXPIRED'
  | 'RESERVATION_CANCELLED'
  | 'FINE_ISSUED'
  | 'FINE_PAID'
  | 'FINE_CANCELLED'
  | 'ARTICLE_PUBLISHED';
