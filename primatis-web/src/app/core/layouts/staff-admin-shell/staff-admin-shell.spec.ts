import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../../notifications/services/notification-unread-state.service';
import { StaffAdminShell } from './staff-admin-shell';

describe('StaffAdminShell', () => {
  let fixture: ComponentFixture<StaffAdminShell>;
  let authServiceMock: {
    authenticated: ReturnType<typeof vi.fn>;
    roles: ReturnType<typeof vi.fn>;
    permissions: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  function render(zoneTitle = 'Espace personnel'): void {
    fixture = TestBed.createComponent(StaffAdminShell);
    fixture.componentRef.setInput('zoneTitle', zoneTitle);
    fixture.detectChanges();
  }

  function sidebarLinkLabels(): string[] {
    const anchors: HTMLAnchorElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.staff-admin-sidebar .nav-list a'),
    );
    return anchors.map((anchor) => anchor.textContent?.trim() ?? '');
  }

  beforeEach(() => {
    authServiceMock = {
      authenticated: vi.fn().mockReturnValue(true),
      roles: vi.fn().mockReturnValue([]),
      permissions: vi.fn().mockReturnValue([]),
      logout: vi.fn(),
    };
    const unreadStateMock = { unreadCount: vi.fn().mockReturnValue(0), refresh: vi.fn(), decrement: vi.fn(), reset: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffAdminShell],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
      ],
    });

    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  it('should render the zoneTitle in the header', () => {
    render('Administration');

    expect(fixture.nativeElement.querySelector('.staff-admin-zone-title')?.textContent).toBe('Administration');
  });

  it('should render the zone title as h2, not h1, so the page own page-title remains the single h1 (DEV-15.12)', () => {
    render('Administration');

    expect(fixture.nativeElement.querySelector('h1.staff-admin-zone-title')).toBeNull();
    expect(fixture.nativeElement.querySelector('h2.staff-admin-zone-title')?.textContent).toBe('Administration');
  });

  it('should show Espace personnel and Gestion du catalogue for ROLE_LIBRARIAN', () => {
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(sidebarLinkLabels()).toContain('Espace personnel');
    expect(sidebarLinkLabels()).toContain('Gestion du catalogue');
    expect(fixture.nativeElement.querySelector('a[href="/staff/users"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/staff/catalogue"]')).not.toBeNull();
  });

  it('should show Espace personnel for ROLE_ADMIN too (DEV-05.11-DEC-04)', () => {
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render('Administration');

    expect(sidebarLinkLabels()).toContain('Espace personnel');
  });

  it('should show Prêts only for LOAN_READ', () => {
    authServiceMock.permissions.mockReturnValue(['LOAN_READ']);
    render();

    expect(sidebarLinkLabels()).toContain('Prêts');
    expect(fixture.nativeElement.querySelector('a[href="/staff/loans"]')).not.toBeNull();
  });

  it('should hide Prêts for a librarian without LOAN_READ', () => {
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    authServiceMock.permissions.mockReturnValue([]);
    render();

    expect(sidebarLinkLabels()).not.toContain('Prêts');
  });

  it('should show Réservations only for RESERVATION_READ, never for RESERVATION_MANAGE alone', () => {
    authServiceMock.permissions.mockReturnValue(['RESERVATION_MANAGE']);
    render();

    expect(sidebarLinkLabels()).not.toContain('Réservations');
  });

  it('should show Amendes only for FINE_READ, never for FINE_MANAGE alone', () => {
    authServiceMock.permissions.mockReturnValue(['FINE_MANAGE']);
    render();

    expect(sidebarLinkLabels()).not.toContain('Amendes');
  });

  it('should show Gestion des articles only for ARTICLE_MANAGE', () => {
    authServiceMock.permissions.mockReturnValue(['ARTICLE_MANAGE']);
    render();

    expect(sidebarLinkLabels()).toContain('Gestion des articles');
    expect(fixture.nativeElement.querySelector('a[href="/staff/articles"]')).not.toBeNull();
  });

  it('should show Administration only for ROLE_ADMIN', () => {
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render('Administration');

    expect(sidebarLinkLabels()).toContain('Administration');
    expect(fixture.nativeElement.querySelector('a[href="/admin/users"]')).not.toBeNull();
  });

  it('should hide Administration for a librarian without ROLE_ADMIN', () => {
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();

    expect(sidebarLinkLabels()).not.toContain('Administration');
  });

  it('should show Paramètres only for SETTING_READ, never for SETTING_MANAGE alone', () => {
    authServiceMock.permissions.mockReturnValue(['SETTING_MANAGE']);
    render('Administration');

    expect(sidebarLinkLabels()).not.toContain('Paramètres');
  });

  it('should never show Member-only entries (Mes prêts...) — they belong to MemberNavigation only', () => {
    authServiceMock.roles.mockReturnValue(['ROLE_ADMIN']);
    render();

    expect(sidebarLinkLabels()).not.toContain('Mes prêts');
    expect(sidebarLinkLabels()).not.toContain('Espace membre');
  });

  it('should render the shared AccountMenu', () => {
    render();

    expect(fixture.nativeElement.querySelector('app-account-menu')).not.toBeNull();
  });

  it('should render a brand link back to the public home route', () => {
    render();

    expect(fixture.nativeElement.querySelector('.staff-admin-brand')?.getAttribute('href')).toBe('/');
  });

  it('should open the mobile sidebar drawer when the hamburger toggle is clicked', () => {
    render();
    expect(fixture.componentInstance.mobileSidebarOpen()).toBe(false);

    (fixture.nativeElement.querySelector('.staff-admin-sidebar-toggle') as HTMLButtonElement).click();

    expect(fixture.componentInstance.mobileSidebarOpen()).toBe(true);
  });

  it('should close the mobile sidebar drawer when a navigation link is activated', () => {
    authServiceMock.roles.mockReturnValue(['ROLE_LIBRARIAN']);
    render();
    fixture.componentInstance.openMobileSidebar();
    fixture.detectChanges();

    const anchors: HTMLAnchorElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.staff-admin-sidebar .nav-list a'),
    );
    anchors[0].click();

    expect(fixture.componentInstance.mobileSidebarOpen()).toBe(false);
  });
});
