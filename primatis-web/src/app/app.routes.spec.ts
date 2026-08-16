import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';
import { routes } from './app.routes';

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
});
