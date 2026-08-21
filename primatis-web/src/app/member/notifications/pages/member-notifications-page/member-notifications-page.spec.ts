import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../../core/models/page-response';
import { NotificationResponse } from '../../../../notifications/models/notification-response';
import { NotificationApiService } from '../../../../notifications/services/notification-api.service';
import { NotificationUnreadStateService } from '../../../../notifications/services/notification-unread-state.service';
import { MemberNotificationsPage } from './member-notifications-page';

function buildNotification(overrides: Partial<NotificationResponse> = {}): NotificationResponse {
  return {
    id: 1,
    notificationType: 'LOAN_DUE_SOON',
    notificationStatus: 'UNREAD',
    title: 'Échéance de prêt proche',
    message: 'La date de retour prévue de votre prêt approche.',
    originId: 20,
    createdAt: '2026-08-05T10:00:00Z',
    readAt: null,
    ...overrides,
  };
}

function buildPage(content: NotificationResponse[], totalElements = content.length): PageResponse<NotificationResponse> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)) };
}

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: {
      timestamp: new Date().toISOString(),
      status,
      error: 'Error',
      code,
      message,
      path: '/api/v1/me/notifications',
      fieldErrors: [],
    },
  });
}

describe('MemberNotificationsPage', () => {
  let fixture: ComponentFixture<MemberNotificationsPage>;
  let notificationApiServiceMock: {
    listOwnNotifications: ReturnType<typeof vi.fn>;
    getUnreadCount: ReturnType<typeof vi.fn>;
    markAsRead: ReturnType<typeof vi.fn>;
    markAllAsRead: ReturnType<typeof vi.fn>;
  };
  let unreadStateMock: { refresh: ReturnType<typeof vi.fn>; decrement: ReturnType<typeof vi.fn>; reset: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  function configure(): void {
    notificationApiServiceMock = {
      listOwnNotifications: vi.fn(),
      getUnreadCount: vi.fn().mockReturnValue(of({ count: 0 })),
      markAsRead: vi.fn(),
      markAllAsRead: vi.fn(),
    };
    unreadStateMock = { refresh: vi.fn(), decrement: vi.fn(), reset: vi.fn() };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [MemberNotificationsPage],
      providers: [
        { provide: NotificationApiService, useValue: notificationApiServiceMock },
        { provide: NotificationUnreadStateService, useValue: unreadStateMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(MemberNotificationsPage);
    fixture.detectChanges();
  }

  // ---------------------------------------------------------------
  // Chargement / contrat API
  // ---------------------------------------------------------------

  it('should call listOwnNotifications(0, 20) on initial load', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));

    createComponent();

    expect(notificationApiServiceMock.listOwnNotifications).toHaveBeenCalledWith(0, 20);
  });

  it('should refresh the shared unread state on initial load', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));

    createComponent();

    expect(unreadStateMock.refresh).toHaveBeenCalledTimes(1);
  });

  it('should render the notifications returned by the API (title/message/date)', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(
      of(buildPage([buildNotification({ title: 'Prêt en retard', message: 'Votre prêt est en retard.' })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Prêt en retard');
    expect(text).toContain('Votre prêt est en retard.');
    expect(text).toContain('2026-08-05T10:00:00Z');
  });

  it('should map a PrimeNG lazy load event to page/size and call listOwnNotifications again', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()], 100)));
    createComponent();
    expect(fixture.componentInstance.totalRecords()).toBe(100);
    notificationApiServiceMock.listOwnNotifications.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(notificationApiServiceMock.listOwnNotifications).toHaveBeenCalledWith(2, 20);
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));
    createComponent();
    notificationApiServiceMock.listOwnNotifications.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(notificationApiServiceMock.listOwnNotifications).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // États UI
  // ---------------------------------------------------------------

  it('should show the loading state before the first response arrives', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when the backend returns no notification', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([], 0)));

    createComponent();

    const emptyState = fixture.nativeElement.querySelector('app-empty-state');
    expect(emptyState).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucune notification à afficher.');
  });

  it('should show the error state on a failed request', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the last request when retry is triggered', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();
    notificationApiServiceMock.listOwnNotifications.mockClear();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));

    fixture.componentInstance.retry();

    expect(notificationApiServiceMock.listOwnNotifications).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // UNREAD / READ — rendu et visibilité de l'action
  // ---------------------------------------------------------------

  it('should render a distinct label for an UNREAD notification', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(
      of(buildPage([buildNotification({ notificationStatus: 'UNREAD' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Non lue');
  });

  it('should render a distinct label for a READ notification', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(
      of(buildPage([buildNotification({ notificationStatus: 'READ', readAt: '2026-08-06T09:00:00Z' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Lue');
  });

  it('should show the "Marquer comme lue" action for an UNREAD notification', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(
      of(buildPage([buildNotification({ notificationStatus: 'UNREAD' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Marquer comme lue');
  });

  it('should never show the "Marquer comme lue" action for a READ notification', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(
      of(buildPage([buildNotification({ notificationStatus: 'READ', readAt: '2026-08-06T09:00:00Z' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Marquer comme lue');
  });

  it('should never hide a notification after it becomes READ (full history always visible)', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(
      of(buildPage([buildNotification({ id: 1, title: 'Ancienne notification lue', notificationStatus: 'READ' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Ancienne notification lue');
  });

  // ---------------------------------------------------------------
  // markAsRead individuel
  // ---------------------------------------------------------------

  it('should call markAsRead(id) when the action is triggered on an UNREAD notification', () => {
    configure();
    const notification = buildNotification({ id: 1, notificationStatus: 'UNREAD' });
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([notification])));
    notificationApiServiceMock.markAsRead.mockReturnValue(
      of(buildNotification({ id: 1, notificationStatus: 'READ', readAt: '2026-08-21T10:00:00Z' })),
    );
    createComponent();

    fixture.componentInstance.markAsRead(notification);

    expect(notificationApiServiceMock.markAsRead).toHaveBeenCalledWith(1);
  });

  it('should replace the row with the exact backend response after a successful markAsRead, without fabricating readAt', () => {
    configure();
    const notification = buildNotification({ id: 1, notificationStatus: 'UNREAD' });
    const read = buildNotification({ id: 1, notificationStatus: 'READ', readAt: '2026-08-21T10:00:00Z' });
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([notification])));
    notificationApiServiceMock.markAsRead.mockReturnValue(of(read));
    createComponent();

    fixture.componentInstance.markAsRead(notification);

    expect(fixture.componentInstance.rows()).toEqual([read]);
  });

  it('should decrement the shared unread count on a successful markAsRead', () => {
    configure();
    const notification = buildNotification({ id: 1, notificationStatus: 'UNREAD' });
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([notification])));
    notificationApiServiceMock.markAsRead.mockReturnValue(
      of(buildNotification({ id: 1, notificationStatus: 'READ', readAt: '2026-08-21T10:00:00Z' })),
    );
    createComponent();

    fixture.componentInstance.markAsRead(notification);

    expect(unreadStateMock.decrement).toHaveBeenCalledTimes(1);
  });

  it('should show an error toast and never mutate the row when markAsRead fails', () => {
    configure();
    const notification = buildNotification({ id: 1, notificationStatus: 'UNREAD' });
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([notification])));
    notificationApiServiceMock.markAsRead.mockReturnValue(
      throwError(() => apiHttpError(404, 'NOTIFICATION_NOT_FOUND', 'Aucune notification pour cet identifiant.')),
    );
    createComponent();

    fixture.componentInstance.markAsRead(notification);

    expect(fixture.componentInstance.rows()).toEqual([notification]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Aucune notification pour cet identifiant.' }),
    );
    expect(unreadStateMock.decrement).not.toHaveBeenCalled();
  });

  it('should never call markAsRead for an already READ notification (defensive no-op)', () => {
    configure();
    const notification = buildNotification({ id: 1, notificationStatus: 'READ', readAt: '2026-08-06T09:00:00Z' });
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([notification])));
    createComponent();

    fixture.componentInstance.markAsRead(notification);

    expect(notificationApiServiceMock.markAsRead).not.toHaveBeenCalled();
  });

  it('should disable the action button for the row currently being marked as read (prevents double click)', () => {
    configure();
    const notification = buildNotification({ id: 1, notificationStatus: 'UNREAD' });
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([notification])));
    notificationApiServiceMock.markAsRead.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();

    fixture.componentInstance.markAsRead(notification);

    expect(fixture.componentInstance.markingReadId()).toBe(1);
  });

  // ---------------------------------------------------------------
  // markAllAsRead
  // ---------------------------------------------------------------

  it('should render the "Tout marquer comme lu" action', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Tout marquer comme lu');
  });

  it('should call markAllAsRead when the action is triggered', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));
    notificationApiServiceMock.markAllAsRead.mockReturnValue(of({ updatedCount: 3 }));
    createComponent();

    fixture.componentInstance.markAllAsRead();

    expect(notificationApiServiceMock.markAllAsRead).toHaveBeenCalledTimes(1);
  });

  it('should reload the current page from the server after a successful markAllAsRead (Option A)', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()], 100)));
    notificationApiServiceMock.markAllAsRead.mockReturnValue(of({ updatedCount: 3 }));
    createComponent();
    fixture.componentInstance.onLazyLoad({ first: 20, rows: 20 }); // page courante = 1
    notificationApiServiceMock.listOwnNotifications.mockClear();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([], 0)));

    fixture.componentInstance.markAllAsRead();

    expect(notificationApiServiceMock.listOwnNotifications).toHaveBeenCalledWith(1, 20);
  });

  it('should refresh the shared unread count after a successful markAllAsRead, never fabricating it locally', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));
    notificationApiServiceMock.markAllAsRead.mockReturnValue(of({ updatedCount: 3 }));
    createComponent();
    unreadStateMock.refresh.mockClear();

    fixture.componentInstance.markAllAsRead();

    expect(unreadStateMock.refresh).toHaveBeenCalledTimes(1);
  });

  it('should show a success toast with the exact updatedCount returned by the backend', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));
    notificationApiServiceMock.markAllAsRead.mockReturnValue(of({ updatedCount: 3 }));
    createComponent();

    fixture.componentInstance.markAllAsRead();

    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'success', detail: '3 notification(s) marquée(s) comme lue(s).' }),
    );
  });

  it('should treat updatedCount = 0 as a normal success (idempotence), not an error', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));
    notificationApiServiceMock.markAllAsRead.mockReturnValue(of({ updatedCount: 0 }));
    createComponent();

    fixture.componentInstance.markAllAsRead();

    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show an error toast when markAllAsRead fails, without reloading the list', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));
    notificationApiServiceMock.markAllAsRead.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();
    notificationApiServiceMock.listOwnNotifications.mockClear();

    fixture.componentInstance.markAllAsRead();

    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(notificationApiServiceMock.listOwnNotifications).not.toHaveBeenCalled();
  });

  it('should disable the "Tout marquer comme lu" button while the mutation is in flight', () => {
    configure();
    notificationApiServiceMock.listOwnNotifications.mockReturnValue(of(buildPage([buildNotification()])));
    notificationApiServiceMock.markAllAsRead.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();

    fixture.componentInstance.markAllAsRead();

    expect(fixture.componentInstance.markingAll()).toBe(true);
  });
});
