import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../../core/models/page-response';
import { LoanResponse } from '../../../../loans/models/loan-response';
import { LoanApiService } from '../../../../loans/services/loan-api.service';
import { MemberLoansPage } from './member-loans-page';

function buildLoan(overrides: Partial<LoanResponse> = {}): LoanResponse {
  return {
    id: 1,
    borrower: { id: 7, memberNumber: 'M000000001', firstName: 'Marie', lastName: 'Curie' },
    copy: { id: 20, inventoryCode: 'INV-000020', titleId: 30 },
    loanDate: '2026-08-01T09:00:00Z',
    dueDate: '2026-08-22',
    returnDate: null,
    loanStatus: 'ACTIVE',
    notes: null,
    createdAt: '2026-08-01T09:00:00Z',
    updatedAt: '2026-08-01T09:00:00Z',
    ...overrides,
  };
}

function buildPage(content: LoanResponse[], totalElements = content.length): PageResponse<LoanResponse> {
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
      path: '/api/v1/me/loans',
      fieldErrors: [],
    },
  });
}

describe('MemberLoansPage', () => {
  let fixture: ComponentFixture<MemberLoansPage>;
  let loanApiServiceMock: { listOwnLoans: ReturnType<typeof vi.fn>; listLoans: ReturnType<typeof vi.fn> };

  function configure(): void {
    loanApiServiceMock = { listOwnLoans: vi.fn(), listLoans: vi.fn() };

    TestBed.configureTestingModule({
      imports: [MemberLoansPage],
      providers: [{ provide: LoanApiService, useValue: loanApiServiceMock }],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(MemberLoansPage);
    fixture.detectChanges();
  }

  // ---------------------------------------------------------------
  // Chargement / contrat API
  // ---------------------------------------------------------------

  it('should call listOwnLoans(0, 20) on initial load', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan()])));

    createComponent();

    expect(loanApiServiceMock.listOwnLoans).toHaveBeenCalledWith(0, 20);
  });

  it('should render the loans returned by the API', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(
      of(buildPage([buildLoan({ copy: { id: 20, inventoryCode: 'INV-000099', titleId: 30 } })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('INV-000099');
    expect(text).toContain('2026-08-01T09:00:00Z');
    expect(text).toContain('2026-08-22');
  });

  it('should map a PrimeNG lazy load event to page/size and call listOwnLoans again', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan()], 100)));
    createComponent();
    loanApiServiceMock.listOwnLoans.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(loanApiServiceMock.listOwnLoans).toHaveBeenCalledWith(2, 20);
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan()])));
    createComponent();
    loanApiServiceMock.listOwnLoans.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(loanApiServiceMock.listOwnLoans).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // LoanStatus
  // ---------------------------------------------------------------

  it('should render ACTIVE loans', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan({ loanStatus: 'ACTIVE' })])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('ACTIVE');
  });

  it('should render OVERDUE loans', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan({ loanStatus: 'OVERDUE' })])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('OVERDUE');
  });

  it('should render RETURNED loans', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(
      of(buildPage([buildLoan({ loanStatus: 'RETURNED', returnDate: '2026-08-19' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('RETURNED');
  });

  it('should never recompute LoanStatus from dueDate — it renders exactly what the backend returns', () => {
    configure();
    // dueDate is in the past relative to today, yet loanStatus is ACTIVE:
    // the component must not override it to OVERDUE on its own.
    loanApiServiceMock.listOwnLoans.mockReturnValue(
      of(buildPage([buildLoan({ loanStatus: 'ACTIVE', dueDate: '2020-01-01' })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('ACTIVE');
    expect(text).not.toContain('OVERDUE');
  });

  // ---------------------------------------------------------------
  // returnDate
  // ---------------------------------------------------------------

  it('should render returnDate when present', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(
      of(buildPage([buildLoan({ loanStatus: 'RETURNED', returnDate: '2026-08-19' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2026-08-19');
  });

  it('should render a placeholder, not a raw null, when returnDate is absent', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan({ returnDate: null })])));

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
    loanApiServiceMock.listOwnLoans.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when the backend returns no loan', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([], 0)));

    createComponent();

    const emptyState = fixture.nativeElement.querySelector('app-empty-state');
    expect(emptyState).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucun prêt à afficher.');
  });

  it('should show the error state on a failed request', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the last request when retry is triggered', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();
    loanApiServiceMock.listOwnLoans.mockClear();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan()])));

    fixture.componentInstance.retry();

    expect(loanApiServiceMock.listOwnLoans).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // Sécurité UX — lecture seule stricte
  // ---------------------------------------------------------------

  it('should never render a registerLoan button', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan()])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Emprunter');
    expect(text).not.toContain('Enregistrer un prêt');
  });

  it('should never render a return button', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan()])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Retourner');
    expect(text).not.toContain('Marquer comme retourné');
  });

  it('should render no action <button> other than the PrimeNG paginator controls (strictly read-only)', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan()])));

    createComponent();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nonPaginatorButtons = buttons.filter((button) => !button.closest('.p-paginator'));
    expect(nonPaginatorButtons).toHaveLength(0);
  });

  it('should never call the staff listLoans endpoint', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan()])));

    createComponent();
    fixture.componentInstance.onLazyLoad({ first: 20, rows: 20 });
    fixture.componentInstance.retry();

    expect(loanApiServiceMock.listLoans).not.toHaveBeenCalled();
  });

  it('should never expose the borrower on its own consultation page (redundant on /me/loans)', () => {
    configure();
    loanApiServiceMock.listOwnLoans.mockReturnValue(of(buildPage([buildLoan()])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Marie');
    expect(text).not.toContain('Curie');
    expect(text).not.toContain('M000000001');
  });
});
