import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { OverlayBadge } from 'primeng/overlaybadge';

import { AuthService } from '../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../notifications/services/notification-unread-state.service';
import { NavigationItem, isNavigationItemVisible } from './navigation-item';

/**
 * Navigation principale PRIMATIS (DEV-04.10), partagée par les quatre
 * layouts. Purement UX : masquer un lien ne constitue jamais une
 * autorisation réelle — le backend Spring Security reste l'autorité.
 *
 * Zones actuelles : `/staff` visible pour `ROLE_LIBRARIAN` et `ROLE_ADMIN`
 * (DEV-05.11-DEC-04 — un Admin porte `USER_READ`/`USER_PROFILE_MANAGE` au
 * même titre qu'un Librarian ; cela ne lui donne aucune capacité
 * `USER_MANAGE` supplémentaire, qui reste une question d'écrans DEV-05.12,
 * pas de visibilité de zone).
 *
 * <p><b>Cloche/badge Notifications</b> (DEV-10.10, DEV-DEC-0053) : visible
 * pour tout utilisateur authentifié (pas seulement `ROLE_MEMBER` — la
 * décision fige « navigation authentifiée », et `/api/v1/me/notifications`
 * reste accessible à tout `AppUser`, ownership backend). Navigue
 * directement vers `/member/notifications`, aucun dropdown/overlay de
 * contenu (seul `p-overlaybadge` — un simple positionnement visuel du
 * badge sur l'icône, jamais un panneau de notifications récentes). Le
 * compteur provient de {@link NotificationUnreadStateService}, jamais d'un
 * appel HTTP direct dupliqué ici.
 */
@Component({
  selector: 'app-navigation',
  imports: [RouterLink, OverlayBadge],
  templateUrl: './navigation.html',
  styleUrl: './navigation.scss',
})
export class Navigation {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly unreadState = inject(NotificationUnreadStateService);

  private static readonly ITEMS: readonly NavigationItem[] = [
    { label: 'Accueil', routerLink: '/' },
    // Catalogue public (DEV-06.8) : toujours visible, aucun requiredRoles —
    // surface backend permitAll (DEV-DEC-0027), même statut qu'Accueil.
    { label: 'Catalogue', routerLink: '/catalogue' },
    { label: 'Espace membre', routerLink: '/member/profile', requiredRoles: ['ROLE_MEMBER'] },
    { label: 'Mes prêts', routerLink: '/member/loans', requiredRoles: ['ROLE_MEMBER'] },
    // DEV-08.14 : ownership self-service par identité JWT, jamais par
    // permission frontend — mêmes principes exacts que 'Mes prêts'
    // (DEV-DEC-0041).
    { label: 'Mes réservations', routerLink: '/member/reservations', requiredRoles: ['ROLE_MEMBER'] },
    // DEV-09.12 : même principe exact que 'Mes prêts'/'Mes réservations' —
    // consultation self-service par rôle, aucune permission FINE_READ (page
    // strictement en lecture seule, aucune action FINE_MANAGE côté membre).
    { label: 'Mes amendes', routerLink: '/member/fines', requiredRoles: ['ROLE_MEMBER'] },
    { label: 'Espace personnel', routerLink: '/staff/users', requiredRoles: ['ROLE_LIBRARIAN', 'ROLE_ADMIN'] },
    {
      label: 'Gestion du catalogue',
      routerLink: '/staff/catalogue',
      requiredRoles: ['ROLE_LIBRARIAN', 'ROLE_ADMIN'],
    },
    // DEV-07.9 : accès/navigation piloté par la permission LOAN_READ
    // (comme le guard réel de la route), pas par rôle — contrairement aux
    // autres entrées staff ci-dessus.
    { label: 'Prêts', routerLink: '/staff/loans', requiredPermissions: ['LOAN_READ'] },
    // DEV-08.14 : même principe exact que 'Prêts' — RESERVATION_READ pilote
    // la visibilité de l'entrée, comme le guard réel de la route ; l'action
    // mutatrice RESERVATION_MANAGE reste contrôlée dans la page elle-même.
    { label: 'Réservations', routerLink: '/staff/reservations', requiredPermissions: ['RESERVATION_READ'] },
    // DEV-09.13 : même principe exact que 'Prêts'/'Réservations' —
    // FINE_READ pilote la visibilité de l'entrée, comme le guard réel de la
    // route ; l'action mutatrice FINE_MANAGE reste contrôlée dans la page
    // elle-même.
    { label: 'Amendes', routerLink: '/staff/fines', requiredPermissions: ['FINE_READ'] },
    { label: 'Administration', routerLink: '/admin/users', requiredRoles: ['ROLE_ADMIN'] },
  ];

  constructor() {
    if (this.authenticated()) {
      this.unreadState.refresh();
    }
  }

  authenticated(): boolean {
    return this.authService.authenticated();
  }

  visibleItems(): readonly NavigationItem[] {
    const authenticated = this.authService.authenticated();
    const roles = this.authService.roles();
    const permissions = this.authService.permissions();
    return Navigation.ITEMS.filter((item) => isNavigationItemVisible(item, authenticated, roles, permissions));
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
