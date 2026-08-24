import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../core/models/page-response';
import { ArticleSummaryResponse } from '../../models/article-summary-response';
import { ArticleApiService } from '../../services/article-api.service';
import { ArticleListPage } from './article-list-page';

function buildArticle(overrides: Partial<ArticleSummaryResponse> = {}): ArticleSummaryResponse {
  return {
    id: 1,
    title: 'Les horaires d’été',
    summary: 'La bibliothèque adapte ses horaires pour la période estivale.',
    slug: 'les-horaires-dete',
    author: { id: 10, firstName: 'Prénom', lastName: 'Nom' },
    publishedAt: '2026-08-01T10:00:00Z',
    ...overrides,
  };
}

function buildPage(
  content: ArticleSummaryResponse[],
  totalElements = content.length,
): PageResponse<ArticleSummaryResponse> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)) };
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

describe('ArticleListPage', () => {
  let fixture: ComponentFixture<ArticleListPage>;
  let component: ArticleListPage;
  let articleApiServiceMock: { listPublishedArticles: ReturnType<typeof vi.fn> };

  function configure(): void {
    articleApiServiceMock = { listPublishedArticles: vi.fn().mockReturnValue(of(buildPage([buildArticle()]))) };

    TestBed.configureTestingModule({
      imports: [ArticleListPage],
      providers: [provideRouter([]), { provide: ArticleApiService, useValue: articleApiServiceMock }],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(ArticleListPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function lastCall(): [number, number] {
    return articleApiServiceMock.listPublishedArticles.mock.calls.at(-1) as [number, number];
  }

  beforeEach(() => configure());

  it('should load Articles on initial construction with page=0 and the default size', () => {
    createComponent();

    expect(articleApiServiceMock.listPublishedArticles).toHaveBeenCalledTimes(1);
    expect(lastCall()).toEqual([0, 20]);
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
    createComponent();

    expect(component.rows().map((row) => row.title)).toEqual(['Les horaires d’été', 'Nouvelle collection BD']);
    expect(component.totalRecords()).toBe(2);
  });

  it('should render title, summary, author and publishedAt for each row', () => {
    createComponent();

    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Les horaires d’été');
    expect(text).toContain('La bibliothèque adapte ses horaires pour la période estivale.');
    expect(text).toContain('Prénom Nom');
    expect(text).toContain('2026-08-01T10:00:00Z');
  });

  it('should never call the API more than once per page (no N+1 detail fetch)', () => {
    articleApiServiceMock.listPublishedArticles.mockReturnValue(
      of(buildPage([buildArticle({ id: 1 }), buildArticle({ id: 2 }), buildArticle({ id: 3 })])),
    );
    createComponent();

    expect(articleApiServiceMock.listPublishedArticles).toHaveBeenCalledTimes(1);
  });

  it('should render a link to the Article detail page using the slug', () => {
    articleApiServiceMock.listPublishedArticles.mockReturnValue(of(buildPage([buildArticle({ slug: 'mon-slug' })])));
    createComponent();

    const link: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/articles/mon-slug"]');
    expect(link).not.toBeNull();
  });

  it('should reload with the requested page/size on lazy load', () => {
    createComponent();
    articleApiServiceMock.listPublishedArticles.mockClear();

    component.onLazyLoad({ first: 40, rows: 20 });

    expect(lastCall()).toEqual([2, 20]);
  });

  it('should fall back to defaults when the lazy load event omits first/rows', () => {
    createComponent();
    articleApiServiceMock.listPublishedArticles.mockClear();

    component.onLazyLoad({});

    expect(lastCall()).toEqual([0, 20]);
  });

  it('should show the loading state before the first response arrives', () => {
    // Observable never emits synchronously: keeps the component in its initial loading state.
    articleApiServiceMock.listPublishedArticles.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();

    expect(component.loading()).toBe(true);
    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when no Article is published and there is no error', () => {
    articleApiServiceMock.listPublishedArticles.mockReturnValue(of(buildPage([])));
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Aucun article');
  });

  it('should not render "null" when summary is null', () => {
    articleApiServiceMock.listPublishedArticles.mockReturnValue(of(buildPage([buildArticle({ summary: null })])));
    createComponent();

    expect(fixture.nativeElement.textContent).not.toContain('null');
  });

  it('should show the error state when the request fails', () => {
    articleApiServiceMock.listPublishedArticles.mockReturnValue(
      throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();

    expect(component.error()?.message).toBe('Erreur serveur.');
    expect(fixture.nativeElement.textContent).toContain('Erreur serveur.');
  });

  it('should retry the last page/size when retry() is called', () => {
    articleApiServiceMock.listPublishedArticles.mockReturnValue(
      throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')),
    );
    createComponent();
    component.onLazyLoad({ first: 40, rows: 20 });
    articleApiServiceMock.listPublishedArticles.mockClear();
    articleApiServiceMock.listPublishedArticles.mockReturnValue(of(buildPage([buildArticle()])));

    component.retry();

    expect(lastCall()).toEqual([2, 20]);
    expect(component.error()).toBeNull();
  });
});
