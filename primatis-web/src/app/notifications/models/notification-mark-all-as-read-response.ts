/**
 * Voir `be.primatis.notification.dto.NotificationMarkAllAsReadResponse`
 * côté backend (DEV-DEC-0052). `updatedCount = 0` est un succès normal
 * (idempotence), jamais une erreur.
 */
export interface NotificationMarkAllAsReadResponse {
  updatedCount: number;
}
