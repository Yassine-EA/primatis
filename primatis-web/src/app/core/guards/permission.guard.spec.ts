import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../auth/services/auth.service';
import { permissionGuard } from './permission.guard';

describe('permissionGuard', () => {
  let authServiceMock: {
    authenticated: ReturnType<typeof vi.fn>;
    permissions: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(() => {
    authServiceMock = {
      authenticated: vi.fn(),
      permissions: vi.fn(),
      logout: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceMock }],
    });

    router = TestBed.inject(Router);
  });

  function runGuard(permissionsData: unknown, url = '/staff/loans'): boolean | UrlTree {
    const route = { data: { permissions: permissionsData } } as unknown as ActivatedRouteSnapshot;
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => permissionGuard(route, state)) as boolean | UrlTree;
  }

  it('should allow activation when authenticated and all required permissions are granted', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['LOAN_MANAGE', 'CATALOGUE_READ']);

    expect(runGuard(['LOAN_MANAGE'])).toBe(true);
  });

  it('should return a UrlTree to /forbidden when authenticated but a required permission is missing', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['CATALOGUE_READ']);

    const result = runGuard(['LOAN_MANAGE']) as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result)).toBe('/forbidden');
  });

  it('should return a UrlTree to /login with returnUrl when not authenticated', () => {
    authServiceMock.authenticated.mockReturnValue(false);
    authServiceMock.permissions.mockReturnValue([]);

    const result = runGuard(['LOAN_MANAGE'], '/admin/users') as UrlTree;

    expect(router.serializeUrl(result)).toBe('/login?returnUrl=%2Fadmin%2Fusers');
  });

  it('should use ALL semantics: deny when only some of the required permissions are granted', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['USER_READ']); // ROLE_READ missing

    const result = runGuard(['USER_READ', 'ROLE_READ']) as UrlTree;

    expect(router.serializeUrl(result)).toBe('/forbidden');
  });

  it('should use ALL semantics: allow only when every required permission is granted', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['USER_READ', 'ROLE_READ', 'SETTING_READ']);

    expect(runGuard(['USER_READ', 'ROLE_READ'])).toBe(true);
  });

  it('should fail closed (redirect to /forbidden) when route.data.permissions is missing', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['LOAN_MANAGE']);

    const result = runGuard(undefined) as UrlTree;

    expect(router.serializeUrl(result)).toBe('/forbidden');
  });

  it('should fail closed when route.data.permissions is an empty array', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['LOAN_MANAGE']);

    const result = runGuard([]) as UrlTree;

    expect(router.serializeUrl(result)).toBe('/forbidden');
  });

  it('should fail closed when route.data.permissions is malformed (not a string array)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['LOAN_MANAGE']);

    const result = runGuard(['LOAN_MANAGE', 42]) as UrlTree;

    expect(router.serializeUrl(result)).toBe('/forbidden');
  });

  it('should never call logout when access is denied for a missing permission', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue([]);

    runGuard(['LOAN_MANAGE']);

    expect(authServiceMock.logout).not.toHaveBeenCalled();
  });

  it('should never manipulate sessionStorage/localStorage directly', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue([]);
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');
    const removeItemSpy = vi.spyOn(Storage.prototype, 'removeItem');

    runGuard(['LOAN_MANAGE']);

    expect(setItemSpy).not.toHaveBeenCalled();
    expect(removeItemSpy).not.toHaveBeenCalled();
    setItemSpy.mockRestore();
    removeItemSpy.mockRestore();
  });
});
