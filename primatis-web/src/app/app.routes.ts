import { Routes } from '@angular/router';

import { AdminLayout } from './core/layouts/admin-layout/admin-layout';
import { MemberLayout } from './core/layouts/member-layout/member-layout';
import { PublicLayout } from './core/layouts/public-layout/public-layout';
import { StaffLayout } from './core/layouts/staff-layout/staff-layout';
import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';
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
    ],
  },
  {
    // AuthGuard uniquement (DEV-04.9) : aucune route métier n'existe encore
    // sous ces layouts, donc aucune permission ne serait légitime à y
    // brancher pour l'instant (voir PermissionGuard, testé isolément).
    path: 'member',
    component: MemberLayout,
    canActivate: [authGuard],
    children: [],
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
    ],
  },
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard],
    children: [],
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
