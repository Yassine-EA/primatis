import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../auth/services/auth.service';
import { Navigation } from './navigation';

describe('Navigation', () => {
  let fixture: ComponentFixture<Navigation>;
  let authServiceMock: {
    authenticated: ReturnType<typeof vi.fn>;
    roles: ReturnType<typeof vi.fn>;
    permissions: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
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

    TestBed.configureTestingModule({
      imports: [Navigation],
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceMock }],
    });

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  it('should show only Accueil, Catalogue and a Connexion link for an anonymous user', () => {
    render();

    expect(linkLabels()).toEqual(['Accueil', 'Catalogue']);
    expect(fixture.nativeElement.textContent).toContain('Connexion');
    expect(fixture.nativeElement.textContent).not.toContain('Déconnexion');
  });

  it('should show the Catalogue link pointing at /catalogue for an anonymous user (DEV-06.8)', () => {
    render();

    expect(fixture.nativeElement.querySelector('a[href="/catalogue"]')).not.toBeNull();
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
});
