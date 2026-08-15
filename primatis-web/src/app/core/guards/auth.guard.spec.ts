import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../auth/services/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let authServiceMock: {
    authenticated: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(() => {
    authServiceMock = {
      authenticated: vi.fn(),
      logout: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceMock }],
    });

    router = TestBed.inject(Router);
  });

  function runGuard(url: string): boolean | UrlTree {
    const route = {} as ActivatedRouteSnapshot;
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => authGuard(route, state)) as boolean | UrlTree;
  }

  it('should allow activation when the user is authenticated', () => {
    authServiceMock.authenticated.mockReturnValue(true);

    expect(runGuard('/member')).toBe(true);
  });

  it('should return a UrlTree to /login when the user is anonymous', () => {
    authServiceMock.authenticated.mockReturnValue(false);

    const result = runGuard('/member/loans');

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Fmember%2Floans');
  });

  it('should preserve the requested URL as an internal Angular returnUrl only', () => {
    authServiceMock.authenticated.mockReturnValue(false);

    const result = runGuard('/staff/catalogue?query=1') as UrlTree;
    const serialized = router.serializeUrl(result);

    expect(serialized.startsWith('/login?returnUrl=')).toBe(true);
    expect(serialized).not.toContain('http://');
    expect(serialized).not.toContain('https://');
  });

  it('should never manipulate sessionStorage/localStorage directly', () => {
    authServiceMock.authenticated.mockReturnValue(false);
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');
    const removeItemSpy = vi.spyOn(Storage.prototype, 'removeItem');

    runGuard('/admin');

    expect(setItemSpy).not.toHaveBeenCalled();
    expect(removeItemSpy).not.toHaveBeenCalled();
    setItemSpy.mockRestore();
    removeItemSpy.mockRestore();
  });

  it('should never call logout, even when denying access', () => {
    authServiceMock.authenticated.mockReturnValue(false);

    runGuard('/admin');

    expect(authServiceMock.logout).not.toHaveBeenCalled();
  });
});
