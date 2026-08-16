import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AddressResponse } from '../../../../user/models/address-response';
import { ResidenceResponse } from '../../../../user/models/residence-response';
import { UserResponse } from '../../../../user/models/user-response';
import { ResidenceApiService } from '../../../../user/services/residence-api.service';
import { UserApiService } from '../../../../user/services/user-api.service';
import { StaffUserDetailPage } from './staff-user-detail-page';

function buildUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: 7,
    email: 'member@primatis.test',
    firstName: 'Marie',
    lastName: 'Curie',
    phoneNumber: '+32470123456',
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

function buildAddress(overrides: Partial<AddressResponse> = {}): AddressResponse {
  return {
    id: 1,
    street: 'Rue du Parlement',
    streetNumber: '10',
    boxNumber: null,
    additionalInfo: null,
    city: { id: 1, name: 'Bruxelles', postalCode: '1000', country: { id: 1, name: 'Belgique', code: 'BE' } },
    ...overrides,
  };
}

function buildResidence(overrides: Partial<ResidenceResponse> = {}): ResidenceResponse {
  return { id: 1, address: buildAddress(), startDate: '2026-01-01', endDate: null, ...overrides };
}

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { timestamp: new Date().toISOString(), status, error: 'Error', code, message, path: '/api/v1/x', fieldErrors: [] },
  });
}

describe('StaffUserDetailPage', () => {
  let fixture: ComponentFixture<StaffUserDetailPage>;
  let userApiServiceMock: { getUser: ReturnType<typeof vi.fn> };
  let residenceApiServiceMock: { getResidence: ReturnType<typeof vi.fn>; getResidenceHistory: ReturnType<typeof vi.fn> };

  function configure(rawId: string | null): void {
    const paramMap$ = new BehaviorSubject<ParamMap>(convertToParamMap(rawId === null ? {} : { id: rawId }));
    userApiServiceMock = { getUser: vi.fn().mockReturnValue(of({ user: buildUser(), roles: [] })) };
    residenceApiServiceMock = {
      getResidence: vi.fn().mockReturnValue(of(buildResidence())),
      getResidenceHistory: vi.fn().mockReturnValue(of([])),
    };

    TestBed.configureTestingModule({
      imports: [StaffUserDetailPage],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$ } },
        { provide: UserApiService, useValue: userApiServiceMock },
        { provide: ResidenceApiService, useValue: residenceApiServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffUserDetailPage);
    fixture.detectChanges();
  }

  it('should resolve id from the route and call getUser/getResidence/getResidenceHistory', () => {
    configure('7');

    createComponent();

    expect(userApiServiceMock.getUser).toHaveBeenCalledWith(7);
    expect(residenceApiServiceMock.getResidence).toHaveBeenCalledWith(7);
    expect(residenceApiServiceMock.getResidenceHistory).toHaveBeenCalledWith(7);
  });

  it('should never call any API with NaN for a non-numeric id', () => {
    configure('abc');

    createComponent();

    expect(userApiServiceMock.getUser).not.toHaveBeenCalled();
    expect(residenceApiServiceMock.getResidence).not.toHaveBeenCalled();
    expect(residenceApiServiceMock.getResidenceHistory).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should render UserResponse fields', () => {
    configure('7');

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Marie Curie');
    expect(text).toContain('member@primatis.test');
    expect(text).toContain('+32470123456');
    expect(text).toContain('M000000001');
  });

  it('should show the current residence when present', () => {
    configure('7');

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Rue du Parlement');
    expect(text).toContain('Bruxelles');
  });

  it('should treat CURRENT_RESIDENCE_NOT_FOUND as a normal state, never an error', () => {
    configure('7');
    residenceApiServiceMock.getResidence.mockReturnValue(
      throwError(() => apiHttpError(404, 'CURRENT_RESIDENCE_NOT_FOUND', 'Aucune résidence courante.')),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Aucune résidence actuelle');
    // Only one ErrorState may exist on the page (e.g. history), never for the current-residence section itself.
    expect(fixture.componentInstance.residenceError()).toBeNull();
  });

  it('should show an ErrorState scoped to the residence section for a non-404 residence error', () => {
    configure('7');
    residenceApiServiceMock.getResidence.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );

    createComponent();

    expect(fixture.componentInstance.residenceError()).not.toBeNull();
    expect(fixture.componentInstance.userError()).toBeNull();
  });

  it('should render history entries without re-sorting them', () => {
    configure('7');
    const older = buildResidence({ id: 1, startDate: '2020-01-01', endDate: '2020-12-31' });
    const newer = buildResidence({ id: 2, startDate: '2026-01-01', endDate: null });
    residenceApiServiceMock.getResidenceHistory.mockReturnValue(of([newer, older]));

    createComponent();

    expect(fixture.componentInstance.residenceHistory()).toEqual([newer, older]);
  });

  it('should show the empty state for an empty history', () => {
    configure('7');

    createComponent();

    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  it('should show a full-page error state when getUser fails', () => {
    configure('7');
    userApiServiceMock.getUser.mockReturnValue(throwError(() => apiHttpError(404, 'USER_NOT_FOUND', 'Introuvable.')));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('h1')).toBeNull();
  });

  it('should never render an edit control or USER_MANAGE action', () => {
    configure('7');

    createComponent();

    expect(fixture.nativeElement.querySelector('button, input, select, form')).toBeNull();
  });
});
