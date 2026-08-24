import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { vi } from 'vitest';

import { AuthService } from '../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../notifications/services/notification-unread-state.service';
import { Navigation } from './navigation';

describe('Navigation', () => {
  let fixture: ComponentFixture<Navigation>;
  let authServiceMock: {
    authenticated: ReturnType<typeof vi.fn>;
    roles: ReturnType<typeof vi.fn>;
    permissions: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let unreadStateMock: {
    unreadCount: ReturnType<typeof signal<number>>;
    refresh: ReturnType<typeof vi.fn>;
    decrement: ReturnType<typeof vi.fn>;
    reset: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  function render(): void {
    fixture = TestBed.createComponent(Navigation);
    fixture.detectChanges();
  }

  function linkLabels(): string[] {
    const anchors: HTMLAnchorElement[] = Array.from(fixture.nativeElement.querySelectorAll('.nav-list a'));
    return anchors.map((anchor) => anchor.textContent?.trim() ?? '');
  }

  beforeEach(() => {
    authServiceMock = {
      authenticated: vi.fn().mockReturnValue(false),
      roles: vi.fn().mockReturnValue([]),
      permissions: vi.fn().mockReturnValue([]),
      logout: vi.fn(),
    };
    unreadStateMock = {
      unreadCount: signal(0),
      refresh: vi.fn(),
      decrement: vi.fn(),
      reset: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [Navigation],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
      ],
    });

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  it('should show only Accueil, Catalogue, Articles and a Connexion link for an anonymous user', () => {
    render();

    expect(linkLabels()).toEqual(['Accueil', 'Catalogue', 'Articles']);
    expect(fixture.nativeElement.textContent).toContain('Connexion');
    expect(fixture.nativeElement.textContent).not.toContain('Déconnexion');
  });

  it('should show the Catalogue link pointing at /catalogue for an anonymous user (DEV-06.8)', () => {
    render();

    expect(fixture.nativeElement.querySelector('a[href="/catalogue"]')).not.toBeNull();
  });

  it('should show the Articles link pointing at /articles for an anonymous user (DEV-11.11)', () => {
    render();

    expect(fixture.nativeElement.querySelector('a[href="/articles"]')).not.toBeNull();
  });

  it('should show the Articles link for ROLE_MEMBER (DEV-11.11)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).toContain('Articles');
  });

  it('should show the Articles link for ROLE_LIBRARIAN (DEV-11.11)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(linkLabels()).toContain('Articles');
  });

  it('should show the Articles link for ROLE_ADMIN (DEV-11.11)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render();

    expect(linkLabels()).toContain('Articles');
  });

  it('should show the Catalogue link for ROLE_MEMBER (DEV-06.8)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).toContain('Catalogue');
  });

  it('should show the Catalogue link for ROLE_LIBRARIAN (DEV-06.8)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(linkLabels()).toContain('Catalogue');
  });

  it('should show the Catalogue link for ROLE_ADMIN (DEV-06.8)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render();

    expect(linkLabels()).toContain('Catalogue');
  });

  it('should show Déconnexion and hide Connexion for an authenticated user', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();

    expect(fixture.nativeElement.textContent).toContain('Déconnexion');
    expect(fixture.nativeElement.querySelector('a[href="/login"]')).toBeNull();
  });

  it('should show the Espace membre link for ROLE_MEMBER', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).toContain('Espace membre');
  });

  it('should point the Espace membre link at /member/profile (DEV-05.13)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/member/profile"]')).not.toBeNull();
  });

  it('should hide the Espace membre link for a user without ROLE_MEMBER (DEV-05.13)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(linkLabels()).not.toContain('Espace membre');
  });

  it('should show the Mes prêts link for ROLE_MEMBER (DEV-07.8)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).toContain('Mes prêts');
  });

  it('should point the Mes prêts link at /member/loans (DEV-07.8)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/member/loans"]')).not.toBeNull();
  });

  it('should hide the Mes prêts link for a user without ROLE_MEMBER (DEV-07.8)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(linkLabels()).not.toContain('Mes prêts');
  });

  it('should show the Mes réservations link for ROLE_MEMBER (DEV-08.14)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).toContain('Mes réservations');
  });

  it('should point the Mes réservations link at /member/reservations (DEV-08.14)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/member/reservations"]')).not.toBeNull();
  });

  it('should hide the Mes réservations link for a user without ROLE_MEMBER (DEV-08.14)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(linkLabels()).not.toContain('Mes réservations');
  });

  it('should show the Mes amendes link for ROLE_MEMBER (DEV-09.12)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).toContain('Mes amendes');
  });

  it('should point the Mes amendes link at /member/fines (DEV-09.12)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/member/fines"]')).not.toBeNull();
  });

  it('should hide the Mes amendes link for a user without ROLE_MEMBER (DEV-09.12)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(linkLabels()).not.toContain('Mes amendes');
  });

  it('should show the Prêts link for LOAN_READ (DEV-07.9)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['LOAN_READ']);
    render();

    expect(linkLabels()).toContain('Prêts');
  });

  it('should point the Prêts link at /staff/loans (DEV-07.9)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['LOAN_READ']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/staff/loans"]')).not.toBeNull();
  });

  it('should hide the Prêts link for a user without LOAN_READ (DEV-07.9)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    authServiceMock.permissions.mockReturnValue([]);
    render();

    expect(linkLabels()).not.toContain('Prêts');
  });

  it('should show the Réservations link for RESERVATION_READ (DEV-08.14)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['RESERVATION_READ']);
    render();

    expect(linkLabels()).toContain('Réservations');
  });

  it('should point the Réservations link at /staff/reservations (DEV-08.14)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['RESERVATION_READ']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/staff/reservations"]')).not.toBeNull();
  });

  it('should hide the Réservations link for a user without RESERVATION_READ (DEV-08.14)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    authServiceMock.permissions.mockReturnValue([]);
    render();

    expect(linkLabels()).not.toContain('Réservations');
  });

  it('should hide the Réservations link for a user with only RESERVATION_MANAGE, without RESERVATION_READ (DEV-08.14)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['RESERVATION_MANAGE']);
    render();

    expect(linkLabels()).not.toContain('Réservations');
  });

  it('should show the Amendes link for FINE_READ (DEV-09.13)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['FINE_READ']);
    render();

    expect(linkLabels()).toContain('Amendes');
  });

  it('should point the Amendes link at /staff/fines (DEV-09.13)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['FINE_READ']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/staff/fines"]')).not.toBeNull();
  });

  it('should hide the Amendes link for a user without FINE_READ (DEV-09.13)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    authServiceMock.permissions.mockReturnValue([]);
    render();

    expect(linkLabels()).not.toContain('Amendes');
  });

  it('should hide the Amendes link for a user with only FINE_MANAGE, without FINE_READ (DEV-09.13)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['FINE_MANAGE']);
    render();

    expect(linkLabels()).not.toContain('Amendes');
  });

  it('should show the Administration link for ROLE_ADMIN', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render();

    expect(linkLabels()).toContain('Administration');
  });

  it('should point the Administration link at /admin/users (DEV-05.12)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/admin/users"]')).not.toBeNull();
  });

  it('should hide the Administration link for a user without ROLE_ADMIN', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).not.toContain('Administration');
  });

  it('should show the Espace personnel link for ROLE_LIBRARIAN', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(linkLabels()).toContain('Espace personnel');
  });

  it('should show the Espace personnel link for ROLE_ADMIN (DEV-05.11)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render();

    expect(linkLabels()).toContain('Espace personnel');
  });

  it('should show the Gestion du catalogue link for ROLE_LIBRARIAN (DEV-06.9)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(linkLabels()).toContain('Gestion du catalogue');
  });

  it('should show the Gestion du catalogue link for ROLE_ADMIN (DEV-06.9)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render();

    expect(linkLabels()).toContain('Gestion du catalogue');
  });

  it('should point the Gestion du catalogue link at /staff/catalogue (DEV-06.9)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/staff/catalogue"]')).not.toBeNull();
  });

  it('should hide the Gestion du catalogue link for ROLE_MEMBER (DEV-06.9)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_MEMBER']);
    render();

    expect(linkLabels()).not.toContain('Gestion du catalogue');
  });

  it('should hide the Gestion du catalogue link for an anonymous user (DEV-06.9)', () => {
    render();

    expect(linkLabels()).not.toContain('Gestion du catalogue');
  });

  it('should show the Gestion des articles link for ARTICLE_MANAGE (DEV-11.12)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['ARTICLE_MANAGE']);
    render();

    expect(linkLabels()).toContain('Gestion des articles');
  });

  it('should point the Gestion des articles link at /staff/articles (DEV-11.12)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['ARTICLE_MANAGE']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/staff/articles"]')).not.toBeNull();
  });

  it('should hide the Gestion des articles link for a user without ARTICLE_MANAGE (DEV-11.12)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    authServiceMock.permissions.mockReturnValue([]);
    render();

    expect(linkLabels()).not.toContain('Gestion des articles');
  });

  it('should hide the Gestion des articles link for a user with only ARTICLE_PUBLISH, without ARTICLE_MANAGE (DEV-11.12)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.permissions.mockReturnValue(['ARTICLE_PUBLISH']);
    render();

    expect(linkLabels()).not.toContain('Gestion des articles');
  });

  it('should point the Espace personnel link at /staff/users (DEV-05.11)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(fixture.nativeElement.querySelector('a[href="/staff/users"]')).not.toBeNull();
  });

  it('should call AuthService.logout() and navigate to "/" when logging out', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();

    (fixture.nativeElement.querySelector('.nav-logout') as HTMLButtonElement).click();

    expect(authServiceMock.logout).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('should never manipulate sessionStorage/localStorage directly (delegates entirely to AuthService)', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');
    const removeItemSpy = vi.spyOn(Storage.prototype, 'removeItem');

    (fixture.nativeElement.querySelector('.nav-logout') as HTMLButtonElement).click();

    expect(setItemSpy).not.toHaveBeenCalled();
    expect(removeItemSpy).not.toHaveBeenCalled();
    setItemSpy.mockRestore();
    removeItemSpy.mockRestore();
  });

  it('should use a native, keyboard-accessible <button> for logout', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();

    const logoutButton = fixture.nativeElement.querySelector('.nav-logout');
    expect(logoutButton?.tagName).toBe('BUTTON');
  });

  // ---------------------------------------------------------------
  // Cloche / badge Notifications (DEV-10.10, DEV-DEC-0053)
  // ---------------------------------------------------------------

  it('should show the bell for an authenticated user', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();

    expect(fixture.nativeElement.querySelector('.nav-bell')).not.toBeNull();
  });

  it('should hide the bell for an anonymous user', () => {
    authServiceMock.authenticated.mockReturnValue(false);
    render();

    expect(fixture.nativeElement.querySelector('.nav-bell')).toBeNull();
  });

  it('should refresh the shared unread state on construction for an authenticated user', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();

    expect(unreadStateMock.refresh).toHaveBeenCalledTimes(1);
  });

  it('should never refresh the shared unread state for an anonymous user', () => {
    authServiceMock.authenticated.mockReturnValue(false);
    render();

    expect(unreadStateMock.refresh).not.toHaveBeenCalled();
  });

  it('should show a badge with the unread count when count > 0', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    unreadStateMock.unreadCount.set(3);
    render();

    const badge = fixture.nativeElement.querySelector('.nav-bell .p-badge');
    expect(badge).not.toBeNull();
    expect(badge?.textContent?.trim()).toBe('3');
  });

  it('should hide the badge entirely when the unread count is 0', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    unreadStateMock.unreadCount.set(0);
    render();

    expect(fixture.nativeElement.querySelector('.nav-bell .p-badge')).toBeNull();
  });

  it('should navigate the bell link directly to /member/notifications, no dropdown/overlay of content', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();

    const bell = fixture.nativeElement.querySelector('.nav-bell');
    expect(bell?.getAttribute('href')).toBe('/member/notifications');
    expect(fixture.nativeElement.querySelector('.nav-bell [role="menu"], .nav-bell .p-overlaypanel')).toBeNull();
  });

  it('should give the bell an accessible name', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();

    const bell = fixture.nativeElement.querySelector('.nav-bell');
    expect(bell?.getAttribute('aria-label')).toBe('Notifications');
  });

  it('should reset the shared unread state on logout', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();

    (fixture.nativeElement.querySelector('.nav-logout') as HTMLButtonElement).click();

    expect(unreadStateMock.reset).toHaveBeenCalledTimes(1);
  });
});
