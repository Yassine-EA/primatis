import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { BlockMembershipRequest } from '../models/block-membership-request';
import { CreateUserRequest } from '../models/create-user-request';
import { CreateUserResponse } from '../models/create-user-response';
import { UpdateAccountStatusRequest } from '../models/update-account-status-request';
import { UpdateUserRequest } from '../models/update-user-request';
import { UserDetailResponse } from '../models/user-detail-response';
import { UserResponse } from '../models/user-response';

/**
 * Accès HTTP au contrat REST Users/Membership staff/admin
 * (`/api/v1/users/**`). Aucune logique d'état, aucune règle métier : le
 * backend reste l'autorité. Même convention que `AuthApiService`.
 */
@Injectable({ providedIn: 'root' })
export class UserApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listUsers(page = 0, size = 20): Observable<PageResponse<UserResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<UserResponse>>(`${this.baseUrl}/users`, { params });
  }

  getUser(id: number): Observable<UserDetailResponse> {
    return this.http.get<UserDetailResponse>(`${this.baseUrl}/users/${id}`);
  }

  createUser(request: CreateUserRequest): Observable<CreateUserResponse> {
    return this.http.post<CreateUserResponse>(`${this.baseUrl}/users`, request);
  }

  updateUser(id: number, request: UpdateUserRequest): Observable<UserResponse> {
    return this.http.patch<UserResponse>(`${this.baseUrl}/users/${id}`, request);
  }

  updateAccountStatus(id: number, request: UpdateAccountStatusRequest): Observable<UserResponse> {
    return this.http.patch<UserResponse>(`${this.baseUrl}/users/${id}/account-status`, request);
  }

  blockMembership(id: number, request: BlockMembershipRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${this.baseUrl}/users/${id}/membership/block`, request);
  }

  unblockMembership(id: number): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${this.baseUrl}/users/${id}/membership/unblock`, null);
  }

  reactivateMembership(id: number): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${this.baseUrl}/users/${id}/membership/reactivate`, null);
  }
}
