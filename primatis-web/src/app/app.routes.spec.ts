import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';
import { roleGuard } from './core/guards/role.guard';
import { routes } from './app.routes';
import { AdminSettingsPage } from './admin/settings/pages/admin-settings-page/admin-settings-page';
import { MemberFinesPage } from './member/fines/pages/member-fines-page/member-fines-page';
import { MemberLoansPage } from './member/loans/pages/member-loans-page/member-loans-page';
import { MemberNotificationsPage } from './member/notifications/pages/member-notifications-page/member-notifications-page';
import { MemberReservationsPage } from './member/reservations/pages/member-reservations-page/member-reservations-page';
import { StaffFinesPage } from './staff/fines/pages/staff-fines-page/staff-fines-page';
import { StaffLoansPage } from './staff/loans/pages/staff-loans-page/staff-loans-page';
import { StaffReservationsPage } from './staff/reservations/pages/staff-reservations-page/staff-reservations-page';

describe('Application routes', () => {
  it('should expose the expected application zones', () => {
    const paths = routes.map((route) => route.path);

    expect(paths).toContain('');
    expect(paths).toContain('member');
    expect(paths).toContain('staff');
    expect(paths).toContain('admin');
    expect(paths).toContain('forbidden');
    expect(paths).toContain('**');
  });

  it('should keep the wildcard route last', () => {
    expect(routes.at(-1)?.path).toBe('**');
  });

  it('should expose a public /login route under the public layout', () => {
    const publicRoute = routes.find((route) => route.path === '');

    expect(publicRoute?.children?.some((child) => child.path === 'login')).toBe(true);
  });

  it('should expose public /catalogue and /catalogue/:id routes under the public layout (DEV-06.8)', () => {
    const publicRoute = routes.find((route) => route.path === '');
    const childPaths = publicRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('catalogue');
    expect(childPaths).toContain('catalogue/:id');
  });

  it('should never guard /catalogue or /catalogue/:id (DEV-06.8, backend permitAll)', () => {
    const publicRoute = routes.find((route) => route.path === '');
    const catalogueRoute = publicRoute?.children?.find((child) => child.path === 'catalogue');
    const titleDetailRoute = publicRoute?.children?.find((child) => child.path === 'catalogue/:id');

    expect(catalogueRoute?.canActivate).toBeUndefined();
    expect(titleDetailRoute?.canActivate).toBeUndefined();
  });

  it('should expose public /articles and /articles/:slug routes under the public layout (DEV-11.11)', () => {
    const publicRoute = routes.find((route) => route.path === '');
    const childPaths = publicRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('articles');
    expect(childPaths).toContain('articles/:slug');
  });

  it('should never guard /articles or /articles/:slug (DEV-11.11, backend permitAll)', () => {
    const publicRoute = routes.find((route) => route.path === '');
    const articleListRoute = publicRoute?.children?.find((child) => child.path === 'articles');
    const articleDetailRoute = publicRoute?.children?.find((child) => child.path === 'articles/:slug');

    expect(articleListRoute?.canActivate).toBeUndefined();
    expect(articleDetailRoute?.canActivate).toBeUndefined();
  });

  it('should protect /member, /staff and /admin with authGuard', () => {
    for (const path of ['member', 'staff', 'admin']) {
      const route = routes.find((candidate) => candidate.path === path);
      expect(route?.canActivate).toContain(authGuard);
    }
  });

  it('should keep /forbidden accessible without any guard', () => {
    const route = routes.find((candidate) => candidate.path === 'forbidden');

    expect(route?.canActivate).toBeUndefined();
  });

  it('should redirect the bare /staff zone to /staff/users (DEV-05.11)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const redirectChild = staffRoute?.children?.find((child) => child.path === '');

    expect(redirectChild?.redirectTo).toBe('users');
  });

  it('should protect /staff/users with permissionGuard and USER_READ (DEV-05.11)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const usersRoute = staffRoute?.children?.find((child) => child.path === 'users');

    expect(usersRoute?.canActivate).toContain(permissionGuard);
    expect(usersRoute?.data?.['permissions']).toEqual(['USER_READ']);
  });

  it('should expose the staff users list and detail routes as children of /staff/users (DEV-05.11)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const usersRoute = staffRoute?.children?.find((child) => child.path === 'users');
    const childPaths = usersRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('');
    expect(childPaths).toContain(':id');
  });

  it('should protect /staff/catalogue with permissionGuard and CATALOGUE_MANAGE (DEV-06.9)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const catalogueRoute = staffRoute?.children?.find((child) => child.path === 'catalogue');

    expect(catalogueRoute?.canActivate).toContain(permissionGuard);
    expect(catalogueRoute?.data?.['permissions']).toEqual(['CATALOGUE_MANAGE']);
  });

  it('should expose the staff catalogue list, create and detail routes as children of /staff/catalogue (DEV-06.9)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const catalogueRoute = staffRoute?.children?.find((child) => child.path === 'catalogue');
    const childPaths = catalogueRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('');
    expect(childPaths).toContain('new');
    expect(childPaths).toContain(':id');
  });

  it('should declare /staff/catalogue/new before /staff/catalogue/:id (DEV-06.9)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const catalogueRoute = staffRoute?.children?.find((child) => child.path === 'catalogue');
    const childPaths = catalogueRoute?.children?.map((child) => child.path) ?? [];

    expect(childPaths.indexOf('new')).toBeLessThan(childPaths.indexOf(':id'));
  });

  it('should never expose a dedicated route for Copy under /staff/catalogue (K.4, DEV-06.9)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const catalogueRoute = staffRoute?.children?.find((child) => child.path === 'catalogue');
    const childPaths = catalogueRoute?.children?.map((child) => child.path) ?? [];

    expect(childPaths.some((path) => path?.includes('copies'))).toBe(false);
    expect(childPaths).not.toContain('authors');
    expect(childPaths).not.toContain('genres');
  });

  it('should protect /staff/articles with permissionGuard and ARTICLE_MANAGE (DEV-11.12)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const articlesRoute = staffRoute?.children?.find((child) => child.path === 'articles');

    expect(articlesRoute?.canActivate).toContain(permissionGuard);
    expect(articlesRoute?.data?.['permissions']).toEqual(['ARTICLE_MANAGE']);
  });

  it('should expose the staff articles list, create, tags and detail routes as children of /staff/articles (DEV-11.12)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const articlesRoute = staffRoute?.children?.find((child) => child.path === 'articles');
    const childPaths = articlesRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('');
    expect(childPaths).toContain('new');
    expect(childPaths).toContain('tags');
    expect(childPaths).toContain(':id');
  });

  it('should declare /staff/articles/new and /staff/articles/tags before /staff/articles/:id (DEV-11.12)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const articlesRoute = staffRoute?.children?.find((child) => child.path === 'articles');
    const childPaths = articlesRoute?.children?.map((child) => child.path) ?? [];

    expect(childPaths.indexOf('new')).toBeLessThan(childPaths.indexOf(':id'));
    expect(childPaths.indexOf('tags')).toBeLessThan(childPaths.indexOf(':id'));
  });

  it('should never add a separate guard to /staff/articles/tags — it inherits ARTICLE_MANAGE from /staff/articles (DEV-11.12)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const articlesRoute = staffRoute?.children?.find((child) => child.path === 'articles');
    const tagsRoute = articlesRoute?.children?.find((child) => child.path === 'tags');

    expect(tagsRoute?.canActivate).toBeUndefined();
    expect(tagsRoute?.data).toBeUndefined();
  });

  it('should protect /staff/loans with permissionGuard and LOAN_READ (DEV-07.9)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const loansRoute = staffRoute?.children?.find((child) => child.path === 'loans');

    expect(loansRoute?.canActivate).toContain(permissionGuard);
    expect(loansRoute?.data?.['permissions']).toEqual(['LOAN_READ']);
  });

  it('should expose a single list route as the only child of /staff/loans, no :id route (DEV-DEC-0031, DEV-07.9)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const loansRoute = staffRoute?.children?.find((child) => child.path === 'loans');
    const childPaths = loansRoute?.children?.map((child) => child.path) ?? [];

    expect(childPaths).toEqual(['']);
  });

  it('should lazy-load StaffLoansPage for /staff/loans (DEV-07.9)', async () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const loansRoute = staffRoute?.children?.find((child) => child.path === 'loans');
    const listRoute = loansRoute?.children?.find((child) => child.path === '');

    expect(listRoute?.loadComponent).toBeDefined();
    const loadedComponent = await listRoute!.loadComponent!();

    expect(loadedComponent).toBe(StaffLoansPage);
  });

  it('should protect /staff/reservations with permissionGuard and RESERVATION_READ (DEV-08.14)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const reservationsRoute = staffRoute?.children?.find((child) => child.path === 'reservations');

    expect(reservationsRoute?.canActivate).toContain(permissionGuard);
    expect(reservationsRoute?.data?.['permissions']).toEqual(['RESERVATION_READ']);
  });

  it('should never require RESERVATION_MANAGE at the /staff/reservations route level (DEV-08.14)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const reservationsRoute = staffRoute?.children?.find((child) => child.path === 'reservations');

    expect(reservationsRoute?.data?.['permissions']).not.toContain('RESERVATION_MANAGE');
  });

  it('should expose a single list route as the only child of /staff/reservations, no :id route (DEV-DEC-0036, DEV-08.14)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const reservationsRoute = staffRoute?.children?.find((child) => child.path === 'reservations');
    const childPaths = reservationsRoute?.children?.map((child) => child.path) ?? [];

    expect(childPaths).toEqual(['']);
  });

  it('should lazy-load StaffReservationsPage for /staff/reservations (DEV-08.14)', async () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const reservationsRoute = staffRoute?.children?.find((child) => child.path === 'reservations');
    const listRoute = reservationsRoute?.children?.find((child) => child.path === '');

    expect(listRoute?.loadComponent).toBeDefined();
    const loadedComponent = await listRoute!.loadComponent!();

    expect(loadedComponent).toBe(StaffReservationsPage);
  });

  it('should protect /staff/fines with permissionGuard and FINE_READ (DEV-09.13)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const finesRoute = staffRoute?.children?.find((child) => child.path === 'fines');

    expect(finesRoute?.canActivate).toContain(permissionGuard);
    expect(finesRoute?.data?.['permissions']).toEqual(['FINE_READ']);
  });

  it('should never require FINE_MANAGE at the /staff/fines route level (DEV-09.13)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const finesRoute = staffRoute?.children?.find((child) => child.path === 'fines');

    expect(finesRoute?.data?.['permissions']).not.toContain('FINE_MANAGE');
  });

  it('should expose a single list route as the only child of /staff/fines, no :id route (DEV-09.13)', () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const finesRoute = staffRoute?.children?.find((child) => child.path === 'fines');
    const childPaths = finesRoute?.children?.map((child) => child.path) ?? [];

    expect(childPaths).toEqual(['']);
  });

  it('should lazy-load StaffFinesPage for /staff/fines (DEV-09.13)', async () => {
    const staffRoute = routes.find((candidate) => candidate.path === 'staff');
    const finesRoute = staffRoute?.children?.find((child) => child.path === 'fines');
    const listRoute = finesRoute?.children?.find((child) => child.path === '');

    expect(listRoute?.loadComponent).toBeDefined();
    const loadedComponent = await listRoute!.loadComponent!();

    expect(loadedComponent).toBe(StaffFinesPage);
  });

  it('should redirect the bare /admin zone to /admin/users (DEV-05.12)', () => {
    const adminRoute = routes.find((candidate) => candidate.path === 'admin');
    const redirectChild = adminRoute?.children?.find((child) => child.path === '');

    expect(redirectChild?.redirectTo).toBe('users');
  });

  it('should protect /admin/users with permissionGuard and USER_MANAGE (DEV-05.12)', () => {
    const adminRoute = routes.find((candidate) => candidate.path === 'admin');
    const usersRoute = adminRoute?.children?.find((child) => child.path === 'users');

    expect(usersRoute?.canActivate).toContain(permissionGuard);
    expect(usersRoute?.data?.['permissions']).toEqual(['USER_MANAGE']);
  });

  it('should expose the admin users list, create and detail routes as children of /admin/users (DEV-05.12)', () => {
    const adminRoute = routes.find((candidate) => candidate.path === 'admin');
    const usersRoute = adminRoute?.children?.find((child) => child.path === 'users');
    const childPaths = usersRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('');
    expect(childPaths).toContain('new');
    expect(childPaths).toContain(':id');
  });

  it('should declare /admin/users/new before /admin/users/:id (DEV-05.12)', () => {
    const adminRoute = routes.find((candidate) => candidate.path === 'admin');
    const usersRoute = adminRoute?.children?.find((child) => child.path === 'users');
    const childPaths = usersRoute?.children?.map((child) => child.path) ?? [];

    expect(childPaths.indexOf('new')).toBeLessThan(childPaths.indexOf(':id'));
  });

  it('should protect /admin/settings with permissionGuard and SETTING_READ (DEV-12.3)', () => {
    const adminRoute = routes.find((candidate) => candidate.path === 'admin');
    const settingsRoute = adminRoute?.children?.find((child) => child.path === 'settings');

    expect(settingsRoute?.canActivate).toContain(permissionGuard);
    expect(settingsRoute?.data?.['permissions']).toEqual(['SETTING_READ']);
  });

  it('should never require SETTING_MANAGE at the /admin/settings route level (DEV-12.3)', () => {
    const adminRoute = routes.find((candidate) => candidate.path === 'admin');
    const settingsRoute = adminRoute?.children?.find((child) => child.path === 'settings');

    expect(settingsRoute?.data?.['permissions']).not.toContain('SETTING_MANAGE');
  });

  it('should expose a single list route as the only child of /admin/settings, no :id route (DEV-12.3)', () => {
    const adminRoute = routes.find((candidate) => candidate.path === 'admin');
    const settingsRoute = adminRoute?.children?.find((child) => child.path === 'settings');
    const childPaths = settingsRoute?.children?.map((child) => child.path) ?? [];

    expect(childPaths).toEqual(['']);
  });

  it('should lazy-load AdminSettingsPage for /admin/settings (DEV-12.3)', async () => {
    const adminRoute = routes.find((candidate) => candidate.path === 'admin');
    const settingsRoute = adminRoute?.children?.find((child) => child.path === 'settings');
    const listRoute = settingsRoute?.children?.find((child) => child.path === '');

    expect(listRoute?.loadComponent).toBeDefined();
    const loadedComponent = await listRoute!.loadComponent!();

    expect(loadedComponent).toBe(AdminSettingsPage);
  });

  it('should never change the existing /admin redirect to /admin/users (DEV-12.3)', () => {
    const adminRoute = routes.find((candidate) => candidate.path === 'admin');
    const redirectChild = adminRoute?.children?.find((child) => child.path === '');

    expect(redirectChild?.redirectTo).toBe('users');
  });

  it('should redirect the bare /member zone to /member/profile (DEV-05.13)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const redirectChild = memberRoute?.children?.find((child) => child.path === '');

    expect(redirectChild?.redirectTo).toBe('profile');
  });

  it('should protect /member with roleGuard and ROLE_MEMBER (DEV-05.13)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');

    expect(memberRoute?.canActivate).toContain(roleGuard);
    expect(memberRoute?.data?.['roles']).toEqual(['ROLE_MEMBER']);
  });

  it('should expose the member profile route as a child of /member (DEV-05.13)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const childPaths = memberRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('');
    expect(childPaths).toContain('profile');
  });

  it('should expose /member/loans as a child of /member, accessible for ROLE_MEMBER via the zone-level roleGuard (DEV-07.8)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const childPaths = memberRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('loans');
    expect(memberRoute?.canActivate).toContain(roleGuard);
    expect(memberRoute?.data?.['roles']).toEqual(['ROLE_MEMBER']);
  });

  it('should never add its own guard to /member/loans — it inherits the zone-level roleGuard (DEV-07.8)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const loansRoute = memberRoute?.children?.find((child) => child.path === 'loans');

    expect(loansRoute?.canActivate).toBeUndefined();
    expect(loansRoute?.data).toBeUndefined();
  });

  it('should lazy-load MemberLoansPage for /member/loans (DEV-07.8)', async () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const loansRoute = memberRoute?.children?.find((child) => child.path === 'loans');

    expect(loansRoute?.loadComponent).toBeDefined();
    const loadedComponent = await loansRoute!.loadComponent!();

    expect(loadedComponent).toBe(MemberLoansPage);
  });

  it('should expose /member/reservations as a child of /member, accessible for ROLE_MEMBER via the zone-level roleGuard (DEV-08.14)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const childPaths = memberRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('reservations');
    expect(memberRoute?.canActivate).toContain(roleGuard);
    expect(memberRoute?.data?.['roles']).toEqual(['ROLE_MEMBER']);
  });

  it('should never add its own guard to /member/reservations — it inherits the zone-level roleGuard (DEV-08.14)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const reservationsRoute = memberRoute?.children?.find((child) => child.path === 'reservations');

    expect(reservationsRoute?.canActivate).toBeUndefined();
    expect(reservationsRoute?.data).toBeUndefined();
  });

  it('should lazy-load MemberReservationsPage for /member/reservations (DEV-08.14)', async () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const reservationsRoute = memberRoute?.children?.find((child) => child.path === 'reservations');

    expect(reservationsRoute?.loadComponent).toBeDefined();
    const loadedComponent = await reservationsRoute!.loadComponent!();

    expect(loadedComponent).toBe(MemberReservationsPage);
  });

  it('should expose /member/fines as a child of /member, accessible for ROLE_MEMBER via the zone-level roleGuard (DEV-09.12)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const childPaths = memberRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('fines');
    expect(memberRoute?.canActivate).toContain(roleGuard);
    expect(memberRoute?.data?.['roles']).toEqual(['ROLE_MEMBER']);
  });

  it('should never add its own guard to /member/fines — it inherits the zone-level roleGuard (DEV-09.12)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const finesRoute = memberRoute?.children?.find((child) => child.path === 'fines');

    expect(finesRoute?.canActivate).toBeUndefined();
    expect(finesRoute?.data).toBeUndefined();
  });

  it('should lazy-load MemberFinesPage for /member/fines (DEV-09.12)', async () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const finesRoute = memberRoute?.children?.find((child) => child.path === 'fines');

    expect(finesRoute?.loadComponent).toBeDefined();
    const loadedComponent = await finesRoute!.loadComponent!();

    expect(loadedComponent).toBe(MemberFinesPage);
  });

  it('should expose /member/notifications as a child of /member, accessible for ROLE_MEMBER via the zone-level roleGuard (DEV-10.10)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const childPaths = memberRoute?.children?.map((child) => child.path);

    expect(childPaths).toContain('notifications');
    expect(memberRoute?.canActivate).toContain(roleGuard);
    expect(memberRoute?.data?.['roles']).toEqual(['ROLE_MEMBER']);
  });

  it('should never add its own guard to /member/notifications — it inherits the zone-level roleGuard (DEV-10.10)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const notificationsRoute = memberRoute?.children?.find((child) => child.path === 'notifications');

    expect(notificationsRoute?.canActivate).toBeUndefined();
    expect(notificationsRoute?.data).toBeUndefined();
  });

  it('should lazy-load MemberNotificationsPage for /member/notifications (DEV-10.10)', async () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');
    const notificationsRoute = memberRoute?.children?.find((child) => child.path === 'notifications');

    expect(notificationsRoute?.loadComponent).toBeDefined();
    const loadedComponent = await notificationsRoute!.loadComponent!();

    expect(loadedComponent).toBe(MemberNotificationsPage);
  });

  it('should never add roleGuard to /staff or /admin (DEV-05.13)', () => {
    for (const path of ['staff', 'admin']) {
      const route = routes.find((candidate) => candidate.path === path);
      expect(route?.canActivate ?? []).not.toContain(roleGuard);
    }
  });

  it('should never add permissionGuard to /member (DEV-05.13)', () => {
    const memberRoute = routes.find((candidate) => candidate.path === 'member');

    expect(memberRoute?.canActivate ?? []).not.toContain(permissionGuard);
  });
});
