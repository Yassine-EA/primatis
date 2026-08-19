import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { LoanResponse } from '../models/loan-response';
import { LoanApiService } from './loan-api.service';

describe('LoanApiService', () => {
  let service: LoanApiService;
  let httpTestingController: HttpTestingController;

  const loan: LoanResponse = {
    id: 1,
    borrower: { id: 10, memberNumber: 'M-000010', firstName: 'Prénom', lastName: 'Nom' },
    copy: { id: 20, inventoryCode: 'INV-000020', titleId: 30 },
    loanDate: '2026-08-01T09:00:00Z',
    dueDate: '2026-08-22',
    returnDate: null,
    loanStatus: 'ACTIVE',
    notes: null,
    createdAt: '2026-08-01T09:00:00Z',
    updatedAt: '2026-08-01T09:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(LoanApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // listLoans
  // ---------------------------------------------------------------

  it('should GET /api/v1/loans with default page/size params', () => {
    service.listLoans().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/loans' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');

    request.flush({ content: [loan], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/loans with explicit page/size params', () => {
    service.listLoans(2, 50).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/loans' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');

    request.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<LoanResponse> returned by the backend', () => {
    let received: unknown;
    service.listLoans().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/loans' && req.method === 'GET')
      .flush({ content: [loan], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(received).toEqual({ content: [loan], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  // ---------------------------------------------------------------
  // listOwnLoans
  // ---------------------------------------------------------------

  it('should GET /api/v1/me/loans with default page/size params, no userId sent', () => {
    service.listOwnLoans().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/me/loans' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('userId')).toBe(false);

    request.flush({ content: [loan], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/me/loans with explicit page/size params', () => {
    service.listOwnLoans(1, 10).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/me/loans' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');

    request.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('should propagate an empty PageResponse<LoanResponse> when the user has no loan', () => {
    let received: unknown;
    service.listOwnLoans().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/me/loans' && req.method === 'GET')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    expect(received).toEqual({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  // ---------------------------------------------------------------
  // registerLoan
  // ---------------------------------------------------------------

  it('should POST the exact CreateLoanRequest body to /api/v1/loans', () => {
    service.registerLoan({ borrowerUserId: 10, copyId: 20 }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/loans');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ borrowerUserId: 10, copyId: 20 });

    request.flush(loan);
  });

  it('should propagate the LoanResponse returned by the backend on registerLoan', () => {
    let received: LoanResponse | undefined;
    service.registerLoan({ borrowerUserId: 10, copyId: 20 }).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/loans').flush(loan);

    expect(received).toEqual(loan);
  });

  // ---------------------------------------------------------------
  // registerReturn
  // ---------------------------------------------------------------

  it('should POST /api/v1/loans/{loanId}/return with no body', () => {
    service.registerReturn(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/loans/1/return');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    const returned: LoanResponse = { ...loan, returnDate: '2026-08-19', loanStatus: 'RETURNED' };
    request.flush(returned);
  });

  it('should propagate the LoanResponse returned by the backend on registerReturn', () => {
    let received: LoanResponse | undefined;
    service.registerReturn(1).subscribe((value) => (received = value));

    const returned: LoanResponse = { ...loan, returnDate: '2026-08-19', loanStatus: 'RETURNED' };
    httpTestingController.expectOne('/api/v1/loans/1/return').flush(returned);

    expect(received).toEqual(returned);
  });
});
