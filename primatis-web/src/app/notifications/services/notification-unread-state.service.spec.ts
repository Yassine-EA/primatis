import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { NotificationApiService } from './notification-api.service';
import { NotificationUnreadStateService } from './notification-unread-state.service';

describe('NotificationUnreadStateService', () => {
  let service: NotificationUnreadStateService;
  let notificationApiServiceMock: { getUnreadCount: ReturnType<typeof vi.fn> };

  function configure(): void {
    notificationApiServiceMock = { getUnreadCount: vi.fn() };

    TestBed.configureTestingModule({
      providers: [{ provide: NotificationApiService, useValue: notificationApiServiceMock }],
    });

    service = TestBed.inject(NotificationUnreadStateService);
  }

  it('should start at 0 before any refresh', () => {
    configure();

    expect(service.unreadCount()).toBe(0);
  });

  it('should set unreadCount from getUnreadCount on refresh', () => {
    configure();
    notificationApiServiceMock.getUnreadCount.mockReturnValue(of({ count: 4 }));

    service.refresh();

    expect(service.unreadCount()).toBe(4);
  });

  it('should silently ignore a failed refresh, without throwing and without changing the count', () => {
    configure();
    notificationApiServiceMock.getUnreadCount.mockReturnValue(throwError(() => new Error('network error')));

    expect(() => service.refresh()).not.toThrow();
    expect(service.unreadCount()).toBe(0);
  });

  it('should decrement the count by 1', () => {
    configure();
    notificationApiServiceMock.getUnreadCount.mockReturnValue(of({ count: 3 }));
    service.refresh();

    service.decrement();

    expect(service.unreadCount()).toBe(2);
  });

  it('should never decrement below 0', () => {
    configure();

    service.decrement();
    service.decrement();

    expect(service.unreadCount()).toBe(0);
  });

  it('should reset the count to 0', () => {
    configure();
    notificationApiServiceMock.getUnreadCount.mockReturnValue(of({ count: 7 }));
    service.refresh();

    service.reset();

    expect(service.unreadCount()).toBe(0);
  });
});
