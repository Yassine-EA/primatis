import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { ArticleResponse } from '../models/article-response';
import { CreateArticleRequest } from '../models/create-article-request';
import { StaffArticleSummaryResponse } from '../models/staff-article-summary-response';
import { UpdateArticleRequest } from '../models/update-article-request';
import { UpdateArticleTagsRequest } from '../models/update-article-tags-request';
import { StaffArticleApiService } from './staff-article-api.service';

describe('StaffArticleApiService', () => {
  let service: StaffArticleApiService;
  let httpTestingController: HttpTestingController;

  const article: ArticleResponse = {
    id: 1,
    title: 'Titre staff',
    content: '<p>Contenu</p>',
    summary: 'Résumé',
    slug: 'titre-staff',
    articleStatus: 'DRAFT',
    author: { id: 10, firstName: 'Prénom', lastName: 'Nom' },
    lastModifiedBy: null,
    tags: [],
    publishedAt: null,
    createdAt: '2026-07-01T08:00:00Z',
    updatedAt: '2026-07-01T08:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(StaffArticleApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  const staffSummary: StaffArticleSummaryResponse = {
    id: 1,
    title: 'Titre staff',
    summary: 'Résumé',
    slug: 'titre-staff',
    articleStatus: 'DRAFT',
    author: { id: 10, firstName: 'Prénom', lastName: 'Nom' },
    publishedAt: null,
    updatedAt: '2026-07-01T08:00:00Z',
  };

  // ---------------------------------------------------------------
  // listStaffArticles
  // ---------------------------------------------------------------

  it('should GET /api/v1/staff/articles with default page/size', () => {
    service.listStaffArticles().subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles?page=0&size=20');
    expect(request.request.method).toBe('GET');

    const page: PageResponse<StaffArticleSummaryResponse> = {
      content: [staffSummary],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    request.flush(page);
  });

  it('should GET /api/v1/staff/articles with explicit page/size', () => {
    service.listStaffArticles(2, 50).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles?page=2&size=50');
    expect(request.request.method).toBe('GET');

    request.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('should never send a status/tag/sort filter param on listStaffArticles', () => {
    service.listStaffArticles().subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles?page=0&size=20');
    expect(request.request.params.has('status')).toBe(false);
    expect(request.request.params.has('tag')).toBe(false);
    expect(request.request.params.has('sort')).toBe(false);

    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<StaffArticleSummaryResponse> returned by the backend on listStaffArticles', () => {
    const page: PageResponse<StaffArticleSummaryResponse> = {
      content: [staffSummary],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    let received: PageResponse<StaffArticleSummaryResponse> | undefined;
    service.listStaffArticles().subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/staff/articles?page=0&size=20').flush(page);

    expect(received).toEqual(page);
  });

  // ---------------------------------------------------------------
  // getStaffArticleById
  // ---------------------------------------------------------------

  it('should GET /api/v1/staff/articles/{articleId}', () => {
    service.getStaffArticleById(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles/1');
    expect(request.request.method).toBe('GET');

    request.flush(article);
  });

  it('should propagate the ArticleResponse returned by the backend on getStaffArticleById, whatever the status', () => {
    const archived: ArticleResponse = { ...article, articleStatus: 'ARCHIVED', publishedAt: '2026-08-01T10:00:00Z' };
    let received: ArticleResponse | undefined;
    service.getStaffArticleById(1).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/staff/articles/1').flush(archived);

    expect(received).toEqual(archived);
  });

  // ---------------------------------------------------------------
  // createArticle
  // ---------------------------------------------------------------

  it('should POST /api/v1/staff/articles with the exact CreateArticleRequest body', () => {
    const requestBody: CreateArticleRequest = { title: 'Nouveau', content: '<p>Contenu</p>', summary: 'Résumé' };
    service.createArticle(requestBody).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);

    request.flush(article);
  });

  it('should propagate the ArticleResponse returned by the backend on createArticle', () => {
    let received: ArticleResponse | undefined;
    service.createArticle({ title: 'Nouveau', content: '<p>Contenu</p>' }).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/staff/articles').flush(article);

    expect(received).toEqual(article);
  });

  // ---------------------------------------------------------------
  // updateArticle
  // ---------------------------------------------------------------

  it('should PATCH /api/v1/staff/articles/{articleId} with the exact UpdateArticleRequest body', () => {
    const requestBody: UpdateArticleRequest = { title: 'Titre édité' };
    service.updateArticle(1, requestBody).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles/1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual(requestBody);

    request.flush(article);
  });

  it('should send an explicit null summary as null in the JSON body, never omitted (PATCH sparse)', () => {
    const requestBody: UpdateArticleRequest = { summary: null };
    service.updateArticle(1, requestBody).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles/1');
    expect(request.request.body).toEqual({ summary: null });
    expect(Object.prototype.hasOwnProperty.call(request.request.body, 'summary')).toBe(true);

    request.flush(article);
  });

  it('should send an empty body when no field is present (PATCH sparse no-op)', () => {
    service.updateArticle(1, {}).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles/1');
    expect(request.request.body).toEqual({});

    request.flush(article);
  });

  it('should propagate the ArticleResponse returned by the backend on updateArticle', () => {
    let received: ArticleResponse | undefined;
    service.updateArticle(1, { title: 'Titre édité' }).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/staff/articles/1').flush(article);

    expect(received).toEqual(article);
  });

  // ---------------------------------------------------------------
  // publishArticle
  // ---------------------------------------------------------------

  it('should POST /api/v1/staff/articles/{articleId}/publish with no business payload', () => {
    service.publishArticle(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles/1/publish');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    const published: ArticleResponse = { ...article, articleStatus: 'PUBLISHED', publishedAt: '2026-08-01T10:00:00Z' };
    request.flush(published);
  });

  it('should propagate the ArticleResponse returned by the backend on publishArticle', () => {
    let received: ArticleResponse | undefined;
    service.publishArticle(1).subscribe((value) => (received = value));

    const published: ArticleResponse = { ...article, articleStatus: 'PUBLISHED', publishedAt: '2026-08-01T10:00:00Z' };
    httpTestingController.expectOne('/api/v1/staff/articles/1/publish').flush(published);

    expect(received).toEqual(published);
  });

  // ---------------------------------------------------------------
  // archiveArticle
  // ---------------------------------------------------------------

  it('should POST /api/v1/staff/articles/{articleId}/archive with no business payload', () => {
    service.archiveArticle(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles/1/archive');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    const archived: ArticleResponse = { ...article, articleStatus: 'ARCHIVED', publishedAt: '2026-08-01T10:00:00Z' };
    request.flush(archived);
  });

  it('should propagate the ArticleResponse returned by the backend on archiveArticle', () => {
    let received: ArticleResponse | undefined;
    service.archiveArticle(1).subscribe((value) => (received = value));

    const archived: ArticleResponse = { ...article, articleStatus: 'ARCHIVED', publishedAt: '2026-08-01T10:00:00Z' };
    httpTestingController.expectOne('/api/v1/staff/articles/1/archive').flush(archived);

    expect(received).toEqual(archived);
  });

  // ---------------------------------------------------------------
  // deleteArticle
  // ---------------------------------------------------------------

  it('should DELETE /api/v1/staff/articles/{articleId} and expect no response body', () => {
    let completed = false;
    service.deleteArticle(1).subscribe(() => (completed = true));

    const request = httpTestingController.expectOne('/api/v1/staff/articles/1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBe(true);
  });

  // ---------------------------------------------------------------
  // updateArticleTags
  // ---------------------------------------------------------------

  it('should PATCH /api/v1/staff/articles/{articleId}/tags with the exact { tagIds } body', () => {
    const requestBody: UpdateArticleTagsRequest = { tagIds: [1, 2, 3] };
    service.updateArticleTags(1, requestBody).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles/1/tags');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ tagIds: [1, 2, 3] });

    request.flush({ ...article, tags: [{ id: 1, code: 'a', label: 'A', description: null }] });
  });

  it('should PATCH with an empty tagIds array to dissociate all Tags', () => {
    service.updateArticleTags(1, { tagIds: [] }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/articles/1/tags');
    expect(request.request.body).toEqual({ tagIds: [] });

    request.flush({ ...article, tags: [] });
  });

  it('should propagate the ArticleResponse returned by the backend on updateArticleTags', () => {
    const updated: ArticleResponse = { ...article, tags: [{ id: 1, code: 'a', label: 'A', description: null }] };
    let received: ArticleResponse | undefined;
    service.updateArticleTags(1, { tagIds: [1] }).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/staff/articles/1/tags').flush(updated);

    expect(received).toEqual(updated);
  });
});
