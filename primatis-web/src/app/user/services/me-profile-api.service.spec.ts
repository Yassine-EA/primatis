import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { MeProfileResponse } from '../models/me-profile-response';
import { MeProfileApiService } from './me-profile-api.service';

describe('MeProfileApiService', () => {
  let service: MeProfileApiService;
  let httpTestingController: HttpTestingController;

  const profile: MeProfileResponse = {
    id: 1,
    email: 'member@primatis.test',
    firstName: 'Prénom',
    lastName: 'Nom',
    phoneNumber: '+32470123456',
    accountStatus: 'ACTIVE',
    memberNumber: 'M000000001',
    memberStatus: 'ACTIVE',
    registrationDate: '2026-01-01',
    memberExpirationDate: '2027-01-01',
    blockedReason: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(MeProfileApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should GET /api/v1/me/profile', () => {
    service.getProfile().subscribe();

    const request = httpTestingController.expectOne('/api/v1/me/profile');
    expect(request.request.method).toBe('GET');

    request.flush(profile);
  });

  it('should propagate the MeProfileResponse returned by the backend', () => {
    let received: MeProfileResponse | undefined;
    service.getProfile().subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/me/profile').flush(profile);

    expect(received).toEqual(profile);
  });

  it('should PATCH an empty body when phoneNumber is not provided', () => {
    service.updateProfile({}).subscribe();

    const request = httpTestingController.expectOne('/api/v1/me/profile');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({});

    request.flush(profile);
  });

  it('should PATCH an explicit null and preserve it in the body', () => {
    service.updateProfile({ phoneNumber: null }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/me/profile');
    expect(request.request.body).toEqual({ phoneNumber: null });

    request.flush(profile);
  });

  it('should PATCH a new phoneNumber value', () => {
    service.updateProfile({ phoneNumber: '+32470123456' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/me/profile');
    expect(request.request.body).toEqual({ phoneNumber: '+32470123456' });

    request.flush(profile);
  });
});
