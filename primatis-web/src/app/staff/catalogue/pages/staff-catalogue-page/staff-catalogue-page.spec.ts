import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../../core/models/page-response';
import { StaffTitleSearchParams } from '../../../../catalogue/models/title-search-params';
import { TitleResponse } from '../../../../catalogue/models/title-response';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { StaffCataloguePage } from './staff-catalogue-page';

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
      path: '/api/v1/staff/titles',
      fieldErrors: [],
    },
  });
}

describe('StaffCataloguePage', () => {
  let fixture: ComponentFixture<StaffCataloguePage>;
  let component: StaffCataloguePage;
  let staffCatalogueApiServiceMock: { searchTitles: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffCatalogueApiServiceMock = { searchTitles: vi.fn().mockReturnValue(of(buildPage([buildTitle()]))) };

    TestBed.configureTestingModule({
      imports: [StaffCataloguePage],
      providers: [provideRouter([]), { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock }],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffCataloguePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function lastParams(): StaffTitleSearchParams {
    return staffCatalogueApiServiceMock.searchTitles.mock.calls.at(-1)?.[0] as StaffTitleSearchParams;
  }

  beforeEach(() => {
    vi.useFakeTimers();
    configure();
  });

  afterEach(() => vi.useRealTimers());

  it('should load Titles on initial construction with page=0 and the default size', () => {
    createComponent();

    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });

  it('should reload with the requested page/size on lazy load', () => {
    createComponent();
    staffCatalogueApiServiceMock.searchTitles.mockClear();

    component.onLazyLoad({ first: 40, rows: 20 });

    expect(lastParams()).toEqual({ page: 2, size: 20 });
  });

  it('should debounce the q filter and reset to page 0', () => {
    createComponent();
    component.onLazyLoad({ first: 40, rows: 20 });
    staffCatalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.q.setValue('Zola');
    vi.advanceTimersByTime(299);
    expect(staffCatalogueApiServiceMock.searchTitles).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(lastParams()).toEqual({ page: 0, size: 20, q: 'Zola' });
  });

  it('should send the language filter immediately and reset to page 0', () => {
    createComponent();
    component.onLazyLoad({ first: 40, rows: 20 });
    staffCatalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.language.setValue('EN');

    expect(lastParams()).toEqual({ page: 0, size: 20, language: 'EN' });
  });

  it('should send the titleStatus filter immediately and reset to page 0', () => {
    createComponent();
    component.onLazyLoad({ first: 40, rows: 20 });
    staffCatalogueApiServiceMock.searchTitles.mockClear();

    component.filtersForm.controls.titleStatus.setValue('WITHDRAWN');

    expect(lastParams()).toEqual({ page: 0, size: 20, titleStatus: 'WITHDRAWN' });
  });

  it('should never send authorId or genreCode as list filters (K.7)', () => {
    createComponent();

    const request = lastParams() as Record<string, unknown>;
    expect(request).not.toHaveProperty('authorId');
    expect(request).not.toHaveProperty('genreCode');
  });

  it('should show the loading state before the first response arrives', () => {
    staffCatalogueApiServiceMock.searchTitles.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();

    expect(component.loading()).toBe(true);
  });

  it('should show the empty state when no Title matches and there is no error', () => {
    staffCatalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([])));
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Aucun titre');
  });

  it('should show the error state when the request fails', () => {
    staffCatalogueApiServiceMock.searchTitles.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();

    expect(component.error()?.message).toBe('Erreur serveur.');
  });

  it('should retry the last page/size when retry() is called', () => {
    staffCatalogueApiServiceMock.searchTitles.mockReturnValue(throwError(() => apiHttpError('INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();
    component.onLazyLoad({ first: 40, rows: 20 });
    staffCatalogueApiServiceMock.searchTitles.mockClear();
    staffCatalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([buildTitle()])));

    component.retry();

    expect(lastParams()).toEqual({ page: 2, size: 20 });
    expect(component.error()).toBeNull();
  });

  it('should render the "Créer un titre" link to /staff/catalogue/new', () => {
    createComponent();

    const link: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/staff/catalogue/new"]');
    expect(link).not.toBeNull();
  });

  it('should render a link to the Title detail page', () => {
    staffCatalogueApiServiceMock.searchTitles.mockReturnValue(of(buildPage([buildTitle({ id: 42 })])));
    createComponent();

    const link: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/staff/catalogue/42"]');
    expect(link).not.toBeNull();
  });
});
