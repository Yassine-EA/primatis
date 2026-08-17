import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AddressResponse } from '../../../../user/models/address-response';
import { MeProfileResponse } from '../../../../user/models/me-profile-response';
import { ResidenceResponse } from '../../../../user/models/residence-response';
import { MeProfileApiService } from '../../../../user/services/me-profile-api.service';
import { ResidenceApiService } from '../../../../user/services/residence-api.service';
import { MemberProfilePage } from './member-profile-page';

function buildProfile(overrides: Partial<MeProfileResponse> = {}): MeProfileResponse {
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
    error: { timestamp: new Date().toISOString(), status, error: 'Error', code, message, path: '/api/v1/me/x', fieldErrors: [] },
  });
}

describe('MemberProfilePage', () => {
  let fixture: ComponentFixture<MemberProfilePage>;
  let meProfileApiServiceMock: { getProfile: ReturnType<typeof vi.fn>; updateProfile: ReturnType<typeof vi.fn> };
  let residenceApiServiceMock: { getOwnResidence: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  function configure(): void {
    meProfileApiServiceMock = { getProfile: vi.fn(), updateProfile: vi.fn() };
    residenceApiServiceMock = { getOwnResidence: vi.fn() };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [MemberProfilePage],
      providers: [
        { provide: MeProfileApiService, useValue: meProfileApiServiceMock },
        { provide: ResidenceApiService, useValue: residenceApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(MemberProfilePage);
    fixture.detectChanges();
  }

  it('should show the loading state before the profile response arrives', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should load profile and residence independently on init', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile()));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();

    expect(meProfileApiServiceMock.getProfile).toHaveBeenCalledTimes(1);
    expect(residenceApiServiceMock.getOwnResidence).toHaveBeenCalledTimes(1);
  });

  it('should render identity fields and AccountStatus', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile({ accountStatus: 'ACTIVE' })));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Marie');
    expect(text).toContain('Curie');
    expect(text).toContain('member@primatis.test');
    expect(text).toContain('ACTIVE');
  });

  it('should render full Membership details when present', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(
      of(
        buildProfile({
          memberNumber: 'M000000042',
          memberStatus: 'ACTIVE',
          registrationDate: '2026-02-01',
          memberExpirationDate: '2027-02-01',
        }),
      ),
    );
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('M000000042');
    expect(text).toContain('2026-02-01');
    expect(text).toContain('2027-02-01');
  });

  it('should render — for null Membership fields', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(
      of(
        buildProfile({
          memberNumber: null,
          memberStatus: null,
          registrationDate: null,
          memberExpirationDate: null,
          blockedReason: null,
        }),
      ),
    );
    residenceApiServiceMock.getOwnResidence.mockReturnValue(
      throwError(() => apiHttpError(404, 'CURRENT_RESIDENCE_NOT_FOUND', 'Aucune résidence courante.')),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('—');
  });

  it('should show blockedReason when present', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(
      of(buildProfile({ memberStatus: 'BLOCKED', blockedReason: 'Retard de paiement' })),
    );
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Retard de paiement');
  });

  it('should not show a blockedReason row when absent', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile({ blockedReason: null })));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();

    expect(fixture.nativeElement.textContent).not.toContain('Motif de blocage');
  });

  it('should render the current residence when present', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile()));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(
      of(buildResidence({ address: buildAddress({ street: 'Rue Neuve', streetNumber: '5' }) })),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Rue Neuve');
    expect(text).toContain('5');
  });

  it('should treat CURRENT_RESIDENCE_NOT_FOUND as a normal empty state, not an error', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile()));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(
      throwError(() => apiHttpError(404, 'CURRENT_RESIDENCE_NOT_FOUND', 'Aucune résidence courante.')),
    );

    createComponent();

    expect(fixture.componentInstance.residenceError()).toBeNull();
    expect(fixture.componentInstance.currentResidence()).toBeNull();
    expect(fixture.nativeElement.querySelector('app-error-state')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Aucune résidence actuelle.');
  });

  it('should show a section-scoped error state for a real residence error, without hiding the profile', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile()));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );

    createComponent();

    expect(fixture.componentInstance.residenceError()).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
    // Le profil reste affiché malgré l'erreur Residence.
    expect(fixture.nativeElement.textContent).toContain('Marie');
  });

  it('should show a full-page error state when the profile fails to load', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Marie');
  });

  it('should prefill the phone form with the loaded profile value', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile({ phoneNumber: '+32470000000' })));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();

    expect(fixture.componentInstance.phoneForm.controls.phoneNumber.value).toBe('+32470000000');
  });

  it('should disable the submit button when the phone value is unchanged', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile({ phoneNumber: '+32470000000' })));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();

    expect(fixture.componentInstance.isPhoneSubmitDisabled).toBe(true);
  });

  it('should disable the submit button when the phone value is blank', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile({ phoneNumber: '+32470000000' })));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();
    fixture.componentInstance.phoneForm.controls.phoneNumber.setValue('   ');

    expect(fixture.componentInstance.isPhoneSubmitDisabled).toBe(true);
  });

  it('should enable the submit button once the phone value actually changes', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile({ phoneNumber: '+32470000000' })));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();
    fixture.componentInstance.phoneForm.controls.phoneNumber.setValue('+32499999999');

    expect(fixture.componentInstance.isPhoneSubmitDisabled).toBe(false);
  });

  it('should call updateProfile with exactly { phoneNumber }, no other field', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile({ phoneNumber: '+32470000000' })));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));
    meProfileApiServiceMock.updateProfile.mockReturnValue(of(buildProfile({ phoneNumber: '+32499999999' })));
    createComponent();
    fixture.componentInstance.phoneForm.controls.phoneNumber.setValue('+32499999999');

    fixture.componentInstance.submitPhone();

    expect(meProfileApiServiceMock.updateProfile).toHaveBeenCalledWith({ phoneNumber: '+32499999999' });
    expect(meProfileApiServiceMock.updateProfile).toHaveBeenCalledTimes(1);
    const [request] = meProfileApiServiceMock.updateProfile.mock.calls[0];
    expect(Object.keys(request)).toEqual(['phoneNumber']);
  });

  it('should show a success toast and refresh the profile/baseline after a successful update', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile({ phoneNumber: '+32470000000' })));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));
    meProfileApiServiceMock.updateProfile.mockReturnValue(of(buildProfile({ phoneNumber: '+32499999999' })));
    createComponent();
    fixture.componentInstance.phoneForm.controls.phoneNumber.setValue('+32499999999');

    fixture.componentInstance.submitPhone();

    expect(fixture.componentInstance.profile()?.phoneNumber).toBe('+32499999999');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    // Nouvelle baseline : la valeur vient d'être enregistrée, le bouton redevient désactivé.
    expect(fixture.componentInstance.isPhoneSubmitDisabled).toBe(true);
  });

  it('should show an inline error and no success toast when the update fails', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile({ phoneNumber: '+32470000000' })));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));
    meProfileApiServiceMock.updateProfile.mockReturnValue(
      throwError(() => apiHttpError(400, 'VALIDATION_FAILED', 'Numéro de téléphone invalide.')),
    );
    createComponent();
    fixture.componentInstance.phoneForm.controls.phoneNumber.setValue('invalid');

    fixture.componentInstance.submitPhone();

    expect(fixture.componentInstance.phoneErrorMessage()).toBe('Numéro de téléphone invalide.');
    expect(messageServiceMock.add).not.toHaveBeenCalled();
  });

  it('should prevent a second submission while the first request is pending', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile({ phoneNumber: '+32470000000' })));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));
    meProfileApiServiceMock.updateProfile.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();
    fixture.componentInstance.phoneForm.controls.phoneNumber.setValue('+32499999999');

    fixture.componentInstance.submitPhone();
    fixture.componentInstance.submitPhone();

    expect(meProfileApiServiceMock.updateProfile).toHaveBeenCalledTimes(1);
  });

  it('should never call updateProfile on initial page load', () => {
    configure();
    meProfileApiServiceMock.getProfile.mockReturnValue(of(buildProfile()));
    residenceApiServiceMock.getOwnResidence.mockReturnValue(of(buildResidence()));

    createComponent();

    expect(meProfileApiServiceMock.updateProfile).not.toHaveBeenCalled();
  });
});
