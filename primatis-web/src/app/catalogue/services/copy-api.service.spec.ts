import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { CopyResponse } from '../models/copy-response';
import { CopyApiService } from './copy-api.service';

describe('CopyApiService', () => {
  let service: CopyApiService;
  let httpTestingController: HttpTestingController;

  const copy: CopyResponse = {
    id: 1,
    titleId: 10,
    inventoryCode: 'INV-000001',
    location: null,
    copyCondition: 'GOOD',
    availabilityStatus: 'AVAILABLE',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(CopyApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // listCopies
  // ---------------------------------------------------------------

  it('should GET /api/v1/staff/titles/{titleId}/copies with no pagination params', () => {
    service.listCopies(10).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/10/copies');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys().length).toBe(0);

    request.flush([copy]);
  });

  it('should propagate the CopyResponse[] returned by the backend', () => {
    let received: CopyResponse[] | undefined;
    service.listCopies(10).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/staff/titles/10/copies').flush([copy]);

    expect(received).toEqual([copy]);
  });

  // ---------------------------------------------------------------
  // getCopyById
  // ---------------------------------------------------------------

  it('should GET /api/v1/staff/titles/{titleId}/copies/{copyId}', () => {
    service.getCopyById(10, 1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/10/copies/1');
    expect(request.request.method).toBe('GET');

    request.flush(copy);
  });

  // ---------------------------------------------------------------
  // createCopy
  // ---------------------------------------------------------------

  it('should POST the exact CreateCopyRequest body, without titleId in the body', () => {
    service
      .createCopy(10, { inventoryCode: 'INV-000001', copyCondition: 'GOOD', availabilityStatus: 'AVAILABLE' })
      .subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/10/copies');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      inventoryCode: 'INV-000001',
      copyCondition: 'GOOD',
      availabilityStatus: 'AVAILABLE',
    });
    expect(Object.prototype.hasOwnProperty.call(request.request.body as object, 'titleId')).toBe(false);

    request.flush(copy);
  });

  // ---------------------------------------------------------------
  // updateCopy — sparse PATCH, preuve obligatoire null vs absent
  // ---------------------------------------------------------------

  it('should PATCH an empty body when no field is provided', () => {
    service.updateCopy(10, 1, {}).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/10/copies/1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({});

    request.flush(copy);
  });

  it('should PATCH exactly {"location": null} when clearing location, omitting every other key', () => {
    service.updateCopy(10, 1, { location: null }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/10/copies/1');
    const body = request.request.body as Record<string, unknown>;

    expect(body).toEqual({ location: null });
    expect(Object.keys(body)).toEqual(['location']);
    expect(Object.prototype.hasOwnProperty.call(body, 'inventoryCode')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(body, 'copyCondition')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(body, 'availabilityStatus')).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(body, 'titleId')).toBe(false);

    request.flush(copy);
  });

  it('should PATCH only copyCondition, never sending null for it', () => {
    service.updateCopy(10, 1, { copyCondition: 'DAMAGED' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/10/copies/1');
    expect(request.request.body).toEqual({ copyCondition: 'DAMAGED' });

    request.flush(copy);
  });

  // ---------------------------------------------------------------
  // updateAvailability
  // ---------------------------------------------------------------

  it('should PATCH /api/v1/staff/titles/{titleId}/copies/{copyId}/availability with field name "status"', () => {
    service.updateAvailability(10, 1, { status: 'UNAVAILABLE' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/10/copies/1/availability');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'UNAVAILABLE' });
    expect(Object.prototype.hasOwnProperty.call(request.request.body as object, 'availabilityStatus')).toBe(false);

    request.flush(copy);
  });
});
