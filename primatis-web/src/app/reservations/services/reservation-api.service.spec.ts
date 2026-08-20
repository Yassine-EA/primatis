import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { ReservationResponse } from '../models/reservation-response';
import { ReservationApiService } from './reservation-api.service';

describe('ReservationApiService', () => {
  let service: ReservationApiService;
  let httpTestingController: HttpTestingController;

  const reservation: ReservationResponse = {
    id: 1,
    member: { id: 10, memberNumber: 'M-000010', firstName: 'Prénom', lastName: 'Nom' },
    title: { id: 20, title: 'Titre réservé', isbn: '978-2-1234-5680-3' },
    assignedCopy: null,
    fulfilledByLoanId: null,
    reservationDate: '2026-08-01T09:00:00Z',
    expirationDate: null,
    reservationStatus: 'WAITING',
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

    service = TestBed.inject(ReservationApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // listOwnReservations
  // ---------------------------------------------------------------

  it('should GET /api/v1/me/reservations with default page/size params, no userId sent', () => {
    service.listOwnReservations().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/me/reservations' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('userId')).toBe(false);
    expect(request.request.params.has('sort')).toBe(false);

    request.flush({ content: [reservation], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/me/reservations with explicit page/size params', () => {
    service.listOwnReservations(2, 50).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/me/reservations' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');

    request.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<ReservationResponse> returned by the backend on listOwnReservations', () => {
    let received: unknown;
    service.listOwnReservations().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/me/reservations' && req.method === 'GET')
      .flush({ content: [reservation], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(received).toEqual({ content: [reservation], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  // ---------------------------------------------------------------
  // listReservations
  // ---------------------------------------------------------------

  it('should GET /api/v1/reservations with default page/size params', () => {
    service.listReservations().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/reservations' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');

    request.flush({ content: [reservation], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/reservations with explicit page/size params', () => {
    service.listReservations(3, 10).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/reservations' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('3');
    expect(request.request.params.get('size')).toBe('10');

    request.flush({ content: [], page: 3, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<ReservationResponse> returned by the backend on listReservations', () => {
    let received: unknown;
    service.listReservations().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/reservations' && req.method === 'GET')
      .flush({ content: [reservation], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(received).toEqual({ content: [reservation], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  // ---------------------------------------------------------------
  // createOwnReservation
  // ---------------------------------------------------------------

  it('should POST exactly { titleId } to /api/v1/me/reservations, no parasitic userId', () => {
    service.createOwnReservation({ titleId: 20 }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/me/reservations');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ titleId: 20 });
    expect(Object.keys(request.request.body as object)).toEqual(['titleId']);

    request.flush(reservation);
  });

  it('should propagate the ReservationResponse returned by the backend on createOwnReservation', () => {
    let received: ReservationResponse | undefined;
    service.createOwnReservation({ titleId: 20 }).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/me/reservations').flush(reservation);

    expect(received).toEqual(reservation);
  });

  // ---------------------------------------------------------------
  // createReservation
  // ---------------------------------------------------------------

  it('should POST exactly { userId, titleId } to /api/v1/reservations', () => {
    service.createReservation({ userId: 10, titleId: 20 }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/reservations');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ userId: 10, titleId: 20 });

    request.flush(reservation);
  });

  it('should propagate the ReservationResponse returned by the backend on createReservation', () => {
    let received: ReservationResponse | undefined;
    service.createReservation({ userId: 10, titleId: 20 }).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/reservations').flush(reservation);

    expect(received).toEqual(reservation);
  });

  // ---------------------------------------------------------------
  // cancelOwnReservation
  // ---------------------------------------------------------------

  it('should POST /api/v1/me/reservations/{reservationId}/cancel with no business payload', () => {
    service.cancelOwnReservation(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/me/reservations/1/cancel');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    const cancelled: ReservationResponse = { ...reservation, reservationStatus: 'CANCELLED' };
    request.flush(cancelled);
  });

  it('should propagate the ReservationResponse returned by the backend on cancelOwnReservation', () => {
    let received: ReservationResponse | undefined;
    service.cancelOwnReservation(1).subscribe((value) => (received = value));

    const cancelled: ReservationResponse = { ...reservation, reservationStatus: 'CANCELLED' };
    httpTestingController.expectOne('/api/v1/me/reservations/1/cancel').flush(cancelled);

    expect(received).toEqual(cancelled);
  });

  // ---------------------------------------------------------------
  // cancelReservation
  // ---------------------------------------------------------------

  it('should POST /api/v1/reservations/{reservationId}/cancel with no business payload', () => {
    service.cancelReservation(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/reservations/1/cancel');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    const cancelled: ReservationResponse = { ...reservation, reservationStatus: 'CANCELLED' };
    request.flush(cancelled);
  });

  it('should propagate the ReservationResponse returned by the backend on cancelReservation', () => {
    let received: ReservationResponse | undefined;
    service.cancelReservation(1).subscribe((value) => (received = value));

    const cancelled: ReservationResponse = { ...reservation, reservationStatus: 'CANCELLED' };
    httpTestingController.expectOne('/api/v1/reservations/1/cancel').flush(cancelled);

    expect(received).toEqual(cancelled);
  });
});
