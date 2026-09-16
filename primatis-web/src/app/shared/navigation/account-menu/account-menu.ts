import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { OverlayBadge } from 'primeng/overlaybadge';

import { AuthService } from '../../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../../notifications/services/notification-unread-state.service';

/**
 * Utilitaire "compte" (DEV-15.3) — cloche Notifications (DEV-10.10,
 * DEV-DEC-0053) + déconnexion, mutualisé entre les trois navigations par
 * univers (Public/Member/Staff-Admin) : la seule chose réellement
 * transverse aux trois shells (un utilisateur authentifié doit toujours
 * pouvoir voir ses notifications et se déconnecter, quel que soit
 * l'univers dans lequel il se trouve), contrairement à la liste de liens
 * de navigation elle-même qui, elle, diffère par univers.
 *
 * `.nav-bell`/`.nav-logout` conservés à l'identique (sélecteurs consommés
 * par les tests E2E Playwright — `e2e/fixtures/auth-helpers.ts`,
 * `smoke.spec.ts`, `auth-rbac.spec.ts`, `notifications.spec.ts`).
 */
@Component({
  selector: 'app-account-menu',
  imports: [RouterLink, OverlayBadge, ButtonModule],
  templateUrl: './account-menu.html',
  styleUrl: './account-menu.scss',
})
export class AccountMenu {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly unreadState = inject(NotificationUnreadStateService);

  constructor() {
    if (this.authenticated()) {
      this.unreadState.refresh();
    }
  }

  authenticated(): boolean {
    return this.authService.authenticated();
  }

  unreadCount(): number {
    return this.unreadState.unreadCount();
  }

  logout(): void {
    this.unreadState.reset();
    this.authService.logout();
    void this.router.navigateByUrl('/');
  }
}
