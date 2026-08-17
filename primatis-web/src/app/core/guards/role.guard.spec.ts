import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../auth/services/auth.service';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  let authServiceMock: {
    authenticated: ReturnType<typeof vi.fn>;
    roles: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(() => {
    authServiceMock = {
      authenticated: vi.fn(),
      roles: vi.fn(),
      logout: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceMock }],
    });

    router = TestBed.inject(Router);
  });

  function runGuard(rolesData: unknown, url = '/member/profile'): boolean | UrlTree {
    const route = { data: { roles: rolesData } } as unknown as ActivatedRouteSnapshot;
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => roleGuard(route, state)) as boolean | UrlTree;
  }

  it('should allow activation when authenticated and ROLE_MEMBER is granted', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);

    expect(runGuard(['ROLE_MEMBER'])).toBe(true);
  });

  it('should return a UrlTree to /forbidden when authenticated but the required role is missing', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);

    const result = runGuard(['ROLE_MEMBER']) as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result)).toBe('/forbidden');
  });

  it('should return a UrlTree to /login with returnUrl when not authenticated', () => {
    authServiceMock.authenticated.mockReturnValue(false);
    authServiceMock.roles.mockReturnValue([]);

    const result = runGuard(['ROLE_MEMBER'], '/member/profile') as UrlTree;

    expect(router.serializeUrl(result)).toBe('/login?returnUrl=%2Fmember%2Fprofile');
  });

  it('should use ANY semantics: allow when only one of several required roles is granted', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);

    expect(runGuard(['ROLE_MEMBER', 'ROLE_LIBRARIAN'])).toBe(true);
  });

  it('should allow a multi-role user carrying ROLE_MEMBER among other roles', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN', 'ROLE_MEMBER']);

    expect(runGuard(['ROLE_MEMBER'])).toBe(true);
  });

  it('should fail closed (redirect to /forbidden) when route.data.roles is missing', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);

    const result = runGuard(undefined) as UrlTree;

    expect(router.serializeUrl(result)).toBe('/forbidden');
  });

  it('should fail closed when route.data.roles is an empty array', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);

    const result = runGuard([]) as UrlTree;

    expect(router.serializeUrl(result)).toBe('/forbidden');
  });

  it('should fail closed when route.data.roles is malformed (not a string array)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);

    const result = runGuard(['ROLE_MEMBER', 42]) as UrlTree;

    expect(router.serializeUrl(result)).toBe('/forbidden');
  });

  it('should never call logout when access is denied for a missing role', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue([]);

    runGuard(['ROLE_MEMBER']);

    expect(authServiceMock.logout).not.toHaveBeenCalled();
  });

  it('should never manipulate sessionStorage/localStorage directly', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue([]);
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');
    const removeItemSpy = vi.spyOn(Storage.prototype, 'removeItem');

    runGuard(['ROLE_MEMBER']);

    expect(setItemSpy).not.toHaveBeenCalled();
    expect(removeItemSpy).not.toHaveBeenCalled();
    setItemSpy.mockRestore();
    removeItemSpy.mockRestore();
  });
});
