import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { TitleDetailResponse } from '../../models/title-detail-response';
import { CatalogueApiService } from '../../services/catalogue-api.service';
import { TitleDetailPage } from './title-detail-page';

function buildTitleDetail(overrides: Partial<TitleDetailResponse> = {}): TitleDetailResponse {
  return {
    id: 1,
    isbn: '9782070409181',
    title: 'Les Misérables',
    subtitle: null,
    summary: 'Une fresque sociale du XIXe siècle.',
    publicationYear: 1862,
    language: 'FR',
    pageCount: 1500,
    publisher: 'Gallimard',
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
    authors: [{ id: 1, fullName: 'Victor Hugo', birthDate: null, deathDate: null, nationality: null, biography: null }],
    genres: [{ id: 1, code: 'CLASSIC', label: 'Classique', description: null }],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function apiHttpError(code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 404,
    error: {
      timestamp: new Date().toISOString(),
      status: 404,
      error: 'Not Found',
      code,
      message,
      path: '/api/v1/titles/999',
      fieldErrors: [],
    },
  });
}

describe('TitleDetailPage', () => {
  let fixture: ComponentFixture<TitleDetailPage>;
  let component: TitleDetailPage;
  let catalogueApiServiceMock: { getTitleById: ReturnType<typeof vi.fn> };
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  function configure(initialId = '1'): void {
    paramMap$ = new BehaviorSubject(convertToParamMap({ id: initialId }));
    catalogueApiServiceMock = { getTitleById: vi.fn().mockReturnValue(of(buildTitleDetail())) };

    TestBed.configureTestingModule({
      imports: [TitleDetailPage],
      providers: [
        { provide: CatalogueApiService, useValue: catalogueApiServiceMock },
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$ } },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(TitleDetailPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should call getTitleById with the numeric id from the route', () => {
    configure('42');
    createComponent();

    expect(catalogueApiServiceMock.getTitleById).toHaveBeenCalledWith(42);
  });

  it('should render the loaded Title detail', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Les Misérables');
    expect(fixture.nativeElement.textContent).toContain('Une fresque sociale du XIXe siècle.');
  });

  it('should render authors', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Victor Hugo');
  });

  it('should render genres', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Classique');
  });

  it('should omit nullable fields instead of rendering null/undefined', () => {
    configure();
    catalogueApiServiceMock.getTitleById.mockReturnValue(
      of(buildTitleDetail({ subtitle: null, isbn: null, publisher: null, pageCount: null, summary: null })),
    );
    createComponent();

    expect(fixture.nativeElement.textContent).not.toContain('null');
    expect(fixture.nativeElement.textContent).not.toContain('undefined');
  });

  it('should not call the API when the route id is not numeric', () => {
    configure('abc');
    createComponent();

    expect(catalogueApiServiceMock.getTitleById).not.toHaveBeenCalled();
  });

  it('should show an ErrorState when the route id is not numeric', () => {
    configure('abc');
    createComponent();

    expect(component.error()?.message).toBe('Identifiant de titre invalide.');
    expect(fixture.nativeElement.textContent).toContain('Identifiant de titre invalide.');
  });

  it('should show an ErrorState (not an empty state) on a 404 TITLE_NOT_FOUND', () => {
    configure('999');
    catalogueApiServiceMock.getTitleById.mockReturnValue(
      throwError(() => apiHttpError('TITLE_NOT_FOUND', 'Aucun titre disponible pour l’identifiant 999.')),
    );
    createComponent();

    expect(component.error()?.message).toBe('Aucun titre disponible pour l’identifiant 999.');
    expect(fixture.nativeElement.querySelector('.error-state')).not.toBeNull();
  });

  it('should show a generic ErrorState message on a network error', () => {
    configure();
    catalogueApiServiceMock.getTitleById.mockReturnValue(throwError(() => new Error('boom')));
    createComponent();

    expect(component.error()?.message).toBe('Une erreur est survenue. Veuillez réessayer.');
  });

  it('should show the loading state before the response arrives', () => {
    configure();
    // Observable never emits synchronously: keeps the component in its initial loading state.
    catalogueApiServiceMock.getTitleById.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();

    expect(component.loading()).toBe(true);
    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });
});
