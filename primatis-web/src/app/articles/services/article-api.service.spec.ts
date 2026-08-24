import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { ArticleResponse } from '../models/article-response';
import { ArticleSummaryResponse } from '../models/article-summary-response';
import { ArticleApiService } from './article-api.service';

describe('ArticleApiService', () => {
  let service: ArticleApiService;
  let httpTestingController: HttpTestingController;

  const summary: ArticleSummaryResponse = {
    id: 1,
    title: 'Titre public',
    summary: 'Résumé',
    slug: 'titre-public',
    author: { id: 10, firstName: 'Prénom', lastName: 'Nom' },
    publishedAt: '2026-08-01T10:00:00Z',
  };

  const detail: ArticleResponse = {
    id: 1,
    title: 'Titre public',
    content: '<p>Contenu</p>',
    summary: 'Résumé',
    slug: 'titre-public',
    articleStatus: 'PUBLISHED',
    author: { id: 10, firstName: 'Prénom', lastName: 'Nom' },
    lastModifiedBy: null,
    tags: [],
    publishedAt: '2026-08-01T10:00:00Z',
    createdAt: '2026-07-01T08:00:00Z',
    updatedAt: '2026-08-01T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(ArticleApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // listPublishedArticles
  // ---------------------------------------------------------------

  it('should GET /api/v1/articles with default page/size params', () => {
    service.listPublishedArticles().subscribe();

    const request = httpTestingController.expectOne((req) => req.url === '/api/v1/articles' && req.method === 'GET');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');

    request.flush({ content: [summary], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/articles with explicit page/size params', () => {
    service.listPublishedArticles(2, 50).subscribe();

    const request = httpTestingController.expectOne((req) => req.url === '/api/v1/articles' && req.method === 'GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');

    request.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('should never send a search or tag filter param (DEV-DEC-0061)', () => {
    service.listPublishedArticles().subscribe();

    const request = httpTestingController.expectOne((req) => req.url === '/api/v1/articles' && req.method === 'GET');
    expect(request.request.params.has('q')).toBe(false);
    expect(request.request.params.has('search')).toBe(false);
    expect(request.request.params.has('tag')).toBe(false);
    expect(request.request.params.has('sort')).toBe(false);

    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<ArticleSummaryResponse> returned by the backend', () => {
    let received: unknown;
    service.listPublishedArticles().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/articles' && req.method === 'GET')
      .flush({ content: [summary], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(received).toEqual({ content: [summary], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  // ---------------------------------------------------------------
  // getPublishedArticleBySlug
  // ---------------------------------------------------------------

  it('should GET /api/v1/articles/{slug}', () => {
    service.getPublishedArticleBySlug('titre-public').subscribe();

    const request = httpTestingController.expectOne('/api/v1/articles/titre-public');
    expect(request.request.method).toBe('GET');

    request.flush(detail);
  });

  it('should URL-encode the slug path segment', () => {
    service.getPublishedArticleBySlug('a b/c').subscribe();

    const request = httpTestingController.expectOne(`/api/v1/articles/${encodeURIComponent('a b/c')}`);
    expect(request.request.method).toBe('GET');

    request.flush(detail);
  });

  it('should propagate the ArticleResponse returned by the backend on getPublishedArticleBySlug', () => {
    let received: ArticleResponse | undefined;
    service.getPublishedArticleBySlug('titre-public').subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/articles/titre-public').flush(detail);

    expect(received).toEqual(detail);
  });
});
