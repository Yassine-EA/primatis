import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CreateUserResponse } from '../../../../user/models/create-user-response';
import { UserApiService } from '../../../../user/services/user-api.service';
import { AdminUserCreatePage } from './admin-user-create-page';

function apiHttpError(status: number, code: string, message: string, fieldErrors: { field: string; message: string }[] = []): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { timestamp: new Date().toISOString(), status, error: 'Error', code, message, path: '/api/v1/users', fieldErrors },
  });
}

function buildCreateUserResponse(overrides: Partial<CreateUserResponse> = {}): CreateUserResponse {
  return {
    user: {
      id: 99,
      email: 'new@primatis.test',
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
    },
    initialPassword: 'Generated-Password-2026!',
    ...overrides,
  };
}

describe('AdminUserCreatePage', () => {
  let fixture: ComponentFixture<AdminUserCreatePage>;
  let userApiServiceMock: { createUser: ReturnType<typeof vi.fn> };
  let router: Router;

  function configure(): void {
    userApiServiceMock = { createUser: vi.fn() };

    TestBed.configureTestingModule({
      imports: [AdminUserCreatePage],
      providers: [provideRouter([]), { provide: UserApiService, useValue: userApiServiceMock }],
    });

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(AdminUserCreatePage);
    fixture.detectChanges();
  }

  function fillMinimalValidForm(component: AdminUserCreatePage): void {
    component.form.setValue({
      email: 'new@primatis.test',
      firstName: 'Prénom',
      lastName: 'Nom',
      phoneNumber: '',
      roles: ['ROLE_LIBRARIAN'],
      memberStatus: null,
      registrationDate: '',
      memberExpirationDate: '',
      blockedReason: '',
    });
  }

  it('should not call createUser when the form is invalid', () => {
    configure();
    createComponent();

    fixture.componentInstance.submit();

    expect(userApiServiceMock.createUser).not.toHaveBeenCalled();
    expect(fixture.componentInstance.form.controls.email.touched).toBe(true);
  });

  it('should require at least one role', () => {
    configure();
    createComponent();
    const component = fixture.componentInstance;
    fillMinimalValidForm(component);
    component.form.controls.roles.setValue([]);

    component.submit();

    expect(userApiServiceMock.createUser).not.toHaveBeenCalled();
    expect(component.form.controls.roles.invalid).toBe(true);
  });

  it('should expose the Membership fields only when ROLE_MEMBER is selected', () => {
    configure();
    createComponent();
    const component = fixture.componentInstance;

    expect(component.isMember).toBe(false);

    component.form.controls.roles.setValue(['ROLE_MEMBER']);
    component.onRolesChange();
    fixture.detectChanges();

    expect(component.isMember).toBe(true);
    expect(component.form.controls.memberStatus.value).toBe('ACTIVE');
    expect(fixture.nativeElement.querySelector('.membership-fields')).not.toBeNull();
  });

  it('should require memberStatus and registrationDate once ROLE_MEMBER is selected', () => {
    configure();
    createComponent();
    const component = fixture.componentInstance;
    fillMinimalValidForm(component);
    component.form.controls.roles.setValue(['ROLE_MEMBER']);
    component.onRolesChange();
    component.form.controls.memberStatus.setValue(null);

    component.submit();

    expect(userApiServiceMock.createUser).not.toHaveBeenCalled();
    expect(component.form.controls.memberStatus.invalid).toBe(true);
    expect(component.form.controls.registrationDate.invalid).toBe(true);
  });

  it('should clear Membership fields when ROLE_MEMBER is deselected', () => {
    configure();
    createComponent();
    const component = fixture.componentInstance;
    component.form.controls.roles.setValue(['ROLE_MEMBER']);
    component.onRolesChange();
    component.form.controls.memberExpirationDate.setValue('2027-01-01');
    component.form.controls.blockedReason.setValue('Motif');

    component.form.controls.roles.setValue(['ROLE_LIBRARIAN']);
    component.onRolesChange();

    expect(component.isMember).toBe(false);
    expect(component.form.controls.memberStatus.value).toBeNull();
    expect(component.form.controls.registrationDate.value).toBe('');
    expect(component.form.controls.memberExpirationDate.value).toBe('');
    expect(component.form.controls.blockedReason.value).toBe('');
  });

  it('should send the exact CreateUserRequest body for a non-member', () => {
    configure();
    userApiServiceMock.createUser.mockReturnValue(of(buildCreateUserResponse()));
    createComponent();
    const component = fixture.componentInstance;
    fillMinimalValidForm(component);

    component.submit();

    expect(userApiServiceMock.createUser).toHaveBeenCalledWith({
      email: 'new@primatis.test',
      firstName: 'Prénom',
      lastName: 'Nom',
      roles: ['ROLE_LIBRARIAN'],
    });
  });

  it('should send Membership fields only when ROLE_MEMBER is selected', () => {
    configure();
    userApiServiceMock.createUser.mockReturnValue(of(buildCreateUserResponse()));
    createComponent();
    const component = fixture.componentInstance;
    fillMinimalValidForm(component);
    component.form.controls.roles.setValue(['ROLE_MEMBER']);
    component.onRolesChange();
    component.form.controls.registrationDate.setValue('2026-01-01');

    component.submit();

    expect(userApiServiceMock.createUser).toHaveBeenCalledWith({
      email: 'new@primatis.test',
      firstName: 'Prénom',
      lastName: 'Nom',
      roles: ['ROLE_MEMBER'],
      memberStatus: 'ACTIVE',
      registrationDate: '2026-01-01',
    });
  });

  it('should show the one-time password dialog on success', () => {
    configure();
    userApiServiceMock.createUser.mockReturnValue(of(buildCreateUserResponse({ initialPassword: 'Secret-Pass-1!' })));
    createComponent();
    fillMinimalValidForm(fixture.componentInstance);

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.componentInstance.createdPassword()).toBe('Secret-Pass-1!');
    expect(fixture.nativeElement.querySelector('[data-testid="generated-password"]').textContent).toContain(
      'Secret-Pass-1!',
    );
  });

  it('should copy the password via the Clipboard API and show non-blocking feedback', async () => {
    configure();
    userApiServiceMock.createUser.mockReturnValue(of(buildCreateUserResponse({ initialPassword: 'Secret-Pass-1!' })));
    createComponent();
    fillMinimalValidForm(fixture.componentInstance);
    fixture.componentInstance.submit();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    fixture.componentInstance.copyPassword();
    await Promise.resolve();

    expect(writeText).toHaveBeenCalledWith('Secret-Pass-1!');
    expect(fixture.componentInstance.copyFeedback()).toContain('copié');
  });

  it('should show non-blocking feedback when the Clipboard API is unavailable', () => {
    configure();
    userApiServiceMock.createUser.mockReturnValue(of(buildCreateUserResponse({ initialPassword: 'Secret-Pass-1!' })));
    createComponent();
    fillMinimalValidForm(fixture.componentInstance);
    fixture.componentInstance.submit();
    Object.assign(navigator, { clipboard: undefined });

    fixture.componentInstance.copyPassword();

    expect(fixture.componentInstance.copyFeedback()).toContain('manuellement');
  });

  it('should clear the password signal and destroy the password DOM node on close', () => {
    configure();
    userApiServiceMock.createUser.mockReturnValue(of(buildCreateUserResponse({ initialPassword: 'Secret-Pass-1!' })));
    createComponent();
    fillMinimalValidForm(fixture.componentInstance);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    fixture.componentInstance.closePasswordDialog();
    fixture.detectChanges();

    expect(fixture.componentInstance.createdPassword()).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="generated-password"]')).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/users');
  });

  it('should surface a duplicate email business error without calling the clipboard/log', () => {
    configure();
    userApiServiceMock.createUser.mockReturnValue(
      throwError(() => apiHttpError(409, 'USER_EMAIL_ALREADY_EXISTS', 'Un utilisateur existe déjà avec cet email.')),
    );
    createComponent();
    fillMinimalValidForm(fixture.componentInstance);

    fixture.componentInstance.submit();

    expect(fixture.componentInstance.errorMessage()).toBe('Un utilisateur existe déjà avec cet email.');
    expect(fixture.componentInstance.createdPassword()).toBeNull();
  });

  it('should prevent a second submission while the first request is pending', () => {
    configure();
    userApiServiceMock.createUser.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();
    fillMinimalValidForm(fixture.componentInstance);

    fixture.componentInstance.submit();
    fixture.componentInstance.submit();

    expect(userApiServiceMock.createUser).toHaveBeenCalledTimes(1);
  });
});
