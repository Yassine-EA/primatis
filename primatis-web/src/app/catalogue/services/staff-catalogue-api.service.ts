import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { AuthorResponse } from '../models/author-response';
import { AuthorSearchParams } from '../models/author-search-params';
import { CreateAuthorRequest } from '../models/create-author-request';
import { CreateGenreRequest } from '../models/create-genre-request';
import { CreateTitleRequest } from '../models/create-title-request';
import { GenreListParams } from '../models/genre-list-params';
import { GenreResponse } from '../models/genre-response';
import { StaffTitleSearchParams } from '../models/title-search-params';
import { TitleDetailResponse } from '../models/title-detail-response';
import { TitleResponse } from '../models/title-response';
import { UpdateAuthorRequest } from '../models/update-author-request';
import { UpdateGenreRequest } from '../models/update-genre-request';
import { UpdateTitleRequest } from '../models/update-title-request';
import { UpdateTitleStatusRequest } from '../models/update-title-status-request';

/**
 * Accès HTTP au contrat REST staff du catalogue : `Title`
 * (`/api/v1/staff/titles`, `CATALOGUE_MANAGE`), `Author`
 * (`/api/v1/staff/authors`), `Genre` (`/api/v1/staff/genres`). Regroupés
 * dans un seul service (même racine `CATALOGUE_MANAGE`, même domaine
 * catalogue) — `Copy` reste distinct (`CopyApiService`, sous-ressource
 * `COPY_MANAGE`). Aucune logique d'état, aucune règle métier : le backend
 * reste l'autorité.
 */
@Injectable({ providedIn: 'root' })
export class StaffCatalogueApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  // ---------------------------------------------------------------
  // Title
  // ---------------------------------------------------------------

  searchTitles(params: StaffTitleSearchParams = {}): Observable<PageResponse<TitleResponse>> {
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
    if (params.titleStatus !== undefined) {
      httpParams = httpParams.set('titleStatus', params.titleStatus);
    }
    if (params.page !== undefined) {
      httpParams = httpParams.set('page', params.page);
    }
    if (params.size !== undefined) {
      httpParams = httpParams.set('size', params.size);
    }

    return this.http.get<PageResponse<TitleResponse>>(`${this.baseUrl}/staff/titles`, { params: httpParams });
  }

  getTitleById(titleId: number): Observable<TitleDetailResponse> {
    return this.http.get<TitleDetailResponse>(`${this.baseUrl}/staff/titles/${titleId}`);
  }

  createTitle(request: CreateTitleRequest): Observable<TitleDetailResponse> {
    return this.http.post<TitleDetailResponse>(`${this.baseUrl}/staff/titles`, request);
  }

  updateTitle(titleId: number, request: UpdateTitleRequest): Observable<TitleDetailResponse> {
    return this.http.patch<TitleDetailResponse>(`${this.baseUrl}/staff/titles/${titleId}`, request);
  }

  updateTitleStatus(titleId: number, request: UpdateTitleStatusRequest): Observable<TitleDetailResponse> {
    return this.http.patch<TitleDetailResponse>(`${this.baseUrl}/staff/titles/${titleId}/status`, request);
  }

  // ---------------------------------------------------------------
  // Author
  // ---------------------------------------------------------------

  searchAuthors(params: AuthorSearchParams = {}): Observable<PageResponse<AuthorResponse>> {
    let httpParams = new HttpParams();
    if (params.q !== undefined) {
      httpParams = httpParams.set('q', params.q);
    }
    if (params.page !== undefined) {
      httpParams = httpParams.set('page', params.page);
    }
    if (params.size !== undefined) {
      httpParams = httpParams.set('size', params.size);
    }

    return this.http.get<PageResponse<AuthorResponse>>(`${this.baseUrl}/staff/authors`, { params: httpParams });
  }

  createAuthor(request: CreateAuthorRequest): Observable<AuthorResponse> {
    return this.http.post<AuthorResponse>(`${this.baseUrl}/staff/authors`, request);
  }

  updateAuthor(authorId: number, request: UpdateAuthorRequest): Observable<AuthorResponse> {
    return this.http.patch<AuthorResponse>(`${this.baseUrl}/staff/authors/${authorId}`, request);
  }

  // ---------------------------------------------------------------
  // Genre
  // ---------------------------------------------------------------

  searchGenres(params: GenreListParams = {}): Observable<PageResponse<GenreResponse>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) {
      httpParams = httpParams.set('page', params.page);
    }
    if (params.size !== undefined) {
      httpParams = httpParams.set('size', params.size);
    }

    return this.http.get<PageResponse<GenreResponse>>(`${this.baseUrl}/staff/genres`, { params: httpParams });
  }

  createGenre(request: CreateGenreRequest): Observable<GenreResponse> {
    return this.http.post<GenreResponse>(`${this.baseUrl}/staff/genres`, request);
  }

  updateGenre(genreId: number, request: UpdateGenreRequest): Observable<GenreResponse> {
    return this.http.patch<GenreResponse>(`${this.baseUrl}/staff/genres/${genreId}`, request);
  }
}
