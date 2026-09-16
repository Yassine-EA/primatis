import { NgTemplateOutlet } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { Drawer } from 'primeng/drawer';

import { AuthService } from '../../../auth/services/auth.service';
import { AccountMenu } from '../../../shared/navigation/account-menu/account-menu';
import { NavigationItem, isNavigationItemVisible } from '../../../shared/navigation/navigation-item';

/**
 * Shell métier commun à `StaffLayout` et `AdminLayout` (DEV-15.3, §11) :
 * sidebar de domaines + header utilitaire + zone de contenu large. Évite
 * de dupliquer deux fois la même structure (l'ancien constat DEV-15.1
 * ID-07 concernait les 4 layouts quasi identiques ; Staff et Admin restent
 * le même outil métier, seul `zoneTitle` distingue l'écran affiché).
 *
 * La liste d'entrées reproduit exactement les items Staff/Admin de
 * l'ancienne navigation unique (mêmes routes, mêmes rôles/permissions,
 * DEV-05.11-DEC-04 inclus) — restructuration purement visuelle (§12 du
 * cadrage), aucune règle d'autorisation modifiée.
 */
@Component({
  selector: 'app-staff-admin-shell',
  imports: [RouterLink, RouterLinkActive, NgTemplateOutlet, ButtonModule, Drawer, AccountMenu],
  templateUrl: './staff-admin-shell.html',
  styleUrl: './staff-admin-shell.scss',
})
export class StaffAdminShell {
  readonly zoneTitle = input.required<string>();

  private readonly authService = inject(AuthService);

  readonly mobileSidebarOpen = signal(false);

  private static readonly ITEMS: readonly NavigationItem[] = [
    {
      label: 'Espace personnel',
      routerLink: '/staff/users',
      requiredRoles: ['ROLE_LIBRARIAN', 'ROLE_ADMIN'],
      icon: 'pi pi-user',
    },
    {
      label: 'Gestion du catalogue',
      routerLink: '/staff/catalogue',
      requiredRoles: ['ROLE_LIBRARIAN', 'ROLE_ADMIN'],
      icon: 'pi pi-book',
    },
    { label: 'Prêts', routerLink: '/staff/loans', requiredPermissions: ['LOAN_READ'], icon: 'pi pi-arrow-right-arrow-left' },
    {
      label: 'Réservations',
      routerLink: '/staff/reservations',
      requiredPermissions: ['RESERVATION_READ'],
      icon: 'pi pi-calendar',
    },
    { label: 'Amendes', routerLink: '/staff/fines', requiredPermissions: ['FINE_READ'], icon: 'pi pi-wallet' },
    {
      label: 'Gestion des articles',
      routerLink: '/staff/articles',
      requiredPermissions: ['ARTICLE_MANAGE'],
      icon: 'pi pi-file-edit',
    },
    { label: 'Administration', routerLink: '/admin/users', requiredRoles: ['ROLE_ADMIN'], icon: 'pi pi-shield' },
    { label: 'Paramètres', routerLink: '/admin/settings', requiredPermissions: ['SETTING_READ'], icon: 'pi pi-cog' },
  ];

  visibleItems(): readonly NavigationItem[] {
    const authenticated = this.authService.authenticated();
    const roles = this.authService.roles();
    const permissions = this.authService.permissions();
    return StaffAdminShell.ITEMS.filter((item) => isNavigationItemVisible(item, authenticated, roles, permissions));
  }

  openMobileSidebar(): void {
    this.mobileSidebarOpen.set(true);
  }

  closeMobileSidebar(): void {
    this.mobileSidebarOpen.set(false);
  }
}
