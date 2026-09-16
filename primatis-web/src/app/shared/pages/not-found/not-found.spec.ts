import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../../notifications/services/notification-unread-state.service';
import { NotFound } from './not-found';

describe('NotFound', () => {
  let fixture: ComponentFixture<NotFound>;

  beforeEach(() => {
    const authServiceMock = {
      authenticated: vi.fn().mockReturnValue(false),
      roles: vi.fn().mockReturnValue([]),
      permissions: vi.fn().mockReturnValue([]),
      logout: vi.fn(),
    };
    const unreadStateMock = { unreadCount: vi.fn().mockReturnValue(0), refresh: vi.fn(), decrement: vi.fn(), reset: vi.fn() };

    TestBed.configureTestingModule({
      imports: [NotFound],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
      ],
    });

    fixture = TestBed.createComponent(NotFound);
    fixture.detectChanges();
  });

  it('should render inside the shared PublicShell (DEV-15.3, §15) instead of a bare page', () => {
    expect(fixture.nativeElement.querySelector('app-public-shell')).not.toBeNull();
  });

  it('should keep the "Page introuvable" heading', () => {
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toBe('Page introuvable');
  });

  it('should keep a link back to the home route', () => {
    expect(fixture.nativeElement.querySelector('a[href="/"]')).not.toBeNull();
  });
});
