import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { FineResponse } from '../models/fine-response';

/**
 * Accès HTTP au contrat REST des Fines (`be.primatis.fine.web.FineController`).
 * `listFines` (staff, `FINE_READ`) et `listOwnFines` (self, ownership
 * structurelle backend — aucun `userId` envoyé) sont deux endpoints
 * distincts, jamais un seul paramétré par un flag — même convention que
 * `LoanApiService`/`ReservationApiService`. Tri (`issuedAt` décroissant)
 * imposé côté backend : aucun paramètre `sort` envoyé ici. Aucun
 * `GET /api/v1/fines/{id}`, aucun `DELETE`/`PATCH`, aucune création
 * (`POST /api/v1/fines`) : la Fine est créée automatiquement au retour
 * tardif (DEV-DEC-0048) — seuls les quatre endpoints réellement exposés
 * par le backend sont implémentés. Aucune logique d'état, aucune règle
 * métier (paiement, annulabilité, formatage) : le backend reste l'autorité
 * exclusive — ce service construit une URL, des `HttpParams`, appelle
 * `HttpClient` et type la réponse, rien de plus.
 */
@Injectable({ providedIn: 'root' })
export class FineApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listOwnFines(page = 0, size = 20): Observable<PageResponse<FineResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<FineResponse>>(`${this.baseUrl}/me/fines`, { params });
  }

  listFines(page = 0, size = 20): Observable<PageResponse<FineResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<FineResponse>>(`${this.baseUrl}/fines`, { params });
  }

  confirmPayment(fineId: number): Observable<FineResponse> {
    return this.http.post<FineResponse>(`${this.baseUrl}/fines/${fineId}/payment-confirmation`, null);
  }

  cancelFine(fineId: number): Observable<FineResponse> {
    return this.http.post<FineResponse>(`${this.baseUrl}/fines/${fineId}/cancel`, null);
  }
}
