import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../../core/models/page-response';
import { UserResponse } from '../../../../user/models/user-response';
import { UserApiService } from '../../../../user/services/user-api.service';
import { StaffUsersPage } from './staff-users-page';

function buildUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
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
    ...overrides,
  };
}

function buildPage(content: UserResponse[], totalElements = content.length): PageResponse<UserResponse> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)) };
}

function apiHttpError(code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 500,
    error: {
      timestamp: new Date().toISOString(),
      status: 500,
      error: 'Internal Server Error',
      code,
      message,
      path: '/api/v1/users',
      fieldErrors: [],
    },
  });
}

describe('StaffUsersPage', () => {
  let fixture: ComponentFixture<StaffUsersPage>;
  let userApiServiceMock: { listUsers: ReturnType<typeof vi.fn> };

  function configure(): void {
    userApiServiceMock = { listUsers: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffUsersPage],
      providers: [provideRouter([]), { provide: UserApiService, useValue: userApiServiceMock }],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffUsersPage);
    fixture.detectChanges();
  }

  it('should call listUsers(0, 20) on initial load', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));

    createComponent();

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20);
  });

  it('should render the expected columns for a user', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(
      of(
        buildPage([
          buildUser({
            firstName: 'Marie',
            lastName: 'Curie',
            email: 'marie@primatis.test',
            memberNumber: 'M000000001',
            memberStatus: 'ACTIVE',
          }),
        ]),
      ),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Marie Curie');
    expect(text).toContain('marie@primatis.test');
    expect(text).toContain('M000000001');
    expect(text).toContain('ACTIVE');
  });

  it('should render — for a null memberNumber and a null memberStatus', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(
      of(buildPage([buildUser({ memberNumber: null, memberStatus: null })])),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('—');
  });

  it('should map a PrimeNG lazy load event to page/size and call listUsers again', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()], 100)));
    createComponent();
    userApiServiceMock.listUsers.mockClear();

    fixture.componentInstance.onLazyLoad({ first: 40, rows: 20 });

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(2, 20);
  });

  it('should default to page 0 / size 20 when the lazy load event omits first/rows', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));
    createComponent();
    userApiServiceMock.listUsers.mockClear();

    fixture.componentInstance.onLazyLoad({});

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20);
  });

  it('should show the loading state before the first response arrives', () => {
    configure();
    // Observable never emits synchronously: keeps the component in its initial loading state.
    userApiServiceMock.listUsers.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when the backend returns no user', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([], 0)));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  it('should show the error state on a failed request', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the last request when retry is triggered', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();
    userApiServiceMock.listUsers.mockClear();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser()])));

    fixture.componentInstance.retry();

    expect(userApiServiceMock.listUsers).toHaveBeenCalledWith(0, 20);
  });

  it('should link each row to its detail page', () => {
    configure();
    userApiServiceMock.listUsers.mockReturnValue(of(buildPage([buildUser({ id: 42 })])));

    createComponent();

    expect(fixture.nativeElement.querySelector('a[href="/staff/users/42"]')).not.toBeNull();
  });
});
