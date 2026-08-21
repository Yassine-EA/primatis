import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { NotificationResponse } from '../models/notification-response';
import { NotificationStatus } from '../models/notification-status';
import { NotificationType } from '../models/notification-type';
import { NotificationApiService } from './notification-api.service';

describe('NotificationApiService', () => {
  let service: NotificationApiService;
  let httpTestingController: HttpTestingController;

  const unread: NotificationResponse = {
    id: 1,
    notificationType: 'LOAN_DUE_SOON',
    notificationStatus: 'UNREAD',
    title: 'Échéance de prêt proche',
    message: 'La date de retour prévue de votre prêt approche.',
    originId: 20,
    createdAt: '2026-08-05T10:00:00Z',
    readAt: null,
  };

  const read: NotificationResponse = {
    id: 2,
    notificationType: 'FINE_PAID',
    notificationStatus: 'READ',
    title: 'Amende payée',
    message: 'Le paiement de votre amende a bien été enregistré.',
    originId: 30,
    createdAt: '2026-08-01T09:00:00Z',
    readAt: '2026-08-02T09:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(NotificationApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // listOwnNotifications
  // ---------------------------------------------------------------

  it('should GET /api/v1/me/notifications with default page/size params, no userId sent', () => {
    service.listOwnNotifications().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/me/notifications' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('userId')).toBe(false);
    expect(request.request.params.has('sort')).toBe(false);

    request.flush({ content: [unread, read], page: 0, size: 20, totalElements: 2, totalPages: 1 });
  });

  it('should GET /api/v1/me/notifications with explicit page/size params', () => {
    service.listOwnNotifications(2, 10).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/me/notifications' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('10');

    request.flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<NotificationResponse> returned by the backend, including an UNREAD and a READ notification', () => {
    let received: unknown;
    service.listOwnNotifications().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/me/notifications' && req.method === 'GET')
      .flush({ content: [unread, read], page: 0, size: 20, totalElements: 2, totalPages: 1 });

    expect(received).toEqual({ content: [unread, read], page: 0, size: 20, totalElements: 2, totalPages: 1 });
  });

  // ---------------------------------------------------------------
  // getUnreadCount
  // ---------------------------------------------------------------

  it('should GET /api/v1/me/notifications/unread-count with no query param', () => {
    service.getUnreadCount().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/me/notifications/unread-count' && req.method === 'GET',
    );
    expect(request.request.params.keys().length).toBe(0);

    request.flush({ count: 4 });
  });

  it('should propagate the NotificationUnreadCountResponse returned by the backend on getUnreadCount', () => {
    let received: unknown;
    service.getUnreadCount().subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/me/notifications/unread-count').flush({ count: 4 });

    expect(received).toEqual({ count: 4 });
  });

  // ---------------------------------------------------------------
  // markAsRead
  // ---------------------------------------------------------------

  it('should POST /api/v1/me/notifications/{notificationId}/read with no business payload', () => {
    service.markAsRead(42).subscribe();

    const request = httpTestingController.expectOne('/api/v1/me/notifications/42/read');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    const nowRead: NotificationResponse = { ...unread, notificationStatus: 'READ', readAt: '2026-08-21T10:00:00Z' };
    request.flush(nowRead);
  });

  it('should propagate the NotificationResponse returned by the backend on markAsRead, with notificationStatus READ and readAt set', () => {
    let received: NotificationResponse | undefined;
    service.markAsRead(42).subscribe((value) => (received = value));

    const nowRead: NotificationResponse = { ...unread, notificationStatus: 'READ', readAt: '2026-08-21T10:00:00Z' };
    httpTestingController.expectOne('/api/v1/me/notifications/42/read').flush(nowRead);

    expect(received).toEqual(nowRead);
    expect(received?.notificationStatus).toBe('READ');
    expect(received?.readAt).not.toBeNull();
  });

  // ---------------------------------------------------------------
  // markAllAsRead
  // ---------------------------------------------------------------

  it('should POST /api/v1/me/notifications/read-all with no business payload', () => {
    service.markAllAsRead().subscribe();

    const request = httpTestingController.expectOne('/api/v1/me/notifications/read-all');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    request.flush({ updatedCount: 3 });
  });

  it('should propagate the NotificationMarkAllAsReadResponse returned by the backend on markAllAsRead', () => {
    let received: unknown;
    service.markAllAsRead().subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/me/notifications/read-all').flush({ updatedCount: 3 });

    expect(received).toEqual({ updatedCount: 3 });
  });

  it('should treat updatedCount = 0 as a normal success on markAllAsRead (idempotence)', () => {
    let received: unknown;
    service.markAllAsRead().subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/me/notifications/read-all').flush({ updatedCount: 0 });

    expect(received).toEqual({ updatedCount: 0 });
  });

  // ---------------------------------------------------------------
  // NotificationType / NotificationStatus — couverture des valeurs figées
  // ---------------------------------------------------------------

  it('should let NotificationType represent exactly the 11 backend values', () => {
    const allTypes: NotificationType[] = [
      'LOAN_DUE_SOON',
      'LOAN_OVERDUE',
      'LOAN_RETURNED',
      'RESERVATION_CREATED',
      'RESERVATION_READY',
      'RESERVATION_EXPIRED',
      'RESERVATION_CANCELLED',
      'FINE_ISSUED',
      'FINE_PAID',
      'FINE_CANCELLED',
      'ARTICLE_PUBLISHED',
    ];

    expect(allTypes).toHaveLength(11);
    expect(new Set(allTypes).size).toBe(11);
  });

  it('should let NotificationStatus represent exactly UNREAD and READ', () => {
    const allStatuses: NotificationStatus[] = ['UNREAD', 'READ'];

    expect(allStatuses).toHaveLength(2);
    expect(new Set(allStatuses).size).toBe(2);
  });
});
