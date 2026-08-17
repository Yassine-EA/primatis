import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { MultiSelectModule } from 'primeng/multiselect';
import { TagModule } from 'primeng/tag';

import { RoleCode } from '../../../../auth/models/role';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { AccountStatus } from '../../../../user/models/account-status';
import { MemberStatus } from '../../../../user/models/member-status';
import { ResidenceResponse } from '../../../../user/models/residence-response';
import { UpdateUserRequest } from '../../../../user/models/update-user-request';
import { UserResponse } from '../../../../user/models/user-response';
import { ResidenceApiService } from '../../../../user/services/residence-api.service';
import { UserApiService } from '../../../../user/services/user-api.service';

const CURRENT_RESIDENCE_NOT_FOUND_CODE = 'CURRENT_RESIDENCE_NOT_FOUND';
const INVALID_USER_ID_ERROR: AppError = { message: "Identifiant d'utilisateur invalide.", fieldErrors: [] };

interface RoleOption {
  label: string;
  value: RoleCode;
  disabled: boolean;
}

function requireAtLeastOneRole(): ValidatorFn {
  return (control) => (Array.isArray(control.value) && control.value.length > 0 ? null : { requiredRoles: true });
}

function normalizeOptional(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function sameRoleSet(a: readonly RoleCode[], b: readonly RoleCode[]): boolean {
  const setA = new Set(a);
  const setB = new Set(b);
  return setA.size === setB.size && [...setA].every((role) => setB.has(role));
}

/**
 * Détail Admin (DEV-05.12, `USER_MANAGE`) : identité/rôles éditables via
 * PATCH sparse, `AccountStatus` (un bouton, libellé dérivé), Membership
 * (block/unblock/reactivate, jamais un dropdown générique), Résidence en
 * lecture seule (GAP-05.11-01 toujours OPEN). Markup Résidence dupliqué
 * depuis {@code StaffUserDetailPage} (Décision 2/10) — jamais partagé.
 */
@Component({
  selector: 'app-admin-user-detail-page',
  imports: [
    ReactiveFormsModule,
    TagModule,
    ButtonModule,
    InputTextModule,
    MessageModule,
    MultiSelectModule,
    DialogModule,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './admin-user-detail-page.html',
  styleUrl: './admin-user-detail-page.scss',
})
export class AdminUserDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly userApiService = inject(UserApiService);
  private readonly residenceApiService = inject(ResidenceApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);

  private userId: number | null = null;
  private roles: RoleCode[] = [];
  private lastUpdateFieldErrors: readonly FieldError[] = [];

  readonly user = signal<UserResponse | null>(null);
  readonly userLoading = signal(false);
  readonly userError = signal<AppError | null>(null);

  readonly currentResidence = signal<ResidenceResponse | null>(null);
  readonly residenceLoading = signal(false);
  readonly residenceError = signal<AppError | null>(null);

  readonly residenceHistory = signal<ResidenceResponse[]>([]);
  readonly historyLoading = signal(false);
  readonly historyError = signal<AppError | null>(null);

  readonly form = this.formBuilder.group({
    firstName: this.formBuilder.control('', [Validators.required]),
    lastName: this.formBuilder.control('', [Validators.required]),
    phoneNumber: this.formBuilder.control(''),
    roles: this.formBuilder.control<RoleCode[]>([], [requireAtLeastOneRole()]),
    registrationDate: this.formBuilder.control(''),
    memberExpirationDate: this.formBuilder.control(''),
    blockedReason: this.formBuilder.control(''),
  });

  readonly updateSubmitting = signal(false);
  readonly updateErrorMessage = signal<string | null>(null);

  readonly accountStatusSubmitting = signal(false);
  readonly membershipActionSubmitting = signal(false);

  readonly blockDialogVisible = signal(false);
  readonly blockSubmitting = signal(false);
  readonly blockReasonControl = this.formBuilder.control('', [Validators.required]);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = parseUserId(params.get('id'));
      if (id === null) {
        this.userId = null;
        this.user.set(null);
        this.userLoading.set(false);
        this.userError.set(INVALID_USER_ID_ERROR);
        return;
      }
      this.userId = id;
      this.loadUser(id);
      this.loadCurrentResidence(id);
      this.loadResidenceHistory(id);
    });
  }

  get hasMembership(): boolean {
    const currentUser = this.user();
    return currentUser !== null && currentUser.memberNumber !== null;
  }

  get roleOptions(): RoleOption[] {
    const disableMemberRole = !this.hasMembership;
    return [
      { label: 'Adhérent', value: 'ROLE_MEMBER', disabled: disableMemberRole },
      { label: 'Bibliothécaire', value: 'ROLE_LIBRARIAN', disabled: false },
      { label: 'Administrateur', value: 'ROLE_ADMIN', disabled: false },
    ];
  }

  get canBlock(): boolean {
    const status = this.user()?.memberStatus;
    return status === 'ACTIVE' || status === 'EXPIRED';
  }

  get canReactivate(): boolean {
    return this.user()?.memberStatus === 'EXPIRED';
  }

  get canUnblock(): boolean {
    return this.user()?.memberStatus === 'BLOCKED';
  }

  get canModifyBlockedReason(): boolean {
    return this.user()?.memberStatus === 'BLOCKED';
  }

  fieldError(field: string): string | undefined {
    return this.lastUpdateFieldErrors.find((fieldError) => fieldError.field === field)?.message;
  }

  accountStatusActionLabel(): string {
    return this.user()?.accountStatus === 'ACTIVE' ? 'Désactiver le compte' : 'Réactiver le compte';
  }

  // ---------------------------------------------------------------
  // Chargement
  // ---------------------------------------------------------------

  private loadUser(id: number): void {
    this.userLoading.set(true);
    this.userError.set(null);
    this.userApiService.getUser(id).subscribe({
      next: (value) => {
        this.roles = value.roles;
        this.user.set(value.user);
        this.resetFormFromUser(value.user, value.roles);
        this.userLoading.set(false);
      },
      error: (err: unknown) => {
        this.userLoading.set(false);
        this.userError.set(toAppError(err));
      },
    });
  }

  private loadCurrentResidence(id: number): void {
    this.residenceLoading.set(true);
    this.residenceError.set(null);
    this.currentResidence.set(null);
    this.residenceApiService.getResidence(id).subscribe({
      next: (value) => {
        this.currentResidence.set(value);
        this.residenceLoading.set(false);
      },
      error: (err: unknown) => {
        this.residenceLoading.set(false);
        const appError = toAppError(err);
        if (appError.code === CURRENT_RESIDENCE_NOT_FOUND_CODE) {
          // État normal (même principe que StaffUserDetailPage, DEV-05.11-DEC-05/06).
          this.currentResidence.set(null);
          return;
        }
        this.residenceError.set(appError);
      },
    });
  }

  private loadResidenceHistory(id: number): void {
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.residenceApiService.getResidenceHistory(id).subscribe({
      next: (value) => {
        this.residenceHistory.set(value);
        this.historyLoading.set(false);
      },
      error: (err: unknown) => {
        this.historyLoading.set(false);
        this.historyError.set(toAppError(err));
      },
    });
  }

  private resetFormFromUser(user: UserResponse, roles: RoleCode[]): void {
    this.form.reset({
      firstName: user.firstName,
      lastName: user.lastName,
      phoneNumber: user.phoneNumber ?? '',
      roles: [...roles],
      registrationDate: user.registrationDate ?? '',
      memberExpirationDate: user.memberExpirationDate ?? '',
      blockedReason: user.blockedReason ?? '',
    });
  }

  // ---------------------------------------------------------------
  // Update sparse (identité + rôles)
  // ---------------------------------------------------------------

  submitUpdate(): void {
    if (this.updateSubmitting() || this.userId === null) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const request = this.buildUpdateRequest();
    if (Object.keys(request).length === 0) {
      this.messageService.add({
        severity: 'info',
        summary: 'Aucune modification',
        detail: "Aucun champ n'a été modifié.",
      });
      return;
    }

    this.updateSubmitting.set(true);
    this.updateErrorMessage.set(null);
    this.lastUpdateFieldErrors = [];

    const requestedRoles = request.roles;
    this.userApiService.updateUser(this.userId, request).subscribe({
      next: (response) => {
        this.updateSubmitting.set(false);
        if (requestedRoles !== undefined) {
          this.roles = requestedRoles;
        }
        this.user.set(response);
        this.resetFormFromUser(response, this.roles);
        this.messageService.add({
          severity: 'success',
          summary: 'Utilisateur modifié',
          detail: 'Les modifications ont été enregistrées.',
        });
      },
      error: (err: unknown) => {
        this.updateSubmitting.set(false);
        const appError = toAppError(err);
        this.updateErrorMessage.set(appError.message);
        this.lastUpdateFieldErrors = appError.fieldErrors;
      },
    });
  }

  private buildUpdateRequest(): UpdateUserRequest {
    const currentUser = this.user();
    if (currentUser === null) {
      return {};
    }
    const raw = this.form.getRawValue();
    const request: UpdateUserRequest = {};

    const trimmedFirstName = raw.firstName.trim();
    if (trimmedFirstName !== currentUser.firstName) {
      request.firstName = trimmedFirstName;
    }
    const trimmedLastName = raw.lastName.trim();
    if (trimmedLastName !== currentUser.lastName) {
      request.lastName = trimmedLastName;
    }

    const normalizedPhone = normalizeOptional(raw.phoneNumber);
    if (normalizedPhone !== currentUser.phoneNumber) {
      request.phoneNumber = normalizedPhone;
    }

    if (!sameRoleSet(raw.roles, this.roles)) {
      request.roles = raw.roles;
    }

    if (this.hasMembership) {
      const trimmedRegistrationDate = raw.registrationDate.trim();
      if (trimmedRegistrationDate !== (currentUser.registrationDate ?? '')) {
        request.registrationDate = trimmedRegistrationDate;
      }
      const normalizedExpiration = normalizeOptional(raw.memberExpirationDate);
      if (normalizedExpiration !== currentUser.memberExpirationDate) {
        request.memberExpirationDate = normalizedExpiration;
      }
      const normalizedBlockedReason = normalizeOptional(raw.blockedReason);
      if (normalizedBlockedReason !== currentUser.blockedReason) {
        request.blockedReason = normalizedBlockedReason;
      }
    }

    return request;
  }

  // ---------------------------------------------------------------
  // AccountStatus
  // ---------------------------------------------------------------

  toggleAccountStatus(): void {
    const currentUser = this.user();
    if (currentUser === null || this.userId === null) {
      return;
    }
    const next: AccountStatus = currentUser.accountStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    const verb = next === 'DISABLED' ? 'désactiver' : 'réactiver';

    this.confirmationService.confirm({
      header: this.accountStatusActionLabel(),
      message: `Voulez-vous vraiment ${verb} ce compte ?`,
      accept: () => this.performAccountStatusChange(next),
    });
  }

  private performAccountStatusChange(next: AccountStatus): void {
    if (this.userId === null) {
      return;
    }
    this.accountStatusSubmitting.set(true);
    this.userApiService.updateAccountStatus(this.userId, { status: next }).subscribe({
      next: (response) => {
        this.accountStatusSubmitting.set(false);
        this.user.set(response);
        this.messageService.add({
          severity: 'success',
          summary: 'Statut du compte modifié',
          detail: `Le compte est maintenant ${response.accountStatus === 'ACTIVE' ? 'actif' : 'désactivé'}.`,
        });
      },
      error: (err: unknown) => {
        this.accountStatusSubmitting.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  // ---------------------------------------------------------------
  // Membership — block/unblock/reactivate
  // ---------------------------------------------------------------

  openBlockDialog(): void {
    this.blockReasonControl.setValue(this.user()?.blockedReason ?? '');
    this.blockReasonControl.markAsUntouched();
    this.blockDialogVisible.set(true);
  }

  cancelBlock(): void {
    this.blockDialogVisible.set(false);
  }

  confirmBlock(): void {
    if (this.blockSubmitting() || this.userId === null) {
      return;
    }
    if (this.blockReasonControl.invalid) {
      this.blockReasonControl.markAsTouched();
      return;
    }
    this.blockSubmitting.set(true);
    this.userApiService.blockMembership(this.userId, { blockedReason: this.blockReasonControl.value }).subscribe({
      next: (response) => {
        this.blockSubmitting.set(false);
        this.blockDialogVisible.set(false);
        this.user.set(response);
        this.resetFormFromUser(response, this.roles);
        this.messageService.add({
          severity: 'success',
          summary: 'Adhérent bloqué',
          detail: 'Le motif de blocage a été enregistré.',
        });
      },
      error: (err: unknown) => {
        this.blockSubmitting.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  confirmUnblock(): void {
    this.confirmationService.confirm({
      header: 'Débloquer',
      message: 'Voulez-vous débloquer cet adhérent ?',
      accept: () => this.performUnblock(),
    });
  }

  private performUnblock(): void {
    if (this.userId === null) {
      return;
    }
    this.membershipActionSubmitting.set(true);
    this.userApiService.unblockMembership(this.userId).subscribe({
      next: (response) => {
        this.membershipActionSubmitting.set(false);
        this.user.set(response);
        this.resetFormFromUser(response, this.roles);
        this.messageService.add({
          severity: 'success',
          summary: 'Adhérent débloqué',
          detail: "Le blocage de l'adhérent a été levé.",
        });
      },
      error: (err: unknown) => {
        this.membershipActionSubmitting.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  confirmReactivate(): void {
    this.confirmationService.confirm({
      header: 'Réactiver',
      message: "Voulez-vous réactiver l'adhésion de cet utilisateur ?",
      accept: () => this.performReactivate(),
    });
  }

  private performReactivate(): void {
    if (this.userId === null) {
      return;
    }
    this.membershipActionSubmitting.set(true);
    this.userApiService.reactivateMembership(this.userId).subscribe({
      next: (response) => {
        this.membershipActionSubmitting.set(false);
        this.user.set(response);
        this.resetFormFromUser(response, this.roles);
        this.messageService.add({
          severity: 'success',
          summary: 'Adhésion réactivée',
          detail: "L'adhésion est de nouveau active.",
        });
      },
      error: (err: unknown) => {
        this.membershipActionSubmitting.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }
}

/**
 * `null` si l'identifiant de route est absent ou n'est pas un entier —
 * n'appelle alors jamais une API avec `NaN`.
 */
function parseUserId(rawId: string | null): number | null {
  if (rawId === null) {
    return null;
  }
  const id = Number(rawId);
  return Number.isInteger(id) ? id : null;
}
