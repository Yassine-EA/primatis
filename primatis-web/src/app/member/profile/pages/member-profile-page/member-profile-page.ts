import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { MemberStatus } from '../../../../user/models/member-status';
import { MeProfileResponse } from '../../../../user/models/me-profile-response';
import { ResidenceResponse } from '../../../../user/models/residence-response';
import { MeProfileApiService } from '../../../../user/services/me-profile-api.service';
import { ResidenceApiService } from '../../../../user/services/residence-api.service';

const CURRENT_RESIDENCE_NOT_FOUND_CODE = 'CURRENT_RESIDENCE_NOT_FOUND';

/**
 * Profil personnel de l'utilisateur authentifié (DEV-05.13, `ROLE_MEMBER`
 * via `roleGuard` — restriction UX uniquement, le backend `/api/v1/me/**`
 * reste accessible à tout `AppUser` authentifié). Consultation complète
 * (identité, adhésion, résidence courante), seul `phoneNumber` est
 * modifiable (contrat `/me/profile`, DEV-05.9 — jamais de sémantique de
 * clear pour ce champ). Aucune action `AccountStatus`/Membership, aucune
 * édition Residence, aucun historique (`GAP-05.13-01`).
 */
@Component({
  selector: 'app-member-profile-page',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, MessageModule, TagModule, LoadingState, ErrorState],
  templateUrl: './member-profile-page.html',
  styleUrl: './member-profile-page.scss',
})
export class MemberProfilePage {
  private readonly meProfileApiService = inject(MeProfileApiService);
  private readonly residenceApiService = inject(ResidenceApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly messageService = inject(MessageService);

  readonly profile = signal<MeProfileResponse | null>(null);
  readonly profileLoading = signal(true);
  readonly profileError = signal<AppError | null>(null);

  readonly currentResidence = signal<ResidenceResponse | null>(null);
  readonly residenceLoading = signal(true);
  readonly residenceError = signal<AppError | null>(null);

  readonly phoneForm = this.formBuilder.group({
    phoneNumber: this.formBuilder.control(''),
  });
  readonly phoneSubmitting = signal(false);
  readonly phoneErrorMessage = signal<string | null>(null);

  private phoneBaseline = '';

  constructor() {
    this.loadProfile();
    this.loadResidence();
  }

  get isPhoneUnchanged(): boolean {
    return this.phoneForm.controls.phoneNumber.value.trim() === this.phoneBaseline;
  }

  get isPhoneBlank(): boolean {
    return this.phoneForm.controls.phoneNumber.value.trim().length === 0;
  }

  get isPhoneSubmitDisabled(): boolean {
    return this.phoneSubmitting() || this.isPhoneBlank || this.isPhoneUnchanged;
  }

  memberStatusSeverity(status: MemberStatus): 'success' | 'danger' | 'warn' {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'BLOCKED':
        return 'danger';
      case 'EXPIRED':
        return 'warn';
    }
  }

  submitPhone(): void {
    if (this.isPhoneSubmitDisabled) {
      return;
    }
    this.phoneSubmitting.set(true);
    this.phoneErrorMessage.set(null);

    const phoneNumber = this.phoneForm.controls.phoneNumber.value.trim();
    this.meProfileApiService.updateProfile({ phoneNumber }).subscribe({
      next: (response) => {
        this.phoneSubmitting.set(false);
        this.profile.set(response);
        this.resetPhoneForm(response);
        this.messageService.add({
          severity: 'success',
          summary: 'Téléphone mis à jour',
          detail: 'Votre numéro de téléphone a été enregistré.',
        });
      },
      error: (err: unknown) => {
        this.phoneSubmitting.set(false);
        this.phoneErrorMessage.set(toAppError(err).message);
      },
    });
  }

  private loadProfile(): void {
    this.profileLoading.set(true);
    this.profileError.set(null);
    this.meProfileApiService.getProfile().subscribe({
      next: (value) => {
        this.profile.set(value);
        this.resetPhoneForm(value);
        this.profileLoading.set(false);
      },
      error: (err: unknown) => {
        this.profileLoading.set(false);
        this.profileError.set(toAppError(err));
      },
    });
  }

  private loadResidence(): void {
    this.residenceLoading.set(true);
    this.residenceError.set(null);
    this.currentResidence.set(null);
    this.residenceApiService.getOwnResidence().subscribe({
      next: (value) => {
        this.currentResidence.set(value);
        this.residenceLoading.set(false);
      },
      error: (err: unknown) => {
        this.residenceLoading.set(false);
        const appError = toAppError(err);
        if (appError.code === CURRENT_RESIDENCE_NOT_FOUND_CODE) {
          // État normal (même principe que StaffUserDetailPage/AdminUserDetailPage) :
          // pas de résidence courante, jamais un ErrorState pour ce cas précis.
          this.currentResidence.set(null);
          return;
        }
        this.residenceError.set(appError);
      },
    });
  }

  private resetPhoneForm(profile: MeProfileResponse): void {
    this.phoneBaseline = profile.phoneNumber ?? '';
    this.phoneForm.reset({ phoneNumber: this.phoneBaseline });
  }
}
