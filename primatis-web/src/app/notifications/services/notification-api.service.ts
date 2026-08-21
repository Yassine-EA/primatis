import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { NotificationMarkAllAsReadResponse } from '../models/notification-mark-all-as-read-response';
import { NotificationResponse } from '../models/notification-response';
import { NotificationUnreadCountResponse } from '../models/notification-unread-count-response';

/**
 * Accès HTTP au contrat REST self-service des Notifications
 * (`be.primatis.notification.web.NotificationController`, DEV-10.4).
 * Quatre endpoints exposés, aucun autre (aucune permission staff/écran
 * global Notification en V1, DEV-10.1 §10) : liste paginée, compteur
 * UNREAD dédié (DEV-DEC-0051, jamais dérivé côté frontend), marquage
 * individuel et marquage global (DEV-DEC-0052). Ownership backend
 * (identité JWT) : aucun `userId` envoyé, même convention exacte que
 * `FineApiService.listOwnFines`/`LoanApiService`/`ReservationApiService`.
 * Aucune logique d'état, aucun polling, aucune conversion d'erreur ici :
 * le backend reste l'autorité exclusive, ce service construit une URL, des
 * `HttpParams`, appelle `HttpClient` et type la réponse, rien de plus.
 */
@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listOwnNotifications(page = 0, size = 20): Observable<PageResponse<NotificationResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<NotificationResponse>>(`${this.baseUrl}/me/notifications`, { params });
  }

  getUnreadCount(): Observable<NotificationUnreadCountResponse> {
    return this.http.get<NotificationUnreadCountResponse>(`${this.baseUrl}/me/notifications/unread-count`);
  }

  markAsRead(notificationId: number): Observable<NotificationResponse> {
    return this.http.post<NotificationResponse>(`${this.baseUrl}/me/notifications/${notificationId}/read`, null);
  }

  markAllAsRead(): Observable<NotificationMarkAllAsReadResponse> {
    return this.http.post<NotificationMarkAllAsReadResponse>(`${this.baseUrl}/me/notifications/read-all`, null);
  }
}
