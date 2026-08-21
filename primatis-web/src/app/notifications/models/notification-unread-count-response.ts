/**
 * Voir `be.primatis.notification.dto.NotificationUnreadCountResponse` côté
 * backend (DEV-DEC-0051). Compteur dédié : jamais dérivé côté frontend à
 * partir de la page courante.
 */
export interface NotificationUnreadCountResponse {
  count: number;
}
