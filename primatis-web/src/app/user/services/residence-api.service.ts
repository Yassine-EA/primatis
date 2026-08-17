import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { ResidenceResponse } from '../models/residence-response';
import { UpdateResidenceRequest } from '../models/update-residence-request';

/**
 * Accès HTTP Address/Residence, parcours personnel (`/api/v1/me/residence`)
 * et staff (`/api/v1/users/{id}/residence(s)`). Ressource séparée de
 * `UserApiService`/`MeProfileApiService` — jamais fusionnée dans
 * `UserResponse`/`MeProfileResponse`.
 */
@Injectable({ providedIn: 'root' })
export class ResidenceApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getOwnResidence(): Observable<ResidenceResponse> {
    return this.http.get<ResidenceResponse>(`${this.baseUrl}/me/residence`);
  }

  replaceOwnResidence(request: UpdateResidenceRequest): Observable<ResidenceResponse> {
    return this.http.put<ResidenceResponse>(`${this.baseUrl}/me/residence`, request);
  }

  getResidence(userId: number): Observable<ResidenceResponse> {
    return this.http.get<ResidenceResponse>(`${this.baseUrl}/users/${userId}/residence`);
  }

  getResidenceHistory(userId: number): Observable<ResidenceResponse[]> {
    return this.http.get<ResidenceResponse[]>(`${this.baseUrl}/users/${userId}/residences`);
  }

  replaceResidence(userId: number, request: UpdateResidenceRequest): Observable<ResidenceResponse> {
    return this.http.put<ResidenceResponse>(`${this.baseUrl}/users/${userId}/residence`, request);
  }
}
