import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../auth/services/auth.service';
import { Navigation } from '../../shared/navigation/navigation';
import { API_BASE_URL } from '../api/api-base-url.token';

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
 * DEV-04.12 : `Navigation` avec un `AuthService` réel (jamais mocké,
 * contrairement à `navigation.spec.ts`) — preuve que la visibilité des
 * liens et le logout fonctionnent avec la chaîne réelle sessionStorage ->
 * claims -> Signals-like accessors, pas seulement avec un double de test.
 */
describe('Navigation + real AuthService integration', () => {
  let router: Router;

  function renderWithClaims(claims: Record<string, unknown> | null): {
    fixture: ComponentFixture<Navigation>;
    authService: AuthService;
  } {
    sessionStorage.clear();
    if (claims) {
      sessionStorage.setItem(STORAGE_KEY, buildJwt(claims));
    }

    TestBed.configureTestingModule({
      imports: [Navigation],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const authService = TestBed.inject(AuthService);
    const fixture = TestBed.createComponent(Navigation);
    fixture.detectChanges();

    return { fixture, authService };
  }

  afterEach(() => {
    sessionStorage.clear();
  });

  function linkLabels(fixture: ComponentFixture<Navigation>): string[] {
    const anchors: HTMLAnchorElement[] = Array.from(fixture.nativeElement.querySelectorAll('.nav-list a'));
    return anchors.map((anchor) => anchor.textContent?.trim() ?? '');
  }

  it('should show only Accueil and Connexion for a real anonymous AuthService', () => {
    const { fixture } = renderWithClaims(null);

    expect(linkLabels(fixture)).toEqual(['Accueil']);
    expect(fixture.nativeElement.textContent).toContain('Connexion');
  });

  it('should show Espace membre when AuthService is really restored with ROLE_MEMBER', () => {
    const { fixture } = renderWithClaims({
      sub: '1',
      roles: ['ROLE_MEMBER'],
      permissions: ['CATALOGUE_READ', 'ARTICLE_READ'],
      exp: futureExp(),
    });

    expect(linkLabels(fixture)).toContain('Espace membre');
  });

  it('should show Espace personnel when AuthService is really restored with ROLE_LIBRARIAN', () => {
    const { fixture } = renderWithClaims({
      sub: '2',
      roles: ['ROLE_LIBRARIAN'],
      permissions: ['LOAN_MANAGE'],
      exp: futureExp(),
    });

    expect(linkLabels(fixture)).toContain('Espace personnel');
  });

  it('should show Administration when AuthService is really restored with ROLE_ADMIN', () => {
    const { fixture } = renderWithClaims({
      sub: '3',
      roles: ['ROLE_ADMIN'],
      permissions: ['ROLE_MANAGE'],
      exp: futureExp(),
    });

    expect(linkLabels(fixture)).toContain('Administration');
  });

  it('should really clear sessionStorage and reset AuthService state when logging out from Navigation', () => {
    const { fixture, authService } = renderWithClaims({
      sub: '2',
      roles: ['ROLE_LIBRARIAN'],
      permissions: [],
      exp: futureExp(),
    });
    expect(authService.authenticated()).toBe(true);
    expect(sessionStorage.getItem(STORAGE_KEY)).not.toBeNull();

    (fixture.nativeElement.querySelector('.nav-logout') as HTMLButtonElement).click();

    expect(authService.authenticated()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });
});
