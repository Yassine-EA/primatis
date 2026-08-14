import { NavigationItem, isNavigationItemVisible } from './navigation-item';

describe('isNavigationItemVisible', () => {
  const unconstrained: NavigationItem = { label: 'Accueil', routerLink: '/' };
  const singlePermission: NavigationItem = { label: 'X', routerLink: '/x', requiredPermissions: ['USER_READ'] };
  const multiPermission: NavigationItem = {
    label: 'Y',
    routerLink: '/y',
    requiredPermissions: ['USER_READ', 'ROLE_READ'],
  };
  const singleRole: NavigationItem = { label: 'Admin', routerLink: '/admin', requiredRoles: ['ROLE_ADMIN'] };
  const multiRole: NavigationItem = {
    label: 'Staff or Admin',
    routerLink: '/staff',
    requiredRoles: ['ROLE_LIBRARIAN', 'ROLE_ADMIN'],
  };

  it('should always show an item without any constraint, even for an anonymous user', () => {
    expect(isNavigationItemVisible(unconstrained, false, [], [])).toBe(true);
  });

  it('should hide a permission-constrained item for an anonymous user', () => {
    expect(isNavigationItemVisible(singlePermission, false, [], [])).toBe(false);
  });

  it('should show a permission-constrained item when the required permission is granted', () => {
    expect(isNavigationItemVisible(singlePermission, true, [], ['USER_READ'])).toBe(true);
  });

  it('should hide a permission-constrained item when the required permission is missing', () => {
    expect(isNavigationItemVisible(singlePermission, true, [], ['CATALOGUE_READ'])).toBe(false);
  });

  it('should require ALL declared permissions (semantics: ALL, not ANY)', () => {
    expect(isNavigationItemVisible(multiPermission, true, [], ['USER_READ'])).toBe(false);
    expect(isNavigationItemVisible(multiPermission, true, [], ['USER_READ', 'ROLE_READ'])).toBe(true);
  });

  it('should show a role-constrained item when the required role is granted', () => {
    expect(isNavigationItemVisible(singleRole, true, ['ROLE_ADMIN'], [])).toBe(true);
  });

  it('should hide a role-constrained item when the required role is absent', () => {
    expect(isNavigationItemVisible(singleRole, true, ['ROLE_MEMBER'], [])).toBe(false);
  });

  it('should require only ANY one of several declared roles (semantics: ANY, not ALL)', () => {
    expect(isNavigationItemVisible(multiRole, true, ['ROLE_ADMIN'], [])).toBe(true);
    expect(isNavigationItemVisible(multiRole, true, ['ROLE_LIBRARIAN'], [])).toBe(true);
    expect(isNavigationItemVisible(multiRole, true, ['ROLE_MEMBER'], [])).toBe(false);
  });

  it('should hide a role-constrained item for an anonymous user', () => {
    expect(isNavigationItemVisible(singleRole, false, [], [])).toBe(false);
  });
});
