import { Routes } from '@angular/router';

import { AdminLayout } from './core/layouts/admin-layout/admin-layout';
import { MemberLayout } from './core/layouts/member-layout/member-layout';
import { PublicLayout } from './core/layouts/public-layout/public-layout';
import { StaffLayout } from './core/layouts/staff-layout/staff-layout';
import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';
import { roleGuard } from './core/guards/role.guard';
import { Forbidden } from './shared/pages/forbidden/forbidden';
import { Home } from './shared/pages/home/home';
import { NotFound } from './shared/pages/not-found/not-found';

export const routes: Routes = [
  {
    path: '',
    component: PublicLayout,
    children: [
      {
        path: '',
        component: Home,
      },
      {
        // Route publique (DEV-04.7) : aucun guard, aucune protection —
        // l'autorité de sécurité reste le backend Spring Security.
        // Lazy loading : évite d'embarquer Login et ses modules PrimeNG
        // (Password/Message/InputText) dans le bundle initial.
        path: 'login',
        loadComponent: () => import('./auth/pages/login/login').then((m) => m.Login),
      },
      {
        // Catalogue public (DEV-06.8) : surface backend permitAll
        // (Title.titleStatus = ACTIVE uniquement, DEV-DEC-0027) — aucun
        // guard, même principe que 'login'. Deux routes distinctes (pas de
        // segment parent avec redirectTo) : /catalogue n'a aucune capacité
        // commune à protéger, contrairement à /staff ou /admin.
        path: 'catalogue',
        loadComponent: () => import('./catalogue/pages/catalogue-page/catalogue-page').then((m) => m.CataloguePage),
      },
      {
        path: 'catalogue/:id',
        loadComponent: () =>
          import('./catalogue/pages/title-detail-page/title-detail-page').then((m) => m.TitleDetailPage),
      },
    ],
  },
  {
    // roleGuard porté par la zone elle-même (DEV-05.13), pas par un segment
    // enfant comme permissionGuard en Staff/Admin : ROLE_MEMBER conditionne
    // TOUTE la zone /member par définition ("Espace membre"), pas une
    // ressource spécifique parmi plusieurs possibles. Backend /api/v1/me/**
    // reste inchangé (ownership structurelle, accessible à tout AppUser
    // authentifié) — cette restriction est strictement UX/frontend.
    path: 'member',
    component: MemberLayout,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ROLE_MEMBER'] },
    children: [
      { path: '', redirectTo: 'profile', pathMatch: 'full' },
      {
        path: 'profile',
        loadComponent: () =>
          import('./member/profile/pages/member-profile-page/member-profile-page').then(
            (m) => m.MemberProfilePage,
          ),
      },
      {
        // DEV-07.8 : consultation seule des prêts du membre authentifié,
        // aucun guard propre — hérite du roleGuard ROLE_MEMBER porté par la
        // zone /member (même principe que 'profile').
        path: 'loans',
        loadComponent: () =>
          import('./member/loans/pages/member-loans-page/member-loans-page').then((m) => m.MemberLoansPage),
      },
    ],
  },
  {
    // AuthGuard au niveau de la zone (DEV-04.9) ; PermissionGuard porté par
    // le segment 'users' (DEV-05.11) protège liste ET détail par héritage
    // de route, une seule déclaration pour les deux pages.
    path: 'staff',
    component: StaffLayout,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'users', pathMatch: 'full' },
      {
        path: 'users',
        canActivate: [permissionGuard],
        data: { permissions: ['USER_READ'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./staff/users/pages/staff-users-page/staff-users-page').then((m) => m.StaffUsersPage),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./staff/users/pages/staff-user-detail-page/staff-user-detail-page').then(
                (m) => m.StaffUserDetailPage,
              ),
          },
        ],
      },
      {
        // Segment 'catalogue' (DEV-06.9, CATALOGUE_MANAGE) — sibling de
        // 'users', même structure que /admin/users : 'new' déclaré avant
        // ':id' pour que le routing statique l'emporte sur le paramétrique.
        // Aucune route Copy séparée (K.4, FIGÉ) : Copies gérées inline dans
        // le détail Title.
        path: 'catalogue',
        canActivate: [permissionGuard],
        data: { permissions: ['CATALOGUE_MANAGE'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./staff/catalogue/pages/staff-catalogue-page/staff-catalogue-page').then(
                (m) => m.StaffCataloguePage,
              ),
          },
          {
            path: 'new',
            loadComponent: () =>
              import('./staff/catalogue/pages/staff-title-create-page/staff-title-create-page').then(
                (m) => m.StaffTitleCreatePage,
              ),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./staff/catalogue/pages/staff-title-detail-page/staff-title-detail-page').then(
                (m) => m.StaffTitleDetailPage,
              ),
          },
        ],
      },
      {
        // Segment 'loans' (DEV-07.9, LOAN_READ) — sibling de 'users'/
        // 'catalogue', même structure. Un seul écran (liste + retour) :
        // aucun GET /api/v1/loans/{id} (DEV-DEC-0031), donc aucune route
        // ':id' comme pour users/catalogue.
        path: 'loans',
        canActivate: [permissionGuard],
        data: { permissions: ['LOAN_READ'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./staff/loans/pages/staff-loans-page/staff-loans-page').then((m) => m.StaffLoansPage),
          },
        ],
      },
    ],
  },
  {
    // AuthGuard au niveau de la zone (DEV-04.9) ; PermissionGuard porté par
    // le segment 'users' (DEV-05.12, USER_MANAGE) protège liste, création
    // ET détail par héritage de route, une seule déclaration pour les
    // trois pages. 'new' déclaré avant ':id' pour que le routing statique
    // l'emporte sur le paramétrique.
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'users', pathMatch: 'full' },
      {
        path: 'users',
        canActivate: [permissionGuard],
        data: { permissions: ['USER_MANAGE'] },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./admin/users/pages/admin-users-page/admin-users-page').then((m) => m.AdminUsersPage),
          },
          {
            path: 'new',
            loadComponent: () =>
              import('./admin/users/pages/admin-user-create-page/admin-user-create-page').then(
                (m) => m.AdminUserCreatePage,
              ),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./admin/users/pages/admin-user-detail-page/admin-user-detail-page').then(
                (m) => m.AdminUserDetailPage,
              ),
          },
        ],
      },
    ],
  },
  {
    path: 'forbidden',
    component: Forbidden,
  },
  {
    path: '**',
    component: NotFound,
  },
];
