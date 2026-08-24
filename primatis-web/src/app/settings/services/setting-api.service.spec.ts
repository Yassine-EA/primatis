import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { SettingResponse } from '../models/setting-response';
import { SettingApiService } from './setting-api.service';

describe('SettingApiService', () => {
  let service: SettingApiService;
  let httpTestingController: HttpTestingController;

  const setting: SettingResponse = {
    settingKey: 'LOAN_DUE_SOON_DAYS',
    settingValue: '3',
    valueType: 'INTEGER',
    description: "Nombre de jours avant échéance déclenchant la notification.",
    updatedAt: null,
    updatedByUser: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(SettingApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // getSettings
  // ---------------------------------------------------------------

  it('should GET /api/v1/settings with no query parameters', () => {
    service.getSettings().subscribe();

    const request = httpTestingController.expectOne('/api/v1/settings');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys().length).toBe(0);

    request.flush([setting]);
  });

  it('should propagate the SettingResponse[] returned by the backend on getSettings', () => {
    let received: SettingResponse[] | undefined;
    service.getSettings().subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/settings').flush([setting]);

    expect(received).toEqual([setting]);
  });

  // ---------------------------------------------------------------
  // updateSettingValue
  // ---------------------------------------------------------------

  it('should PATCH /api/v1/settings/{settingKey} with exactly {settingValue} as body', () => {
    service.updateSettingValue('LOAN_DUE_SOON_DAYS', { settingValue: '5' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/settings/LOAN_DUE_SOON_DAYS');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ settingValue: '5' });
    expect(Object.keys(request.request.body as object)).toEqual(['settingValue']);

    request.flush({ ...setting, settingValue: '5' });
  });

  it('should URL-encode a settingKey containing special characters', () => {
    service.updateSettingValue('KEY WITH SPACE', { settingValue: '5' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/settings/KEY%20WITH%20SPACE');
    expect(request.request.method).toBe('PATCH');

    request.flush({ ...setting, settingKey: 'KEY WITH SPACE', settingValue: '5' });
  });

  it('should propagate the SettingResponse returned by the backend on updateSettingValue', () => {
    const updated: SettingResponse = { ...setting, settingValue: '5' };
    let received: SettingResponse | undefined;
    service.updateSettingValue('LOAN_DUE_SOON_DAYS', { settingValue: '5' }).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/settings/LOAN_DUE_SOON_DAYS').flush(updated);

    expect(received).toEqual(updated);
  });
});
