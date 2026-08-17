import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { ResidenceResponse } from '../models/residence-response';
import { UpdateResidenceRequest } from '../models/update-residence-request';
import { ResidenceApiService } from './residence-api.service';

describe('ResidenceApiService', () => {
  let service: ResidenceApiService;
  let httpTestingController: HttpTestingController;

  const residence: ResidenceResponse = {
    id: 1,
    address: {
      id: 1,
      street: 'Rue du Parlement',
      streetNumber: '10',
      boxNumber: null,
      additionalInfo: null,
      city: {
        id: 1,
        name: 'Bruxelles',
        postalCode: '1000',
        country: { id: 1, name: 'Belgique', code: 'BE' },
      },
    },
    startDate: '2026-01-01',
    endDate: null,
  };

  const updateRequest: UpdateResidenceRequest = {
    cityId: 1,
    street: 'Rue du Parlement',
    streetNumber: '10',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(ResidenceApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // /me/residence
  // ---------------------------------------------------------------

  it('should GET /api/v1/me/residence', () => {
    service.getOwnResidence().subscribe();

    const request = httpTestingController.expectOne('/api/v1/me/residence');
    expect(request.request.method).toBe('GET');

    request.flush(residence);
  });

  it('should propagate the ResidenceResponse returned for /me/residence', () => {
    let received: ResidenceResponse | undefined;
    service.getOwnResidence().subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/me/residence').flush(residence);

    expect(received).toEqual(residence);
  });

  it('should PUT the exact UpdateResidenceRequest body to /api/v1/me/residence', () => {
    service.replaceOwnResidence(updateRequest).subscribe();

    const request = httpTestingController.expectOne('/api/v1/me/residence');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(updateRequest);

    request.flush(residence);
  });

  // ---------------------------------------------------------------
  // /users/{id}/residence(s)
  // ---------------------------------------------------------------

  it('should GET /api/v1/users/{id}/residence', () => {
    service.getResidence(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/users/1/residence');
    expect(request.request.method).toBe('GET');

    request.flush(residence);
  });

  it('should GET /api/v1/users/{id}/residences and propagate an array response', () => {
    let received: ResidenceResponse[] | undefined;
    service.getResidenceHistory(1).subscribe((value) => (received = value));

    const request = httpTestingController.expectOne('/api/v1/users/1/residences');
    expect(request.request.method).toBe('GET');
    request.flush([residence]);

    expect(received).toEqual([residence]);
  });

  it('should PUT the exact UpdateResidenceRequest body to /api/v1/users/{id}/residence', () => {
    service.replaceResidence(1, updateRequest).subscribe();

    const request = httpTestingController.expectOne('/api/v1/users/1/residence');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(updateRequest);

    request.flush(residence);
  });
});
