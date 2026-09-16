import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../../notifications/services/notification-unread-state.service';
import { Forbidden } from './forbidden';

describe('Forbidden', () => {
  let fixture: ComponentFixture<Forbidden>;

  beforeEach(() => {
    const authServiceMock = {
      authenticated: vi.fn().mockReturnValue(false),
      roles: vi.fn().mockReturnValue([]),
      permissions: vi.fn().mockReturnValue([]),
      logout: vi.fn(),
    };
    const unreadStateMock = { unreadCount: vi.fn().mockReturnValue(0), refresh: vi.fn(), decrement: vi.fn(), reset: vi.fn() };

    TestBed.configureTestingModule({
      imports: [Forbidden],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
      ],
    });

    fixture = TestBed.createComponent(Forbidden);
    fixture.detectChanges();
  });

  it('should render inside the shared PublicShell (DEV-15.3, §15) instead of a bare page', () => {
    expect(fixture.nativeElement.querySelector('app-public-shell')).not.toBeNull();
  });

  it('should keep the "Accès interdit" heading used by the RBAC E2E scenarios', () => {
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toBe('Accès interdit');
  });

  it('should keep a link back to the home route', () => {
    expect(fixture.nativeElement.querySelector('a[href="/"]')).not.toBeNull();
  });
});
