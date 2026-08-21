import { Injectable, inject, signal } from '@angular/core';

import { NotificationApiService } from './notification-api.service';

/**
 * État partagé du compteur UNREAD (DEV-10.10, DEV-DEC-0053), consommé à la
 * fois par la cloche (`shared/navigation`) et par `MemberNotificationsPage`
 * — évite les appels dispersés et non coordonnés à
 * `NotificationApiService.getUnreadCount`. Volontairement minimal (pas de
 * store global, pas de NgRx, cf. frontend.md « State management ») : un
 * seul Signal en lecture seule, trois méthodes.
 *
 * Aucun polling/temps réel (DEV-DEC-0051/0053, architecture.md §11.4) :
 * {@link refresh} n'est appelée qu'à des moments UX naturels (montage de la
 * navigation authentifiée, arrivée sur la page Notifications, après une
 * action mark-all). {@link decrement} permet une mise à jour locale
 * optimiste après un `markAsRead` individuel réussi, sans round-trip HTTP
 * supplémentaire — ne descend jamais sous 0.
 */
@Injectable({ providedIn: 'root' })
export class NotificationUnreadStateService {
  private readonly notificationApiService = inject(NotificationApiService);
  private readonly countSignal = signal(0);

  readonly unreadCount = this.countSignal.asReadonly();

  /**
   * Rafraîchissement passif : une erreur ne doit jamais perturber la
   * navigation globale (aucun Toast, aucun état d'erreur exposé ici) — la
   * page `MemberNotificationsPage` porte sa propre gestion d'erreur pour la
   * consultation complète.
   */
  refresh(): void {
    this.notificationApiService.getUnreadCount().subscribe({
      next: (response) => this.countSignal.set(response.count),
      error: () => {
        // Échec silencieux, volontaire (voir Javadoc de la méthode).
      },
    });
  }

  decrement(): void {
    this.countSignal.update((current) => Math.max(0, current - 1));
  }

  /** Appelée au logout (DEV-10.10 §22) — aucun compteur périmé ne doit survivre à une session. */
  reset(): void {
    this.countSignal.set(0);
  }
}
