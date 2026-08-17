import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { CopyResponse } from '../models/copy-response';
import { CreateCopyRequest } from '../models/create-copy-request';
import { UpdateCopyAvailabilityRequest } from '../models/update-copy-availability-request';
import { UpdateCopyRequest } from '../models/update-copy-request';

/**
 * Accès HTTP au contrat REST staff de la sous-ressource `Copy`
 * (`/api/v1/staff/titles/{titleId}/copies`, `COPY_READ`/`COPY_MANAGE`).
 * Distinct de `StaffCatalogueApiService` : `Copy` est une sous-ressource
 * de `Title` avec sa propre autorisation (`COPY_*`, pas `CATALOGUE_*`).
 * Aucune logique d'état, aucune règle métier (notamment aucune déduction
 * `LOST`/`OUT_OF_SERVICE` → `UNAVAILABLE` côté frontend) : le backend
 * reste l'autorité. Pas de pagination — `listCopies` retourne la liste
 * complète des Copies d'un Title, conformément au contrat backend.
 */
@Injectable({ providedIn: 'root' })
export class CopyApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listCopies(titleId: number): Observable<CopyResponse[]> {
    return this.http.get<CopyResponse[]>(`${this.baseUrl}/staff/titles/${titleId}/copies`);
  }

  getCopyById(titleId: number, copyId: number): Observable<CopyResponse> {
    return this.http.get<CopyResponse>(`${this.baseUrl}/staff/titles/${titleId}/copies/${copyId}`);
  }

  createCopy(titleId: number, request: CreateCopyRequest): Observable<CopyResponse> {
    return this.http.post<CopyResponse>(`${this.baseUrl}/staff/titles/${titleId}/copies`, request);
  }

  updateCopy(titleId: number, copyId: number, request: UpdateCopyRequest): Observable<CopyResponse> {
    return this.http.patch<CopyResponse>(`${this.baseUrl}/staff/titles/${titleId}/copies/${copyId}`, request);
  }

  updateAvailability(
    titleId: number,
    copyId: number,
    request: UpdateCopyAvailabilityRequest,
  ): Observable<CopyResponse> {
    return this.http.patch<CopyResponse>(
      `${this.baseUrl}/staff/titles/${titleId}/copies/${copyId}/availability`,
      request,
    );
  }
}
