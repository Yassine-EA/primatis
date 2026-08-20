import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../../core/models/page-response';
import { FineResponse } from '../../../../fines/models/fine-response';
import { FineApiService } from '../../../../fines/services/fine-api.service';
import { MemberFinesPage } from './member-fines-page';

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

function apiHttpError(code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 500,
    error: {
      timestamp: new Date().toISOString(),
      status: 500,
      error: 'Internal Server Error',
      code,
      message,
      path: '/api/v1/me/fines',
      fieldErrors: [],
    },
  });
}

describe('MemberFinesPage', () => {
  let fixture: ComponentFixture<MemberFinesPage>;
  let fineApiServiceMock: { listOwnFines: ReturnType<typeof vi.fn>; listFines: ReturnType<typeof vi.fn> };

  function configure(): void {
    fineApiServiceMock = { listOwnFines: vi.fn(), listFines: vi.fn() };

    TestBed.configureTestingModule({
      imports: [MemberFinesPage],
      providers: [{ provide: FineApiService, useValue: fineApiServiceMock }],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(MemberFinesPage);
    fixture.detectChanges();
  }

  // ---------------------------------------------------------------
  // Chargement / contrat API / ownership
  // ---------------------------------------------------------------

  it('should call listOwnFines(0, 20) on initial load, with no extra userId argument', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine()])));

    createComponent();

    expect(fineApiServiceMock.listOwnFines).toHaveBeenCalledWith(0, 20);
    expect(fineApiServiceMock.listOwnFines).toHaveBeenCalledTimes(1);
  });

  it('should never call the staff listFines endpoint', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine()])));

    createComponent();
    fixture.componentInstance.onLazyLoad({ first: 20, rows: 20 });
    fixture.componentInstance.retry();

    expect(fineApiServiceMock.listFines).not.toHaveBeenCalled();
  });

  // ---------------------------------------------------------------
  // Pagination
  // ---------------------------------------------------------------

  it('should map a PrimeNG lazy load event to page/size and call listOwnFines again', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine()], 100)));
    createComponent();
    fineApiServiceMock.listOwnFines.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(fineApiServiceMock.listOwnFines).toHaveBeenCalledWith(2, 20);
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine()])));
    createComponent();
    fineApiServiceMock.listOwnFines.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(fineApiServiceMock.listOwnFines).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // FineStatus
  // ---------------------------------------------------------------

  it('should render an UNPAID fine', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine({ fineStatus: 'UNPAID' })])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Impayée');
  });

  it('should render a PAID fine', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(
      of(buildPage([buildFine({ fineStatus: 'PAID', paidAt: '2026-08-10T09:00:00Z' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Payée');
  });

  it('should render a CANCELLED fine', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(
      of(buildPage([buildFine({ fineStatus: 'CANCELLED', cancelledAt: '2026-08-10T09:00:00Z' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Annulée');
  });

  // ---------------------------------------------------------------
  // Montant (CurrencyPipe)
  // ---------------------------------------------------------------

  it('should render the amount formatted as an EUR currency, not a raw number or manual concatenation', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine({ amount: 11.2 })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('€');
    expect(text).not.toContain('11.2 ');
  });

  // ---------------------------------------------------------------
  // paidAt / cancelledAt
  // ---------------------------------------------------------------

  it('should render paidAt only when present', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(
      of(buildPage([buildFine({ fineStatus: 'PAID', paidAt: '2026-08-10T09:00:00Z' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2026-08-10T09:00:00Z');
  });

  it('should render a placeholder, not a raw null, when paidAt is absent', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine({ paidAt: null })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('—');
    expect(text).not.toContain('null');
  });

  it('should render cancelledAt only when present', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(
      of(buildPage([buildFine({ fineStatus: 'CANCELLED', cancelledAt: '2026-08-11T09:00:00Z' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2026-08-11T09:00:00Z');
  });

  it('should render a placeholder, not a raw null, when cancelledAt is absent', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine({ cancelledAt: null })])));

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
    fineApiServiceMock.listOwnFines.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when the backend returns no fine', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([], 0)));

    createComponent();

    const emptyState = fixture.nativeElement.querySelector('app-empty-state');
    expect(emptyState).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucune amende à afficher.');
  });

  it('should show the error state on a failed request', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the last request when retry is triggered', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();
    fineApiServiceMock.listOwnFines.mockClear();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine()])));

    fixture.componentInstance.retry();

    expect(fineApiServiceMock.listOwnFines).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // Sécurité UX — lecture seule stricte
  // ---------------------------------------------------------------

  it('should never render a payment confirmation or cancellation action', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine()])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Confirmer paiement');
    expect(text).not.toContain('Annuler');
    expect(text).not.toContain('Payer');
  });

  it('should render no action <button> other than the PrimeNG paginator controls (strictly read-only)', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine()])));

    createComponent();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nonPaginatorButtons = buttons.filter((button) => !button.closest('.p-paginator'));
    expect(nonPaginatorButtons).toHaveLength(0);
  });

  it('should never expose the borrower on its own consultation page (redundant on /me/fines)', () => {
    configure();
    fineApiServiceMock.listOwnFines.mockReturnValue(of(buildPage([buildFine()])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Marie');
    expect(text).not.toContain('Curie');
    expect(text).not.toContain('M000000001');
  });
});
