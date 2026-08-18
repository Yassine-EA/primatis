import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { CreateLoanRequest } from '../models/create-loan-request';
import { LoanResponse } from '../models/loan-response';

/**
 * Accès HTTP au contrat REST des Loans (`be.primatis.loan.web.LoanController`).
 * `listLoans` (staff, `LOAN_READ`) et `listOwnLoans` (self, ownership
 * structurelle backend — aucun `userId` envoyé) sont deux endpoints
 * distincts, jamais un seul paramétré par un flag. `registerLoan`
 * (`LOAN_MANAGE`) et `registerReturn` (`LOAN_MANAGE`, aucun corps de
 * requête) suivent la même convention que `UserApiService`. Aucune logique
 * d'état, aucune règle métier (éligibilité, FIFO Reservation, retard) :
 * le backend reste l'autorité exclusive.
 */
@Injectable({ providedIn: 'root' })
export class LoanApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listLoans(page = 0, size = 20): Observable<PageResponse<LoanResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<LoanResponse>>(`${this.baseUrl}/loans`, { params });
  }

  listOwnLoans(page = 0, size = 20): Observable<PageResponse<LoanResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<LoanResponse>>(`${this.baseUrl}/me/loans`, { params });
  }

  registerLoan(request: CreateLoanRequest): Observable<LoanResponse> {
    return this.http.post<LoanResponse>(`${this.baseUrl}/loans`, request);
  }

  registerReturn(loanId: number): Observable<LoanResponse> {
    return this.http.post<LoanResponse>(`${this.baseUrl}/loans/${loanId}/return`, null);
  }
}
