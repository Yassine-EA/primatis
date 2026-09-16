import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
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
  let queryParamMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let navigateSpy: ReturnType<typeof vi.spyOn>;

  function configure(initialQuery: Record<string, string> = {}): void {
    TestBed.resetTestingModule();
    queryParamMap$ = new BehaviorSubject(convertToParamMap(initialQuery));
    catalogueApiServiceMock = { searchTitles: vi.fn().mockReturnValue(of(buildPage([buildTitle()]))) };

    TestBed.configureTestingModule({
      imports: [CataloguePage],
      providers: [
        provideRouter([]),
        { provide: CatalogueApiService, useValue: catalogueApiServiceMock },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: queryParamMap$.value }, queryParamMap: queryParamMap$ },
        },
      ],
    });

    const router = TestBed.inject(Router);
    navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
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

  it('should reload with the requested page/rows on paginator page change', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.onPageChange({ first: 40, rows: 20, page: 2, pageCount: 5 });

    expect(lastParams()).toEqual({ page: 2, size: 20 });
  });

  it('should fall back to defaults when the page change event omits first/rows', () => {
    createComponent();
    catalogueApiServiceMock.searchTitles.mockClear();

    component.onPageChange({});

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
    component.onPageChange({ first: 40, rows: 20 });
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
    component.onPageChange({ first: 40, rows: 20 });
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

  it('should keep the active q filter across a page change', () => {
    createComponent();
    component.filtersForm.controls.q.setValue('Zola');
    vi.advanceTimersByTime(300);
    catalogueApiServiceMock.searchTitles.mockClear();

    component.onPageChange({ first: 20, rows: 20 });

    expect(lastParams()).toEqual({ page: 1, size: 20, q: 'Zola' });
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

    expect(fixture.nativeElement.textContent).toContain('Aucun ouvrage trouvé');
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
    component.onPageChange({ first: 40, rows: 20 });
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

  it('should set the document title (DEV-15.5)', () => {
    createComponent();

    expect(document.title).toBe('Catalogue — PRIMATIS');
  });

  it('should show a fallback cover when coverImageUrl is null', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([buildTitle({ coverImageUrl: null })])));
    createComponent();

    expect(fixture.nativeElement.querySelector('.catalogue-card-cover .cover-fallback')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.catalogue-card-cover img')).toBeNull();
  });

  it('should render the real cover image when coverImageUrl is present', () => {
    catalogueApiServiceMock.searchTitles.mockReturnValue(
      of(buildPage([buildTitle({ coverImageUrl: 'https://covers.example/1.jpg' })])),
    );
    createComponent();

    const img: HTMLImageElement | null = fixture.nativeElement.querySelector('.catalogue-card-cover img');
    expect(img?.getAttribute('src')).toBe('https://covers.example/1.jpg');
  });

  it('should clear the q filter and reload when clearSearch() is called', () => {
    createComponent();
    component.filtersForm.controls.q.setValue('Zola');
    vi.advanceTimersByTime(300);
    catalogueApiServiceMock.searchTitles.mockClear();

    component.clearSearch();
    vi.advanceTimersByTime(300);

    expect(component.filtersForm.controls.q.value).toBe('');
    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should show the clear button only when a search term is present', () => {
    createComponent();
    expect(fixture.nativeElement.querySelector('.catalogue-search-clear')).toBeNull();

    component.filtersForm.controls.q.setValue('Zola');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.catalogue-search-clear')).not.toBeNull();
  });

  it('should reload immediately on form submission, without waiting for the debounce', () => {
    createComponent();
    component.filtersForm.controls.q.setValue('Zola');
    catalogueApiServiceMock.searchTitles.mockClear();

    component.onSubmit();

    expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Zola' });
  });

  it('should reset both q and language when resetFilters() is called', () => {
    createComponent();
    component.filtersForm.controls.q.setValue('Zola');
    component.filtersForm.controls.language.setValue('FR');
    vi.advanceTimersByTime(300);

    component.resetFilters();
    vi.advanceTimersByTime(300);

    expect(component.filtersForm.controls.q.value).toBe('');
    expect(component.filtersForm.controls.language.value).toBeNull();
  });

  it('should hide the "Réinitialiser" button when no filter is active', () => {
    createComponent();

    expect(fixture.nativeElement.querySelector('.catalogue-filters-reset')).toBeNull();
  });

  it('should show the "Réinitialiser" button once a filter is active', () => {
    createComponent();

    component.filtersForm.controls.language.setValue('FR');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.catalogue-filters-reset')).not.toBeNull();
  });

  describe('synchronisation URL (q)', () => {
    it('should initialise the search field from the initial ?q= query parameter', () => {
      configure({ q: 'cosmologie' });
      createComponent();

      expect(component.filtersForm.controls.q.value).toBe('cosmologie');
      expect(lastParams()).toEqual({ page: 0, size: 20, q: 'cosmologie' });
    });

    it('should update the URL (merged, replaceUrl) once the debounced search settles', () => {
      createComponent();
      navigateSpy.mockClear();

      component.filtersForm.controls.q.setValue('Zola');
      vi.advanceTimersByTime(300);

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({ queryParams: { q: 'Zola' }, queryParamsHandling: 'merge', replaceUrl: true }),
      );
    });

    it('should remove the q query parameter once the search is cleared', () => {
      createComponent();
      component.filtersForm.controls.q.setValue('Zola');
      vi.advanceTimersByTime(300);
      navigateSpy.mockClear();

      component.clearSearch();
      vi.advanceTimersByTime(300);

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({ queryParams: { q: null }, queryParamsHandling: 'merge', replaceUrl: true }),
      );
    });

    it('should apply an externally navigated ?q= change (e.g. a link from the Home page)', () => {
      createComponent();
      catalogueApiServiceMock.searchTitles.mockClear();

      queryParamMap$.next(convertToParamMap({ q: 'Einstein' }));
      vi.advanceTimersByTime(300);

      expect(component.filtersForm.controls.q.value).toBe('Einstein');
      expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Einstein' });
    });

    it('should never re-trigger a search when the URL merely reflects our own write (no feedback loop)', () => {
      createComponent();
      component.filtersForm.controls.q.setValue('Zola');
      vi.advanceTimersByTime(300);
      catalogueApiServiceMock.searchTitles.mockClear();

      // The URL now genuinely carries the same value we just wrote — simulates the Router echoing our own navigate().
      queryParamMap$.next(convertToParamMap({ q: 'Zola' }));
      vi.advanceTimersByTime(300);

      expect(catalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();
    });

    it('should allow searching the same term again after it was cleared in between (no distinctUntilChanged swallow, DEV-15)', () => {
      createComponent();
      component.filtersForm.controls.q.setValue('Zola');
      vi.advanceTimersByTime(300);
      component.clearSearch();
      vi.advanceTimersByTime(300);
      catalogueApiServiceMock.searchTitles.mockClear();

      component.filtersForm.controls.q.setValue('Zola');
      vi.advanceTimersByTime(300);

      expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Zola' });
    });
  });
});
