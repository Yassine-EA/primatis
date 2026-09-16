import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Badge } from 'primeng/badge';

import { NotificationUnreadStateService } from '../../../../notifications/services/notification-unread-state.service';

interface MemberNavigationItem {
  readonly label: string;
  readonly routerLink: string;
  readonly icon: string;
}

/**
 * Navigation de l'espace Member (DEV-15.3, ID-05) — cinq entrées fixes,
 * sans logique de visibilité par rôle/permission : la zone `/member` est
 * déjà entièrement gardée par `roleGuard(ROLE_MEMBER)` au niveau de la
 * route (`app.routes.ts`), donc toute entrée listée ici est par
 * définition accessible à quiconque atteint ce composant. Contrairement à
 * `PublicNavigation`/`StaffAdminShell`, aucun `NavigationItem`/
 * `isNavigationItemVisible` n'est nécessaire.
 */
@Component({
  selector: 'app-member-navigation',
  imports: [RouterLink, RouterLinkActive, Badge],
  templateUrl: './member-navigation.html',
  styleUrl: './member-navigation.scss',
})
export class MemberNavigation {
  private readonly unreadState = inject(NotificationUnreadStateService);

  readonly mobileMenuOpen = signal(false);

  readonly items: readonly MemberNavigationItem[] = [
    { label: 'Profil', routerLink: '/member/profile', icon: 'pi-user' },
    { label: 'Mes prêts', routerLink: '/member/loans', icon: 'pi-book' },
    { label: 'Mes réservations', routerLink: '/member/reservations', icon: 'pi-bookmark' },
    { label: 'Mes amendes', routerLink: '/member/fines', icon: 'pi-receipt' },
    { label: 'Notifications', routerLink: '/member/notifications', icon: 'pi-bell' },
  ];

  unreadCount(): number {
    return this.unreadState.unreadCount();
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen.update((open) => !open);
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }
}
