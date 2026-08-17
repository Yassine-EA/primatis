import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';

import { RoleCode } from '../../../../auth/models/role';
import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { CreateUserRequest } from '../../../../user/models/create-user-request';
import { MemberStatus } from '../../../../user/models/member-status';
import { UserApiService } from '../../../../user/services/user-api.service';

interface RoleOption {
  label: string;
  value: RoleCode;
}

interface MemberStatusOption {
  label: string;
  value: MemberStatus;
}

const ROLE_OPTIONS: RoleOption[] = [
  { label: 'Adhérent', value: 'ROLE_MEMBER' },
  { label: 'Bibliothécaire', value: 'ROLE_LIBRARIAN' },
  { label: 'Administrateur', value: 'ROLE_ADMIN' },
];

const MEMBER_STATUS_OPTIONS: MemberStatusOption[] = [
  { label: 'Actif', value: 'ACTIVE' },
  { label: 'Bloqué', value: 'BLOCKED' },
  { label: 'Expiré', value: 'EXPIRED' },
];

const CLIPBOARD_FAILURE_MESSAGE =
  'Copie automatique impossible : sélectionnez et copiez le mot de passe manuellement.';

function requireAtLeastOneRole(): ValidatorFn {
  return (control) => (Array.isArray(control.value) && control.value.length > 0 ? null : { requiredRoles: true });
}

/**
 * Page dédiée `/admin/users/new` (DEV-05.12 Décision 3), pas un dialog.
 * `ROLE_MEMBER` reste disponible ici (contrairement à l'édition, Décision
 * 5/16) : la création est le seul workflow autorisé à faire naître une
 * adhésion. Le mot de passe initial n'est affiché qu'une fois, dans un
 * dialog dont le contenu est détruit du DOM (pas seulement masqué) à la
 * fermeture — jamais dans l'URL, un Toast, `console.log` ou un storage.
 */
@Component({
  selector: 'app-admin-user-create-page',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    InputTextModule,
    MessageModule,
    MultiSelectModule,
    SelectModule,
    DialogModule,
  ],
  templateUrl: './admin-user-create-page.html',
  styleUrl: './admin-user-create-page.scss',
})
export class AdminUserCreatePage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly userApiService = inject(UserApiService);
  private readonly router = inject(Router);

  readonly roleOptions = ROLE_OPTIONS;
  readonly memberStatusOptions = MEMBER_STATUS_OPTIONS;

  readonly form = this.formBuilder.group({
    email: this.formBuilder.control('', [Validators.required, Validators.email]),
    firstName: this.formBuilder.control('', [Validators.required]),
    lastName: this.formBuilder.control('', [Validators.required]),
    phoneNumber: this.formBuilder.control(''),
    roles: this.formBuilder.control<RoleCode[]>([], [requireAtLeastOneRole()]),
    memberStatus: this.formBuilder.control<MemberStatus | null>(null),
    registrationDate: this.formBuilder.control(''),
    memberExpirationDate: this.formBuilder.control(''),
    blockedReason: this.formBuilder.control(''),
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly createdPassword = signal<string | null>(null);
  readonly copyFeedback = signal<string | null>(null);

  get isMember(): boolean {
    return this.form.controls.roles.value.includes('ROLE_MEMBER');
  }

  get isBlocked(): boolean {
    return this.form.controls.memberStatus.value === 'BLOCKED';
  }

  fieldError(field: string): string | undefined {
    return this.lastFieldErrors.find((fieldError) => fieldError.field === field)?.message;
  }

  private lastFieldErrors: readonly FieldError[] = [];

  /**
   * Rebasculement des validators Membership selon la présence de {@code
   * ROLE_MEMBER} (Décision 3/11 audit) : jamais une règle métier reproduite
   * côté frontend, seulement le miroir UX de {@code
   * validateMembershipCoherence} côté backend, qui reste l'autorité réelle.
   */
  onRolesChange(): void {
    const memberStatusControl = this.form.controls.memberStatus;
    const registrationDateControl = this.form.controls.registrationDate;

    if (this.isMember) {
      memberStatusControl.setValidators([Validators.required]);
      registrationDateControl.setValidators([Validators.required]);
      if (memberStatusControl.value === null) {
        memberStatusControl.setValue('ACTIVE');
      }
    } else {
      memberStatusControl.setValidators([]);
      registrationDateControl.setValidators([]);
      memberStatusControl.setValue(null);
      registrationDateControl.setValue('');
      this.form.controls.memberExpirationDate.setValue('');
      this.form.controls.blockedReason.setValue('');
    }
    memberStatusControl.updateValueAndValidity();
    registrationDateControl.updateValueAndValidity();
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.lastFieldErrors = [];

    this.userApiService.createUser(this.buildRequest()).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.createdPassword.set(response.initialPassword);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const appError = toAppError(err);
        this.errorMessage.set(appError.message);
        this.lastFieldErrors = appError.fieldErrors;
      },
    });
  }

  copyPassword(): void {
    const password = this.createdPassword();
    if (password === null) {
      return;
    }
    if (!navigator.clipboard?.writeText) {
      this.copyFeedback.set(CLIPBOARD_FAILURE_MESSAGE);
      return;
    }
    navigator.clipboard.writeText(password).then(
      () => this.copyFeedback.set('Mot de passe copié dans le presse-papiers.'),
      () => this.copyFeedback.set(CLIPBOARD_FAILURE_MESSAGE),
    );
  }

  closePasswordDialog(): void {
    this.createdPassword.set(null);
    this.copyFeedback.set(null);
    void this.router.navigateByUrl('/admin/users');
  }

  private buildRequest(): CreateUserRequest {
    const raw = this.form.getRawValue();
    const request: CreateUserRequest = {
      email: raw.email,
      firstName: raw.firstName,
      lastName: raw.lastName,
      roles: raw.roles,
    };
    if (raw.phoneNumber.trim().length > 0) {
      request.phoneNumber = raw.phoneNumber;
    }
    if (this.isMember) {
      if (raw.memberStatus !== null) {
        request.memberStatus = raw.memberStatus;
      }
      if (raw.registrationDate.trim().length > 0) {
        request.registrationDate = raw.registrationDate;
      }
      if (raw.memberExpirationDate.trim().length > 0) {
        request.memberExpirationDate = raw.memberExpirationDate;
      }
      if (raw.blockedReason.trim().length > 0) {
        request.blockedReason = raw.blockedReason;
      }
    }
    return request;
  }
}
