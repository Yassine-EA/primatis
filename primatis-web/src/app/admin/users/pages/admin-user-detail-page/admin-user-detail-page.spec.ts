import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AddressResponse } from '../../../../user/models/address-response';
import { ResidenceResponse } from '../../../../user/models/residence-response';
import { UserDetailResponse } from '../../../../user/models/user-detail-response';
import { UserResponse } from '../../../../user/models/user-response';
import { ResidenceApiService } from '../../../../user/services/residence-api.service';
import { UserApiService } from '../../../../user/services/user-api.service';
import { AdminUserDetailPage } from './admin-user-detail-page';

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

function buildDetail(overrides: Partial<UserDetailResponse> = {}): UserDetailResponse {
  return { user: buildUser(), roles: ['ROLE_MEMBER'], ...overrides };
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

function apiHttpError(status: number, code: string, message: string, fieldErrors: { field: string; message: string }[] = []): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { timestamp: new Date().toISOString(), status, error: 'Error', code, message, path: '/api/v1/users/7', fieldErrors },
  });
}

describe('AdminUserDetailPage', () => {
  let fixture: ComponentFixture<AdminUserDetailPage>;
  let userApiServiceMock: {
    getUser: ReturnType<typeof vi.fn>;
    updateUser: ReturnType<typeof vi.fn>;
    updateAccountStatus: ReturnType<typeof vi.fn>;
    blockMembership: ReturnType<typeof vi.fn>;
    unblockMembership: ReturnType<typeof vi.fn>;
    reactivateMembership: ReturnType<typeof vi.fn>;
  };
  let residenceApiServiceMock: { getResidence: ReturnType<typeof vi.fn>; getResidenceHistory: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let confirmationServiceMock: { confirm: ReturnType<typeof vi.fn> };

  function configure(rawId: string | null, detail: UserDetailResponse = buildDetail()): void {
    const paramMap$ = new BehaviorSubject<ParamMap>(convertToParamMap(rawId === null ? {} : { id: rawId }));
    userApiServiceMock = {
      getUser: vi.fn().mockReturnValue(of(detail)),
      updateUser: vi.fn(),
      updateAccountStatus: vi.fn(),
      blockMembership: vi.fn(),
      unblockMembership: vi.fn(),
      reactivateMembership: vi.fn(),
    };
    residenceApiServiceMock = {
      getResidence: vi.fn().mockReturnValue(of(buildResidence())),
      getResidenceHistory: vi.fn().mockReturnValue(of([])),
    };
    messageServiceMock = { add: vi.fn() };
    confirmationServiceMock = { confirm: vi.fn() };

    TestBed.configureTestingModule({
      imports: [AdminUserDetailPage],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$ } },
        { provide: UserApiService, useValue: userApiServiceMock },
        { provide: ResidenceApiService, useValue: residenceApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(AdminUserDetailPage);
    fixture.detectChanges();
  }

  it('should load the user detail response and preload roles into the form', () => {
    configure('7', buildDetail({ roles: ['ROLE_LIBRARIAN', 'ROLE_MEMBER'] }));

    createComponent();

    expect(userApiServiceMock.getUser).toHaveBeenCalledWith(7);
    expect(fixture.componentInstance.user()?.id).toBe(7);
    expect(fixture.componentInstance.form.controls.roles.value).toEqual(['ROLE_LIBRARIAN', 'ROLE_MEMBER']);
  });

  it('should never call any API with NaN for a non-numeric id', () => {
    configure('abc');

    createComponent();

    expect(userApiServiceMock.getUser).not.toHaveBeenCalled();
    expect(residenceApiServiceMock.getResidence).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should show a full-page error state when getUser fails', () => {
    configure('7');
    userApiServiceMock.getUser.mockReturnValue(throwError(() => apiHttpError(404, 'USER_NOT_FOUND', 'Introuvable.')));

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  // ---------------------------------------------------------------
  // Sparse update
  // ---------------------------------------------------------------

  it('should omit unchanged roles from the PATCH body', () => {
    configure('7', buildDetail({ roles: ['ROLE_MEMBER', 'ROLE_LIBRARIAN'] }));
    userApiServiceMock.updateUser.mockReturnValue(of(buildUser()));
    createComponent();
    const component = fixture.componentInstance;
    component.form.controls.firstName.setValue('Autre prénom');

    component.submitUpdate();

    const [, request] = userApiServiceMock.updateUser.mock.calls[0];
    expect(request.roles).toBeUndefined();
  });

  it('should omit reordered-but-identical roles from the PATCH body', () => {
    configure('7', buildDetail({ roles: ['ROLE_MEMBER', 'ROLE_LIBRARIAN'] }));
    userApiServiceMock.updateUser.mockReturnValue(of(buildUser()));
    createComponent();
    const component = fixture.componentInstance;
    component.form.controls.roles.setValue(['ROLE_LIBRARIAN', 'ROLE_MEMBER']);
    component.form.controls.firstName.setValue('Autre prénom');

    component.submitUpdate();

    const [, request] = userApiServiceMock.updateUser.mock.calls[0];
    expect(request.roles).toBeUndefined();
  });

  it('should include the full final role array when the set actually changes', () => {
    configure('7', buildDetail({ roles: ['ROLE_MEMBER'] }));
    userApiServiceMock.updateUser.mockReturnValue(of(buildUser()));
    createComponent();
    const component = fixture.componentInstance;
    component.form.controls.roles.setValue(['ROLE_MEMBER', 'ROLE_ADMIN']);

    component.submitUpdate();

    expect(userApiServiceMock.updateUser).toHaveBeenCalledWith(7, { roles: ['ROLE_MEMBER', 'ROLE_ADMIN'] });
  });

  it('should block submission when roles is emptied', () => {
    configure('7');
    createComponent();
    const component = fixture.componentInstance;
    component.form.controls.roles.setValue([]);

    component.submitUpdate();

    expect(userApiServiceMock.updateUser).not.toHaveBeenCalled();
    expect(component.form.controls.roles.invalid).toBe(true);
  });

  it('should send only the changed field, omitting every other key', () => {
    configure('7');
    userApiServiceMock.updateUser.mockReturnValue(of(buildUser()));
    createComponent();
    fixture.componentInstance.form.controls.phoneNumber.setValue('+32499999999');

    fixture.componentInstance.submitUpdate();

    expect(userApiServiceMock.updateUser).toHaveBeenCalledWith(7, { phoneNumber: '+32499999999' });
  });

  it('should send an explicit null to clear phoneNumber', () => {
    configure('7');
    userApiServiceMock.updateUser.mockReturnValue(of(buildUser()));
    createComponent();
    fixture.componentInstance.form.controls.phoneNumber.setValue('');

    fixture.componentInstance.submitUpdate();

    expect(userApiServiceMock.updateUser).toHaveBeenCalledWith(7, { phoneNumber: null });
  });

  it('should show a toast and skip the request when nothing changed', () => {
    configure('7');
    createComponent();

    fixture.componentInstance.submitUpdate();

    expect(userApiServiceMock.updateUser).not.toHaveBeenCalled();
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'info' }));
  });

  it('should show a success toast and refresh the user on a successful update', () => {
    configure('7');
    userApiServiceMock.updateUser.mockReturnValue(of(buildUser({ firstName: 'Nouveau' })));
    createComponent();
    fixture.componentInstance.form.controls.firstName.setValue('Nouveau');

    fixture.componentInstance.submitUpdate();

    expect(fixture.componentInstance.user()?.firstName).toBe('Nouveau');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should surface a business error message on a failed update', () => {
    configure('7');
    userApiServiceMock.updateUser.mockReturnValue(
      throwError(() => apiHttpError(409, 'UNKNOWN_ROLE_CODE', 'Le rôle demandé n\'existe pas.')),
    );
    createComponent();
    fixture.componentInstance.form.controls.firstName.setValue('X');

    fixture.componentInstance.submitUpdate();

    expect(fixture.componentInstance.updateErrorMessage()).toBe("Le rôle demandé n'existe pas.");
  });

  // ---------------------------------------------------------------
  // ROLE_MEMBER restriction
  // ---------------------------------------------------------------

  it('should disable the ROLE_MEMBER option for a user who was never a member', () => {
    configure('7', buildDetail({ user: buildUser({ memberNumber: null, memberStatus: null }), roles: ['ROLE_LIBRARIAN'] }));

    createComponent();

    const memberOption = fixture.componentInstance.roleOptions.find((option) => option.value === 'ROLE_MEMBER');
    expect(memberOption?.disabled).toBe(true);
  });

  it('should keep the ROLE_MEMBER option enabled for an existing member', () => {
    configure('7', buildDetail());

    createComponent();

    const memberOption = fixture.componentInstance.roleOptions.find((option) => option.value === 'ROLE_MEMBER');
    expect(memberOption?.disabled).toBe(false);
  });

  // ---------------------------------------------------------------
  // AccountStatus
  // ---------------------------------------------------------------

  it('should ask for confirmation before disabling an active account', () => {
    configure('7', buildDetail({ user: buildUser({ accountStatus: 'ACTIVE' }) }));
    createComponent();

    fixture.componentInstance.toggleAccountStatus();

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(userApiServiceMock.updateAccountStatus).not.toHaveBeenCalled();
  });

  it('should call updateAccountStatus only after confirmation is accepted', () => {
    configure('7', buildDetail({ user: buildUser({ accountStatus: 'ACTIVE' }) }));
    userApiServiceMock.updateAccountStatus.mockReturnValue(of(buildUser({ accountStatus: 'DISABLED' })));
    createComponent();

    fixture.componentInstance.toggleAccountStatus();
    const config = confirmationServiceMock.confirm.mock.calls[0][0];
    config.accept();

    expect(userApiServiceMock.updateAccountStatus).toHaveBeenCalledWith(7, { status: 'DISABLED' });
    expect(fixture.componentInstance.user()?.accountStatus).toBe('DISABLED');
  });

  it('should not call updateAccountStatus when confirmation is not accepted', () => {
    configure('7');
    createComponent();

    fixture.componentInstance.toggleAccountStatus();

    expect(userApiServiceMock.updateAccountStatus).not.toHaveBeenCalled();
  });

  // ---------------------------------------------------------------
  // Membership action matrix
  // ---------------------------------------------------------------

  it('should show only Bloquer for an ACTIVE member', () => {
    configure('7', buildDetail({ user: buildUser({ memberStatus: 'ACTIVE' }) }));
    createComponent();

    expect(fixture.componentInstance.canBlock).toBe(true);
    expect(fixture.componentInstance.canReactivate).toBe(false);
    expect(fixture.componentInstance.canUnblock).toBe(false);
  });

  it('should show Bloquer and Réactiver for an EXPIRED member', () => {
    configure('7', buildDetail({ user: buildUser({ memberStatus: 'EXPIRED' }) }));
    createComponent();

    expect(fixture.componentInstance.canBlock).toBe(true);
    expect(fixture.componentInstance.canReactivate).toBe(true);
  });

  it('should show Débloquer and modifier le motif for a BLOCKED member', () => {
    configure('7', buildDetail({ user: buildUser({ memberStatus: 'BLOCKED', blockedReason: 'Retard' }) }));
    createComponent();

    expect(fixture.componentInstance.canUnblock).toBe(true);
    expect(fixture.componentInstance.canModifyBlockedReason).toBe(true);
    expect(fixture.componentInstance.canBlock).toBe(false);
  });

  it('should show no Membership action for a user who was never a member', () => {
    configure('7', buildDetail({ user: buildUser({ memberNumber: null, memberStatus: null }), roles: ['ROLE_LIBRARIAN'] }));
    createComponent();

    expect(fixture.componentInstance.canBlock).toBe(false);
    expect(fixture.componentInstance.canReactivate).toBe(false);
    expect(fixture.componentInstance.canUnblock).toBe(false);
  });

  // ---------------------------------------------------------------
  // Block dialog
  // ---------------------------------------------------------------

  it('should require a blockedReason before confirming a block', () => {
    configure('7');
    createComponent();
    fixture.componentInstance.openBlockDialog();
    fixture.componentInstance.blockReasonControl.setValue('');

    fixture.componentInstance.confirmBlock();

    expect(userApiServiceMock.blockMembership).not.toHaveBeenCalled();
    expect(fixture.componentInstance.blockReasonControl.invalid).toBe(true);
  });

  it('should call blockMembership with the entered reason and close the dialog on success', () => {
    configure('7');
    userApiServiceMock.blockMembership.mockReturnValue(of(buildUser({ memberStatus: 'BLOCKED', blockedReason: 'Retard de paiement' })));
    createComponent();
    fixture.componentInstance.openBlockDialog();
    fixture.componentInstance.blockReasonControl.setValue('Retard de paiement');

    fixture.componentInstance.confirmBlock();

    expect(userApiServiceMock.blockMembership).toHaveBeenCalledWith(7, { blockedReason: 'Retard de paiement' });
    expect(fixture.componentInstance.blockDialogVisible()).toBe(false);
    expect(fixture.componentInstance.user()?.memberStatus).toBe('BLOCKED');
  });

  it('should prefill the block dialog with the current blockedReason ("modifier le motif")', () => {
    configure('7', buildDetail({ user: buildUser({ memberStatus: 'BLOCKED', blockedReason: 'Motif initial' }) }));
    createComponent();

    fixture.componentInstance.openBlockDialog();

    expect(fixture.componentInstance.blockReasonControl.value).toBe('Motif initial');
  });

  it('should close the block dialog without calling the API when cancelled', () => {
    configure('7');
    createComponent();
    fixture.componentInstance.openBlockDialog();

    fixture.componentInstance.cancelBlock();

    expect(fixture.componentInstance.blockDialogVisible()).toBe(false);
    expect(userApiServiceMock.blockMembership).not.toHaveBeenCalled();
  });

  // ---------------------------------------------------------------
  // Unblock / Reactivate
  // ---------------------------------------------------------------

  it('should call unblockMembership only after confirmation is accepted', () => {
    configure('7', buildDetail({ user: buildUser({ memberStatus: 'BLOCKED' }) }));
    userApiServiceMock.unblockMembership.mockReturnValue(of(buildUser({ memberStatus: 'ACTIVE' })));
    createComponent();

    fixture.componentInstance.confirmUnblock();
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(userApiServiceMock.unblockMembership).toHaveBeenCalledWith(7);
    expect(fixture.componentInstance.user()?.memberStatus).toBe('ACTIVE');
  });

  it('should call reactivateMembership only after confirmation is accepted', () => {
    configure('7', buildDetail({ user: buildUser({ memberStatus: 'EXPIRED' }) }));
    userApiServiceMock.reactivateMembership.mockReturnValue(of(buildUser({ memberStatus: 'ACTIVE' })));
    createComponent();

    fixture.componentInstance.confirmReactivate();
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(userApiServiceMock.reactivateMembership).toHaveBeenCalledWith(7);
    expect(fixture.componentInstance.user()?.memberStatus).toBe('ACTIVE');
  });

  it('should show an error toast when a membership action fails', () => {
    configure('7', buildDetail({ user: buildUser({ memberStatus: 'BLOCKED' }) }));
    userApiServiceMock.unblockMembership.mockReturnValue(
      throwError(() => apiHttpError(409, 'MEMBER_NOT_BLOCKED', "Cet adhérent n'est pas bloqué.")),
    );
    createComponent();

    fixture.componentInstance.confirmUnblock();
    confirmationServiceMock.confirm.mock.calls[0][0].accept();

    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });

  // ---------------------------------------------------------------
  // Residence
  // ---------------------------------------------------------------

  it('should treat CURRENT_RESIDENCE_NOT_FOUND as a normal empty state, not an error', () => {
    configure('7');
    residenceApiServiceMock.getResidence.mockReturnValue(
      throwError(() => apiHttpError(404, 'CURRENT_RESIDENCE_NOT_FOUND', 'Aucune résidence courante.')),
    );

    createComponent();

    expect(fixture.componentInstance.residenceError()).toBeNull();
    expect(fixture.componentInstance.currentResidence()).toBeNull();
  });

  it('should show an error state for a real residence error', () => {
    configure('7');
    residenceApiServiceMock.getResidence.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );

    createComponent();

    expect(fixture.componentInstance.residenceError()).not.toBeNull();
  });

  it('should show an empty state for an empty residence history', () => {
    configure('7');

    createComponent();

    expect(fixture.nativeElement.querySelectorAll('app-empty-state').length).toBeGreaterThan(0);
  });

  // ---------------------------------------------------------------
  // Action loading / forbidden controls
  // ---------------------------------------------------------------

  it('should disable the update submit button while a request is pending', () => {
    configure('7');
    userApiServiceMock.updateUser.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();
    fixture.componentInstance.form.controls.firstName.setValue('X');

    fixture.componentInstance.submitUpdate();
    fixture.detectChanges();

    expect(fixture.componentInstance.updateSubmitting()).toBe(true);
  });

  it('should never render an email input, a delete control, or a password reset control', () => {
    configure('7');
    createComponent();

    expect(fixture.nativeElement.querySelector('input#email')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Supprimer');
    expect(fixture.nativeElement.textContent).not.toContain('mot de passe');
  });
});
