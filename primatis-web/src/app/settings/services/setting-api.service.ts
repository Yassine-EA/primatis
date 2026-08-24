import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { SettingResponse } from '../models/setting-response';
import { UpdateSettingValueRequest } from '../models/update-setting-value-request';

/**
 * Accès HTTP au contrat REST Application Settings
 * (`be.primatis.setting.web.SettingController`, DEV-12.2/12.3).
 * `getSettings` (`SETTING_READ`) retourne la liste fixe des six paramètres,
 * déjà triée par `settingKey` côté backend — aucun tri client, aucune
 * pagination, aucune recherche (collection bornée). `updateSettingValue`
 * (`SETTING_MANAGE`) encode `settingKey` dans l'URL (même précédent que
 * `ArticleApiService.getArticleBySlug`). Aucune logique métier, aucune
 * validation ici : le backend reste l'autorité exclusive.
 */
@Injectable({ providedIn: 'root' })
export class SettingApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getSettings(): Observable<SettingResponse[]> {
    return this.http.get<SettingResponse[]>(`${this.baseUrl}/settings`);
  }

  updateSettingValue(settingKey: string, request: UpdateSettingValueRequest): Observable<SettingResponse> {
    return this.http.patch<SettingResponse>(
      `${this.baseUrl}/settings/${encodeURIComponent(settingKey)}`,
      request,
    );
  }
}
