import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CopyResponse } from '../../../../catalogue/models/copy-response';
import { TitleResponse } from '../../../../catalogue/models/title-response';
import { CopyApiService } from '../../../../catalogue/services/copy-api.service';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { LoanResponse } from '../../../../loans/models/loan-response';
import { LoanApiService } from '../../../../loans/services/loan-api.service';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';
import { LoanCreateDialog } from './loan-create-dialog';

function buildUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: 7,
    email: 'marie.curie@primatis.test',
    firstName: 'Marie',
    lastName: 'Curie',
    phoneNumber: null,
    accountStatus: 'ACTIVE',
    memberNumber: 'M000000001',
    memberStatus: 'ACTIVE',
    registrationDate: '2026-01-01',
    memberExpirationDate: '2027-01-01',
    blockedReason: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function buildTitle(overrides: Partial<TitleResponse> = {}): TitleResponse {
  return {
    id: 30,
    isbn: null,
    title: 'Les Misérables',
    subtitle: null,
    publicationYear: 1862,
    language: 'FR',
    publisher: null,
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
    ...overrides,
  };
}

function buildCopy(overrides: Partial<CopyResponse> = {}): CopyResponse {
  return {
    id: 20,
    titleId: 30,
    inventoryCode: 'INV-000020',
    location: null,
    copyCondition: 'GOOD',
    availabilityStatus: 'AVAILABLE',
    ...overrides,
  };
}

function buildLoan(overrides: Partial<LoanResponse> = {}): LoanResponse {
  return {
    id: 1,
    borrower: { id: 7, memberNumber: 'M000000001', firstName: 'Marie', lastName: 'Curie' },
    copy: { id: 20, inventoryCode: 'INV-000020', titleId: 30 },
    loanDate: '2026-08-19T09:00:00Z',
    dueDate: '2026-09-09',
    returnDate: null,
    loanStatus: 'ACTIVE',
    notes: null,
    createdAt: '2026-08-19T09:00:00Z',
    updatedAt: '2026-08-19T09:00:00Z',
    ...overrides,
  };
}

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { timestamp: new Date().toISOString(), status, error: 'Error', code, message, path: '/api/v1/loans', fieldErrors: [] },
  });
}

describe('LoanCreateDialog', () => {
  let fixture: ComponentFixture<LoanCreateDialog>;
  let component: LoanCreateDialog;
  let userApiServiceMock: { listUsers: ReturnType<typeof vi.fn> };
  let staffCatalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };
  let copyApiServiceMock: { listCopies: ReturnType<typeof vi.fn> };
  let loanApiServiceMock: { registerLoan: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  function configure(): void {
    userApiServiceMock = { listUsers: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })) };
    staffCatalogueApiServiceMock = {
      searchTitles: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
    };
    copyApiServiceMock = { listCopies: vi.fn().mockReturnValue(of([])) };
    loanApiServiceMock = { registerLoan: vi.fn() };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [LoanCreateDialog],
      providers: [
        { provide: UserApiService, useValue: userApiServiceMock },
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
        { provide: CopyApiService, useValue: copyApiServiceMock },
        { provide: LoanApiService, useValue: loanApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });
  }

  function createComponent(visible = true): void {
    fixture = TestBed.createComponent(LoanCreateDialog);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('visible', visible);
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    configure();
  });

  afterEach(() => vi.useRealTimers());

  // ---------------------------------------------------------------
  // Emprunteur
  // ---------------------------------------------------------------

  it('should not search borrowers when the input is empty', () => {
    createComponent();

    component.onBorrowerSearchInput('');
    vi.advanceTimersByTime(300);

    expect(userApiServiceMock.listUsers).not.toHaveBeenCalled();
  });

  it('should debounce the borrower search and call listUsers(0, 20, q)', () => {
    createComponent();

    component.onBorrowerSearchInput('martin');
    vi.advanceTimersByTime(299);
    expect(userApiServiceMock.listUsers).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20, 'martin');
  });

  it('should display borrower identity and memberNumber in search results', () => {
    userApiServiceMock.listUsers.mockReturnValue(
      of({ content: [buildUser({ firstName: 'Ada', lastName: 'Lovelace', memberNumber: 'M000000042' })], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    );
    createComponent();

    component.onBorrowerSearchInput('ada');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Ada');
    expect(text).toContain('Lovelace');
    expect(text).toContain('M000000042');
  });

  it('should mark a result without memberNumber as "Non adhérent" and refuse to select it', () => {
    const nonMember = buildUser({ id: 55, memberNumber: null, firstName: 'Non', lastName: 'Adherent' });
    userApiServiceMock.listUsers.mockReturnValue(of({ content: [nonMember], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
    createComponent();

    component.onBorrowerSearchInput('non');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Non adhérent');
    component.selectBorrower(nonMember);
    expect(component.selectedBorrower()).toBeNull();
  });

  it('should still show non-member results (never silently filtered from the backend results)', () => {
    const nonMember = buildUser({ id: 55, memberNumber: null });
    userApiServiceMock.listUsers.mockReturnValue(of({ content: [nonMember], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
    createComponent();

    component.onBorrowerSearchInput('x');
    vi.advanceTimersByTime(300);

    expect(component.borrowerSearchResults()).toEqual([nonMember]);
  });

  it('should select a borrower and keep its real numeric id', () => {
    const member = buildUser({ id: 42, memberNumber: 'M000000042' });
    createComponent();

    component.selectBorrower(member);

    expect(component.selectedBorrower()?.id).toBe(42);
  });

  it('should never render a raw numeric borrowerUserId input', () => {
    createComponent();

    const numberInputs = fixture.nativeElement.querySelectorAll('input[type="number"]');
    expect(numberInputs.length).toBe(0);
  });

  // ---------------------------------------------------------------
  // Exemplaire (Title puis Copy)
  // ---------------------------------------------------------------

  it('should not search titles when the input is empty', () => {
    createComponent();

    component.onTitleSearchInput('');
    vi.advanceTimersByTime(300);

    expect(staffCatalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
  });

  it('should debounce the title search and call searchTitles with q/page/size', () => {
    createComponent();

    component.onTitleSearchInput('misérables');
    vi.advanceTimersByTime(299);
    expect(staffCatalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(staffCatalogueApiServiceMock.searchTitles).toHaveBeenCalledWith({ q: 'misérables', page: 0, size: 20 });
  });

  it('should load copies for the selected title via listCopies(titleId)', () => {
    createComponent();

    component.selectTitle(buildTitle({ id: 77 }));

    expect(copyApiServiceMock.listCopies).toHaveBeenCalledWith(77);
  });

  it('should reset the selected copy when the title changes', () => {
    copyApiServiceMock.listCopies.mockReturnValue(of([buildCopy()]));
    createComponent();
    component.selectTitle(buildTitle());
    component.selectCopy(buildCopy());
    expect(component.selectedCopy()).not.toBeNull();

    component.changeTitle();

    expect(component.selectedCopy()).toBeNull();
    expect(component.selectedTitle()).toBeNull();
  });

  it('should display inventoryCode for each copy', () => {
    copyApiServiceMock.listCopies.mockReturnValue(of([buildCopy({ inventoryCode: 'INV-000099' })]));
    createComponent();

    component.selectTitle(buildTitle());
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('INV-000099');
  });

  it('should select a copy and keep its real numeric id', () => {
    createComponent();

    component.selectCopy(buildCopy({ id: 88 }));

    expect(component.selectedCopy()?.id).toBe(88);
  });

  it('should display and allow selecting a RESERVED copy — never filtered to AVAILABLE only', () => {
    const reserved = buildCopy({ id: 5, availabilityStatus: 'RESERVED' });
    copyApiServiceMock.listCopies.mockReturnValue(of([reserved]));
    createComponent();

    component.selectTitle(buildTitle());
    fixture.detectChanges();

    expect(component.copies()).toEqual([reserved]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('RESERVED');
    component.selectCopy(reserved);
    expect(component.selectedCopy()).toEqual(reserved);
  });

  it('should never render a raw numeric copyId input', () => {
    copyApiServiceMock.listCopies.mockReturnValue(of([buildCopy()]));
    createComponent();
    component.selectTitle(buildTitle());
    fixture.detectChanges();

    const numberInputs = fixture.nativeElement.querySelectorAll('input[type="number"]');
    expect(numberInputs.length).toBe(0);
  });

  // ---------------------------------------------------------------
  // Soumission
  // ---------------------------------------------------------------

  it('should keep submit disabled until both a borrower and a copy are selected', () => {
    createComponent();
    expect(component.canSubmit).toBe(false);

    component.selectBorrower(buildUser());
    expect(component.canSubmit).toBe(false);

    component.selectCopy(buildCopy());
    expect(component.canSubmit).toBe(true);
  });

  it('should send exactly { borrowerUserId, copyId }, no other field', () => {
    loanApiServiceMock.registerLoan.mockReturnValue(of(buildLoan()));
    createComponent();
    component.selectBorrower(buildUser({ id: 7 }));
    component.selectCopy(buildCopy({ id: 20 }));

    component.submit();

    expect(loanApiServiceMock.registerLoan).toHaveBeenCalledWith({ borrowerUserId: 7, copyId: 20 });
    const [request] = loanApiServiceMock.registerLoan.mock.calls[0];
    expect(Object.keys(request)).toEqual(['borrowerUserId', 'copyId']);
  });

  it('should do nothing when submit is called without both selections', () => {
    createComponent();

    component.submit();

    expect(loanApiServiceMock.registerLoan).not.toHaveBeenCalled();
  });

  it('should prevent a double submit while the first request is pending', () => {
    loanApiServiceMock.registerLoan.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();
    component.selectBorrower(buildUser());
    component.selectCopy(buildCopy());

    component.submit();
    component.submit();

    expect(loanApiServiceMock.registerLoan).toHaveBeenCalledTimes(1);
  });

  it('should show a success toast on a successful registerLoan', () => {
    loanApiServiceMock.registerLoan.mockReturnValue(of(buildLoan()));
    createComponent();
    component.selectBorrower(buildUser());
    component.selectCopy(buildCopy());

    component.submit();

    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should emit saved with exactly the backend LoanResponse — never a locally fabricated one', () => {
    const backendLoan = buildLoan({ id: 999, notes: 'exact backend payload' });
    loanApiServiceMock.registerLoan.mockReturnValue(of(backendLoan));
    createComponent();
    component.selectBorrower(buildUser());
    component.selectCopy(buildCopy());
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).toHaveBeenCalledWith(backendLoan);
  });

  it('should reset all selections after a successful submit', () => {
    loanApiServiceMock.registerLoan.mockReturnValue(of(buildLoan()));
    createComponent();
    component.selectBorrower(buildUser());
    component.selectCopy(buildCopy());

    component.submit();

    expect(component.selectedBorrower()).toBeNull();
    expect(component.selectedCopy()).toBeNull();
    expect(component.selectedTitle()).toBeNull();
  });

  it('should reset stale state whenever the dialog becomes visible again', () => {
    createComponent(false);
    component.selectBorrower(buildUser());
    component.selectCopy(buildCopy());
    expect(component.selectedBorrower()).not.toBeNull();

    fixture.componentRef.setInput('visible', true);
    fixture.detectChanges();

    expect(component.selectedBorrower()).toBeNull();
    expect(component.selectedCopy()).toBeNull();
  });

  it('should emit closed when cancel is called', () => {
    createComponent();
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.cancel();

    expect(closedSpy).toHaveBeenCalledTimes(1);
  });

  // ---------------------------------------------------------------
  // Erreurs métier
  // ---------------------------------------------------------------

  it('should show an error toast on a business eligibility error (MEMBER_BLOCKED)', () => {
    loanApiServiceMock.registerLoan.mockReturnValue(
      throwError(() => apiHttpError(409, 'MEMBER_BLOCKED', 'Cet adhérent est bloqué.')),
    );
    createComponent();
    component.selectBorrower(buildUser());
    component.selectCopy(buildCopy());

    component.submit();

    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Cet adhérent est bloqué.' }),
    );
  });

  it('should keep the dialog usable — selections survive a submit error, no false success', () => {
    loanApiServiceMock.registerLoan.mockReturnValue(
      throwError(() => apiHttpError(409, 'MEMBER_BLOCKED', 'Cet adhérent est bloqué.')),
    );
    createComponent();
    const borrower = buildUser();
    const copy = buildCopy();
    component.selectBorrower(borrower);
    component.selectCopy(copy);

    component.submit();

    expect(component.selectedBorrower()).toEqual(borrower);
    expect(component.selectedCopy()).toEqual(copy);
    expect(component.submitting()).toBe(false);
    expect(messageServiceMock.add).not.toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should propagate COPY_RESERVED_FOR_ANOTHER_MEMBER as a plain error', () => {
    loanApiServiceMock.registerLoan.mockReturnValue(
      throwError(() =>
        apiHttpError(409, 'COPY_RESERVED_FOR_ANOTHER_MEMBER', 'Cet exemplaire est réservé pour un autre adhérent.'),
      ),
    );
    createComponent();
    component.selectBorrower(buildUser());
    component.selectCopy(buildCopy());

    component.submit();

    expect(component.submitError()).toBe('Cet exemplaire est réservé pour un autre adhérent.');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });
});
