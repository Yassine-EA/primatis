import { NotificationStatus } from './notification-status';
import { NotificationType } from './notification-type';

/**
 * Voir `be.primatis.notification.dto.NotificationResponse` côté backend.
 * Un seul contrat, self-service uniquement (`/me/notifications`) — aucune
 * variante staff (DEV-10.1 §10, hors périmètre V1).
 *
 * `originId` expose uniquement l'identifiant de l'unique origine réelle
 * (Loan/Reservation/Fine/Article) — jamais quatre champs nullable en
 * parallèle. Aucun champ `originType` : la catégorie d'origine est déjà
 * intégralement déductible de `notificationType` (business-rules.md §6.4).
 * `createdAt`/`readAt` sont des instants ISO (jamais convertis en `Date`),
 * même convention que `FineResponse.issuedAt`/`paidAt`.
 */
export interface NotificationResponse {
  id: number;
  notificationType: NotificationType;
  notificationStatus: NotificationStatus;
  title: string;
  message: string;
  originId: number;
  createdAt: string;
  readAt: string | null;
}
