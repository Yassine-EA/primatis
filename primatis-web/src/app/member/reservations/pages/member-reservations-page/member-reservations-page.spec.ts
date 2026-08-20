import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CatalogueApiService } from '../../../../catalogue/services/catalogue-api.service';
import { PageResponse } from '../../../../core/models/page-response';
import { ReservationResponse } from '../../../../reservations/models/reservation-response';
import { ReservationStatus } from '../../../../reservations/models/reservation-status';
import { ReservationApiService } from '../../../../reservations/services/reservation-api.service';
import { MemberReservationsPage } from './member-reservations-page';

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
      path: '/api/v1/me/reservations',
      fieldErrors: [],
    },
  });
}

describe('MemberReservationsPage', () => {
  let fixture: ComponentFixture<MemberReservationsPage>;
  let reservationApiServiceMock: {
    listOwnReservations: ReturnType<typeof vi.fn>;
    listReservations: ReturnType<typeof vi.fn>;
    createOwnReservation: ReturnType<typeof vi.fn>;
    createReservation: ReturnType<typeof vi.fn>;
    cancelOwnReservation: ReturnType<typeof vi.fn>;
    cancelReservation: ReturnType<typeof vi.fn>;
  };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let confirmationServiceMock: { confirm: ReturnType<typeof vi.fn> };
  let catalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };

  function configure(): void {
    reservationApiServiceMock = {
      listOwnReservations: vi.fn(),
      listReservations: vi.fn(),
      createOwnReservation: vi.fn(),
      createReservation: vi.fn(),
      cancelOwnReservation: vi.fn(),
      cancelReservation: vi.fn(),
    };
    messageServiceMock = { add: vi.fn() };
    confirmationServiceMock = { confirm: vi.fn() };
    // ReservationCreateDialog (DEV-08.11) est désormais un enfant réel de
    // la page ; ses propres dépendances doivent être fournies même si les
    // tests de ce fichier n'exercent pas directement la recherche Title
    // (déjà couverte par reservation-create-dialog.spec.ts) — même
    // précédent exact que StaffLoansPage/LoanCreateDialog.
    catalogueApiServiceMock = {
      searchTitles: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
    };

    TestBed.configureTestingModule({
      imports: [MemberReservationsPage],
      providers: [
        { provide: ReservationApiService, useValue: reservationApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
        { provide: CatalogueApiService, useValue: catalogueApiServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(MemberReservationsPage);
    fixture.detectChanges();
  }

  // ---------------------------------------------------------------
  // Chargement / contrat API
  // ---------------------------------------------------------------

  it('should call listOwnReservations(0, 20) on initial load', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()])));

    createComponent();

    expect(reservationApiServiceMock.listOwnReservations).toHaveBeenCalledWith(0, 20);
  });

  it('should never call the staff listReservations endpoint', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()])));

    createComponent();
    fixture.componentInstance.onLazyLoad({ first: 20, rows: 20 });
    fixture.componentInstance.retry();

    expect(reservationApiServiceMock.listReservations).not.toHaveBeenCalled();
  });

  it('should render the reservations returned by the API', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
      of(buildPage([buildReservation({ title: { id: 30, title: 'Les Misérables', isbn: null } })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Les Misérables');
    expect(text).toContain('2026-08-01T09:00:00Z');
  });

  it('should never expose its own member name on its own consultation page (redundant on /me/reservations)', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Marie');
    expect(text).not.toContain('Curie');
    expect(text).not.toContain('M000000001');
  });

  it('should map a PrimeNG lazy load event to page/size and call listOwnReservations again', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()], 100)));
    createComponent();
    expect(fixture.componentInstance.totalRecords()).toBe(100);
    reservationApiServiceMock.listOwnReservations.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(reservationApiServiceMock.listOwnReservations).toHaveBeenCalledWith(2, 20);
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()])));
    createComponent();
    reservationApiServiceMock.listOwnReservations.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(reservationApiServiceMock.listOwnReservations).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // ReservationStatus
  // ---------------------------------------------------------------

  it('should render WAITING reservations', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
      of(buildPage([buildReservation({ reservationStatus: 'WAITING' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('WAITING');
  });

  it('should render READY reservations', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
      of(buildPage([buildReservation({ reservationStatus: 'READY' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('READY');
  });

  it('should render FULFILLED reservations', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
      of(buildPage([buildReservation({ reservationStatus: 'FULFILLED' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('FULFILLED');
  });

  it('should render CANCELLED reservations', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
      of(buildPage([buildReservation({ reservationStatus: 'CANCELLED' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('CANCELLED');
  });

  it('should render EXPIRED reservations', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
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
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
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

  it("should render assignedCopy.inventoryCode when present", () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
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
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
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
    reservationApiServiceMock.listOwnReservations.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when the backend returns no reservation', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([], 0)));

    createComponent();

    const emptyState = fixture.nativeElement.querySelector('app-empty-state');
    expect(emptyState).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucune réservation à afficher.');
  });

  it('should show the error state on a failed request', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the last request when retry is triggered', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();
    reservationApiServiceMock.listOwnReservations.mockClear();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()])));

    fixture.componentInstance.retry();

    expect(reservationApiServiceMock.listOwnReservations).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // Pas de création (DEV-DEC-0045)
  // ---------------------------------------------------------------

  // ---------------------------------------------------------------
  // Création (DEV-08.11, intégration minimale du dialog)
  // ---------------------------------------------------------------

  it('should render the "Nouvelle réservation" creation button', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nouvelle réservation');
  });

  it('should open the create dialog when "Nouvelle réservation" is clicked', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()])));
    createComponent();

    fixture.componentInstance.openCreateDialog();

    expect(fixture.componentInstance.createDialogVisible()).toBe(true);
  });

  it('should keep the dialog closed and non-interactive until explicitly opened', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()])));

    createComponent();

    expect(fixture.componentInstance.createDialogVisible()).toBe(false);
  });

  it('should close the dialog without reloading the list when it is simply closed/cancelled', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()])));
    createComponent();
    fixture.componentInstance.openCreateDialog();
    reservationApiServiceMock.listOwnReservations.mockClear();

    fixture.componentInstance.closeCreateDialog();

    expect(fixture.componentInstance.createDialogVisible()).toBe(false);
    expect(reservationApiServiceMock.listOwnReservations).not.toHaveBeenCalled();
  });

  it('should close the dialog and reload from page 0 when a reservation is created', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation()], 100)));
    createComponent();
    fixture.componentInstance.openCreateDialog();
    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 }); // page courante = 2
    reservationApiServiceMock.listOwnReservations.mockClear();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation({ id: 999 })], 101)));

    fixture.componentInstance.onReservationCreated(buildReservation({ id: 999 }));

    expect(fixture.componentInstance.createDialogVisible()).toBe(false);
    expect(reservationApiServiceMock.listOwnReservations).toHaveBeenCalledWith(0, 20);
  });

  it('should never fabricate a ReservationResponse locally — onReservationCreated always reloads from the backend', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([buildReservation({ id: 1 })])));
    createComponent();
    const reloaded = buildPage([buildReservation({ id: 999 }), buildReservation({ id: 1 })], 2);
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(reloaded));

    fixture.componentInstance.onReservationCreated(buildReservation({ id: 999 }));

    expect(fixture.componentInstance.rows()).toEqual(reloaded.content);
  });

  // ---------------------------------------------------------------
  // Visibilité de l'action Annuler
  // ---------------------------------------------------------------

  const nonCancellableStatuses: ReservationStatus[] = ['FULFILLED', 'CANCELLED', 'EXPIRED'];

  it('should show the cancel action for a WAITING reservation', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
      of(buildPage([buildReservation({ id: 1, reservationStatus: 'WAITING' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Annuler');
  });

  it('should show the cancel action for a READY reservation', () => {
    configure();
    reservationApiServiceMock.listOwnReservations.mockReturnValue(
      of(buildPage([buildReservation({ id: 1, reservationStatus: 'READY' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Annuler');
  });

  for (const status of nonCancellableStatuses) {
    it(`should never show the cancel action for a ${status} reservation`, () => {
      configure();
      reservationApiServiceMock.listOwnReservations.mockReturnValue(
        of(buildPage([buildReservation({ id: 1, reservationStatus: status })])),
      );

      createComponent();

      expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Annuler');
    });
  }

  // ---------------------------------------------------------------
  // Annulation self
  // ---------------------------------------------------------------

  it('should require confirmation before calling cancelOwnReservation', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([reservation])));
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(reservationApiServiceMock.cancelOwnReservation).not.toHaveBeenCalled();
  });

  it('should make no HTTP call when the confirmation is not accepted', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([reservation])));
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    // La confirmation n'est jamais acceptée : aucun accept() invoqué.

    expect(reservationApiServiceMock.cancelOwnReservation).not.toHaveBeenCalled();
  });

  it('should call cancelOwnReservation(id) once confirmation is accepted, never the staff cancelReservation', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelOwnReservation.mockReturnValue(
      of(buildReservation({ id: 1, reservationStatus: 'CANCELLED' })),
    );
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(reservationApiServiceMock.cancelOwnReservation).toHaveBeenCalledWith(1);
    expect(reservationApiServiceMock.cancelReservation).not.toHaveBeenCalled();
  });

  it('should show a success toast and replace the row with the exact backend response after a successful cancellation', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    const cancelled = buildReservation({ id: 1, reservationStatus: 'CANCELLED' });
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelOwnReservation.mockReturnValue(of(cancelled));
    createComponent();

    fixture.componentInstance.confirmCancel(reservation);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([cancelled]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show an error toast and never mutate the row when cancelOwnReservation fails', () => {
    configure();
    const reservation = buildReservation({ id: 1, reservationStatus: 'WAITING' });
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelOwnReservation.mockReturnValue(
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
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelOwnReservation.mockReturnValue(
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
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelOwnReservation.mockReturnValue(
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
    reservationApiServiceMock.listOwnReservations.mockReturnValue(of(buildPage([reservation])));
    reservationApiServiceMock.cancelOwnReservation.mockReturnValue(of(cancelled));
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
