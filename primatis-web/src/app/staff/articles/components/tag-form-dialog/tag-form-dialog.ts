import { Component, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { CreateTagRequest } from '../../../../articles/models/create-tag-request';
import { TagResponse } from '../../../../articles/models/tag-response';
import { UpdateTagRequest } from '../../../../articles/models/update-tag-request';
import { StaffTagApiService } from '../../../../articles/services/staff-tag-api.service';
import { normalizeOptional } from '../../form-value-normalization';

/**
 * Dialog de création/correction d'un Tag (`ARTICLE_MANAGE`, DEV-11.9/
 * DEV-11.12), réutilisé par `StaffTagsPage`. Précédent structurel exact
 * `GenreFormDialog` (staff/catalogue), avec une divergence délibérée :
 * `code` est structurellement immuable après création (business-rules.md
 * §7.13 : « Tag.code remains required, unique, stable ») — désactivé
 * (`disable()`) en édition, jamais envoyé dans `UpdateTagRequest` (qui ne
 * porte d'ailleurs aucun champ correspondant). `@Size` alignés sur les
 * colonnes réelles (`code` 50, `label` 100, `description` 255, V001).
 */
@Component({
  selector: 'app-tag-form-dialog',
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, MessageModule],
  templateUrl: './tag-form-dialog.html',
  styleUrl: './tag-form-dialog.scss',
})
export class TagFormDialog {
  private readonly staffTagApiService = inject(StaffTagApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();
  readonly tag = input<TagResponse | null>(null);

  readonly closed = output<void>();
  readonly saved = output<TagResponse>();

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private lastFieldErrors: readonly FieldError[] = [];

  readonly form = this.formBuilder.group({
    code: this.formBuilder.control('', [Validators.required, Validators.maxLength(50)]),
    label: this.formBuilder.control('', [Validators.required, Validators.maxLength(100)]),
    description: this.formBuilder.control('', [Validators.maxLength(255)]),
  });

  constructor() {
    effect(() => {
      const current = this.tag();
      if (!this.visible()) {
        return;
      }
      this.form.reset({
        code: current?.code ?? '',
        label: current?.label ?? '',
        description: current?.description ?? '',
      });
      if (this.isCreate) {
        this.form.controls.code.enable();
      } else {
        this.form.controls.code.disable();
      }
      this.errorMessage.set(null);
      this.lastFieldErrors = [];
    });
  }

  get isCreate(): boolean {
    return this.tag() === null;
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
      const request: CreateTagRequest = { code: raw.code.trim(), label: raw.label.trim() };
      const normalizedDescription = normalizeOptional(raw.description);
      if (normalizedDescription !== null) {
        request.description = normalizedDescription;
      }
      this.staffTagApiService.createTag(request).subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.messageService.add({ severity: 'success', summary: 'Tag créé', detail: response.label });
          this.saved.emit(response);
        },
        error: (err: unknown) => this.handleError(err),
      });
      return;
    }

    const current = this.tag();
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
    this.staffTagApiService.updateTag(current.id, request).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.messageService.add({ severity: 'success', summary: 'Tag modifié', detail: response.label });
        this.saved.emit(response);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  private buildUpdateRequest(current: TagResponse): UpdateTagRequest {
    const raw = this.form.getRawValue();
    const request: UpdateTagRequest = {};

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
