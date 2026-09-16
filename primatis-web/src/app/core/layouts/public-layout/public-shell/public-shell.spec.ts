import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthService } from '../../../../auth/services/auth.service';
import { NotificationUnreadStateService } from '../../../../notifications/services/notification-unread-state.service';
import { PublicShell } from './public-shell';

@Component({
  imports: [PublicShell],
  template: `<app-public-shell><p class="projected">Contenu</p></app-public-shell>`,
})
class HostComponent {}

describe('PublicShell', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    const authServiceMock = {
      authenticated: vi.fn().mockReturnValue(false),
      roles: vi.fn().mockReturnValue([]),
      permissions: vi.fn().mockReturnValue([]),
      logout: vi.fn(),
    };
    const unreadStateMock = { unreadCount: vi.fn().mockReturnValue(0), refresh: vi.fn(), decrement: vi.fn(), reset: vi.fn() };

    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
      ],
    });

    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('should render the PRIMATIS brand link pointing at the home route', () => {
    const brand = fixture.nativeElement.querySelector('.public-shell-brand');
    expect(brand?.getAttribute('href')).toBe('/');
    expect(brand?.textContent).toContain('PRIMATIS');
  });

  it('should render the shared PublicNavigation', () => {
    expect(fixture.nativeElement.querySelector('app-public-navigation')).not.toBeNull();
  });

  it('should project the routed content inside the main content area', () => {
    const projected = fixture.nativeElement.querySelector('.public-shell-content .projected');
    expect(projected?.textContent).toBe('Contenu');
  });

  it('should render a footer carrying the PRIMATIS identity', () => {
    const footer = fixture.nativeElement.querySelector('.public-shell-footer');
    expect(footer?.textContent).toContain('PRIMATIS');
  });
});
