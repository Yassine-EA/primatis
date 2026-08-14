import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { LoginResponse } from '../models/login-response';
import { AuthApiService } from './auth-api.service';

describe('AuthApiService', () => {
  let service: AuthApiService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(AuthApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should POST the credentials to /api/v1/auth/login', () => {
    const response: LoginResponse = {
      token: 'header.payload.signature',
      tokenType: 'Bearer',
      expiresAt: '2026-08-15T13:00:00Z',
      expiresInSeconds: 3600,
    };

    service.login({ email: 'librarian@primatis.test', password: 'Correct-Password-2026!' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'librarian@primatis.test',
      password: 'Correct-Password-2026!',
    });

    request.flush(response);
  });

  it('should propagate the LoginResponse returned by the backend', () => {
    const response: LoginResponse = {
      token: 'header.payload.signature',
      tokenType: 'Bearer',
      expiresAt: '2026-08-15T13:00:00Z',
      expiresInSeconds: 3600,
    };

    let received: LoginResponse | undefined;
    service.login({ email: 'librarian@primatis.test', password: 'Correct-Password-2026!' }).subscribe((value) => {
      received = value;
    });

    httpTestingController.expectOne('/api/v1/auth/login').flush(response);

    expect(received).toEqual(response);
  });
});
