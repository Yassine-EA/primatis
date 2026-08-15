import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { ApiErrorResponse } from '../../core/models/api-error-response';
import { AuthService } from './auth.service';

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

function pastExp(): number {
  return Math.floor(Date.now() / 1000) - 60;
}

describe('AuthService', () => {
  let httpTestingController: HttpTestingController;

  function createService(): AuthService {
    return TestBed.inject(AuthService);
  }

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    sessionStorage.clear();
    localStorage.clear();
  });

  it('should start anonymous when no token is stored', () => {
    const service = createService();

    expect(service.authenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.userId()).toBeNull();
    expect(service.roles()).toEqual([]);
    expect(service.permissions()).toEqual([]);
  });

  it('should restore a valid, unexpired session from sessionStorage', () => {
    const token = buildJwt({ sub: '7', roles: ['ROLE_LIBRARIAN'], permissions: ['LOAN_MANAGE'], exp: futureExp() });
    sessionStorage.setItem(STORAGE_KEY, token);

    const service = createService();

    expect(service.authenticated()).toBe(true);
    expect(service.token()).toBe(token);
    expect(service.userId()).toBe('7');
    expect(service.roles()).toEqual(['ROLE_LIBRARIAN']);
    expect(service.permissions()).toEqual(['LOAN_MANAGE']);
  });

  it('should discard a malformed token found in sessionStorage', () => {
    sessionStorage.setItem(STORAGE_KEY, 'not-a-jwt');

    const service = createService();

    expect(service.authenticated()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('should discard an expired token found in sessionStorage', () => {
    const token = buildJwt({ sub: '7', roles: [], permissions: [], exp: pastExp() });
    sessionStorage.setItem(STORAGE_KEY, token);

    const service = createService();

    expect(service.authenticated()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('should persist the token and build state from its claims on successful login', () => {
    const service = createService();
    const token = buildJwt({
      sub: '9',
      roles: ['ROLE_MEMBER'],
      permissions: ['CATALOGUE_READ', 'ARTICLE_READ'],
      exp: futureExp(),
    });

    let completed = false;
    service.login('member@primatis.test', 'Correct-Password-2026!').subscribe(() => {
      completed = true;
    });

    httpTestingController.expectOne('/api/v1/auth/login').flush({
      token,
      tokenType: 'Bearer',
      expiresAt: '2026-08-15T13:00:00Z',
      expiresInSeconds: 3600,
    });

    expect(completed).toBe(true);
    expect(service.authenticated()).toBe(true);
    expect(service.userId()).toBe('9');
    expect(service.roles()).toEqual(['ROLE_MEMBER']);
    expect(service.permissions()).toEqual(['CATALOGUE_READ', 'ARTICLE_READ']);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBe(token);
  });

  it('should never touch localStorage, only sessionStorage', () => {
    const service = createService();
    const token = buildJwt({ sub: '9', roles: [], permissions: [], exp: futureExp() });

    service.login('member@primatis.test', 'Correct-Password-2026!').subscribe();
    httpTestingController.expectOne('/api/v1/auth/login').flush({
      token,
      tokenType: 'Bearer',
      expiresAt: '2026-08-15T13:00:00Z',
      expiresInSeconds: 3600,
    });

    expect(localStorage.length).toBe(0);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('should not persist anything and keep anonymous state when login fails', () => {
    const service = createService();

    let errored = false;
    service.login('member@primatis.test', 'Wrong-Password').subscribe({
      error: () => {
        errored = true;
      },
    });

    const errorBody: ApiErrorResponse = {
      timestamp: '2026-08-15T12:00:00Z',
      status: 401,
      error: 'Unauthorized',
      code: 'INVALID_CREDENTIALS',
      message: 'Identifiants invalides.',
      path: '/api/v1/auth/login',
      fieldErrors: [],
    };
    httpTestingController
      .expectOne('/api/v1/auth/login')
      .flush(errorBody, { status: 401, statusText: 'Unauthorized' });

    expect(errored).toBe(true);
    expect(service.authenticated()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('should clear session state and storage on logout', () => {
    const token = buildJwt({ sub: '7', roles: ['ROLE_LIBRARIAN'], permissions: [], exp: futureExp() });
    sessionStorage.setItem(STORAGE_KEY, token);
    const service = createService();
    expect(service.authenticated()).toBe(true);

    service.logout();

    expect(service.authenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(service.userId()).toBeNull();
    expect(service.roles()).toEqual([]);
    expect(service.permissions()).toEqual([]);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('should expose hasRole/hasPermission helpers derived from the restored claims', () => {
    const token = buildJwt({
      sub: '7',
      roles: ['ROLE_LIBRARIAN'],
      permissions: ['LOAN_MANAGE'],
      exp: futureExp(),
    });
    sessionStorage.setItem(STORAGE_KEY, token);
    const service = createService();

    expect(service.hasRole('ROLE_LIBRARIAN')).toBe(true);
    expect(service.hasRole('ROLE_ADMIN')).toBe(false);
    expect(service.hasPermission('LOAN_MANAGE')).toBe(true);
    expect(service.hasPermission('ROLE_MANAGE')).toBe(false);
  });

  it('should report an expired session via isSessionExpired without a timer', () => {
    const token = buildJwt({ sub: '7', roles: [], permissions: [], exp: futureExp() });
    sessionStorage.setItem(STORAGE_KEY, token);
    const service = createService();

    expect(service.isSessionExpired()).toBe(false);

    service.logout();

    expect(service.isSessionExpired()).toBe(true);
  });

  it('should not keep reporting authenticated=true once the JWT exp has passed, even without any new state mutation', () => {
    vi.useFakeTimers();
    try {
      const now = Date.now();
      vi.setSystemTime(now);

      const token = buildJwt({
        sub: '7',
        roles: ['ROLE_LIBRARIAN'],
        permissions: ['LOAN_MANAGE'],
        exp: Math.floor(now / 1000) + 2,
      });
      sessionStorage.setItem(STORAGE_KEY, token);
      const service = createService();

      expect(service.authenticated()).toBe(true);

      // Aucune mutation d'état (pas de login/logout/restore) : seul le
      // temps avance. `authenticated` ne doit pas rester figé sur une
      // valeur mise en cache lors de la restauration.
      vi.setSystemTime(now + 3_000);

      expect(service.authenticated()).toBe(false);
      expect(service.userId()).toBeNull();
      expect(service.roles()).toEqual([]);
      expect(service.permissions()).toEqual([]);
    } finally {
      vi.useRealTimers();
    }
  });
});
