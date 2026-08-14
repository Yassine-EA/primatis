import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { appConfig } from '../../app.config';
import { AuthService } from '../../auth/services/auth.service';
import { API_BASE_URL } from '../api/api-base-url.token';
import { ApiErrorResponse } from '../models/api-error-response';
import { authInterceptor } from './auth.interceptor';

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

function apiError(code: string): ApiErrorResponse {
  return {
    timestamp: '2026-08-15T12:00:00Z',
    status: 0,
    error: 'Error',
    code,
    message: 'Message backend non exploité tel quel côté frontend.',
    path: '/api/v1',
    fieldErrors: [],
  };
}

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
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
    localStorage.clear();
  });

  function seedValidToken(): string {
    const token = buildJwt({
      sub: '7',
      roles: ['ROLE_LIBRARIAN'],
      permissions: ['LOAN_MANAGE'],
      exp: futureExp(),
    });
    sessionStorage.setItem(STORAGE_KEY, token);
    return token;
  }

  it('should attach Bearer <token> to a protected /api/v1 request when a valid token is present', () => {
    const token = seedValidToken();
    TestBed.inject(AuthService);

    httpClient.get('/api/v1/titles').subscribe();

    const request = httpTestingController.expectOne('/api/v1/titles');
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    request.flush({});
  });

  it('should not modify an API request when no token is present', () => {
    TestBed.inject(AuthService);

    httpClient.get('/api/v1/titles').subscribe();

    const request = httpTestingController.expectOne('/api/v1/titles');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('should never attach the Bearer token to an external URL', () => {
    seedValidToken();
    TestBed.inject(AuthService);

    httpClient.get('https://attacker.example/api/v1/steal').subscribe();

    const request = httpTestingController.expectOne('https://attacker.example/api/v1/steal');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('should never attach the Bearer token to a path that only shares a string prefix with the API base', () => {
    seedValidToken();
    TestBed.inject(AuthService);

    httpClient.get('/api/v1xyz/evil').subscribe();

    const request = httpTestingController.expectOne('/api/v1xyz/evil');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('should not attach an expired token and should clear the local session before sending', () => {
    vi.useFakeTimers();
    try {
      const now = Date.now();
      vi.setSystemTime(now);
      const token = buildJwt({ sub: '7', roles: [], permissions: [], exp: Math.floor(now / 1000) + 2 });
      sessionStorage.setItem(STORAGE_KEY, token);
      const authService = TestBed.inject(AuthService);
      expect(authService.authenticated()).toBe(true);

      vi.setSystemTime(now + 3_000);

      httpClient.get('/api/v1/titles').subscribe();

      const request = httpTestingController.expectOne('/api/v1/titles');
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({});

      expect(authService.authenticated()).toBe(false);
      expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('should clear the local session and rethrow the error on 401 INVALID_TOKEN', () => {
    seedValidToken();
    const authService = TestBed.inject(AuthService);

    let receivedError: unknown;
    httpClient.get('/api/v1/protected/sample').subscribe({ error: (error: unknown) => (receivedError = error) });

    httpTestingController
      .expectOne('/api/v1/protected/sample')
      .flush(apiError('INVALID_TOKEN'), { status: 401, statusText: 'Unauthorized' });

    expect(receivedError).toBeInstanceOf(HttpErrorResponse);
    expect((receivedError as HttpErrorResponse).status).toBe(401);
    expect(authService.authenticated()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('should clear the local session and rethrow the error on 401 AUTHENTICATION_REQUIRED', () => {
    seedValidToken();
    const authService = TestBed.inject(AuthService);

    let receivedError: unknown;
    httpClient.get('/api/v1/protected/sample').subscribe({ error: (error: unknown) => (receivedError = error) });

    httpTestingController
      .expectOne('/api/v1/protected/sample')
      .flush(apiError('AUTHENTICATION_REQUIRED'), { status: 401, statusText: 'Unauthorized' });

    expect(receivedError).toBeInstanceOf(HttpErrorResponse);
    expect(authService.authenticated()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('should NOT clear an existing session on a 401 INVALID_CREDENTIALS from /auth/login', () => {
    const token = seedValidToken();
    const authService = TestBed.inject(AuthService);

    let receivedError: unknown;
    httpClient
      .post('/api/v1/auth/login', { email: 'x@primatis.test', password: 'wrong' })
      .subscribe({ error: (error: unknown) => (receivedError = error) });

    httpTestingController
      .expectOne('/api/v1/auth/login')
      .flush(apiError('INVALID_CREDENTIALS'), { status: 401, statusText: 'Unauthorized' });

    expect(receivedError).toBeInstanceOf(HttpErrorResponse);
    expect(authService.authenticated()).toBe(true);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBe(token);
  });

  it('should NOT clear an existing session on a 401 ACCOUNT_TEMPORARILY_LOCKED from /auth/login', () => {
    const token = seedValidToken();
    const authService = TestBed.inject(AuthService);

    let receivedError: unknown;
    httpClient
      .post('/api/v1/auth/login', { email: 'x@primatis.test', password: 'wrong' })
      .subscribe({ error: (error: unknown) => (receivedError = error) });

    httpTestingController
      .expectOne('/api/v1/auth/login')
      .flush(apiError('ACCOUNT_TEMPORARILY_LOCKED'), { status: 401, statusText: 'Unauthorized' });

    expect(receivedError).toBeInstanceOf(HttpErrorResponse);
    expect(authService.authenticated()).toBe(true);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBe(token);
  });

  it('should preserve the session and token and rethrow the error on 403 ACCESS_DENIED', () => {
    const token = seedValidToken();
    const authService = TestBed.inject(AuthService);

    let receivedError: unknown;
    httpClient
      .get('/api/v1/protected/role-manage-only')
      .subscribe({ error: (error: unknown) => (receivedError = error) });

    httpTestingController
      .expectOne('/api/v1/protected/role-manage-only')
      .flush(apiError('ACCESS_DENIED'), { status: 403, statusText: 'Forbidden' });

    expect(receivedError).toBeInstanceOf(HttpErrorResponse);
    expect((receivedError as HttpErrorResponse).status).toBe(403);
    expect(authService.authenticated()).toBe(true);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBe(token);
  });
});

describe('authInterceptor production wiring (app.config.ts)', () => {
  afterEach(() => {
    sessionStorage.clear();
  });

  it('should be applied end-to-end when provideHttpClient is configured via the real appConfig providers', () => {
    sessionStorage.setItem(
      STORAGE_KEY,
      buildJwt({ sub: '7', roles: [], permissions: [], exp: futureExp() }),
    );

    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });

    const client = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService);

    client.get('/api/v1/titles').subscribe();

    const request = controller.expectOne('/api/v1/titles');
    expect(request.request.headers.has('Authorization')).toBe(true);
    request.flush({});
    controller.verify();
  });
});
