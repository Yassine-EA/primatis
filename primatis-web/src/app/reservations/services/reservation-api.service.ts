import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { CreateOwnReservationRequest } from '../models/create-own-reservation-request';
import { CreateReservationRequest } from '../models/create-reservation-request';
import { ReservationResponse } from '../models/reservation-response';

/**
 * Accès HTTP au contrat REST des Reservations
 * (`be.primatis.reservation.web.ReservationController`). `listReservations`
 * (staff, `RESERVATION_READ`) et `listOwnReservations` (self, ownership
 * structurelle backend — aucun `userId` envoyé) sont deux endpoints
 * distincts, jamais un seul paramétré par un flag — même convention que
 * `LoanApiService`. Tri (`reservationDate` décroissant) imposé côté
 * backend (DEV-DEC-0036) : aucun paramètre `sort` envoyé ici. Aucun
 * `GET /api/v1/reservations/{id}` (OD-DEV08-05/DEV-DEC-0036), aucun
 * `DELETE`/`PATCH`, aucun endpoint d'expiration ou de disponibilité :
 * seuls les six endpoints réellement exposés par le backend sont
 * implémentés. Aucune logique d'état, aucune règle métier (éligibilité,
 * FIFO, expiration, admissibilité) : le backend reste l'autorité
 * exclusive — ce service construit une URL, des `HttpParams`, appelle
 * `HttpClient` et type la réponse, rien de plus.
 */
@Injectable({ providedIn: 'root' })
export class ReservationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listOwnReservations(page = 0, size = 20): Observable<PageResponse<ReservationResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ReservationResponse>>(`${this.baseUrl}/me/reservations`, { params });
  }

  listReservations(page = 0, size = 20): Observable<PageResponse<ReservationResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ReservationResponse>>(`${this.baseUrl}/reservations`, { params });
  }

  createOwnReservation(request: CreateOwnReservationRequest): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>(`${this.baseUrl}/me/reservations`, request);
  }

  createReservation(request: CreateReservationRequest): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>(`${this.baseUrl}/reservations`, request);
  }

  cancelOwnReservation(reservationId: number): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>(`${this.baseUrl}/me/reservations/${reservationId}/cancel`, null);
  }

  cancelReservation(reservationId: number): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>(`${this.baseUrl}/reservations/${reservationId}/cancel`, null);
  }
}
