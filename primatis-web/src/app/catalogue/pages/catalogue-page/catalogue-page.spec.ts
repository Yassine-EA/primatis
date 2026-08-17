import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../core/models/page-response';
import { TitleResponse } from '../../models/title-response';
import { TitleSearchParams } from '../../models/title-search-params';
import { CatalogueApiService } from '../../services/catalogue-api.service';
import { CataloguePage } from './catalogue-page';

function buildTitle(overrides: Partial<TitleResponse> = {}): TitleResponse {
  return {
    id: 1,
    isbn: null,
    title: 'Les Misérables',
    subtitle: null,
    publicationYear: 1862,
    language: 'FR',
    publisher: null,
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
    ...overrides,
  };
}

function buildPage(content: TitleResponse[], totalElements = content.length): PageResponse<TitleResponse> {
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
      path: '/api/v1/titles',
      fieldErrors: [],
    },
  });
}

describe('CataloguePage', () => {
  let fixture: ComponentFixture<CataloguePage>;
  let component: CataloguePage;
  let catalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };

  function configure(): void {
    catalogueApiServiceMock = { searchTitles: vi.fn().mockReturnValue(of(buildPage([buildTitle()]))) };

    TestBed.configureTestingModule({
      imports: [CataloguePage],
      providers: [provideRouter([]), { provide: CatalogueApiService, useValue: catalogueApiServiceMock }],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(CataloguePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function lastParams(): TitleSearchParams {
    return catalogueApiServiceMock.searchTitles.mock.calls.at(-1)?.[0] as TitleSearchParams;
  }

  beforeEach(() => {
    vi.useFakeTimers();
    configure();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should load Titles on initial construction with page=0 and the default size', () => {
    createComponent();

    expect(catalogueApiServiceMock.searchTitles).toHaveBeenCalledTimes(1);
    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should display the Titles received from the API', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(
      of(buildPage([buildTitle({ id: 1, title: 'Les Misérables' }), buildTitle({ id: 2, title: 'Germinal' })])),
    );
    createComponent();

    expect(component.rows().map((row) => row.title)).toEqual(['Les Misérables', 'Germinal']);
    expect(component.totalRecords()).toBe(2);
  });

  it('should reload with the requested page/size on lazy load', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.onLazyLoad({ first: 40, rows: 20 });

    expect(lastParams()).toEqual({ page: 2, size: 20 });
  });

  it('should fall back to defaults when the lazy load event omits first/rows', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.onLazyLoad({});

    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should debounce the q filter and send it trimmed after the search request settles', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.q.setValue('  Misérables  ');
    vi.advanceTimersByTime(299);
    expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Misérables' });
  });

  it('should not send q when the trimmed value is empty', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.q.setValue('   ');
    vi.advanceTimersByTime(300);

    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should reset to page 0 when the q filter changes after paginating', () => {
    createComponent();
    component.onLazyLoad({ first: 40, rows: 20 });
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.q.setValue('Zola');
    vi.advanceTimersByTime(300);

    expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Zola' });
  });

  it('should send the language filter immediately without debounce', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.language.setValue('FR');

    expect(lastParams()).toEqual({ page: 0, size: 20, language: 'FR' });
  });

  it('should reset to page 0 when the language filter changes after paginating', () => {
    createComponent();
    component.onLazyLoad({ first: 40, rows: 20 });
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.language.setValue('EN');

    expect(lastParams()).toEqual({ page: 0, size: 20, language: 'EN' });
  });

  it('should omit the language filter again when reset to "Toutes les langues" (null)', () => {
    createComponent();
    component.filtersForm.controls.language.setValue('FR');
    catalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.language.setValue(null);

    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should show the loading state before the first response arrives', () => {
    // Observable never emits synchronously: keeps the component in its initial loading state.
    catalogueApiServiceMock.searchTitles.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();

    expect(component.loading()).toBe(true);
    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  it('should show the empty state when no Title matches and there is no error', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([])));
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Aucun titre');
  });

  it('should show the error state when the request fails', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();

    expect(component.error()?.message).toBe('Erreur serveur.');
    expect(fixture.nativeElement.textContent).toContain('Erreur serveur.');
  });

  it('should retry the last page/size when retry() is called', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();
    component.onLazyLoad({ first: 40, rows: 20 });
    catalogueApiServiceMock.searchTitles.mockClear();
    catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([buildTitle()])));

    component.retry();

    expect(lastParams()).toEqual({ page: 2, size: 20 });
    expect(component.error()).toBeNull();
  });

  it('should render a link to the Title detail page', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([buildTitle({ id: 42 })])));
    createComponent();

    const link: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/catalogue/42"]');
    expect(link).not.toBeNull();
  });
});
