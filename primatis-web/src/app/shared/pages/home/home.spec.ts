import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ArticleSummaryResponse } from '../../../articles/models/article-summary-response';
import { ArticleApiService } from '../../../articles/services/article-api.service';
import { AuthService } from '../../../auth/services/auth.service';
import { TitleResponse } from '../../../catalogue/models/title-response';
import { CatalogueApiService } from '../../../catalogue/services/catalogue-api.service';
import { PageResponse } from '../../../core/models/page-response';
import { Home } from './home';

function buildArticle(overrides: Partial<ArticleSummaryResponse> = {}): ArticleSummaryResponse {
  return {
    id: 1,
    title: 'Les horaires d’été',
    summary: 'La bibliothèque adapte ses horaires pour la période estivale.',
    slug: 'les-horaires-dete',
    author: { id: 10, firstName: 'Prénom', lastName: 'Nom' },
    publishedAt: '2026-08-01T10:00:00Z',
    imageUrl: null,
    ...overrides,
  };
}

function buildTitle(overrides: Partial<TitleResponse> = {}): TitleResponse {
  return {
    id: 1,
    isbn: '9780000000001',
    title: 'L’Univers en expansion',
    subtitle: null,
    publicationYear: 2023,
    language: 'FR',
    publisher: null,
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
    ...overrides,
  };
}

function buildPage<T>(content: T[], totalElements = content.length, size = 3): PageResponse<T> {
  return {
    content,
    page: 0,
    size,
    totalElements,
    totalPages: Math.max(1, Math.ceil(totalElements / size)),
  };
}

function apiHttpError(code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 500,
    error: {
      timestamp: new Date().toISOString(),
      status: 500,
      error: 'Internal Server Error',
      code,
      message,
      path: '/api/v1/articles',
      fieldErrors: [],
    },
  });
}

describe('Home', () => {
  let fixture: ComponentFixture<Home>;
  let articleApiServiceMock: { listPublishedArticles: ReturnType<typeof vi.fn> };
  let catalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };
  let authServiceMock: {
    authenticated: ReturnType<typeof vi.fn>;
    hasRole: ReturnType<typeof vi.fn>;
  };

  function configure(): void {
    articleApiServiceMock = {
      listPublishedArticles: vi.fn().mockReturnValue(of(buildPage([buildArticle()]))),
    };
    catalogueApiServiceMock = {
      searchTitles: vi.fn().mockReturnValue(of(buildPage([buildTitle()], 1, 6))),
    };
    authServiceMock = {
      authenticated: vi.fn().mockReturnValue(false),
      hasRole: vi.fn().mockReturnValue(false),
    };

    TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        provideRouter([]),
        { provide: ArticleApiService, useValue: articleApiServiceMock },
        { provide: CatalogueApiService, useValue: catalogueApiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
      ],
    });
  }

  function render(): void {
    fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should render exactly one h1 carrying the PRIMATIS brand', () => {
    render();

    const headings: HTMLHeadingElement[] = fixture.nativeElement.querySelectorAll('h1');
    expect(headings.length).toBe(1);
    expect(headings[0].textContent).toContain('PRIMATIS');
  });

  it('should render a CTA linking to the public catalogue', () => {
    render();

    const links: HTMLAnchorElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('a[href="/catalogue"]'),
    );
    expect(links.length).toBeGreaterThan(0);
  });

  it('should render a Georges Lemaître section linking to the dedicated page', () => {
    render();

    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Georges Lemaître');
    expect(fixture.nativeElement.querySelector('a[href="/georges-lemaitre"]')).not.toBeNull();
  });

  it('should render the approved responsive hero portrait with a sober alt text', () => {
    render();

    const img: HTMLImageElement = fixture.nativeElement.querySelector('.home-hero-portrait');
    expect(img.getAttribute('src')).toBe('/assets/shared/portraits/georges-lemaitre-1024.webp');
    expect(img.getAttribute('alt')).toContain('Georges Lemaître');
  });

  it('should hide the decorative cosmological background from assistive technology', () => {
    render();

    const background = fixture.nativeElement.querySelector('.home-hero-background');
    expect(background.getAttribute('aria-hidden')).toBe('true');
  });

  it('should expose a real catalogue search form in the hero', () => {
    render();

    const search = fixture.nativeElement.querySelector('.home-hero-search');
    expect(search).not.toBeNull();
    expect(search.getAttribute('role')).toBe('search');
    expect(search.querySelector('input[name="q"]')).not.toBeNull();
  });

  it('should send a trimmed hero query to the real catalogue route', () => {
    render();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture.componentInstance.searchCatalogue(new Event('submit'), '  cosmologie  ');

    expect(navigate).toHaveBeenCalledWith(['/catalogue'], { queryParams: { q: 'cosmologie' } });
  });

  it('should never present a hero/section quote as an authentic Lemaître citation (undocumented)', () => {
    render();

    expect(fixture.nativeElement.querySelector('cite')).toBeNull();
    expect(fixture.nativeElement.querySelector('blockquote')).toBeNull();
  });

  describe('Articles ("À la une")', () => {
    it('should load the 3 most recent published Articles on construction', () => {
      render();

      expect(articleApiServiceMock.listPublishedArticles).toHaveBeenCalledWith(0, 3);
    });

    it('should display the Articles received from the API', () => {
      articleApiServiceMock.listPublishedArticles.mockReturnValue(
        of(
          buildPage([
            buildArticle({ id: 1, title: 'Les horaires d’été' }),
            buildArticle({ id: 2, title: 'Nouvelle collection BD' }),
          ]),
        ),
      );
      render();

      const text: string = fixture.nativeElement.textContent;
      expect(text).toContain('Les horaires d’été');
      expect(text).toContain('Nouvelle collection BD');
    });

    it('should link each Article to its detail page by slug', () => {
      articleApiServiceMock.listPublishedArticles.mockReturnValue(
        of(buildPage([buildArticle({ slug: 'mon-slug' })])),
      );
      render();

      expect(fixture.nativeElement.querySelector('a[href="/articles/mon-slug"]')).not.toBeNull();
    });

    it('should show the loading state before the first response arrives', () => {
      articleApiServiceMock.listPublishedArticles.mockReturnValue({
        subscribe: () => ({ unsubscribe: () => {} }),
      });
      render();

      expect(fixture.nativeElement.querySelectorAll('app-loading-state').length).toBeGreaterThan(0);
    });

    it('should show the empty state when no Article is published', () => {
      articleApiServiceMock.listPublishedArticles.mockReturnValue(of(buildPage([])));
      render();

      expect(fixture.nativeElement.textContent).toContain('Aucune actualité');
    });

    it('should show the error state when the request fails, with a working retry', () => {
      articleApiServiceMock.listPublishedArticles.mockReturnValue(
        throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')),
      );
      render();

      expect(fixture.nativeElement.textContent).toContain('Erreur serveur.');

      articleApiServiceMock.listPublishedArticles.mockReturnValue(of(buildPage([buildArticle()])));
      const retryButtons: HTMLButtonElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('app-error-state button'),
      );
      retryButtons[0]?.click();
      fixture.detectChanges();

      expect(articleApiServiceMock.listPublishedArticles).toHaveBeenCalledTimes(2);
    });
  });

  describe('Sélection du catalogue', () => {
    it('should load a page of real Titles on construction', () => {
      render();

      expect(catalogueApiServiceMock.searchTitles).toHaveBeenCalledWith({ page: 0, size: 6 });
    });

    it('should display the real total title count from the API, never a fabricated statistic', () => {
      catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([buildTitle()], 1247, 6)));
      render();

      expect(fixture.nativeElement.textContent).toContain('1247');
    });

    it('should link each Title to its real detail page', () => {
      catalogueApiServiceMock.searchTitles.mockReturnValue(
        of(buildPage([buildTitle({ id: 42 })], 1, 6)),
      );
      render();

      expect(fixture.nativeElement.querySelector('a[href="/catalogue/42"]')).not.toBeNull();
    });

    it('should show the approved generic cover fallback when a Title has no coverImageUrl (never fabricate one)', () => {
      catalogueApiServiceMock.searchTitles.mockReturnValue(
        of(buildPage([buildTitle({ coverImageUrl: null })], 1, 6)),
      );
      render();

      const fallback: HTMLImageElement = fixture.nativeElement.querySelector(
        '.home-title-card-fallback',
      );
      expect(fallback).not.toBeNull();
      expect(fallback.getAttribute('src')).toBe('/assets/fallbacks/cover.svg');
    });

    it('should show the empty state when the catalogue has no Title', () => {
      catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([], 0, 6)));
      render();

      expect(fixture.nativeElement.textContent).toContain('Catalogue vide');
    });

    it('should show the error state when the request fails, with a working retry', () => {
      catalogueApiServiceMock.searchTitles.mockReturnValue(
        throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')),
      );
      render();

      const errorStates = fixture.nativeElement.querySelectorAll('app-error-state');
      expect(errorStates.length).toBeGreaterThan(0);

      catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([buildTitle()], 1, 6)));
      const retryButtons: HTMLButtonElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('app-error-state button'),
      );
      retryButtons[retryButtons.length - 1]?.click();
      fixture.detectChanges();

      expect(catalogueApiServiceMock.searchTitles).toHaveBeenCalledTimes(2);
    });
  });

  describe('Accès rapide contextuel', () => {
    it('should link an anonymous visitor to /login', () => {
      authServiceMock.authenticated.mockReturnValue(false);
      render();

      expect(fixture.nativeElement.querySelector('a[href="/login"]')).not.toBeNull();
    });

    it('should link an authenticated MEMBER to their own profile', () => {
      authServiceMock.authenticated.mockReturnValue(true);
      authServiceMock.hasRole.mockImplementation((role: string) => role === 'ROLE_MEMBER');
      render();

      expect(fixture.nativeElement.querySelector('a[href="/member/profile"]')).not.toBeNull();
    });

    it('should link an authenticated LIBRARIAN/ADMIN to the staff area, never a Member-only route', () => {
      authServiceMock.authenticated.mockReturnValue(true);
      authServiceMock.hasRole.mockReturnValue(false);
      render();

      expect(fixture.nativeElement.querySelector('a[href="/staff/users"]')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('a[href="/member/profile"]')).toBeNull();
    });
  });

  it('should never invent a fake service (no agenda, no opening hours)', () => {
    render();
    const text: string = fixture.nativeElement.textContent.toLowerCase();
    expect(text).not.toContain('atelier');
    expect(text).not.toContain('horaire d’ouverture');
    expect(text).not.toContain('espace jeunes');
    expect(text).not.toContain('agenda');
  });
});
