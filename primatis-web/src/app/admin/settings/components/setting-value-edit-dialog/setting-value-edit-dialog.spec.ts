import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { SettingResponse } from '../../../../settings/models/setting-response';
import { SettingApiService } from '../../../../settings/services/setting-api.service';
import { SettingValueEditDialog } from './setting-value-edit-dialog';

function buildSetting(overrides: Partial<SettingResponse> = {}): SettingResponse {
  return {
    settingKey: 'LOAN_DUE_SOON_DAYS',
    settingValue: '3',
    valueType: 'INTEGER',
    description: "Nombre de jours avant échéance déclenchant la notification.",
    updatedAt: null,
    updatedByUser: null,
    ...overrides,
  };
}

function apiHttpError(status: number, code: string, message: string, fieldErrors: { field: string; message: string }[] = []): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: {
      timestamp: new Date().toISOString(),
      status,
      error: 'Error',
      code,
      message,
      path: '/api/v1/settings/LOAN_DUE_SOON_DAYS',
      fieldErrors,
    },
  });
}

describe('SettingValueEditDialog', () => {
  let fixture: ComponentFixture<SettingValueEditDialog>;
  let component: SettingValueEditDialog;
  let settingApiServiceMock: { updateSettingValue: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  function configure(): void {
    settingApiServiceMock = { updateSettingValue: vi.fn().mockReturnValue(of(buildSetting())) };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [SettingValueEditDialog],
      providers: [
        { provide: SettingApiService, useValue: settingApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });
  }

  function createComponent(setting: SettingResponse | null, visible = true): void {
    fixture = TestBed.createComponent(SettingValueEditDialog);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('setting', setting);
    fixture.componentRef.setInput('visible', visible);
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  // ---------------------------------------------------------------
  // Préremplissage / champs non modifiables
  // ---------------------------------------------------------------

  it('should prefill the value field from the setting', () => {
    createComponent(buildSetting({ settingValue: '3' }));

    expect(component.form.controls.settingValue.value).toBe('3');
  });

  it('should never expose settingKey, valueType or description as form controls', () => {
    createComponent(buildSetting());

    expect(Object.keys(component.form.controls)).toEqual(['settingValue']);
  });

  it('should render settingKey, valueType and description as read-only text', () => {
    createComponent(buildSetting({ settingKey: 'LOAN_DUE_SOON_DAYS', valueType: 'INTEGER', description: 'Une description.' }));

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('LOAN_DUE_SOON_DAYS');
    expect(text).toContain('INTEGER');
    expect(text).toContain('Une description.');
    expect(fixture.nativeElement.querySelector('input#setting-key')).toBeNull();
  });

  // ---------------------------------------------------------------
  // Validation — INTEGER
  // ---------------------------------------------------------------

  it('should reject an empty INTEGER value', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('');

    expect(component.form.controls.settingValue.hasError('required')).toBe(true);
  });

  it('should reject a non-numeric INTEGER value', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('abc');

    expect(component.form.controls.settingValue.hasError('notInteger')).toBe(true);
  });

  it('should reject a decimal value for an INTEGER setting', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('3.5');

    expect(component.form.controls.settingValue.hasError('notInteger')).toBe(true);
  });

  it('should reject zero for an INTEGER value', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('0');

    expect(component.form.controls.settingValue.hasError('notPositive')).toBe(true);
  });

  it('should reject a negative INTEGER value', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('-1');

    expect(component.form.controls.settingValue.hasError('notPositive')).toBe(true);
  });

  it('should accept a valid positive INTEGER value', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('5');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  it('should never impose an upper bound on a valid INTEGER value', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('999999999');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  /**
   * `Integer.parseInt("+15")` réussit (signe `+` accepté depuis Java 7,
   * vérifié empiriquement DEV-12.3 complément) : le frontend ne doit pas
   * rejeter silencieusement une forme que le backend accepte.
   */
  it('should accept an explicit + sign for a positive INTEGER value', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('+15');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  it('should reject the literal string NaN for an INTEGER value', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('NaN');

    expect(component.form.controls.settingValue.hasError('notInteger')).toBe(true);
  });

  it('should reject the literal string Infinity for an INTEGER value', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('Infinity');

    expect(component.form.controls.settingValue.hasError('notInteger')).toBe(true);
  });

  it('should reject hexadecimal notation for an INTEGER value (not accepted by Integer.parseInt)', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('0x10');

    expect(component.form.controls.settingValue.hasError('notInteger')).toBe(true);
  });

  it('should reject a double leading sign for an INTEGER value', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('--1');

    expect(component.form.controls.settingValue.hasError('notInteger')).toBe(true);
  });

  // ---------------------------------------------------------------
  // Validation — DECIMAL
  // ---------------------------------------------------------------

  it('should reject an empty DECIMAL value', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('');

    expect(component.form.controls.settingValue.hasError('required')).toBe(true);
  });

  it('should reject a non-numeric DECIMAL value', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('abc');

    expect(component.form.controls.settingValue.hasError('notDecimal')).toBe(true);
  });

  it('should reject zero for a DECIMAL value', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('0.00');

    expect(component.form.controls.settingValue.hasError('notPositive')).toBe(true);
  });

  it('should reject a negative DECIMAL value', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('-0.5');

    expect(component.form.controls.settingValue.hasError('notPositive')).toBe(true);
  });

  it('should accept a valid positive DECIMAL value', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('1.25');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  it('should accept a whole number as a valid DECIMAL value', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('2');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  /**
   * `new BigDecimal(".80")` réussit (Significand = `. FractionPart`,
   * vérifié empiriquement DEV-12.3 complément) : aucune partie entière
   * n'est requise avant le point.
   */
  it('should accept a leading-dot DECIMAL value (no integer part)', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('.80');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  /**
   * `new BigDecimal("1.")` réussit (Significand = `IntegerPart .
   * FractionPart_opt`, vérifié empiriquement DEV-12.3 complément) :
   * aucune décimale n'est requise après un point final.
   */
  it('should accept a trailing-dot DECIMAL value (no fraction part)', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('1.');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  /**
   * `new BigDecimal("1e2")`/`new BigDecimal("1E-2")` réussissent (notation
   * exponentielle explicitement prévue par la grammaire `BigDecimal`,
   * vérifié empiriquement DEV-12.3 complément).
   */
  it('should accept exponent notation for a DECIMAL value', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('1e2');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  it('should accept exponent notation with a negative exponent for a DECIMAL value', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('1E-2');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  it('should accept an explicit + sign for a positive DECIMAL value', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('+0.5');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  /**
   * `new BigDecimal("1e999").signum() > 0` est vrai (DEV-12.3 second
   * complément, `SignCheck.java`) : aucune borne de magnitude n'existe côté
   * backend pour un DECIMAL syntaxiquement valide.
   */
  it('should accept a very large positive DECIMAL value in exponent notation (1e999)', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('1e999');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  /**
   * `Number("1e-999") === 0` (sous-dépassement IEEE 754) alors que `new
   * BigDecimal("1e-999").signum() > 0` est vrai (DEV-12.3 second
   * complément, `SignCheck.java`) — preuve directe que la positivité ne
   * doit jamais être déterminée par conversion `number`.
   */
  it('should accept a very small positive DECIMAL value in exponent notation (1e-999), never underflowing to zero', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('1e-999');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  it('should accept a very small positive DECIMAL value in plain decimal notation', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue(
      '0.00000000000000000000000000000000000000000000000001',
    );

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  it('should accept a very small positive DECIMAL value with an explicit + sign (+1e-999)', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('+1e-999');

    expect(component.form.controls.settingValue.valid).toBe(true);
  });

  /**
   * `new BigDecimal("0e999").signum() == 0` (DEV-12.3 second complément) :
   * un significande composé uniquement de zéros reste zéro quel que soit
   * l'exposant — jamais strictement positif.
   */
  it('should reject 0e999 as zero, regardless of the exponent', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('0e999');

    expect(component.form.controls.settingValue.hasError('notPositive')).toBe(true);
  });

  it('should reject -1e-999 as negative, regardless of the exponent magnitude', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('-1e-999');

    expect(component.form.controls.settingValue.hasError('notPositive')).toBe(true);
  });

  it('should reject the literal string NaN for a DECIMAL value (BigDecimal has no NaN concept)', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('NaN');

    expect(component.form.controls.settingValue.hasError('notDecimal')).toBe(true);
  });

  it('should reject the literal string Infinity for a DECIMAL value (BigDecimal has no Infinity concept)', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('Infinity');

    expect(component.form.controls.settingValue.hasError('notDecimal')).toBe(true);
  });

  it('should reject hexadecimal notation for a DECIMAL value (not accepted by BigDecimal)', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('0x10');

    expect(component.form.controls.settingValue.hasError('notDecimal')).toBe(true);
  });

  it('should reject a double leading sign for a DECIMAL value', () => {
    createComponent(buildSetting({ valueType: 'DECIMAL', settingValue: '0.80' }));
    component.form.controls.settingValue.setValue('--1');

    expect(component.form.controls.settingValue.hasError('notDecimal')).toBe(true);
  });

  // ---------------------------------------------------------------
  // Sauvegarde
  // ---------------------------------------------------------------

  it('should never call the API and mark the control touched when submitting an invalid form', () => {
    createComponent(buildSetting({ valueType: 'INTEGER' }));
    component.form.controls.settingValue.setValue('-1');

    component.submit();

    expect(settingApiServiceMock.updateSettingValue).not.toHaveBeenCalled();
    expect(component.form.controls.settingValue.touched).toBe(true);
  });

  it('should PATCH with the trimmed value on submit', () => {
    createComponent(buildSetting({ settingKey: 'LOAN_DUE_SOON_DAYS' }));
    component.form.controls.settingValue.setValue('  5  ');

    component.submit();

    expect(settingApiServiceMock.updateSettingValue).toHaveBeenCalledWith('LOAN_DUE_SOON_DAYS', {
      settingValue: '5',
    });
  });

  it('should set the submitting state while the request is pending, disabling the submit button', () => {
    settingApiServiceMock.updateSettingValue.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent(buildSetting());

    component.submit();
    fixture.detectChanges();

    expect(component.submitting()).toBe(true);
    const submitButton = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(submitButton.disabled).toBe(true);
  });

  it('should emit saved with the exact backend response and show a success toast', () => {
    const updated = buildSetting({ settingValue: '5' });
    settingApiServiceMock.updateSettingValue.mockReturnValue(of(updated));
    createComponent(buildSetting());
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).toHaveBeenCalledWith(updated);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show the backend error message, keep the form open and never emit saved on failure', () => {
    settingApiServiceMock.updateSettingValue.mockReturnValue(
      throwError(() => apiHttpError(409, 'SETTING_VALUE_NOT_POSITIVE', 'La valeur doit être strictement positive.')),
    );
    createComponent(buildSetting());
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(component.errorMessage()).toBe('La valeur doit être strictement positive.');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(savedSpy).not.toHaveBeenCalled();
  });

  it('should present SETTING_NOT_FOUND from the backend without swallowing it', () => {
    settingApiServiceMock.updateSettingValue.mockReturnValue(
      throwError(() => apiHttpError(404, 'SETTING_NOT_FOUND', 'Aucun paramètre applicatif pour cette clé.')),
    );
    createComponent(buildSetting());

    component.submit();

    expect(component.errorMessage()).toBe('Aucun paramètre applicatif pour cette clé.');
  });

  it('should present a VALIDATION_FAILED field error inline on the settingValue field', () => {
    settingApiServiceMock.updateSettingValue.mockReturnValue(
      throwError(() =>
        apiHttpError(400, 'VALIDATION_FAILED', 'Un ou plusieurs champs sont invalides.', [
          { field: 'settingValue', message: 'settingValue ne doit pas être vide.' },
        ]),
      ),
    );
    createComponent(buildSetting());

    component.submit();

    expect(component.fieldError('settingValue')).toBe('settingValue ne doit pas être vide.');
  });

  it('should emit closed and never call the API when cancelling', () => {
    createComponent(buildSetting());
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.cancel();

    expect(closedSpy).toHaveBeenCalledTimes(1);
    expect(settingApiServiceMock.updateSettingValue).not.toHaveBeenCalled();
  });
});
