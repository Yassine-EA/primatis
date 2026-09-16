import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { NotificationUnreadStateService } from '../../../../notifications/services/notification-unread-state.service';
import { MemberNavigation } from './member-navigation';

describe('MemberNavigation', () => {
  let fixture: ComponentFixture<MemberNavigation>;
  let unreadStateMock: { unreadCount: ReturnType<typeof vi.fn> };

  function render(): void {
    fixture = TestBed.createComponent(MemberNavigation);
    fixture.detectChanges();
  }

  function linkLabels(): string[] {
    const anchors: HTMLAnchorElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.nav-list a'),
    );
    return anchors.map((anchor) => anchor.querySelector('span')?.textContent?.trim() ?? '');
  }

  beforeEach(() => {
    unreadStateMock = { unreadCount: vi.fn().mockReturnValue(0) };

    TestBed.configureTestingModule({
      imports: [MemberNavigation],
      providers: [
        provideRouter([]),
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
      ],
    });
  });

  it('should always show the five Member entries (zone already guarded by roleGuard ROLE_MEMBER)', () => {
    render();

    expect(linkLabels()).toEqual([
      'Profil',
      'Mes prêts',
      'Mes réservations',
      'Mes amendes',
      'Notifications',
    ]);
  });

  it('should point each entry at its expected route', () => {
    render();

    expect(fixture.nativeElement.querySelector('a[href="/member/profile"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/member/loans"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/member/reservations"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/member/fines"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/member/notifications"]')).not.toBeNull();
  });

  it('should show an unread badge on Notifications when count > 0', () => {
    unreadStateMock.unreadCount.mockReturnValue(2);
    render();

    const notifLink = fixture.nativeElement.querySelector('a[href="/member/notifications"]');
    expect(notifLink?.querySelector('.p-badge')?.textContent?.trim()).toBe('2');
  });

  it('should hide the badge entirely when the unread count is 0', () => {
    render();

    const notifLink = fixture.nativeElement.querySelector('a[href="/member/notifications"]');
    expect(notifLink?.querySelector('.p-badge')).toBeNull();
  });

  it('should expose an accessible navigation landmark', () => {
    render();

    const nav = fixture.nativeElement.querySelector('nav.member-navigation');
    expect(nav?.getAttribute('aria-label')).toBe('Navigation espace membre');
  });

  it('should associate one decorative PrimeIcon with every member entry', () => {
    render();

    expect(fixture.nativeElement.querySelectorAll('.nav-list a > i.pi')).toHaveLength(5);
  });

  it('should expose a compact toggle that controls the member links on mobile', () => {
    render();

    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector(
      '.member-navigation-toggle',
    );
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    fixture.detectChanges();

    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('.nav-list--mobile-open')).not.toBeNull();
  });
});
