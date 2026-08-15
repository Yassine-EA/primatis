import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { AuthService } from '../../auth/services/auth.service';
import { toAppError } from '../errors/api-error.util';
import { authGuard } from '../guards/auth.guard';
import { API_BASE_URL } from '../api/api-base-url.token';
import { authInterceptor } from '../http/auth.interceptor';
import { ApiErrorResponse } from '../models/api-error-response';

const STORAGE_KEY = 'primatis.accessToken';

function base64UrlEncode(value: string): string {
  const base64 = btoa(value);
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function buildJwt(payload: unknown): string {
  const header = base64UrlEncode(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const body = base64UrlEncode(JSON.stringify(payload));
  return `${header}.${body}.signature-not-verified-by-frontend`;
}

function futureExp(): number {
  return Math.floor(Date.now() / 1000) + 3600;
}

function apiError(code: string, status: number): ApiErrorResponse {
  return {
    timestamp: '2026-08-15T12:00:00Z',
    status,
    error: 'Error',
    code,
    message: 'Message backend.',
    path: '/api/v1/protected/sample',
    fieldErrors: [],
  };
}

/**
 * DEV-04.12 : chaînes transversales entre `AuthService`, l'intercepteur JWT
 * (DEV-04.8), `AuthGuard` (DEV-04.9) et la normalisation d'erreur
 * (DEV-04.11). Les scénarios déjà exhaustivement couverts unitairement
 * (401/403 individuels, rejet d'URL externe, etc. — `auth.interceptor.spec.ts`)
 * ne sont pas dupliqués ici.
 */
describe('Auth + HTTP integration', () => {
  let httpClient: HttpClient;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    sessionStorage.clear();
  });

  it('should stop attaching Authorization after a real logout(), within the same live session', () => {
    const token = buildJwt({ sub: '7', roles: ['ROLE_LIBRARIAN'], permissions: ['LOAN_MANAGE'], exp: futureExp() });
    sessionStorage.setItem(STORAGE_KEY, token);
    const authService = TestBed.inject(AuthService);
    expect(authService.authenticated()).toBe(true);

    httpClient.get('/api/v1/protected/sample').subscribe();
    const first = httpTestingController.expectOne('/api/v1/protected/sample');
    expect(first.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    first.flush({});

    authService.logout();

    httpClient.get('/api/v1/protected/sample').subscribe();
    const second = httpTestingController.expectOne('/api/v1/protected/sample');
    expect(second.request.headers.has('Authorization')).toBe(false);
    second.flush({});
  });

  it('should treat a real expired session as invalid and let AuthGuard redirect to /login', () => {
    vi.useFakeTimers();
    try {
      const now = Date.now();
      vi.setSystemTime(now);
      const token = buildJwt({ sub: '7', roles: ['ROLE_MEMBER'], permissions: [], exp: Math.floor(now / 1000) + 2 });
      sessionStorage.setItem(STORAGE_KEY, token);
      const authService = TestBed.inject(AuthService);
      expect(authService.authenticated()).toBe(true);

      vi.setSystemTime(now + 3_000);
      expect(authService.authenticated()).toBe(false);

      const route = {} as ActivatedRouteSnapshot;
      const state = { url: '/member' } as RouterStateSnapshot;
      const result = TestBed.runInInjectionContext(() => authGuard(route, state));

      expect(result).toBeInstanceOf(UrlTree);
      const router = TestBed.inject(Router);
      expect(router.serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Fmember');
    } finally {
      vi.useRealTimers();
    }
  });

  it('should produce a normalized AppError from a real 401 INVALID_TOKEN propagated by the interceptor', () => {
    const token = buildJwt({ sub: '7', roles: [], permissions: [], exp: futureExp() });
    sessionStorage.setItem(STORAGE_KEY, token);
    TestBed.inject(AuthService);

    let caught: unknown;
    httpClient.get('/api/v1/protected/sample').subscribe({ error: (error: unknown) => (caught = error) });

    httpTestingController
      .expectOne('/api/v1/protected/sample')
      .flush(apiError('INVALID_TOKEN', 401), { status: 401, statusText: 'Unauthorized' });

    expect(caught).toBeInstanceOf(HttpErrorResponse);
    const normalized = toAppError(caught);
    expect(normalized.code).toBe('INVALID_TOKEN');
    expect(normalized.status).toBe(401);
    expect(normalized.fieldErrors).toEqual([]);
  });

  it('should produce a normalized network AppError for a status 0 failure propagated by the interceptor', () => {
    TestBed.inject(AuthService);

    let caught: unknown;
    httpClient.get('/api/v1/protected/sample').subscribe({ error: (error: unknown) => (caught = error) });

    httpTestingController.expectOne('/api/v1/protected/sample').flush('', { status: 0, statusText: 'Unknown Error' });

    expect(caught).toBeInstanceOf(HttpErrorResponse);
    const normalized = toAppError(caught);
    expect(normalized.message).toBe('Impossible de contacter le serveur. Veuillez réessayer.');
    expect(normalized.status).toBe(0);
  });
});
