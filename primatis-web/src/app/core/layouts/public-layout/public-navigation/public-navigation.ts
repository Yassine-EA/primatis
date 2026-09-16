import { NgTemplateOutlet } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { Drawer } from 'primeng/drawer';

import { AuthService } from '../../../../auth/services/auth.service';
import { AccountMenu } from '../../../../shared/navigation/account-menu/account-menu';
import { NavigationItem, isNavigationItemVisible } from '../../../../shared/navigation/navigation-item';

/**
 * Navigation du portail public (DEV-15.3, ID-05) — remplace l'ancienne
 * navigation unique partagée par les 4 univers : ne porte plus que les
 * points d'entrée pertinents pour un Visitor/compte (Accueil/Catalogue/
 * Articles, toujours visibles ; Espace membre/Espace personnel/
 * Administration comme points d'entrée vers les univers authentifiés).
 * Les entrées propres à chaque univers (Mes prêts, Gestion du catalogue,
 * Prêts, etc.) vivent désormais exclusivement dans `MemberNavigation`/
 * `StaffAdminShell` — jamais dupliquées ici.
 *
 * `isNavigationItemVisible` (mutualisé, `shared/navigation/navigation-item`)
 * reste l'unique logique de visibilité UX : jamais l'autorité de sécurité,
 * le backend Spring Security reste seul décisionnaire (frontend.md).
 */
@Component({
  selector: 'app-public-navigation',
  imports: [RouterLink, RouterLinkActive, NgTemplateOutlet, ButtonModule, Drawer, AccountMenu],
  templateUrl: './public-navigation.html',
  styleUrl: './public-navigation.scss',
})
export class PublicNavigation {
  private readonly authService = inject(AuthService);

  readonly mobileMenuOpen = signal(false);

  private static readonly ITEMS: readonly NavigationItem[] = [
    { label: 'Accueil', routerLink: '/' },
    // Catalogue public (DEV-06.8) : toujours visible, aucun requiredRoles —
    // surface backend permitAll (DEV-DEC-0027).
    { label: 'Catalogue', routerLink: '/catalogue' },
    // Articles public (DEV-11.11) : toujours visible, aucun requiredRoles —
    // surface backend permitAll, même statut exact que Catalogue.
    { label: 'Articles', routerLink: '/articles' },
    // Page éditoriale dédiée à Georges Lemaître (DEV-15.4) : contenu
    // statique, aucun requiredRoles — même statut que Catalogue/Articles.
    { label: 'Georges Lemaître', routerLink: '/georges-lemaitre' },
    // Points d'entrée vers les univers authentifiés (DEV-15.3) : mêmes
    // routes/permissions que l'ancienne navigation unique, mais ne mènent
    // plus qu'à l'écran d'accueil de chaque univers — la navigation
    // détaillée de cet univers prend ensuite le relais (MemberNavigation /
    // StaffAdminShell).
    { label: 'Espace membre', routerLink: '/member/profile', requiredRoles: ['ROLE_MEMBER'] },
    { label: 'Espace personnel', routerLink: '/staff/users', requiredRoles: ['ROLE_LIBRARIAN', 'ROLE_ADMIN'] },
    { label: 'Administration', routerLink: '/admin/users', requiredRoles: ['ROLE_ADMIN'] },
  ];

  visibleItems(): readonly NavigationItem[] {
    const authenticated = this.authService.authenticated();
    const roles = this.authService.roles();
    const permissions = this.authService.permissions();
    return PublicNavigation.ITEMS.filter((item) => isNavigationItemVisible(item, authenticated, roles, permissions));
  }

  openMobileMenu(): void {
    this.mobileMenuOpen.set(true);
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }
}
