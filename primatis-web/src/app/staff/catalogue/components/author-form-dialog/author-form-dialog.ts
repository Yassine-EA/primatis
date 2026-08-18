import { Component, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { AuthorResponse } from '../../../../catalogue/models/author-response';
import { CreateAuthorRequest } from '../../../../catalogue/models/create-author-request';
import { UpdateAuthorRequest } from '../../../../catalogue/models/update-author-request';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { normalizeOptional } from '../../form-value-normalization';

/**
 * Dialog de création/correction d'un Author (`CATALOGUE_MANAGE`), réutilisé
 * par `AuthorPicker` en mode création (`author() === null`) et correction.
 * `fullName` n'est jamais traité comme une clé unique — aucune validation
 * d'unicité, aucune vérification croisée `birthDate`/`deathDate` : ces
 * règles restent l'autorité exclusive du backend (409 affiché tel quel).
 */
@Component({
  selector: 'app-author-form-dialog',
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, MessageModule],
  templateUrl: './author-form-dialog.html',
  styleUrl: './author-form-dialog.scss',
})
export class AuthorFormDialog {
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();
  readonly author = input<AuthorResponse | null>(null);

  readonly closed = output<void>();
  readonly saved = output<AuthorResponse>();

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private lastFieldErrors: readonly FieldError[] = [];

  readonly form = this.formBuilder.group({
    fullName: this.formBuilder.control('', [Validators.required]),
    birthDate: this.formBuilder.control(''),
    deathDate: this.formBuilder.control(''),
    nationality: this.formBuilder.control(''),
    biography: this.formBuilder.control(''),
  });

  constructor() {
    effect(() => {
      const current = this.author();
      if (!this.visible()) {
        return;
      }
      this.form.reset({
        fullName: current?.fullName ?? '',
        birthDate: current?.birthDate ?? '',
        deathDate: current?.deathDate ?? '',
        nationality: current?.nationality ?? '',
        biography: current?.biography ?? '',
      });
      this.errorMessage.set(null);
      this.lastFieldErrors = [];
    });
  }

  get isCreate(): boolean {
    return this.author() === null;
  }

  fieldError(field: string): string | undefined {
    return this.lastFieldErrors.find((fieldError) => fieldError.field === field)?.message;
  }

  cancel(): void {
    this.closed.emit();
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

    if (this.isCreate) {
      this.staffCatalogueApiService.createAuthor(this.buildCreateRequest()).subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.messageService.add({ severity: 'success', summary: 'Auteur créé', detail: response.fullName });
          this.saved.emit(response);
        },
        error: (err: unknown) => this.handleError(err),
      });
      return;
    }

    const current = this.author();
    if (current === null) {
      this.submitting.set(false);
      return;
    }
    const request = this.buildUpdateRequest(current);
    if (Object.keys(request).length === 0) {
      this.submitting.set(false);
      this.closed.emit();
      return;
    }
    this.staffCatalogueApiService.updateAuthor(current.id, request).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.messageService.add({ severity: 'success', summary: 'Auteur modifié', detail: response.fullName });
        this.saved.emit(response);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  private buildCreateRequest(): CreateAuthorRequest {
    const raw = this.form.getRawValue();
    const request: CreateAuthorRequest = { fullName: raw.fullName.trim() };
    const normalizedBirthDate = normalizeOptional(raw.birthDate);
    if (normalizedBirthDate !== null) {
      request.birthDate = normalizedBirthDate;
    }
    const normalizedDeathDate = normalizeOptional(raw.deathDate);
    if (normalizedDeathDate !== null) {
      request.deathDate = normalizedDeathDate;
    }
    const normalizedNationality = normalizeOptional(raw.nationality);
    if (normalizedNationality !== null) {
      request.nationality = normalizedNationality;
    }
    const normalizedBiography = normalizeOptional(raw.biography);
    if (normalizedBiography !== null) {
      request.biography = normalizedBiography;
    }
    return request;
  }

  private buildUpdateRequest(current: AuthorResponse): UpdateAuthorRequest {
    const raw = this.form.getRawValue();
    const request: UpdateAuthorRequest = {};

    const trimmedFullName = raw.fullName.trim();
    if (trimmedFullName !== current.fullName) {
      request.fullName = trimmedFullName;
    }
    const normalizedBirthDate = normalizeOptional(raw.birthDate);
    if (normalizedBirthDate !== current.birthDate) {
      request.birthDate = normalizedBirthDate;
    }
    const normalizedDeathDate = normalizeOptional(raw.deathDate);
    if (normalizedDeathDate !== current.deathDate) {
      request.deathDate = normalizedDeathDate;
    }
    const normalizedNationality = normalizeOptional(raw.nationality);
    if (normalizedNationality !== current.nationality) {
      request.nationality = normalizedNationality;
    }
    const normalizedBiography = normalizeOptional(raw.biography);
    if (normalizedBiography !== current.biography) {
      request.biography = normalizedBiography;
    }

    return request;
  }

  private handleError(err: unknown): void {
    this.submitting.set(false);
    const appError = toAppError(err);
    this.errorMessage.set(appError.message);
    this.lastFieldErrors = appError.fieldErrors;
    this.messageService.add({ severity: 'error', summary: 'Erreur', detail: appError.message });
  }
}
