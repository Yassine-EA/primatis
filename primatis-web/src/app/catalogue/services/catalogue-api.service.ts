import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { TitleDetailResponse } from '../models/title-detail-response';
import { TitleResponse } from '../models/title-response';
import { TitleSearchParams } from '../models/title-search-params';

/**
 * Accès HTTP au contrat REST public du catalogue (`/api/v1/titles`,
 * `permitAll`, `Title.titleStatus = ACTIVE` uniquement, imposé par le
 * backend). Aucune logique d'état, aucune règle métier : le backend reste
 * l'autorité.
 */
@Injectable({ providedIn: 'root' })
export class CatalogueApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  searchTitles(params: TitleSearchParams = {}): Observable<PageResponse<TitleResponse>> {
    let httpParams = new HttpParams();
    if (params.q !== undefined) {
      httpParams = httpParams.set('q', params.q);
    }
    if (params.authorId !== undefined) {
      httpParams = httpParams.set('authorId', params.authorId);
    }
    if (params.genreCode !== undefined) {
      httpParams = httpParams.set('genreCode', params.genreCode);
    }
    if (params.language !== undefined) {
      httpParams = httpParams.set('language', params.language);
    }
    if (params.page !== undefined) {
      httpParams = httpParams.set('page', params.page);
    }
    if (params.size !== undefined) {
      httpParams = httpParams.set('size', params.size);
    }

    return this.http.get<PageResponse<TitleResponse>>(`${this.baseUrl}/titles`, { params: httpParams });
  }

  getTitleById(titleId: number): Observable<TitleDetailResponse> {
    return this.http.get<TitleDetailResponse>(`${this.baseUrl}/titles/${titleId}`);
  }
}
