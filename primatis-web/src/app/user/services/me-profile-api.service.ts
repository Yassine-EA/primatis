import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { MeProfileResponse } from '../models/me-profile-response';
import { UpdateMeProfileRequest } from '../models/update-me-profile-request';

/**
 * Accès HTTP au profil personnel de l'utilisateur authentifié
 * (`/api/v1/me/profile`). Ownership structurelle côté backend
 * (`authentication.getName()`) : aucun `userId` n'est jamais envoyé ici.
 */
@Injectable({ providedIn: 'root' })
export class MeProfileApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getProfile(): Observable<MeProfileResponse> {
    return this.http.get<MeProfileResponse>(`${this.baseUrl}/me/profile`);
  }

  updateProfile(request: UpdateMeProfileRequest): Observable<MeProfileResponse> {
    return this.http.patch<MeProfileResponse>(`${this.baseUrl}/me/profile`, request);
  }
}
