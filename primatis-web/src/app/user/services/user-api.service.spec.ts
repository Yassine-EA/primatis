import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { CreateUserResponse } from '../models/create-user-response';
import { UserDetailResponse } from '../models/user-detail-response';
import { UserResponse } from '../models/user-response';
import { UserApiService } from './user-api.service';

describe('UserApiService', () => {
  let service: UserApiService;
  let httpTestingController: HttpTestingController;

  const user: UserResponse = {
    id: 1,
    email: 'librarian@primatis.test',
    firstName: 'Prénom',
    lastName: 'Nom',
    phoneNumber: null,
    accountStatus: 'ACTIVE',
    memberNumber: null,
    memberStatus: null,
    registrationDate: null,
    memberExpirationDate: null,
    blockedReason: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(UserApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // listUsers
  // ---------------------------------------------------------------

  it('should GET /api/v1/users with default page/size params', () => {
    service.listUsers().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/users' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');

    request.flush({ content: [user], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/users with explicit page/size params', () => {
    service.listUsers(1, 50).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/users' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('50');

    request.flush({ content: [], page: 1, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<UserResponse> returned by the backend', () => {
    let received: unknown;
    service.listUsers().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/users' && req.method === 'GET')
      .flush({ content: [user], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(received).toEqual({ content: [user], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  // ---------------------------------------------------------------
  // getUser
  // ---------------------------------------------------------------

  it('should GET /api/v1/users/{id}', () => {
    service.getUser(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/users/1');
    expect(request.request.method).toBe('GET');

    const detail: UserDetailResponse = { user, roles: ['ROLE_LIBRARIAN'] };
    request.flush(detail);
  });

  it('should propagate the UserDetailResponse returned by the backend', () => {
    let received: UserDetailResponse | undefined;
    service.getUser(1).subscribe((value) => (received = value));

    const detail: UserDetailResponse = { user, roles: ['ROLE_LIBRARIAN', 'ROLE_MEMBER'] };
    httpTestingController.expectOne('/api/v1/users/1').flush(detail);

    expect(received).toEqual(detail);
  });

  // ---------------------------------------------------------------
  // createUser
  // ---------------------------------------------------------------

  it('should POST the exact CreateUserRequest body to /api/v1/users', () => {
    service
      .createUser({
        email: 'new@primatis.test',
        firstName: 'Prénom',
        lastName: 'Nom',
        roles: ['ROLE_LIBRARIAN'],
      })
      .subscribe();

    const request = httpTestingController.expectOne('/api/v1/users');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'new@primatis.test',
      firstName: 'Prénom',
      lastName: 'Nom',
      roles: ['ROLE_LIBRARIAN'],
    });

    const response: CreateUserResponse = { user, initialPassword: 'Generated-Password-2026!' };
    request.flush(response);
  });

  it('should propagate the CreateUserResponse returned by the backend', () => {
    let received: CreateUserResponse | undefined;
    service
      .createUser({ email: 'new@primatis.test', firstName: 'Prénom', lastName: 'Nom', roles: ['ROLE_MEMBER'] })
      .subscribe((value) => (received = value));

    const response: CreateUserResponse = { user, initialPassword: 'Generated-Password-2026!' };
    httpTestingController.expectOne('/api/v1/users').flush(response);

    expect(received).toEqual(response);
  });

  // ---------------------------------------------------------------
  // updateUser — sparse PATCH
  // ---------------------------------------------------------------

  it('should PATCH an empty body when no field is provided', () => {
    service.updateUser(1, {}).subscribe();

    const request = httpTestingController.expectOne('/api/v1/users/1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({});

    request.flush(user);
  });

  it('should PATCH an explicit null to clear phoneNumber', () => {
    service.updateUser(1, { phoneNumber: null }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/users/1');
    expect(request.request.body).toEqual({ phoneNumber: null });

    request.flush(user);
  });

  it('should PATCH only the provided field, omitting every other key', () => {
    service.updateUser(1, { firstName: 'X' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/users/1');
    expect(request.request.body).toEqual({ firstName: 'X' });
    expect(Object.keys(request.request.body as object)).toEqual(['firstName']);

    request.flush(user);
  });

  // ---------------------------------------------------------------
  // updateAccountStatus
  // ---------------------------------------------------------------

  it('should PATCH /api/v1/users/{id}/account-status with the exact body', () => {
    service.updateAccountStatus(1, { status: 'DISABLED' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/users/1/account-status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'DISABLED' });

    request.flush(user);
  });

  // ---------------------------------------------------------------
  // Membership
  // ---------------------------------------------------------------

  it('should POST /api/v1/users/{id}/membership/block with the exact body', () => {
    service.blockMembership(1, { blockedReason: 'Retard de paiement' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/users/1/membership/block');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ blockedReason: 'Retard de paiement' });

    request.flush(user);
  });

  it('should POST /api/v1/users/{id}/membership/unblock with no body', () => {
    service.unblockMembership(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/users/1/membership/unblock');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    request.flush(user);
  });

  it('should POST /api/v1/users/{id}/membership/reactivate with no body', () => {
    service.reactivateMembership(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/users/1/membership/reactivate');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    request.flush(user);
  });
});
