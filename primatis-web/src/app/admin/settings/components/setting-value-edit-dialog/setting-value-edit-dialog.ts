import { Component, effect, inject, input, output, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { SettingResponse } from '../../../../settings/models/setting-response';
import { UpdateSettingValueRequest } from '../../../../settings/models/update-setting-value-request';
import { SettingApiService } from '../../../../settings/services/setting-api.service';

const DECIMAL_GRAMMAR = /^([+-]?)(\d+(?:\.\d*)?|\.\d+)([eE][+-]?\d+)?$/;

/**
 * `INTEGER` : signe optionnel (`+`/`-`) suivi d'un ou plusieurs chiffres,
 * strictement positif. Reproduit exactement la grammaire acceptée par
 * `Integer.parseInt(String)` côté backend
 * (`ApplicationSettingService#validatePositiveInteger`, DEV-12.2) — y
 * compris le signe `+` explicite (accepté par `Integer.parseInt` depuis
 * Java 7) et les zéros non significatifs (`0015`), vérifié empiriquement
 * (DEV-12.3 complément, `ParseCheck.java`). Rejette explicitement toute
 * forme décimale/exponentielle (`3.5`, `1.`, `1e2`), la notation
 * hexadécimale (`0x10`) et un double signe (`--1`) — aucune de ces formes
 * n'est acceptée par `Integer.parseInt` non plus.
 *
 * <p>Positivité vérifiée via `Number(raw) > 0` : aucune notation
 * exponentielle n'est admise par la grammaire `^[+-]?\d+$` ci-dessous, donc
 * aucun risque de sous-dépassement (`underflow`) vers `0` — contrairement
 * à `DECIMAL` (voir {@link isPositiveDecimalLexically}), une chaîne de
 * chiffres pure reste fidèlement convertie par `Number()` quel que soit
 * son nombre de chiffres (vérifié DEV-12.3 second complément : aucune
 * valeur acceptée par `Integer.parseInt` n'est rejetée à tort ici).
 */
function positiveIntegerValidator(control: AbstractControl): ValidationErrors | null {
  const raw = (control.value ?? '').trim();
  if (raw === '') {
    return null;
  }
  if (!/^[+-]?\d+$/.test(raw)) {
    return { notInteger: true };
  }
  return Number(raw) > 0 ? null : { notPositive: true };
}

/**
 * Détermine la positivité d'une chaîne déjà validée par {@link
 * DECIMAL_GRAMMAR} **sans conversion vers `number`** — `Number(raw) > 0`
 * sous-dépasse silencieusement vers `0` pour un exposant très négatif
 * (`Number("1e-999") === 0`, vérifié DEV-12.3 second complément), ce qui
 * rejetterait à tort une valeur que `new BigDecimal(...).signum() > 0`
 * accepte réellement (`BigDecimal` est un type décimal exact, sans notion
 * de sous/dépassement IEEE 754). Détermination purement lexicale, alignée
 * sur `BigDecimal.signum()` :
 *
 * - signe `-` → négative, jamais positive ;
 * - le significande (partie chiffres, hors signe et exposant) contient au
 *   moins un chiffre non nul → strictement positive, quel que soit
 *   l'exposant (`1e999`, `1e-999` sont tous deux strictement positifs) ;
 * - significande composée uniquement de zéros (`0`, `0.0`, `.0`) → zéro,
 *   jamais positive, quel que soit l'exposant (`0e999` reste zéro).
 */
function isPositiveDecimalLexically(raw: string): boolean {
  const match = DECIMAL_GRAMMAR.exec(raw);
  if (match === null) {
    return false;
  }
  const [, sign, significand] = match;
  if (sign === '-') {
    return false;
  }
  return /[1-9]/.test(significand);
}

/**
 * `DECIMAL` : reproduit exactement la grammaire acceptée par le
 * constructeur `BigDecimal(String)` côté backend
 * (`ApplicationSettingService#validatePositiveDecimal`, DEV-12.2, `new
 * BigDecimal(value)` appelé sur la valeur déjà trimée) — vérifié
 * empiriquement (DEV-12.3 complément, `ParseCheck.java`) : signe optionnel
 * (`+`/`-`), partie entière ou décimale seule autorisée (`.80`), point
 * final sans décimale autorisé (`1.`), notation exponentielle autorisée
 * (`1e2`, `1E-2`, y compris de très grande magnitude comme `1e999`).
 * `NaN`/`Infinity`/notation hexadécimale/double signe restent
 * explicitement rejetés — `BigDecimal` ne les accepte pas non plus (aucun
 * concept d'infini/NaN pour un type décimal exact). Positivité déterminée
 * lexicalement par {@link isPositiveDecimalLexically}, jamais par
 * conversion `number` (aucune borne de magnitude/précision introduite ici
 * que le backend `BigDecimal`, arbitraire, n'aurait pas).
 */
function positiveDecimalValidator(control: AbstractControl): ValidationErrors | null {
  const raw = (control.value ?? '').trim();
  if (raw === '') {
    return null;
  }
  if (!DECIMAL_GRAMMAR.test(raw)) {
    return { notDecimal: true };
  }
  return isPositiveDecimalLexically(raw) ? null : { notPositive: true };
}

/**
 * Dialog de modification de la seule `settingValue` d'un paramètre
 * applicatif existant (DEV-12.3, `SETTING_MANAGE`). Précédent structurel
 * exact : `TagFormDialog` (staff/articles), en plus simple — pas de mode
 * création (Settings ne se créent jamais depuis l'UI, DEV-12.1 §6.2/
 * DEV-12.2 mandat §5). `settingKey`/`valueType`/`description` ne sont
 * jamais des champs de formulaire, seulement affichés en lecture seule par
 * le template — jamais envoyés dans `UpdateSettingValueRequest`.
 *
 * <p>Validation locale alignée sur `ApplicationSettingService` (INTEGER/
 * DECIMAL strictement positif, aucune borne supplémentaire, aucune
 * cohérence croisée entre clés) — le backend reste l'autorité finale.
 */
@Component({
  selector: 'app-setting-value-edit-dialog',
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, MessageModule],
  templateUrl: './setting-value-edit-dialog.html',
  styleUrl: './setting-value-edit-dialog.scss',
})
export class SettingValueEditDialog {
  private readonly settingApiService = inject(SettingApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();
  readonly setting = input<SettingResponse | null>(null);

  readonly closed = output<void>();
  readonly saved = output<SettingResponse>();

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private lastFieldErrors: readonly FieldError[] = [];

  readonly form = this.formBuilder.group({
    settingValue: this.formBuilder.control('', [Validators.required]),
  });

  constructor() {
    effect(() => {
      const current = this.setting();
      if (!this.visible() || current === null) {
        return;
      }
      this.form.reset({ settingValue: current.settingValue });
      this.form.controls.settingValue.clearValidators();
      this.form.controls.settingValue.addValidators(
        current.valueType === 'DECIMAL'
          ? [Validators.required, positiveDecimalValidator]
          : [Validators.required, positiveIntegerValidator],
      );
      this.form.controls.settingValue.updateValueAndValidity();
      this.errorMessage.set(null);
      this.lastFieldErrors = [];
    });
  }

  fieldError(field: string): string | undefined {
    return this.lastFieldErrors.find((fieldError) => fieldError.field === field)?.message;
  }

  cancel(): void {
    this.closed.emit();
  }

  submit(): void {
    const current = this.setting();
    if (this.submitting() || current === null) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.lastFieldErrors = [];

    const request: UpdateSettingValueRequest = { settingValue: this.form.getRawValue().settingValue.trim() };
    this.settingApiService.updateSettingValue(current.settingKey, request).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Paramètre modifié',
          detail: `${response.settingKey} a été mis à jour.`,
        });
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
