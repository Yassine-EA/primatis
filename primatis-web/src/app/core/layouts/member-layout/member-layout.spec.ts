import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../../notifications/services/notification-unread-state.service';
import { MemberLayout } from './member-layout';

describe('MemberLayout', () => {
  let fixture: ComponentFixture<MemberLayout>;

  beforeEach(() => {
    const authServiceMock = {
      authenticated: vi.fn().mockReturnValue(true),
      roles: vi.fn().mockReturnValue(['ROLE_MEMBER']),
      permissions: vi.fn().mockReturnValue([]),
      logout: vi.fn(),
    };
    const unreadStateMock = {
      unreadCount: vi.fn().mockReturnValue(0),
      refresh: vi.fn(),
      decrement: vi.fn(),
      reset: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [MemberLayout],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
      ],
    });

    fixture = TestBed.createComponent(MemberLayout);
    fixture.detectChanges();
  });

  it('should render the dedicated MemberNavigation (DEV-15.3, ID-05) instead of the old shared Navigation', () => {
    expect(fixture.nativeElement.querySelector('app-member-navigation')).not.toBeNull();
  });

  it('should render the shared AccountMenu (bell/logout)', () => {
    expect(fixture.nativeElement.querySelector('app-account-menu')).not.toBeNull();
  });

  it('should render a router outlet for its children', () => {
    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });

  it('should reuse the validated public shell and its brand link', () => {
    expect(fixture.nativeElement.querySelector('app-public-shell')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a.public-shell-brand')?.getAttribute('href')).toBe(
      '/',
    );
  });
});
