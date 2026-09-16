import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../../notifications/services/notification-unread-state.service';
import { AdminLayout } from './admin-layout';

describe('AdminLayout', () => {
  let fixture: ComponentFixture<AdminLayout>;

  beforeEach(() => {
    const authServiceMock = {
      authenticated: vi.fn().mockReturnValue(true),
      roles: vi.fn().mockReturnValue(['ROLE_ADMIN']),
      permissions: vi.fn().mockReturnValue([]),
      logout: vi.fn(),
    };
    const unreadStateMock = { unreadCount: vi.fn().mockReturnValue(0), refresh: vi.fn(), decrement: vi.fn(), reset: vi.fn() };

    TestBed.configureTestingModule({
      imports: [AdminLayout],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
      ],
    });

    fixture = TestBed.createComponent(AdminLayout);
    fixture.detectChanges();
  });

  it('should render the shared StaffAdminShell (DEV-15.3, §11) with the "Administration" zone title', () => {
    const shell = fixture.nativeElement.querySelector('app-staff-admin-shell');
    expect(shell).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.staff-admin-zone-title')?.textContent).toBe('Administration');
  });

  it('should render a router outlet for its children', () => {
    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });
});
