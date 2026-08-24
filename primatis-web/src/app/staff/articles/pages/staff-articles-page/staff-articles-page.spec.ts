import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../../core/models/page-response';
import { StaffArticleSummaryResponse } from '../../../../articles/models/staff-article-summary-response';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';
import { StaffArticlesPage } from './staff-articles-page';

function buildSummary(overrides: Partial<StaffArticleSummaryResponse> = {}): StaffArticleSummaryResponse {
  return {
    id: 1,
    title: 'Nouvelle acquisition',
    summary: 'Résumé',
    slug: 'nouvelle-acquisition',
    articleStatus: 'DRAFT',
    author: { id: 10, firstName: 'Prénom', lastName: 'Nom' },
    publishedAt: null,
    updatedAt: '2026-08-01T08:00:00Z',
    ...overrides,
  };
}

function buildPage(content: StaffArticleSummaryResponse[], totalElements = content.length): PageResponse<StaffArticleSummaryResponse> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)) };
}

function apiHttpError(message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 500,
    error: {
      timestamp: new Date().toISOString(),
      status: 500,
      error: 'Internal Server Error',
      code: 'INTERNAL_ERROR',
      message,
      path: '/api/v1/staff/articles',
      fieldErrors: [],
    },
  });
}

describe('StaffArticlesPage', () => {
  let fixture: ComponentFixture<StaffArticlesPage>;
  let component: StaffArticlesPage;
  let staffArticleApiServiceMock: { listStaffArticles: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffArticleApiServiceMock = { listStaffArticles: vi.fn().mockReturnValue(of(buildPage([buildSummary()]))) };

    TestBed.configureTestingModule({
      imports: [StaffArticlesPage],
      providers: [provideRouter([]), { provide: StaffArticleApiService, useValue: staffArticleApiServiceMock }],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffArticlesPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should load Articles on initial construction with page=0 and the default size', () => {
    createComponent();

    expect(staffArticleApiServiceMock.listStaffArticles).toHaveBeenCalledWith(0, 20);
    expect(component.rows()).toEqual([buildSummary()]);
    expect(component.loading()).toBe(false);
  });

  it('should display all three ArticleStatus values, not only PUBLISHED', () => {
    staffArticleApiServiceMock.listStaffArticles.mockReturnValue(
      of(
        buildPage([
          buildSummary({ id: 1, articleStatus: 'DRAFT', publishedAt: null }),
          buildSummary({ id: 2, articleStatus: 'PUBLISHED', publishedAt: '2026-08-01T08:00:00Z' }),
          buildSummary({ id: 3, articleStatus: 'ARCHIVED', publishedAt: '2026-08-01T08:00:00Z' }),
        ]),
      ),
    );
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('DRAFT');
    expect(fixture.nativeElement.textContent).toContain('PUBLISHED');
    expect(fixture.nativeElement.textContent).toContain('ARCHIVED');
  });

  it('should never render the article content in the list', () => {
    createComponent();

    expect(component.rows()[0]).not.toHaveProperty('content');
  });

  it('should render title/author/updatedAt/publishedAt columns', () => {
    createComponent();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Nouvelle acquisition');
    expect(text).toContain('Prénom Nom');
    expect(text).toContain('2026-08-01T08:00:00Z');
  });

  it('should link each row to its staff detail page by id', () => {
    createComponent();

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href="/staff/articles/1"]');
    expect(link).not.toBeNull();
  });

  it('should link to the create page', () => {
    createComponent();

    expect(fixture.nativeElement.querySelector('a[href="/staff/articles/new"]')).not.toBeNull();
  });

  it('should link to the Tag management page', () => {
    createComponent();

    expect(fixture.nativeElement.querySelector('a[href="/staff/articles/tags"]')).not.toBeNull();
  });

  it('should convert a lazy-load event to a 0-based page/size call', () => {
    createComponent();
    staffArticleApiServiceMock.listStaffArticles.mockClear();

    component.onLazyLoad({ first: 40, rows: 20 });

    expect(staffArticleApiServiceMock.listStaffArticles).toHaveBeenCalledWith(2, 20);
  });

  it('should show the empty state when no Article exists', () => {
    staffArticleApiServiceMock.listStaffArticles.mockReturnValue(of(buildPage([])));
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Aucun article');
  });

  it('should show the error state on load failure, with a retry that reloads', () => {
    staffArticleApiServiceMock.listStaffArticles.mockReturnValue(throwError(() => apiHttpError('Erreur serveur.')));
    createComponent();

    expect(component.error()?.message).toBe('Erreur serveur.');

    staffArticleApiServiceMock.listStaffArticles.mockReturnValue(of(buildPage([buildSummary()])));
    component.retry();

    expect(component.error()).toBeNull();
    expect(component.rows()).toEqual([buildSummary()]);
  });
});
