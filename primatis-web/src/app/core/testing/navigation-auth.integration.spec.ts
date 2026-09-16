import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../auth/services/auth.service';
import { PublicNavigation } from '../layouts/public-layout/public-navigation/public-navigation';
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
 * DEV-04.12 / DEV-15.3 : `PublicNavigation` (ex-`Navigation` monolithique,
 * remplacée par une navigation dédiée par univers) avec un `AuthService`
 * réel (jamais mocké, contrairement à `public-navigation.spec.ts`) —
 * preuve que la visibilité des liens et le logout (via `AccountMenu`,
 * rendu par `PublicNavigation`) fonctionnent avec la chaîne réelle
 * sessionStorage -> claims -> Signals-like accessors, pas seulement avec
 * un double de test.
 */
describe('PublicNavigation + real AuthService integration', () => {
  let router: Router;

  function renderWithClaims(claims: Record<string, unknown> | null): {
    fixture: ComponentFixture<PublicNavigation>;
    authService: AuthService;
  } {
    sessionStorage.clear();
    if (claims) {
      sessionStorage.setItem(STORAGE_KEY, buildJwt(claims));
    }

    TestBed.configureTestingModule({
      imports: [PublicNavigation],
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
    const fixture = TestBed.createComponent(PublicNavigation);
    fixture.detectChanges();

    return { fixture, authService };
  }

  afterEach(() => {
    sessionStorage.clear();
  });

  function linkLabels(fixture: ComponentFixture<PublicNavigation>): string[] {
    const anchors: HTMLAnchorElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.public-navigation-desktop .nav-list a'),
    );
    return anchors.map((anchor) => anchor.textContent?.trim() ?? '');
  }

  it('should show only Accueil, Catalogue, Articles, Georges Lemaître and Connexion for a real anonymous AuthService', () => {
    const { fixture } = renderWithClaims(null);

    // DEV-06.8/DEV-11.11/DEV-15.4 : Catalogue, Articles et Georges Lemaître
    // sont toujours visibles (aucun requiredRoles), même statut qu'Accueil —
    // déviation mécanique, pas une régression.
    expect(linkLabels(fixture)).toEqual(['Accueil', 'Catalogue', 'Articles', 'Georges Lemaître']);
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

  it('should really clear sessionStorage and reset AuthService state when logging out from PublicNavigation', () => {
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
