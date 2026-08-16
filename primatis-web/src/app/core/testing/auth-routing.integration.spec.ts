import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Routes, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { By } from '@angular/platform-browser';

import { routes } from '../../app.routes';
import { Login } from '../../auth/pages/login/login';
import { API_BASE_URL } from '../api/api-base-url.token';
import { permissionGuard } from '../guards/permission.guard';

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

/**
 * DEV-04.12 : preuve que `authGuard` est réellement branché sur les routes
 * de production (`app.routes.ts`) via une navigation Router réelle — pas
 * seulement un appel direct de la fonction guard (déjà couvert
 * unitairement par `auth.guard.spec.ts`).
 */
describe('Routing + AuthGuard integration (real routes, real Router navigation)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('should redirect an anonymous user requesting /member to /login with returnUrl', async () => {
    await RouterTestingHarness.create('/member');
    const location = TestBed.inject(Location);

    expect(location.path()).toBe('/login?returnUrl=%2Fmember');
  });

  it('should redirect an anonymous user requesting /admin to /login with returnUrl', async () => {
    await RouterTestingHarness.create('/admin');
    const location = TestBed.inject(Location);

    // DEV-05.12 : '/admin' redirige désormais vers '/admin/users' (même
    // principe que '/staff' -> '/staff/users', DEV-05.11) ; Angular résout
    // ce redirectTo pendant la reconnaissance de route, avant l'évaluation
    // des guards — returnUrl porte donc déjà l'URL post-redirection.
    expect(location.path()).toBe('/login?returnUrl=%2Fadmin%2Fusers');
  });

  it('should let an authenticated user reach /member directly (AuthGuard passes)', async () => {
    const token = buildJwt({ sub: '1', roles: ['ROLE_MEMBER'], permissions: [], exp: futureExp() });
    sessionStorage.setItem(STORAGE_KEY, token);

    await RouterTestingHarness.create('/member');
    const location = TestBed.inject(Location);

    expect(location.path()).toBe('/member');
  });

  it('should not have permissionGuard artificially applied to /member, /staff or /admin', () => {
    for (const path of ['member', 'staff', 'admin']) {
      const route = routes.find((candidate) => candidate.path === path);
      expect(route?.canActivate).not.toContain(permissionGuard);
    }
  });
});

/**
 * Chaîne complète (DEV-04.9/04.10/04.12) : arrivée sur /login?returnUrl=...
 * via une navigation Router réelle (query params réellement fournis par
 * l'ActivatedRoute, pas un mock), login réussi (HTTP mocké au niveau
 * transport uniquement), puis navigation effective vers returnUrl.
 */
describe('Login returnUrl integration (real routing, real ActivatedRoute)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  async function loginAndReturnLocation(returnUrlQuery: string): Promise<string> {
    const harness = await RouterTestingHarness.create(`/login${returnUrlQuery}`);
    // /login est un enfant de PublicLayout : Login est activé dans le
    // <router-outlet> imbriqué de PublicLayout, pas dans celui de la
    // harness elle-même — on le retrouve par requête dans l'arbre complet.
    const login = harness.fixture.debugElement.query(By.directive(Login))?.componentInstance as Login;
    expect(login).toBeInstanceOf(Login);

    login.form.setValue({ email: 'librarian@primatis.test', password: 'Correct-Password-2026!' });
    login.submit();

    const httpTestingController = TestBed.inject(HttpTestingController);
    const token = buildJwt({ sub: '1', roles: ['ROLE_LIBRARIAN'], permissions: ['LOAN_MANAGE'], exp: futureExp() });
    httpTestingController.expectOne('/api/v1/auth/login').flush({
      token,
      tokenType: 'Bearer',
      expiresAt: '2026-08-15T13:00:00Z',
      expiresInSeconds: 3600,
    });

    await harness.fixture.whenStable();
    return TestBed.inject(Location).path();
  }

  it('should navigate to the internal returnUrl after a successful login', async () => {
    expect(await loginAndReturnLocation('?returnUrl=%2Fmember')).toBe('/member');
  });

  // Location.path() sérialise la racine de l'application en chaîne vide
  // (convention Angular, PathLocationStrategy) — pas '/'. Ce qui compte
  // ici est que la navigation aboutit bien à la racine, jamais à une URL
  // externe : aucun open redirect.
  it('should fall back to "/" when returnUrl is a protocol-relative external URL', async () => {
    expect(await loginAndReturnLocation('?returnUrl=%2F%2Fevil.example')).toBe('');
  });

  it('should fall back to "/" when returnUrl is an absolute external URL', async () => {
    expect(await loginAndReturnLocation('?returnUrl=https%3A%2F%2Fevil.example%2Fsteal')).toBe('');
  });
});

/**
 * DEV-04.12 (H) : permissionGuard avec un AuthService réel (pas mocké),
 * sur une route de test locale à ce fichier — aucune route métier réelle
 * ne justifie encore une permission (DEV-04.9), donc `app.routes.ts` n'est
 * pas modifié pour ce test.
 */
describe('PermissionGuard + real AuthService integration', () => {
  @Component({ selector: 'app-integration-test-target', template: 'protected content' })
  class IntegrationTestTarget {}

  const testRoutes: Routes = [
    {
      path: 'protected-by-permission',
      component: IntegrationTestTarget,
      canActivate: [permissionGuard],
      data: { permissions: ['LOAN_MANAGE'] },
    },
    { path: 'forbidden', component: IntegrationTestTarget },
    { path: 'login', component: IntegrationTestTarget },
  ];

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(testRoutes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('should allow activation when all required permissions are granted (real AuthService)', async () => {
    const token = buildJwt({
      sub: '1',
      roles: ['ROLE_LIBRARIAN'],
      permissions: ['LOAN_MANAGE', 'CATALOGUE_READ'],
      exp: futureExp(),
    });
    sessionStorage.setItem(STORAGE_KEY, token);

    await RouterTestingHarness.create('/protected-by-permission');
    const location = TestBed.inject(Location);

    expect(location.path()).toBe('/protected-by-permission');
  });

  it('should redirect to /forbidden and preserve the session when a required permission is missing', async () => {
    const token = buildJwt({
      sub: '1',
      roles: ['ROLE_LIBRARIAN'],
      permissions: ['CATALOGUE_READ'],
      exp: futureExp(),
    });
    sessionStorage.setItem(STORAGE_KEY, token);

    await RouterTestingHarness.create('/protected-by-permission');
    const location = TestBed.inject(Location);

    expect(location.path()).toBe('/forbidden');
    expect(sessionStorage.getItem(STORAGE_KEY)).toBe(token);
  });

  it('should redirect an anonymous user to /login with returnUrl', async () => {
    await RouterTestingHarness.create('/protected-by-permission');
    const location = TestBed.inject(Location);

    expect(location.path()).toBe('/login?returnUrl=%2Fprotected-by-permission');
  });
});
