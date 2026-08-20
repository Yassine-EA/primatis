import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../../auth/services/auth.service';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { PageResponse } from '../../../../core/models/page-response';
import { ReservationResponse } from '../../../../reservations/models/reservation-response';
import { ReservationStatus } from '../../../../reservations/models/reservation-status';
import { ReservationApiService } from '../../../../reservations/services/reservation-api.service';
import { UserApiService } from '../../../../user/services/user-api.service';
import { StaffReservationsPage } from './staff-reservations-page';

function buildReservation(overrides: Partial<ReservationResponse> = {}): ReservationResponse {
  return {
    id: 1,
    member: { id: 7, memberNumber: 'M000000001', firstName: 'Marie', lastName: 'Curie' },
    title: { id: 30, title: 'Titre réservé', isbn: '978-2-1234-5680-3' },
    assignedCopy: null,
    fulfilledByLoanId: null,
    reservationDate: '2026-08-01T09:00:00Z',
    expirationDate: null,
    reservationStatus: 'WAITING',
    createdAt: '2026-08-01T09:00:00Z',
    updatedAt: '2026-08-01T09:00:00Z',
    ...overrides,
  };
}

function buildPage(
  content: ReservationResponse[],
  totalElements = content.length,
): PageResponse<ReservationResponse> {
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
      path: '/api/v1/reservations',
      fieldErrors: [],
    },
  });
}

describe('StaffReservationsPage', () => {
  let fixture: ComponentFixture<StaffReservationsPage>;
  let reservationApiServiceMock: {
    listOwnReservations: ReturnType<typeof vi.fn>;
    listReservations: ReturnType<typeof vi.fn>;
    createOwnReservation: ReturnType<typeof vi.fn>;
    createReservation: ReturnType<typeof vi.fn>;
    cancelOwnReservation: ReturnType<typeof vi.fn>;
    cancelReservation: ReturnType<typeof vi.fn>;
  };
  let authServiceMock: { hasPermission: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let confirmationServiceMock: { confirm: ReturnType<typeof vi.fn> };
  let userApiServiceMock: { listUsers: ReturnType<typeof vi.fn> };
  let staffCatalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };

  function configure(): void {
    reservationApiServiceMock = {
      listOwnReservations: vi.fn(),
      listReservations: vi.fn(),
      createOwnReservation: vi.fn(),
      createReservation: vi.fn(),
      cancelOwnReservation: vi.fn(),
      cancelReservation: vi.fn(),
    };
    authServiceMock = { hasPermission: vi.fn().mockReturnValue(true) };
    messageServiceMock = { add: vi.fn() };
    confirmationServiceMock = { confirm: vi.fn() };
    // StaffReservationCreateDialog (DEV-08.13) est désormais un enfant réel
    // de la page ; ses propres dépendances doivent être fournies même si
    // les tests de ce fichier n'exercent pas directement la recherche
    // membre/Title (déjà couverte par staff-reservation-create-dialog.spec.ts)
    // — même précédent exact que StaffLoansPage/LoanCreateDialog.
    userApiServiceMock = { listUsers: vi.fn() };
    staffCatalogueApiServiceMock = { searchTitles: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffReservationsPage],
      providers: [
        { provide: ReservationApiService, useValue: reservationApiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
        { provide: UserApiService, useValue: userApiServiceMock },
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffReservationsPage);
    fixture.detectChanges();
  }

  // ---------------------------------------------------------------
  // Chargement / contrat API
  // ---------------------------------------------------------------

  it('should call listReservations(0, 20) on initial load', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()])));

    createComponent();

    expect(reservationApiServiceMock.listReservations).toHaveBeenCalledWith(0, 20);
  });

  it('should never call the self-service listOwnReservations endpoint', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()])));

    createComponent();
    fixture.componentInstance.onLazyLoad({ first: 20, rows: 20 });
    fixture.componentInstance.retry();

    expect(reservationApiServiceMock.listOwnReservations).not.toHaveBeenCalled();
  });

  it('should render member firstName/lastName/memberNumber', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(
        buildPage([
          buildReservation({ member: { id: 7, memberNumber: 'M000000042', firstName: 'Ada', lastName: 'Lovelace' } }),
        ]),
      ),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Ada');
    expect(text).toContain('Lovelace');
    expect(text).toContain('M000000042');
  });

  it('should render the Title', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(buildPage([buildReservation({ title: { id: 30, title: 'Notre-Dame de Paris', isbn: null } })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Notre-Dame de Paris');
  });

  it('should map a PrimeNG lazy load event to page/size and call listReservations again', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()], 100)));
    createComponent();
    expect(fixture.componentInstance.totalRecords()).toBe(100);
    reservationApiServiceMock.listReservations.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(reservationApiServiceMock.listReservations).toHaveBeenCalledWith(2, 20);
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()])));
    createComponent();
    reservationApiServiceMock.listReservations.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(reservationApiServiceMock.listReservations).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // ReservationStatus
  // ---------------------------------------------------------------

  it('should render WAITING reservations', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(buildPage([buildReservation({ reservationStatus: 'WAITING' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('WAITING');
  });

  it('should render READY reservations', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(buildPage([buildReservation({ reservationStatus: 'READY' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('READY');
  });

  it('should render FULFILLED reservations', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(buildPage([buildReservation({ reservationStatus: 'FULFILLED' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('FULFILLED');
  });

  it('should render CANCELLED reservations', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(buildPage([buildReservation({ reservationStatus: 'CANCELLED' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('CANCELLED');
  });

  it('should render EXPIRED reservations', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(buildPage([buildReservation({ reservationStatus: 'EXPIRED' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('EXPIRED');
  });

  // ---------------------------------------------------------------
  // expirationDate / assignedCopy
  // ---------------------------------------------------------------

  it('should render expirationDate when present', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(
        buildPage([
          buildReservation({
            reservationStatus: 'READY',
            expirationDate: '2026-08-22T12:00:00Z',
            assignedCopy: { id: 40, inventoryCode: 'INV-000040', titleId: 30 },
          }),
        ]),
      ),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2026-08-22T12:00:00Z');
  });

  it('should render assignedCopy.inventoryCode when present', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(
        buildPage([
          buildReservation({
            reservationStatus: 'READY',
            assignedCopy: { id: 40, inventoryCode: 'INV-000099', titleId: 30 },
          }),
        ]),
      ),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('INV-000099');
  });

  it('should render placeholders, not raw null, when expirationDate/assignedCopy are absent', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(buildPage([buildReservation({ expirationDate: null, assignedCopy: null })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('—');
    expect(text).not.toContain('null');
  });

  // ---------------------------------------------------------------
  // États UI
  // ---------------------------------------------------------------

  it('should show the loading state before the first response arrives', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when the backend returns no reservation', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([], 0)));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  it('should show the error state on a failed request', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the last request when retry is triggered', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();
    reservationApiServiceMock.listReservations.mockClear();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()])));

    fixture.componentInstance.retry();

    expect(reservationApiServiceMock.listReservations).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // Permissions UX — RESERVATION_READ vs RESERVATION_MANAGE
  // ---------------------------------------------------------------

  it('should show no cancel action and no Action column when the user only has RESERVATION_READ', () => {
    configure();
    authServiceMock.hasPermission.mockImplementation((permission: string) => permission !== 'RESERVATION_MANAGE');
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(buildPage([buildReservation({ reservationStatus: 'WAITING' })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Annuler');
    expect(text).not.toContain('Action');
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nonPaginatorButtons = buttons.filter((button) => !button.closest('.p-paginator'));
    expect(nonPaginatorButtons).toHaveLength(0);
    expect(authServiceMock.hasPermission).toHaveBeenCalledWith('RESERVATION_MANAGE');
  });

  it('should show the cancel action for a WAITING reservation when the user has RESERVATION_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(buildPage([buildReservation({ id: 1, reservationStatus: 'WAITING' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Annuler');
  });

  it('should show the cancel action for a READY reservation when the user has RESERVATION_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    reservationApiServiceMock.listReservations.mockReturnValue(
      of(buildPage([buildReservation({ id: 1, reservationStatus: 'READY' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Annuler');
  });

  const terminalStatuses: ReservationStatus[] = ['FULFILLED', 'CANCELLED', 'EXPIRED'];

  for (const status of terminalStatuses) {
    it(`should never show the cancel action for a ${status} reservation, even with RESERVATION_MANAGE`, () => {
      configure();
      authServiceMock.hasPermission.mockReturnValue(true);
      reservationApiServiceMock.listReservations.mockReturnValue(
        of(buildPage([buildReservation({ id: 1, reservationStatus: status })])),
      );

      createComponent();

      expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Annuler');
    });
  }

  // ---------------------------------------------------------------
  // Pas de création staff (DEV-08.13)
  // ---------------------------------------------------------------

  it('should never render the "Nouvelle réservation" button without RESERVATION_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockImplementation((permission: string) => permission !== 'RESERVATION_MANAGE');
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Nouvelle réservation');
  });

  it('should not render the StaffReservationCreateDialog element at all without RESERVATION_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockImplementation((permission: string) => permission !== 'RESERVATION_MANAGE');
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()])));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-staff-reservation-create-dialog')).toBeNull();
  });

  it('should render the "Nouvelle réservation" button with RESERVATION_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nouvelle réservation');
  });

  it('should open the create dialog when "Nouvelle réservation" is clicked', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()])));
    createComponent();

    fixture.componentInstance.openCreateDialog();

    expect(fixture.componentInstance.createDialogVisible()).toBe(true);
  });

  it('should close the dialog without reloading the list when it is simply closed/cancelled', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()])));
    createComponent();
    fixture.componentInstance.openCreateDialog();
    reservationApiServiceMock.listReservations.mockClear();

    fixture.componentInstance.closeCreateDialog();

    expect(fixture.componentInstance.createDialogVisible()).toBe(false);
    expect(reservationApiServiceMock.listReservations).not.toHaveBeenCalled();
  });

  it('should close the dialog and reload the CURRENT page (not page 0) when a reservation is created', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation()], 100)));
    createComponent();
    fixture.componentInstance.openCreateDialog();
    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 }); // page courante = 2
    reservationApiServiceMock.listReservations.mockClear();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation({ id: 999 })], 101)));

    fixture.componentInstance.onReservationCreated(buildReservation({ id: 999 }));

    expect(fixture.componentInstance.createDialogVisible()).toBe(false);
    expect(reservationApiServiceMock.listReservations).toHaveBeenCalledWith(2, 20);
  });

  it('should never fabricate a ReservationResponse locally — onReservationCreated always reloads from the backend', () => {
    configure();
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([buildReservation({ id: 1 })])));
    createComponent();
    const reloaded = buildPage([buildReservation({ id: 1 }), buildReservation({ id: 999 })], 2);
    reservationApiServiceMock.listReservations.mockReturnValue(of(reloaded));

    fixture.componentInstance.onReservationCreated(buildReservation({ id: 999 }));

    expect(fixture.componentInstance.rows()).toEqual(reloaded.content);
  });

  // ---------------------------------------------------------------
  // Annulation staff
  // ---------------------------------------------------------------

  it('should require confirmation before calling cancelReservation', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([reservation])));
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(reservationApiServiceMock.cancelReservation).not.toHaveBeenCalled();
  });

  it('should make no HTTP call when the confirmation is not accepted', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([reservation])));
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    // La confirmation n'est jamais acceptée : aucun accept() invoqué.

    expect(reservationApiServiceMock.cancelReservation).not.toHaveBeenCalled();
  });

  it('should call cancelReservation(id) once confirmation is accepted, never the self-service cancelOwnReservation', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelReservation.mockReturnValue(
      of(buildReservation({ id: 1, reservationStatus: 'CANCELLED' })),
    );
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(reservationApiServiceMock.cancelReservation).toHaveBeenCalledWith(1);
    expect(reservationApiServiceMock.cancelOwnReservation).not.toHaveBeenCalled();
  });

  it('should show a success toast and replace the row with the exact backend response after a successful cancellation', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    const cancelled = buildReservation({ id: 1, reservationStatus: 'CANCELLED' });
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelReservation.mockReturnValue(of(cancelled));
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([cancelled]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show an error toast and never mutate the row when cancelReservation fails', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelReservation.mockReturnValue(
      throwError(() => apiHttpError(409, 'RESERVATION_NOT_CANCELLABLE', 'Cette réservation n’est plus annulable.')),
    );
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([reservation]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Cette réservation n’est plus annulable.' }),
    );
  });

  it('should surface RESERVATION_CANCELLATION_CONTENTION as a plain error, row left unchanged', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'READY' });
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelReservation.mockReturnValue(
      throwError(() =>
        apiHttpError(
          409,
          'RESERVATION_CANCELLATION_CONTENTION',
          'Impossible d’annuler la réservation : contention concurrente trop forte.',
        ),
      ),
    );
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([reservation]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        detail: 'Impossible d’annuler la réservation : contention concurrente trop forte.',
      }),
    );
    expect(messageServiceMock.add).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should surface RESERVATION_ASSIGNMENT_CONTENTION (FIFO reassignment side effect) as a plain error, never a silent success', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'READY' });
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelReservation.mockReturnValue(
      throwError(() =>
        apiHttpError(409, 'RESERVATION_ASSIGNMENT_CONTENTION', 'Le Copy n’a pas pu être réaffecté, veuillez réessayer.'),
      ),
    );
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([reservation]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(messageServiceMock.add).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should surface RESERVATION_NOT_FOUND as a plain error, row left unchanged', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelReservation.mockReturnValue(
      throwError(() => apiHttpError(404, 'RESERVATION_NOT_FOUND', 'Aucune réservation pour cet identifiant.')),
    );
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([reservation]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Aucune réservation pour cet identifiant.' }),
    );
  });

  // ---------------------------------------------------------------
  // Conservation de l'historique READY→CANCELLED (DEV-DEC-0038)
  // ---------------------------------------------------------------

  it('should replace the row with a CANCELLED response that still carries assignedCopy/expirationDate, never stripping the historical trace', () => {
    configure();
    const reservation = buildReservation({
      id: 1,
      reservationStatus: 'READY',
      assignedCopy: { id: 40, inventoryCode: 'INV-000040', titleId: 30 },
      expirationDate: '2026-08-22T12:00:00Z',
    });
    const cancelled: ReservationResponse = {
      ...reservation,
      reservationStatus: 'CANCELLED',
      updatedAt: '2026-08-20T08:00:00Z',
    };
    reservationApiServiceMock.listReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelReservation.mockReturnValue(of(cancelled));
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([cancelled]);
    expect(fixture.componentInstance.rows()[0].assignedCopy).toEqual({
      id: 40,
      inventoryCode: 'INV-000040',
      titleId: 30,
    });
    expect(fixture.componentInstance.rows()[0].expirationDate).toBe('2026-08-22T12:00:00Z');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('INV-000040');
    expect(text).toContain('2026-08-22T12:00:00Z');
  });
});
