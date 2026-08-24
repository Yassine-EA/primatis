import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { CreateTagRequest } from '../models/create-tag-request';
import { TagResponse } from '../models/tag-response';
import { UpdateTagRequest } from '../models/update-tag-request';

/**
 * Accès HTTP au contrat REST staff de `Tag`
 * (`be.primatis.article.web.StaffTagController`, `/api/v1/staff/tags`,
 * `ARTICLE_MANAGE` exclusivement — DEV-DEC-0060, aucune permission Tag
 * dédiée). Aucun paramètre de filtre sur `listTags` (aucun `q` côté
 * backend). Tri (`label ASC, id ASC`) imposé côté backend : aucun paramètre
 * `sort` envoyé ici. `deleteTag` : `204 No Content` réel côté backend, typé
 * `Observable<void>`. Aucune logique d'état, aucune règle métier : le
 * backend reste l'autorité exclusive.
 */
@Injectable({ providedIn: 'root' })
export class StaffTagApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listTags(page = 0, size = 20): Observable<PageResponse<TagResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<TagResponse>>(`${this.baseUrl}/staff/tags`, { params });
  }

  createTag(request: CreateTagRequest): Observable<TagResponse> {
    return this.http.post<TagResponse>(`${this.baseUrl}/staff/tags`, request);
  }

  updateTag(tagId: number, request: UpdateTagRequest): Observable<TagResponse> {
    return this.http.patch<TagResponse>(`${this.baseUrl}/staff/tags/${tagId}`, request);
  }

  deleteTag(tagId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/staff/tags/${tagId}`);
  }
}
