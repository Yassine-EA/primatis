import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../../../notifications/services/notification-unread-state.service';
import { PublicNavigation } from './public-navigation';

describe('PublicNavigation', () => {
  let fixture: ComponentFixture<PublicNavigation>;
  let authServiceMock: {
    authenticated: ReturnType<typeof vi.fn>;
    roles: ReturnType<typeof vi.fn>;
    permissions: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  function render(): void {
    fixture = TestBed.createComponent(PublicNavigation);
    fixture.detectChanges();
  }

  function linkLabels(): string[] {
    const anchors: HTMLAnchorElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.public-navigation-desktop .nav-list a'),
    );
    return anchors.map((anchor) => anchor.textContent?.trim() ?? '');
  }

  beforeEach(() => {
    authServiceMock = {
      authenticated: vi.fn().mockReturnValue(false),
      roles: vi.fn().mockReturnValue([]),
      permissions: vi.fn().mockReturnValue([]),
      logout: vi.fn(),
    };
    const unreadStateMock = { unreadCount: vi.fn().mockReturnValue(0), refresh: vi.fn(), decrement: vi.fn(), reset: vi.fn() };

    TestBed.configureTestingModule({
      imports: [PublicNavigation],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
      ],
    });
  });

  it('should show only Accueil, Catalogue, Articles and Georges Lemaître for an anonymous visitor', () => {
    render();

    expect(linkLabels()).toEqual(['Accueil', 'Catalogue', 'Articles', 'Georges Lemaître']);
  });

  it('should point Catalogue at /catalogue (DEV-06.8)', () => {
    render();

    expect(fixture.nativeElement.querySelector('a[href="/catalogue"]')).not.toBeNull();
  });

  it('should point Articles at /articles (DEV-11.11)', () => {
    render();

    expect(fixture.nativeElement.querySelector('a[href="/articles"]')).not.toBeNull();
  });

  it('should point Georges Lemaître at /georges-lemaitre (DEV-15.4)', () => {
    render();

    expect(fixture.nativeElement.querySelector('a[href="/georges-lemaitre"]')).not.toBeNull();
  });

  it('should show Espace membre pointing at /member/profile for ROLE_MEMBER', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).toContain('Espace membre');
    expect(fixture.nativeElement.querySelector('a[href="/member/profile"]')).not.toBeNull();
  });

  it('should hide Espace membre for a user without ROLE_MEMBER', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(linkLabels()).not.toContain('Espace membre');
  });

  it('should show Espace personnel pointing at /staff/users for ROLE_LIBRARIAN', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(linkLabels()).toContain('Espace personnel');
    expect(fixture.nativeElement.querySelector('a[href="/staff/users"]')).not.toBeNull();
  });

  it('should show Espace personnel for ROLE_ADMIN too (DEV-05.11-DEC-04)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render();

    expect(linkLabels()).toContain('Espace personnel');
  });

  it('should show Administration pointing at /admin/users for ROLE_ADMIN only', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render();

    expect(linkLabels()).toContain('Administration');
    expect(fixture.nativeElement.querySelector('a[href="/admin/users"]')).not.toBeNull();
  });

  it('should hide Administration for ROLE_MEMBER', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).not.toContain('Administration');
  });

  it('should never show staff domain items (Gestion du catalogue, Prêts...) — they belong to StaffAdminShell only', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    authServiceMock.permissions.mockReturnValue(['LOAN_READ', 'RESERVATION_READ', 'FINE_READ', 'ARTICLE_MANAGE', 'SETTING_READ']);
    render();

    expect(linkLabels()).not.toContain('Prêts');
    expect(linkLabels()).not.toContain('Gestion du catalogue');
    expect(linkLabels()).not.toContain('Paramètres');
  });

  it('should never show Member sub-items (Mes prêts...) — they belong to MemberNavigation only', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).not.toContain('Mes prêts');
    expect(linkLabels()).not.toContain('Mes réservations');
  });

  it('should render the shared AccountMenu (Connexion/Déconnexion, bell) instead of duplicating it', () => {
    render();

    expect(fixture.nativeElement.querySelector('app-account-menu')).not.toBeNull();
  });

  it('should expose an accessible main navigation landmark', () => {
    render();

    const nav = fixture.nativeElement.querySelector('nav.public-navigation');
    expect(nav?.getAttribute('aria-label')).toBe('Navigation principale');
  });

  it('should mark Accueil active on the "/" route while Catalogue stays inactive (exact matching)', async () => {
    render();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/');
    fixture.detectChanges();

    const homeLink = fixture.nativeElement.querySelector('a[href="/"]');
    const catalogueLink = fixture.nativeElement.querySelector('a[href="/catalogue"]');
    expect(homeLink?.classList.contains('nav-link--active')).toBe(true);
    expect(catalogueLink?.classList.contains('nav-link--active')).toBe(false);
  });

  it('should open the mobile drawer when the hamburger toggle is clicked', () => {
    render();
    expect(fixture.componentInstance.mobileMenuOpen()).toBe(false);

    (fixture.nativeElement.querySelector('.public-navigation-toggle') as HTMLButtonElement).click();

    expect(fixture.componentInstance.mobileMenuOpen()).toBe(true);
  });

  it('should close the mobile drawer when a navigation link is activated', () => {
    render();
    fixture.componentInstance.openMobileMenu();
    fixture.detectChanges();

    const anchors: HTMLAnchorElement[] = Array.from(fixture.nativeElement.querySelectorAll('.nav-list a'));
    anchors[0].click();

    expect(fixture.componentInstance.mobileMenuOpen()).toBe(false);
  });

  it('should expose aria-expanded on the mobile toggle reflecting the drawer state', () => {
    render();

    const toggle = fixture.nativeElement.querySelector('.public-navigation-toggle');
    expect(toggle?.getAttribute('aria-expanded')).toBe('false');

    fixture.componentInstance.openMobileMenu();
    fixture.detectChanges();

    expect(toggle?.getAttribute('aria-expanded')).toBe('true');
  });
});
