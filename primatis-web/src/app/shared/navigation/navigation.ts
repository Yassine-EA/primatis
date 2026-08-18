import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../auth/services/auth.service';
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
 */
@Component({
  selector: 'app-navigation',
  imports: [RouterLink],
  templateUrl: './navigation.html',
  styleUrl: './navigation.scss',
})
export class Navigation {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  private static readonly ITEMS: readonly NavigationItem[] = [
    { label: 'Accueil', routerLink: '/' },
    // Catalogue public (DEV-06.8) : toujours visible, aucun requiredRoles —
    // surface backend permitAll (DEV-DEC-0027), même statut qu'Accueil.
    { label: 'Catalogue', routerLink: '/catalogue' },
    { label: 'Espace membre', routerLink: '/member/profile', requiredRoles: ['ROLE_MEMBER'] },
    { label: 'Espace personnel', routerLink: '/staff/users', requiredRoles: ['ROLE_LIBRARIAN', 'ROLE_ADMIN'] },
    {
      label: 'Gestion du catalogue',
      routerLink: '/staff/catalogue',
      requiredRoles: ['ROLE_LIBRARIAN', 'ROLE_ADMIN'],
    },
    { label: 'Administration', routerLink: '/admin/users', requiredRoles: ['ROLE_ADMIN'] },
  ];

  authenticated(): boolean {
    return this.authService.authenticated();
  }

  visibleItems(): readonly NavigationItem[] {
    const authenticated = this.authService.authenticated();
    const roles = this.authService.roles();
    const permissions = this.authService.permissions();
    return Navigation.ITEMS.filter((item) => isNavigationItemVisible(item, authenticated, roles, permissions));
  }

  logout(): void {
    this.authService.logout();
    void this.router.navigateByUrl('/');
  }
}
