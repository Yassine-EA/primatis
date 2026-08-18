import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthorResponse } from '../../../../catalogue/models/author-response';
import { CreateTitleRequest } from '../../../../catalogue/models/create-title-request';
import { GenreResponse } from '../../../../catalogue/models/genre-response';
import { TitleDetailResponse } from '../../../../catalogue/models/title-detail-response';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { StaffTitleCreatePage } from './staff-title-create-page';

function buildAuthor(overrides: Partial<AuthorResponse> = {}): AuthorResponse {
  return { id: 1, fullName: 'Victor Hugo', birthDate: null, deathDate: null, nationality: null, biography: null, ...overrides };
}

function buildGenre(overrides: Partial<GenreResponse> = {}): GenreResponse {
  return { id: 1, code: 'CLASSIC', label: 'Classique', description: null, ...overrides };
}

function buildTitleDetail(overrides: Partial<TitleDetailResponse> = {}): TitleDetailResponse {
  return {
    id: 99,
    isbn: null,
    title: 'Les Misérables',
    subtitle: null,
    summary: null,
    publicationYear: null,
    language: 'FR',
    pageCount: null,
    publisher: null,
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
    authors: [buildAuthor()],
    genres: [],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function apiHttpError(code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 409,
    error: {
      timestamp: new Date().toISOString(),
      status: 409,
      error: 'Conflict',
      code,
      message,
      path: '/api/v1/staff/titles',
      fieldErrors: [],
    },
  });
}

describe('StaffTitleCreatePage', () => {
  let fixture: ComponentFixture<StaffTitleCreatePage>;
  let component: StaffTitleCreatePage;
  let staffCatalogueApiServiceMock: {
    createTitle: ReturnType<typeof vi.fn>;
    searchAuthors: ReturnType<typeof vi.fn>;
    searchGenres: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  function configure(): void {
    staffCatalogueApiServiceMock = {
      createTitle: vi.fn().mockReturnValue(of(buildTitleDetail())),
      searchAuthors: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
      searchGenres: vi.fn().mockReturnValue(of({ content: [buildGenre()], page: 0, size: 100, totalElements: 1, totalPages: 1 })),
    };

    TestBed.configureTestingModule({
      imports: [StaffTitleCreatePage],
      providers: [
        provideRouter([]),
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
        { provide: MessageService, useValue: { add: vi.fn() } },
      ],
    });

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffTitleCreatePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should require title, language and at least one author', () => {
    createComponent();

    component.submit();

    expect(component.form.controls.title.touched).toBe(true);
    expect(component.form.controls.language.touched).toBe(true);
    expect(component.authorsInvalid).toBe(true);
    expect(staffCatalogueApiServiceMock.createTitle).not.toHaveBeenCalled();
  });

  it('should not submit when title/language are valid but no author is selected', () => {
    createComponent();
    component.form.controls.title.setValue('Germinal');
    component.form.controls.language.setValue('FR');

    component.submit();

    expect(staffCatalogueApiServiceMock.createTitle).not.toHaveBeenCalled();
  });

  it('should build the exact payload with only the provided optional fields', () => {
    createComponent();
    component.form.setValue({
      isbn: '',
      title: '  Germinal  ',
      subtitle: '',
      summary: '',
      publicationYear: '1885',
      language: 'FR',
      pageCount: '',
      publisher: '',
      coverImageUrl: '',
    });
    component.onAuthorsChange([buildAuthor({ id: 5 })]);

    component.submit();

    const request = staffCatalogueApiServiceMock.createTitle.mock.calls[0][0] as CreateTitleRequest;
    expect(request).toEqual({ title: 'Germinal', language: 'FR', authorIds: [5], publicationYear: 1885 });
  });

  it('should include genreIds only when at least one genre is selected', () => {
    createComponent();
    component.form.controls.title.setValue('Germinal');
    component.form.controls.language.setValue('FR');
    component.onAuthorsChange([buildAuthor()]);
    component.onGenresChange([buildGenre({ id: 7 })]);

    component.submit();

    const request = staffCatalogueApiServiceMock.createTitle.mock.calls[0][0] as CreateTitleRequest;
    expect(request.genreIds).toEqual([7]);
  });

  it('should navigate to the created Title detail page on success', () => {
    staffCatalogueApiServiceMock.createTitle.mockReturnValue(of(buildTitleDetail({ id: 123 })));
    createComponent();
    component.form.controls.title.setValue('Germinal');
    component.form.controls.language.setValue('FR');
    component.onAuthorsChange([buildAuthor()]);

    component.submit();

    expect(router.navigate).toHaveBeenCalledWith(['/staff/catalogue', 123]);
  });

  it('should show the backend error message on failure', () => {
    staffCatalogueApiServiceMock.createTitle.mockReturnValue(throwError(() => apiHttpError('ISBN_ALREADY_EXISTS', 'Un titre existe déjà avec cet ISBN.')));
    createComponent();
    component.form.controls.title.setValue('Germinal');
    component.form.controls.language.setValue('FR');
    component.onAuthorsChange([buildAuthor()]);

    component.submit();

    expect(component.errorMessage()).toBe('Un titre existe déjà avec cet ISBN.');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
