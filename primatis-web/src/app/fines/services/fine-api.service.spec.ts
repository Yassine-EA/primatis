import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { FineResponse } from '../models/fine-response';
import { FineApiService } from './fine-api.service';

describe('FineApiService', () => {
  let service: FineApiService;
  let httpTestingController: HttpTestingController;

  const fine: FineResponse = {
    id: 1,
    borrower: { id: 10, memberNumber: 'M-000010', firstName: 'Prénom', lastName: 'Nom' },
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
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(FineApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // listOwnFines
  // ---------------------------------------------------------------

  it('should GET /api/v1/me/fines with default page/size params, no userId sent', () => {
    service.listOwnFines().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/me/fines' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('userId')).toBe(false);
    expect(request.request.params.has('sort')).toBe(false);
    expect(request.request.params.has('status')).toBe(false);

    request.flush({ content: [fine], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/me/fines with explicit page/size params', () => {
    service.listOwnFines(2, 50).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/me/fines' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');

    request.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<FineResponse> returned by the backend on listOwnFines', () => {
    let received: unknown;
    service.listOwnFines().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/me/fines' && req.method === 'GET')
      .flush({ content: [fine], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(received).toEqual({ content: [fine], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  // ---------------------------------------------------------------
  // listFines
  // ---------------------------------------------------------------

  it('should GET /api/v1/fines with default page/size params', () => {
    service.listFines().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/fines' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');

    request.flush({ content: [fine], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/fines with explicit page/size params', () => {
    service.listFines(3, 10).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/fines' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('3');
    expect(request.request.params.get('size')).toBe('10');

    request.flush({ content: [], page: 3, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<FineResponse> returned by the backend on listFines', () => {
    let received: unknown;
    service.listFines().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/fines' && req.method === 'GET')
      .flush({ content: [fine], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(received).toEqual({ content: [fine], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  // ---------------------------------------------------------------
  // confirmPayment
  // ---------------------------------------------------------------

  it('should POST /api/v1/fines/{fineId}/payment-confirmation with no business payload', () => {
    service.confirmPayment(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/fines/1/payment-confirmation');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    const paid: FineResponse = { ...fine, fineStatus: 'PAID', paidAt: '2026-08-20T10:00:00Z' };
    request.flush(paid);
  });

  it('should propagate the FineResponse returned by the backend on confirmPayment', () => {
    let received: FineResponse | undefined;
    service.confirmPayment(1).subscribe((value) => (received = value));

    const paid: FineResponse = { ...fine, fineStatus: 'PAID', paidAt: '2026-08-20T10:00:00Z' };
    httpTestingController.expectOne('/api/v1/fines/1/payment-confirmation').flush(paid);

    expect(received).toEqual(paid);
  });

  // ---------------------------------------------------------------
  // cancelFine
  // ---------------------------------------------------------------

  it('should POST /api/v1/fines/{fineId}/cancel with no business payload', () => {
    service.cancelFine(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/fines/1/cancel');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    const cancelled: FineResponse = { ...fine, fineStatus: 'CANCELLED', cancelledAt: '2026-08-20T10:00:00Z' };
    request.flush(cancelled);
  });

  it('should propagate the FineResponse returned by the backend on cancelFine', () => {
    let received: FineResponse | undefined;
    service.cancelFine(1).subscribe((value) => (received = value));

    const cancelled: FineResponse = { ...fine, fineStatus: 'CANCELLED', cancelledAt: '2026-08-20T10:00:00Z' };
    httpTestingController.expectOne('/api/v1/fines/1/cancel').flush(cancelled);

    expect(received).toEqual(cancelled);
  });
});
