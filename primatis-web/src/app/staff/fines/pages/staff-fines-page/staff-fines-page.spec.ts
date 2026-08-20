import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../../auth/services/auth.service';
import { PageResponse } from '../../../../core/models/page-response';
import { FineResponse } from '../../../../fines/models/fine-response';
import { FineApiService } from '../../../../fines/services/fine-api.service';
import { StaffFinesPage } from './staff-fines-page';

function buildFine(overrides: Partial<FineResponse> = {}): FineResponse {
  return {
    id: 1,
    borrower: { id: 7, memberNumber: 'M000000001', firstName: 'Marie', lastName: 'Curie' },
    loan: {
      id: 20,
      loanDate: '2026-07-01T09:00:00Z',
      dueDate: '2026-07-22',
      returnDate: '2026-08-05',
      copy: { id: 30, inventoryCode: 'INV-000030', titleId: 40 },
    },
    amount: 11.2,
    reason: 'Retard de 2 semaine(s)',
    issuedAt: '2026-08-05T10:00:00Z',
    fineStatus: 'UNPAID',
    paidAt: null,
    cancelledAt: null,
    ...overrides,
  };
}

function buildPage(content: FineResponse[], totalElements = content.length): PageResponse<FineResponse> {
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
      path: '/api/v1/fines',
      fieldErrors: [],
    },
  });
}

describe('StaffFinesPage', () => {
  let fixture: ComponentFixture<StaffFinesPage>;
  let fineApiServiceMock: {
    listOwnFines: ReturnType<typeof vi.fn>;
    listFines: ReturnType<typeof vi.fn>;
    confirmPayment: ReturnType<typeof vi.fn>;
    cancelFine: ReturnType<typeof vi.fn>;
  };
  let authServiceMock: { hasPermission: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let confirmationServiceMock: { confirm: ReturnType<typeof vi.fn> };

  function configure(): void {
    fineApiServiceMock = {
      listOwnFines: vi.fn(),
      listFines: vi.fn(),
      confirmPayment: vi.fn(),
      cancelFine: vi.fn(),
    };
    authServiceMock = { hasPermission: vi.fn().mockReturnValue(true) };
    messageServiceMock = { add: vi.fn() };
    confirmationServiceMock = { confirm: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffFinesPage],
      providers: [
        { provide: FineApiService, useValue: fineApiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffFinesPage);
    fixture.detectChanges();
  }

  // ---------------------------------------------------------------
  // Chargement / contrat API
  // ---------------------------------------------------------------

  it('should call listFines(0, 20) on initial load', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()])));

    createComponent();

    expect(fineApiServiceMock.listFines).toHaveBeenCalledWith(0, 20);
  });

  it('should never call the self-service listOwnFines endpoint', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()])));

    createComponent();
    fixture.componentInstance.onLazyLoad({ first: 20, rows: 20 });
    fixture.componentInstance.retry();

    expect(fineApiServiceMock.listOwnFines).not.toHaveBeenCalled();
  });

  it('should map a PrimeNG lazy load event to page/size and call listFines again', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()], 100)));
    createComponent();
    fineApiServiceMock.listFines.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(fineApiServiceMock.listFines).toHaveBeenCalledWith(2, 20);
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()])));
    createComponent();
    fineApiServiceMock.listFines.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(fineApiServiceMock.listFines).toHaveBeenCalledWith(0, 20);
  });

  it('should show the loading state before the first response arrives', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when the backend returns no fine', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([], 0)));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  it('should show the error state on a failed request', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the last request when retry is triggered', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();
    fineApiServiceMock.listFines.mockClear();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine()])));

    fixture.componentInstance.retry();

    expect(fineApiServiceMock.listFines).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // Permissions UX — FINE_READ (implicite, route) vs FINE_MANAGE
  // ---------------------------------------------------------------

  it('should show no action and no Actions column when the user only has FINE_READ', () => {
    configure();
    authServiceMock.hasPermission.mockImplementation((permission: string) => permission !== 'FINE_MANAGE');
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine({ fineStatus: 'UNPAID' })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Confirmer paiement');
    expect(text).not.toContain('Annuler');
    expect(text).not.toContain('Actions');
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nonPaginatorButtons = buttons.filter((button) => !button.closest('.p-paginator'));
    expect(nonPaginatorButtons).toHaveLength(0);
    expect(authServiceMock.hasPermission).toHaveBeenCalledWith('FINE_MANAGE');
  });

  it('should show both actions for an UNPAID fine when the user has FINE_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine({ id: 1, fineStatus: 'UNPAID' })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Confirmer paiement');
    expect(text).toContain('Annuler');
  });

  it('should never show an action for a PAID fine, even with FINE_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    fineApiServiceMock.listFines.mockReturnValue(
      of(buildPage([buildFine({ id: 1, fineStatus: 'PAID', paidAt: '2026-08-10T09:00:00Z' })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Confirmer paiement');
    expect(text).not.toContain('Annuler');
  });

  it('should never show an action for a CANCELLED fine, even with FINE_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    fineApiServiceMock.listFines.mockReturnValue(
      of(buildPage([buildFine({ id: 1, fineStatus: 'CANCELLED', cancelledAt: '2026-08-10T09:00:00Z' })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Confirmer paiement');
    expect(text).not.toContain('Annuler');
  });

  // ---------------------------------------------------------------
  // Confirmation du paiement
  // ---------------------------------------------------------------

  it('should require confirmation before calling confirmPayment', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(fineApiServiceMock.confirmPayment).not.toHaveBeenCalled();
  });

  it('should make no HTTP call when the payment confirmation prompt is not accepted', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);
    // La confirmation n'est jamais acceptée : aucun accept() invoqué.

    expect(fineApiServiceMock.confirmPayment).not.toHaveBeenCalled();
  });

  it('should call confirmPayment(id) once confirmation is accepted', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.confirmPayment.mockReturnValue(
      of(buildFine({ id: 1, fineStatus: 'PAID', paidAt: '2026-08-20T10:00:00Z' })),
    );
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fineApiServiceMock.confirmPayment).toHaveBeenCalledWith(1);
  });

  it('should disable both action buttons on the row while the payment request is pending', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    const pending = new Subject<FineResponse>();
    fineApiServiceMock.confirmPayment.mockReturnValue(pending.asObservable());
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nonPaginatorButtons = buttons.filter((button) => !button.closest('.p-paginator'));
    expect(nonPaginatorButtons.every((button) => button.disabled)).toBe(true);

    pending.next(buildFine({ id: 1, fineStatus: 'PAID', paidAt: '2026-08-20T10:00:00Z' }));
    pending.complete();
  });

  it('should show a success toast and replace the row with the exact backend response after a successful payment confirmation', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    const paid = buildFine({ id: 1, fineStatus: 'PAID', paidAt: '2026-08-20T10:00:00Z' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.confirmPayment.mockReturnValue(of(paid));
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([paid]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show an error toast and never mutate the row when confirmPayment fails', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.confirmPayment.mockReturnValue(
      throwError(() => apiHttpError(409, 'FINE_NOT_PAYABLE', 'Cette amende n’est plus payable.')),
    );
    createComponent();

    fixture.componentInstance.confirmPaymentPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([fine]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Cette amende n’est plus payable.' }),
    );
  });

  // ---------------------------------------------------------------
  // Annulation
  // ---------------------------------------------------------------

  it('should require confirmation before calling cancelFine', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(fineApiServiceMock.cancelFine).not.toHaveBeenCalled();
  });

  it('should make no HTTP call when the cancellation prompt is not accepted', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);

    expect(fineApiServiceMock.cancelFine).not.toHaveBeenCalled();
  });

  it('should call cancelFine(id) once confirmation is accepted', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.cancelFine.mockReturnValue(
      of(buildFine({ id: 1, fineStatus: 'CANCELLED', cancelledAt: '2026-08-20T10:00:00Z' })),
    );
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fineApiServiceMock.cancelFine).toHaveBeenCalledWith(1);
  });

  it('should disable both action buttons on the row while the cancellation request is pending', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    const pending = new Subject<FineResponse>();
    fineApiServiceMock.cancelFine.mockReturnValue(pending.asObservable());
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nonPaginatorButtons = buttons.filter((button) => !button.closest('.p-paginator'));
    expect(nonPaginatorButtons.every((button) => button.disabled)).toBe(true);

    pending.next(buildFine({ id: 1, fineStatus: 'CANCELLED', cancelledAt: '2026-08-20T10:00:00Z' }));
    pending.complete();
  });

  it('should show a success toast and replace the row with the exact backend response after a successful cancellation', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    const cancelled = buildFine({ id: 1, fineStatus: 'CANCELLED', cancelledAt: '2026-08-20T10:00:00Z' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.cancelFine.mockReturnValue(of(cancelled));
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([cancelled]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show an error toast and never mutate the row when cancelFine fails', () => {
    configure();
    const fine = buildFine({ id: 1, fineStatus: 'UNPAID' });
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([fine])));
    fineApiServiceMock.cancelFine.mockReturnValue(
      throwError(() => apiHttpError(409, 'FINE_NOT_CANCELLABLE', 'Cette amende n’est plus annulable.')),
    );
    createComponent();

    fixture.componentInstance.confirmCancelPrompt(fine);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([fine]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Cette amende n’est plus annulable.' }),
    );
  });

  // ---------------------------------------------------------------
  // Rendu
  // ---------------------------------------------------------------

  it('should render the amount formatted as an EUR currency, not a raw number', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(of(buildPage([buildFine({ amount: 11.2 })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('€');
  });

  it('should render borrower firstName/lastName/memberNumber', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(
      of(
        buildPage([
          buildFine({ borrower: { id: 7, memberNumber: 'M000000042', firstName: 'Ada', lastName: 'Lovelace' } }),
        ]),
      ),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Ada');
    expect(text).toContain('Lovelace');
    expect(text).toContain('M000000042');
  });

  it('should render UNPAID/PAID/CANCELLED status labels', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(
      of(
        buildPage([
          buildFine({ id: 1, fineStatus: 'UNPAID' }),
          buildFine({ id: 2, fineStatus: 'PAID', paidAt: '2026-08-10T09:00:00Z' }),
          buildFine({ id: 3, fineStatus: 'CANCELLED', cancelledAt: '2026-08-10T09:00:00Z' }),
        ]),
      ),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Impayée');
    expect(text).toContain('Payée');
    expect(text).toContain('Annulée');
  });

  it('should render placeholders, not raw null, when paidAt/cancelledAt are absent', () => {
    configure();
    fineApiServiceMock.listFines.mockReturnValue(
      of(buildPage([buildFine({ paidAt: null, cancelledAt: null })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('—');
    expect(text).not.toContain('null');
  });
});
