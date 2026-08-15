import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { LoginRequest } from '../models/login-request';
import { LoginResponse } from '../models/login-response';

/**
 * Accès HTTP au contrat REST d'authentification (`POST /api/v1/auth/login`).
 * Ne contient aucune logique d'état : `AuthService` reste seul responsable
 * de la session (sessionStorage, Signals, claims).
 */
@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/auth/login`, request);
  }
}
