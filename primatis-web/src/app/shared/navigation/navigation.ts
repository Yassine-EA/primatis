import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../auth/services/auth.service';
import { NavigationItem, isNavigationItemVisible } from './navigation-item';

/**
 * Navigation principale PRIMATIS (DEV-04.10), partagée par les quatre
 * layouts. Purement UX : masquer un lien ne constitue jamais une
 * autorisation réelle — le backend Spring Security reste l'autorité.
 *
 * Zones actuelles : `/staff` visible pour `ROLE_LIBRARIAN` uniquement.
 * L'éventuelle visibilité de `/staff` pour `ROLE_ADMIN` n'est pas établie
 * par les sources du projet — volontairement non ajoutée (point OPEN,
 * voir tracking DEV-04.10) plutôt qu'inventée.
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
    { label: 'Espace membre', routerLink: '/member', requiredRoles: ['ROLE_MEMBER'] },
    { label: 'Espace personnel', routerLink: '/staff', requiredRoles: ['ROLE_LIBRARIAN'] },
    { label: 'Administration', routerLink: '/admin', requiredRoles: ['ROLE_ADMIN'] },
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
