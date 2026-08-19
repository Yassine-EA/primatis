import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../../auth/services/auth.service';
import { CopyApiService } from '../../../../catalogue/services/copy-api.service';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { PageResponse } from '../../../../core/models/page-response';
import { LoanResponse } from '../../../../loans/models/loan-response';
import { LoanApiService } from '../../../../loans/services/loan-api.service';
import { UserApiService } from '../../../../user/services/user-api.service';
import { StaffLoansPage } from './staff-loans-page';

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

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: {
      timestamp: new Date().toISOString(),
      status,
      error: 'Error',
      code,
      message,
      path: '/api/v1/loans',
      fieldErrors: [],
    },
  });
}

describe('StaffLoansPage', () => {
  let fixture: ComponentFixture<StaffLoansPage>;
  let loanApiServiceMock: {
    listLoans: ReturnType<typeof vi.fn>;
    registerReturn: ReturnType<typeof vi.fn>;
    registerLoan: ReturnType<typeof vi.fn>;
  };
  let authServiceMock: { hasPermission: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let confirmationServiceMock: { confirm: ReturnType<typeof vi.fn> };
  let userApiServiceMock: { listUsers: ReturnType<typeof vi.fn> };
  let staffCatalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };
  let copyApiServiceMock: { listCopies: ReturnType<typeof vi.fn> };

  function configure(): void {
    loanApiServiceMock = { listLoans: vi.fn(), registerReturn: vi.fn(), registerLoan: vi.fn() };
    authServiceMock = { hasPermission: vi.fn().mockReturnValue(true) };
    messageServiceMock = { add: vi.fn() };
    confirmationServiceMock = { confirm: vi.fn() };
    // LoanCreateDialog (DEV-07.9.2) est désormais un enfant réel de la page ;
    // ses propres dépendances doivent être fournies même si les tests de ce
    // fichier n'exercent pas directement la recherche borrower/Title (déjà
    // couverte par loan-create-dialog.spec.ts).
    userApiServiceMock = { listUsers: vi.fn() };
    staffCatalogueApiServiceMock = { searchTitles: vi.fn() };
    copyApiServiceMock = { listCopies: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffLoansPage],
      providers: [
        { provide: LoanApiService, useValue: loanApiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
        { provide: UserApiService, useValue: userApiServiceMock },
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
        { provide: CopyApiService, useValue: copyApiServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffLoansPage);
    fixture.detectChanges();
  }

  // ---------------------------------------------------------------
  // Liste / contrat API
  // ---------------------------------------------------------------

  it('should call listLoans(0, 20) on initial load', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan()])));

    createComponent();

    expect(loanApiServiceMock.listLoans).toHaveBeenCalledWith(0, 20);
  });

  it('should render borrower firstName/lastName/memberNumber', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(
      of(buildPage([buildLoan({ borrower: { id: 7, memberNumber: 'M000000042', firstName: 'Ada', lastName: 'Lovelace' } })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Ada');
    expect(text).toContain('Lovelace');
    expect(text).toContain('M000000042');
  });

  it('should render copy.inventoryCode', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(
      of(buildPage([buildLoan({ copy: { id: 20, inventoryCode: 'INV-000099', titleId: 30 } })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('INV-000099');
  });

  it('should render ACTIVE loans', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan({ loanStatus: 'ACTIVE' })])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('ACTIVE');
  });

  it('should render OVERDUE loans', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan({ loanStatus: 'OVERDUE' })])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('OVERDUE');
  });

  it('should render RETURNED loans', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(
      of(buildPage([buildLoan({ loanStatus: 'RETURNED', returnDate: '2026-08-19' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('RETURNED');
  });

  it('should render a placeholder, not a raw null, when returnDate is absent', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan({ returnDate: null })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('—');
    expect(text).not.toContain('null');
  });

  it('should map a PrimeNG lazy load event to page/size and call listLoans again', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan()], 100)));
    createComponent();
    loanApiServiceMock.listLoans.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(loanApiServiceMock.listLoans).toHaveBeenCalledWith(2, 20);
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan()])));
    createComponent();
    loanApiServiceMock.listLoans.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(loanApiServiceMock.listLoans).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // États UI
  // ---------------------------------------------------------------

  it('should show the loading state before the first response arrives', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when the backend returns no loan', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([], 0)));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  it('should show the error state on a failed request', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the last request when retry is triggered', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();
    loanApiServiceMock.listLoans.mockClear();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan()])));

    fixture.componentInstance.retry();

    expect(loanApiServiceMock.listLoans).toHaveBeenCalledWith(0, 20);
  });

  // ---------------------------------------------------------------
  // Permissions UX — LOAN_READ vs LOAN_MANAGE
  // ---------------------------------------------------------------

  it('should show no create/return action when the user only has LOAN_READ', () => {
    configure();
    authServiceMock.hasPermission.mockImplementation((permission: string) => permission !== 'LOAN_MANAGE');
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan({ loanStatus: 'ACTIVE' })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Retourner');
    expect(text).not.toContain('Enregistrer un prêt');
    expect(text).not.toContain('Action');
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nonPaginatorButtons = buttons.filter((button) => !button.closest('.p-paginator'));
    expect(nonPaginatorButtons).toHaveLength(0);
  });

  it('should show both the return action and the create action when the user has LOAN_MANAGE (no regression)', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan({ loanStatus: 'ACTIVE' })])));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Retourner');
    expect(text).toContain('Enregistrer un prêt');
  });

  // ---------------------------------------------------------------
  // Création d'un Loan (DEV-07.9.2)
  // ---------------------------------------------------------------

  it('should not render the LoanCreateDialog element at all without LOAN_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockImplementation((permission: string) => permission !== 'LOAN_MANAGE');
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan()])));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loan-create-dialog')).toBeNull();
  });

  it('should open the create dialog when "Enregistrer un prêt" is clicked', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan()])));
    createComponent();

    fixture.componentInstance.openCreateDialog();

    expect(fixture.componentInstance.createDialogVisible()).toBe(true);
  });

  it('should close the dialog without reloading the list when it is simply closed/cancelled', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan()])));
    createComponent();
    fixture.componentInstance.openCreateDialog();
    loanApiServiceMock.listLoans.mockClear();

    fixture.componentInstance.closeCreateDialog();

    expect(fixture.componentInstance.createDialogVisible()).toBe(false);
    expect(loanApiServiceMock.listLoans).not.toHaveBeenCalled();
  });

  it('should close the dialog and reload the current page when a loan is created', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan()], 100)));
    createComponent();
    fixture.componentInstance.openCreateDialog();
    fixture.componentInstance.onLazyLoad({ first: 20, rows: 20 }); // page courante = 1
    loanApiServiceMock.listLoans.mockClear();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan()], 101)));

    fixture.componentInstance.onLoanCreated(buildLoan({ id: 999 }));

    expect(fixture.componentInstance.createDialogVisible()).toBe(false);
    expect(loanApiServiceMock.listLoans).toHaveBeenCalledWith(1, 20);
  });

  it('should never fabricate a LoanResponse locally — onLoanCreated always reloads from the backend', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan({ id: 1 })])));
    createComponent();
    const reloaded = buildPage([buildLoan({ id: 1 }), buildLoan({ id: 999 })], 2);
    loanApiServiceMock.listLoans.mockReturnValue(of(reloaded));

    fixture.componentInstance.onLoanCreated(buildLoan({ id: 999 }));

    expect(fixture.componentInstance.rows()).toEqual(reloaded.content);
  });

  // ---------------------------------------------------------------
  // Retour d'un Loan
  // ---------------------------------------------------------------

  it('should show the return action for an ACTIVE loan (LOAN_MANAGE)', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan({ id: 1, loanStatus: 'ACTIVE' })])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Retourner');
  });

  it('should show the return action for an OVERDUE loan (LOAN_MANAGE)', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([buildLoan({ id: 1, loanStatus: 'OVERDUE' })])));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Retourner');
  });

  it('should never show the return action for a RETURNED loan', () => {
    configure();
    loanApiServiceMock.listLoans.mockReturnValue(
      of(buildPage([buildLoan({ id: 1, loanStatus: 'RETURNED', returnDate: '2026-08-19' })])),
    );

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Retourner');
  });

  it('should require confirmation before calling registerReturn', () => {
    configure();
    const loan = buildLoan({ id: 1, loanStatus: 'ACTIVE' });
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([loan])));
    createComponent();

    fixture.componentInstance.confirmReturn(loan);

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(loanApiServiceMock.registerReturn).not.toHaveBeenCalled();
  });

  it('should make no HTTP call when the confirmation is not accepted', () => {
    configure();
    const loan = buildLoan({ id: 1, loanStatus: 'ACTIVE' });
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([loan])));
    createComponent();

    fixture.componentInstance.confirmReturn(loan);
    // La confirmation n'est jamais acceptée : aucun accept() invoqué.

    expect(loanApiServiceMock.registerReturn).not.toHaveBeenCalled();
  });

  it('should call registerReturn(loan.id) once confirmation is accepted', () => {
    configure();
    const loan = buildLoan({ id: 1, loanStatus: 'ACTIVE' });
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([loan])));
    loanApiServiceMock.registerReturn.mockReturnValue(
      of(buildLoan({ id: 1, loanStatus: 'RETURNED', returnDate: '2026-08-19' })),
    );
    createComponent();

    fixture.componentInstance.confirmReturn(loan);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(loanApiServiceMock.registerReturn).toHaveBeenCalledWith(1);
  });

  it('should show a success toast and replace the row with the exact backend response after a successful return', () => {
    configure();
    const loan = buildLoan({ id: 1, loanStatus: 'ACTIVE', returnDate: null });
    const returned = buildLoan({ id: 1, loanStatus: 'RETURNED', returnDate: '2026-08-19' });
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([loan])));
    loanApiServiceMock.registerReturn.mockReturnValue(of(returned));
    createComponent();

    fixture.componentInstance.confirmReturn(loan);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([returned]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show an error toast and never mutate the row when registerReturn fails', () => {
    configure();
    const loan = buildLoan({ id: 1, loanStatus: 'ACTIVE' });
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([loan])));
    loanApiServiceMock.registerReturn.mockReturnValue(
      throwError(() => apiHttpError(409, 'LOAN_ALREADY_RETURNED', 'Ce prêt a déjà été retourné.')),
    );
    createComponent();

    fixture.componentInstance.confirmReturn(loan);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([loan]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Ce prêt a déjà été retourné.' }),
    );
  });

  it('should surface RESERVATION_ASSIGNMENT_CONTENTION as a plain error, never as a silent success', () => {
    configure();
    const loan = buildLoan({ id: 1, loanStatus: 'ACTIVE' });
    loanApiServiceMock.listLoans.mockReturnValue(of(buildPage([loan])));
    loanApiServiceMock.registerReturn.mockReturnValue(
      throwError(() =>
        apiHttpError(409, 'RESERVATION_ASSIGNMENT_CONTENTION', 'Le prêt n’a pas pu être finalisé, veuillez réessayer.'),
      ),
    );
    createComponent();

    fixture.componentInstance.confirmReturn(loan);
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(fixture.componentInstance.rows()).toEqual([loan]);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(messageServiceMock.add).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });
});
