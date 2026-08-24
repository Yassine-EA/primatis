import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../../auth/services/auth.service';
import { SettingResponse } from '../../../../settings/models/setting-response';
import { SettingApiService } from '../../../../settings/services/setting-api.service';
import { AdminSettingsPage } from './admin-settings-page';

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

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { timestamp: new Date().toISOString(), status, error: 'Error', code, message, path: '/api/v1/settings', fieldErrors: [] },
  });
}

describe('AdminSettingsPage', () => {
  let fixture: ComponentFixture<AdminSettingsPage>;
  let settingApiServiceMock: { getSettings: ReturnType<typeof vi.fn>; updateSettingValue: ReturnType<typeof vi.fn> };
  let authServiceMock: { hasPermission: ReturnType<typeof vi.fn> };

  function configure(): void {
    settingApiServiceMock = { getSettings: vi.fn(), updateSettingValue: vi.fn() };
    authServiceMock = { hasPermission: vi.fn().mockReturnValue(true) };

    TestBed.configureTestingModule({
      imports: [AdminSettingsPage],
      providers: [
        { provide: SettingApiService, useValue: settingApiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: MessageService, useValue: { add: vi.fn() } },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(AdminSettingsPage);
    fixture.detectChanges();
  }

  // ---------------------------------------------------------------
  // Chargement / contrat API
  // ---------------------------------------------------------------

  it('should call getSettings on initial load', () => {
    configure();
    settingApiServiceMock.getSettings.mockReturnValue(of([buildSetting()]));

    createComponent();

    expect(settingApiServiceMock.getSettings).toHaveBeenCalledTimes(1);
  });

  it('should show the loading state before the first response arrives', () => {
    configure();
    settingApiServiceMock.getSettings.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });

    createComponent();

    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the error state on a failed GET request', () => {
    configure();
    settingApiServiceMock.getSettings.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );

    createComponent();

    expect(fixture.nativeElement.querySelector('app-error-state')).not.toBeNull();
  });

  it('should retry the request when retry is triggered', () => {
    configure();
    settingApiServiceMock.getSettings.mockReturnValue(
      throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();
    settingApiServiceMock.getSettings.mockClear();
    settingApiServiceMock.getSettings.mockReturnValue(of([buildSetting()]));

    fixture.componentInstance.retry();

    expect(settingApiServiceMock.getSettings).toHaveBeenCalledTimes(1);
  });

  it('should show the empty state defensively when the backend returns no setting, without crashing', () => {
    configure();
    settingApiServiceMock.getSettings.mockReturnValue(of([]));

    expect(() => createComponent()).not.toThrow();
    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  // ---------------------------------------------------------------
  // Affichage
  // ---------------------------------------------------------------

  it('should display settingKey, settingValue, valueType label and description', () => {
    configure();
    settingApiServiceMock.getSettings.mockReturnValue(
      of([buildSetting({ settingKey: 'FINE_WEEKLY_RATE', settingValue: '0.80', valueType: 'DECIMAL', description: 'Tarif hebdomadaire.' })]),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('FINE_WEEKLY_RATE');
    expect(text).toContain('0.80');
    expect(text).toContain('Décimal');
    expect(text).toContain('Tarif hebdomadaire.');
  });

  it('should preserve the order received from the backend', () => {
    configure();
    settingApiServiceMock.getSettings.mockReturnValue(
      of([
        buildSetting({ settingKey: 'FINE_MAX_AMOUNT' }),
        buildSetting({ settingKey: 'LOAN_DURATION_DAYS' }),
        buildSetting({ settingKey: 'RESERVATION_READY_HOLD_HOURS' }),
      ]),
    );

    createComponent();

    const cells: string[] = Array.from(fixture.nativeElement.querySelectorAll('td'))
      .map((cell) => (cell as HTMLElement).textContent?.trim() ?? '');
    const keyOrder = ['FINE_MAX_AMOUNT', 'LOAN_DURATION_DAYS', 'RESERVATION_READY_HOLD_HOURS'].filter((key) =>
      cells.includes(key),
    );
    expect(keyOrder).toEqual(['FINE_MAX_AMOUNT', 'LOAN_DURATION_DAYS', 'RESERVATION_READY_HOLD_HOURS']);
  });

  it('should display "Jamais modifié" when updatedByUser is null', () => {
    configure();
    settingApiServiceMock.getSettings.mockReturnValue(of([buildSetting({ updatedByUser: null })]));

    createComponent();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Jamais modifié');
  });

  it('should display the audit date and user name when updatedByUser is present', () => {
    configure();
    settingApiServiceMock.getSettings.mockReturnValue(
      of([
        buildSetting({
          updatedAt: '2026-08-24T10:00:00Z',
          updatedByUser: { id: 1, firstName: 'Ada', lastName: 'Lovelace' },
        }),
      ]),
    );

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('2026-08-24T10:00:00Z');
    expect(text).toContain('Ada');
    expect(text).toContain('Lovelace');
    expect(text).not.toContain('Jamais modifié');
  });

  it('should never render a create, delete or change-type action', () => {
    configure();
    settingApiServiceMock.getSettings.mockReturnValue(of([buildSetting()]));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Créer');
    expect(text).not.toContain('Supprimer');
    expect(text).not.toContain('Changer le type');
  });

  // ---------------------------------------------------------------
  // Permissions UX — SETTING_READ (implicite, route) vs SETTING_MANAGE
  // ---------------------------------------------------------------

  it('should show no Actions column and no Modifier button when the user only has SETTING_READ', () => {
    configure();
    authServiceMock.hasPermission.mockImplementation((permission: string) => permission !== 'SETTING_MANAGE');
    settingApiServiceMock.getSettings.mockReturnValue(of([buildSetting()]));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Modifier');
    expect(text).not.toContain('Actions');
  });

  it('should show the Modifier action when the user has SETTING_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(true);
    settingApiServiceMock.getSettings.mockReturnValue(of([buildSetting()]));

    createComponent();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Modifier');
    expect(authServiceMock.hasPermission).toHaveBeenCalledWith('SETTING_MANAGE');
  });

  it('should never open the edit dialog when the user lacks SETTING_MANAGE', () => {
    configure();
    authServiceMock.hasPermission.mockReturnValue(false);
    const setting = buildSetting();
    settingApiServiceMock.getSettings.mockReturnValue(of([setting]));
    createComponent();

    fixture.componentInstance.edit(setting);

    expect(fixture.componentInstance.editingSetting()).toBeNull();
  });

  // ---------------------------------------------------------------
  // Dialogue de modification
  // ---------------------------------------------------------------

  it('should open the edit dialog with the selected setting when the user has SETTING_MANAGE', () => {
    configure();
    const setting = buildSetting();
    settingApiServiceMock.getSettings.mockReturnValue(of([setting]));
    createComponent();

    fixture.componentInstance.edit(setting);

    expect(fixture.componentInstance.editingSetting()).toEqual(setting);
  });

  it('should replace the row and close the dialog when the dialog reports a save', () => {
    configure();
    const setting = buildSetting({ settingValue: '3' });
    const updated = buildSetting({ settingValue: '5' });
    settingApiServiceMock.getSettings.mockReturnValue(of([setting]));
    createComponent();
    fixture.componentInstance.edit(setting);

    fixture.componentInstance.onSaved(updated);

    expect(fixture.componentInstance.rows()).toEqual([updated]);
    expect(fixture.componentInstance.editingSetting()).toBeNull();
  });

  it('should close the dialog without changing rows when cancelled', () => {
    configure();
    const setting = buildSetting();
    settingApiServiceMock.getSettings.mockReturnValue(of([setting]));
    createComponent();
    fixture.componentInstance.edit(setting);

    fixture.componentInstance.closeDialog();

    expect(fixture.componentInstance.editingSetting()).toBeNull();
    expect(fixture.componentInstance.rows()).toEqual([setting]);
  });
});
