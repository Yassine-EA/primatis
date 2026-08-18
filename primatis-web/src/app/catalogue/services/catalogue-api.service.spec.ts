import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { TitleDetailResponse } from '../models/title-detail-response';
import { TitleResponse } from '../models/title-response';
import { CatalogueApiService } from './catalogue-api.service';

describe('CatalogueApiService', () => {
  let service: CatalogueApiService;
  let httpTestingController: HttpTestingController;

  const title: TitleResponse = {
    id: 1,
    isbn: '9780000000001',
    title: 'Le Petit Prince',
    subtitle: null,
    publicationYear: 1943,
    language: 'FR',
    publisher: null,
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(CatalogueApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // searchTitles
  // ---------------------------------------------------------------

  it('should GET /api/v1/titles with no params when none are provided', () => {
    service.searchTitles().subscribe();

    const request = httpTestingController.expectOne((req) => req.url === '/api/v1/titles' && req.method === 'GET');
    expect(request.request.params.keys().length).toBe(0);

    request.flush({ content: [title], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/titles preserving page=0 as an explicit param, not dropping it as falsy', () => {
    service.searchTitles({ page: 0, size: 10 }).subscribe();

    const request = httpTestingController.expectOne((req) => req.url === '/api/v1/titles' && req.method === 'GET');
    expect(request.request.params.has('page')).toBe(true);
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('10');

    request.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('should GET /api/v1/titles with every filter param set', () => {
    service.searchTitles({ q: 'prince', authorId: 3, genreCode: 'FANTASY', language: 'FR', page: 2, size: 50 }).subscribe();

    const request = httpTestingController.expectOne((req) => req.url === '/api/v1/titles' && req.method === 'GET');
    expect(request.request.params.get('q')).toBe('prince');
    expect(request.request.params.get('authorId')).toBe('3');
    expect(request.request.params.get('genreCode')).toBe('FANTASY');
    expect(request.request.params.get('language')).toBe('FR');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');

    request.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<TitleResponse> returned by the backend', () => {
    let received: unknown;
    service.searchTitles().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/titles')
      .flush({ content: [title], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(received).toEqual({ content: [title], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  // ---------------------------------------------------------------
  // getTitleById
  // ---------------------------------------------------------------

  it('should GET /api/v1/titles/{id}', () => {
    service.getTitleById(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/titles/1');
    expect(request.request.method).toBe('GET');

    request.flush({} as TitleDetailResponse);
  });

  it('should propagate the TitleDetailResponse returned by the backend', () => {
    const detail: TitleDetailResponse = {
      id: 1,
      isbn: '9780000000001',
      title: 'Le Petit Prince',
      subtitle: null,
      summary: null,
      publicationYear: 1943,
      language: 'FR',
      pageCount: 96,
      publisher: null,
      coverImageUrl: null,
      titleStatus: 'ACTIVE',
      authors: [],
      genres: [],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    };

    let received: TitleDetailResponse | undefined;
    service.getTitleById(1).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/titles/1').flush(detail);

    expect(received).toEqual(detail);
  });
});
