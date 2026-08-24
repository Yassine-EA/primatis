import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { ArticleResponse } from '../models/article-response';
import { CreateArticleRequest } from '../models/create-article-request';
import { StaffArticleSummaryResponse } from '../models/staff-article-summary-response';
import { UpdateArticleRequest } from '../models/update-article-request';
import { UpdateArticleTagsRequest } from '../models/update-article-tags-request';

/**
 * Accès HTTP au contrat REST staff d'Article
 * (`be.primatis.article.web.StaffArticleController`,
 * `/api/v1/staff/articles`, `ARTICLE_MANAGE`/`ARTICLE_PUBLISH` selon
 * l'action — portés par le backend, jamais recalculés ici).
 * `listStaffArticles`/`getStaffArticleById` (DEV-11.12A, `ARTICLE_MANAGE`) :
 * tous statuts confondus (`DRAFT`/`PUBLISHED`/`ARCHIVED`), contrairement à
 * `ArticleApiService` (public, structurellement `PUBLISHED`-only) — aucun
 * paramètre de filtre/tri (imposés côté backend). `publishArticle`/
 * `archiveArticle` sont des actions métier explicites (`POST .../publish`,
 * `POST .../archive`), jamais un `PATCH`/`PUT` générique de statut.
 * `deleteArticle` : `204 No Content` réel côté backend, typé
 * `Observable<void>`. Aucune logique d'état, aucune règle métier : le
 * backend reste l'autorité exclusive.
 */
@Injectable({ providedIn: 'root' })
export class StaffArticleApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listStaffArticles(page = 0, size = 20): Observable<PageResponse<StaffArticleSummaryResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<StaffArticleSummaryResponse>>(`${this.baseUrl}/staff/articles`, { params });
  }

  getStaffArticleById(articleId: number): Observable<ArticleResponse> {
    return this.http.get<ArticleResponse>(`${this.baseUrl}/staff/articles/${articleId}`);
  }

  createArticle(request: CreateArticleRequest): Observable<ArticleResponse> {
    return this.http.post<ArticleResponse>(`${this.baseUrl}/staff/articles`, request);
  }

  updateArticle(articleId: number, request: UpdateArticleRequest): Observable<ArticleResponse> {
    return this.http.patch<ArticleResponse>(`${this.baseUrl}/staff/articles/${articleId}`, request);
  }

  publishArticle(articleId: number): Observable<ArticleResponse> {
    return this.http.post<ArticleResponse>(`${this.baseUrl}/staff/articles/${articleId}/publish`, null);
  }

  archiveArticle(articleId: number): Observable<ArticleResponse> {
    return this.http.post<ArticleResponse>(`${this.baseUrl}/staff/articles/${articleId}/archive`, null);
  }

  deleteArticle(articleId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/staff/articles/${articleId}`);
  }

  updateArticleTags(articleId: number, request: UpdateArticleTagsRequest): Observable<ArticleResponse> {
    return this.http.patch<ArticleResponse>(`${this.baseUrl}/staff/articles/${articleId}/tags`, request);
  }
}
