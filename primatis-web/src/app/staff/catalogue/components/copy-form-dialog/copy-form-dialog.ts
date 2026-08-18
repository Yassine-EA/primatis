import { Component, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';

import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { AvailabilityStatus } from '../../../../catalogue/models/availability-status';
import { CopyCondition } from '../../../../catalogue/models/copy-condition';
import { CopyResponse } from '../../../../catalogue/models/copy-response';
import { CreateCopyRequest } from '../../../../catalogue/models/create-copy-request';
import { UpdateCopyRequest } from '../../../../catalogue/models/update-copy-request';
import { CopyApiService } from '../../../../catalogue/services/copy-api.service';
import { COPY_CONDITION_OPTIONS, CREATE_AVAILABILITY_OPTIONS } from '../../copy-options';
import { normalizeOptional } from '../../form-value-normalization';

/**
 * Dialog de création/correction d'un Copy (`COPY_MANAGE`), réutilisé par
 * `StaffTitleDetailPage`. `availabilityStatus` n'est demandé qu'à la
 * création (`AVAILABLE`/`UNAVAILABLE` uniquement — jamais `ON_LOAN`/
 * `RESERVED`, absents des options) : `UpdateCopyRequest` ne le contient
 * jamais (action dédiée `PATCH .../availability`, gérée par
 * `StaffTitleDetailPage`, pas ici). `titleId` n'est jamais un champ du
 * formulaire — fourni par le parent, immuable. Aucune anticipation locale
 * de `LOST/OUT_OF_SERVICE → UNAVAILABLE` : le `CopyResponse` retourné par
 * le backend est la seule source de vérité après enregistrement.
 */
@Component({
  selector: 'app-copy-form-dialog',
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, SelectModule, MessageModule],
  templateUrl: './copy-form-dialog.html',
  styleUrl: './copy-form-dialog.scss',
})
export class CopyFormDialog {
  private readonly copyApiService = inject(CopyApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();
  readonly titleId = input.required<number>();
  readonly copy = input<CopyResponse | null>(null);

  readonly closed = output<void>();
  readonly saved = output<CopyResponse>();

  readonly copyConditionOptions = COPY_CONDITION_OPTIONS;
  readonly availabilityOptions = CREATE_AVAILABILITY_OPTIONS;

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private lastFieldErrors: readonly FieldError[] = [];

  readonly form = this.formBuilder.group({
    inventoryCode: this.formBuilder.control('', [Validators.required]),
    location: this.formBuilder.control(''),
    copyCondition: this.formBuilder.control<CopyCondition | null>(null, [Validators.required]),
    availabilityStatus: this.formBuilder.control<AvailabilityStatus | null>(null),
  });

  constructor() {
    effect(() => {
      const current = this.copy();
      if (!this.visible()) {
        return;
      }
      this.form.reset({
        inventoryCode: current?.inventoryCode ?? '',
        location: current?.location ?? '',
        copyCondition: current?.copyCondition ?? null,
        availabilityStatus: null,
      });
      const availabilityControl = this.form.controls.availabilityStatus;
      if (current === null) {
        availabilityControl.setValidators([Validators.required]);
      } else {
        availabilityControl.clearValidators();
      }
      availabilityControl.updateValueAndValidity();
      this.errorMessage.set(null);
      this.lastFieldErrors = [];
    });
  }

  get isCreate(): boolean {
    return this.copy() === null;
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

    if (this.isCreate) {
      this.submitCreate();
      return;
    }
    this.submitUpdate();
  }

  private submitCreate(): void {
    const raw = this.form.getRawValue();
    const request: CreateCopyRequest = {
      inventoryCode: raw.inventoryCode.trim(),
      copyCondition: raw.copyCondition as CopyCondition,
      availabilityStatus: raw.availabilityStatus as AvailabilityStatus,
    };
    const normalizedLocation = normalizeOptional(raw.location);
    if (normalizedLocation !== null) {
      request.location = normalizedLocation;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.lastFieldErrors = [];

    this.copyApiService.createCopy(this.titleId(), request).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.messageService.add({ severity: 'success', summary: 'Exemplaire créé', detail: response.inventoryCode });
        this.saved.emit(response);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  private submitUpdate(): void {
    const current = this.copy();
    if (current === null) {
      return;
    }
    const raw = this.form.getRawValue();
    const request: UpdateCopyRequest = {};

    const trimmedInventoryCode = raw.inventoryCode.trim();
    if (trimmedInventoryCode !== current.inventoryCode) {
      request.inventoryCode = trimmedInventoryCode;
    }
    const normalizedLocation = normalizeOptional(raw.location);
    if (normalizedLocation !== current.location) {
      request.location = normalizedLocation;
    }
    if (raw.copyCondition !== null && raw.copyCondition !== current.copyCondition) {
      request.copyCondition = raw.copyCondition;
    }

    if (Object.keys(request).length === 0) {
      this.closed.emit();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.lastFieldErrors = [];

    this.copyApiService.updateCopy(this.titleId(), current.id, request).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.messageService.add({ severity: 'success', summary: 'Exemplaire modifié', detail: response.inventoryCode });
        this.saved.emit(response);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  private handleError(err: unknown): void {
    this.submitting.set(false);
    const appError = toAppError(err);
    this.errorMessage.set(appError.message);
    this.lastFieldErrors = appError.fieldErrors;
    this.messageService.add({ severity: 'error', summary: 'Erreur', detail: appError.message });
  }
}
