import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { vi } from 'vitest';

import { AuthService } from '../../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../../notifications/services/notification-unread-state.service';
import { AccountMenu } from './account-menu';

describe('AccountMenu', () => {
  let fixture: ComponentFixture<AccountMenu>;
  let authServiceMock: {
    authenticated: ReturnType<typeof vi.fn>;
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
    fixture = TestBed.createComponent(AccountMenu);
    fixture.detectChanges();
  }

  beforeEach(() => {
    authServiceMock = { authenticated: vi.fn().mockReturnValue(false), logout: vi.fn() };
    unreadStateMock = { unreadCount: signal(0), refresh: vi.fn(), decrement: vi.fn(), reset: vi.fn() };

    TestBed.configureTestingModule({
      imports: [AccountMenu],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
      ],
    });

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  it('should show a Connexion link and hide Déconnexion for an anonymous user', () => {
    render();

    expect(fixture.nativeElement.querySelector('a[href="/login"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Déconnexion');
  });

  it('should show Déconnexion and hide the Connexion link for an authenticated user', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();

    expect(fixture.nativeElement.textContent).toContain('Déconnexion');
    expect(fixture.nativeElement.querySelector('a[href="/login"]')).toBeNull();
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

  it('should reset the shared unread state on logout', () => {
    authServiceMock.authenticated.mockReturnValue(true);
    render();

    (fixture.nativeElement.querySelector('.nav-logout') as HTMLButtonElement).click();

    expect(unreadStateMock.reset).toHaveBeenCalledTimes(1);
  });

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
});
