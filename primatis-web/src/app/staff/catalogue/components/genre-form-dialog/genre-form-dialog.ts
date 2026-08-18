import { Component, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { CreateGenreRequest } from '../../../../catalogue/models/create-genre-request';
import { GenreResponse } from '../../../../catalogue/models/genre-response';
import { UpdateGenreRequest } from '../../../../catalogue/models/update-genre-request';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { normalizeOptional } from '../../form-value-normalization';

/**
 * Dialog de création/correction d'un Genre (`CATALOGUE_MANAGE`), réutilisé
 * par `GenrePicker`. `code`/`label` uniques : jamais revalidé côté
 * frontend, le 409 backend (`GENRE_CODE_ALREADY_EXISTS`/
 * `GENRE_LABEL_ALREADY_EXISTS`) est affiché tel quel.
 */
@Component({
  selector: 'app-genre-form-dialog',
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, MessageModule],
  templateUrl: './genre-form-dialog.html',
  styleUrl: './genre-form-dialog.scss',
})
export class GenreFormDialog {
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();
  readonly genre = input<GenreResponse | null>(null);

  readonly closed = output<void>();
  readonly saved = output<GenreResponse>();

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private lastFieldErrors: readonly FieldError[] = [];

  readonly form = this.formBuilder.group({
    code: this.formBuilder.control('', [Validators.required]),
    label: this.formBuilder.control('', [Validators.required]),
    description: this.formBuilder.control(''),
  });

  constructor() {
    effect(() => {
      const current = this.genre();
      if (!this.visible()) {
        return;
      }
      this.form.reset({
        code: current?.code ?? '',
        label: current?.label ?? '',
        description: current?.description ?? '',
      });
      this.errorMessage.set(null);
      this.lastFieldErrors = [];
    });
  }

  get isCreate(): boolean {
    return this.genre() === null;
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
      const raw = this.form.getRawValue();
      const request: CreateGenreRequest = { code: raw.code.trim(), label: raw.label.trim() };
      const normalizedDescription = normalizeOptional(raw.description);
      if (normalizedDescription !== null) {
        request.description = normalizedDescription;
      }
      this.staffCatalogueApiService.createGenre(request).subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.messageService.add({ severity: 'success', summary: 'Genre créé', detail: response.label });
          this.saved.emit(response);
        },
        error: (err: unknown) => this.handleError(err),
      });
      return;
    }

    const current = this.genre();
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
    this.staffCatalogueApiService.updateGenre(current.id, request).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.messageService.add({ severity: 'success', summary: 'Genre modifié', detail: response.label });
        this.saved.emit(response);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  private buildUpdateRequest(current: GenreResponse): UpdateGenreRequest {
    const raw = this.form.getRawValue();
    const request: UpdateGenreRequest = {};

    const trimmedCode = raw.code.trim();
    if (trimmedCode !== current.code) {
      request.code = trimmedCode;
    }
    const trimmedLabel = raw.label.trim();
    if (trimmedLabel !== current.label) {
      request.label = trimmedLabel;
    }
    const normalizedDescription = normalizeOptional(raw.description);
    if (normalizedDescription !== current.description) {
      request.description = normalizedDescription;
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
