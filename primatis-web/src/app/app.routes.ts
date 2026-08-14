import { Routes } from '@angular/router';

import { AdminLayout } from './core/layouts/admin-layout/admin-layout';
import { MemberLayout } from './core/layouts/member-layout/member-layout';
import { PublicLayout } from './core/layouts/public-layout/public-layout';
import { StaffLayout } from './core/layouts/staff-layout/staff-layout';
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
    ],
  },
  {
    path: 'member',
    component: MemberLayout,
    children: [],
  },
  {
    path: 'staff',
    component: StaffLayout,
    children: [],
  },
  {
    path: 'admin',
    component: AdminLayout,
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
