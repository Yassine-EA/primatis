import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { AuthorResponse } from '../models/author-response';
import { GenreResponse } from '../models/genre-response';
import { TitleResponse } from '../models/title-response';
import { StaffCatalogueApiService } from './staff-catalogue-api.service';

describe('StaffCatalogueApiService', () => {
  let service: StaffCatalogueApiService;
  let httpTestingController: HttpTestingController;

  const title: TitleResponse = {
    id: 1,
    isbn: null,
    title: 'Le Petit Prince',
    subtitle: null,
    publicationYear: 1943,
    language: 'FR',
    publisher: null,
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
  };

  const author: AuthorResponse = {
    id: 1,
    fullName: 'Antoine de Saint-Exupéry',
    birthDate: null,
    deathDate: null,
    nationality: null,
    biography: null,
  };

  const genre: GenreResponse = { id: 1, code: 'FANTASY', label: 'Fantasy', description: null };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(StaffCatalogueApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // Title
  // ---------------------------------------------------------------

  it('should GET /api/v1/staff/titles with no params when none are provided', () => {
    service.searchTitles().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/staff/titles' && req.method === 'GET',
    );
    expect(request.request.params.keys().length).toBe(0);

    request.flush({ content: [title], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/staff/titles preserving page=0 as an explicit param', () => {
    service.searchTitles({ page: 0 }).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/staff/titles' && req.method === 'GET',
    );
    expect(request.request.params.has('page')).toBe(true);
    expect(request.request.params.get('page')).toBe('0');

    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('should GET /api/v1/staff/titles with titleStatus (staff-only filter)', () => {
    service.searchTitles({ titleStatus: 'WITHDRAWN' }).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/staff/titles' && req.method === 'GET',
    );
    expect(request.request.params.get('titleStatus')).toBe('WITHDRAWN');

    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('should GET /api/v1/staff/titles/{id}', () => {
    service.getTitleById(1).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/1');
    expect(request.request.method).toBe('GET');

    request.flush({});
  });

  it('should POST the exact CreateTitleRequest body to /api/v1/staff/titles', () => {
    service
      .createTitle({ title: 'Le Petit Prince', language: 'FR', authorIds: [1] })
      .subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ title: 'Le Petit Prince', language: 'FR', authorIds: [1] });

    request.flush({});
  });

  it('should PATCH an empty body when no field is provided to updateTitle', () => {
    service.updateTitle(1, {}).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({});

    request.flush({});
  });

  it('should PATCH an explicit null to clear isbn on updateTitle', () => {
    service.updateTitle(1, { isbn: null }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/1');
    expect(request.request.body).toEqual({ isbn: null });

    request.flush({});
  });

  it('should PATCH /api/v1/staff/titles/{id}/status with the exact body', () => {
    service.updateTitleStatus(1, { status: 'WITHDRAWN' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/titles/1/status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'WITHDRAWN' });

    request.flush({});
  });

  // ---------------------------------------------------------------
  // Author
  // ---------------------------------------------------------------

  it('should GET /api/v1/staff/authors with no params when none are provided', () => {
    service.searchAuthors().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/staff/authors' && req.method === 'GET',
    );
    expect(request.request.params.keys().length).toBe(0);

    request.flush({ content: [author], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/staff/authors preserving page=0 as an explicit param', () => {
    service.searchAuthors({ page: 0, size: 5 }).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/staff/authors' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('5');

    request.flush({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 });
  });

  it('should POST the exact CreateAuthorRequest body to /api/v1/staff/authors', () => {
    service.createAuthor({ fullName: 'Antoine de Saint-Exupéry' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/authors');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ fullName: 'Antoine de Saint-Exupéry' });

    request.flush({});
  });

  it('should PATCH an explicit null to clear nationality on updateAuthor', () => {
    service.updateAuthor(1, { nationality: null }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/authors/1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ nationality: null });

    request.flush({});
  });

  // ---------------------------------------------------------------
  // Genre
  // ---------------------------------------------------------------

  it('should GET /api/v1/staff/genres with no q param support (page/size only)', () => {
    service.searchGenres().subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/staff/genres' && req.method === 'GET',
    );
    expect(request.request.params.keys().length).toBe(0);

    request.flush({ content: [genre], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/staff/genres preserving page=0 as an explicit param', () => {
    service.searchGenres({ page: 0 }).subscribe();

    const request = httpTestingController.expectOne(
      (req) => req.url === '/api/v1/staff/genres' && req.method === 'GET',
    );
    expect(request.request.params.get('page')).toBe('0');

    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('should POST the exact CreateGenreRequest body to /api/v1/staff/genres', () => {
    service.createGenre({ code: 'FANTASY', label: 'Fantasy' }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/genres');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ code: 'FANTASY', label: 'Fantasy' });

    request.flush({});
  });

  it('should PATCH an explicit null to clear description on updateGenre', () => {
    service.updateGenre(1, { description: null }).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/genres/1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ description: null });

    request.flush({});
  });
});
