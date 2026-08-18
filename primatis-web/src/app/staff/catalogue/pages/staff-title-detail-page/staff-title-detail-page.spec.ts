import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../../auth/services/auth.service';
import { AuthorResponse } from '../../../../catalogue/models/author-response';
import { CopyResponse } from '../../../../catalogue/models/copy-response';
import { GenreResponse } from '../../../../catalogue/models/genre-response';
import { TitleDetailResponse } from '../../../../catalogue/models/title-detail-response';
import { UpdateTitleRequest } from '../../../../catalogue/models/update-title-request';
import { CopyApiService } from '../../../../catalogue/services/copy-api.service';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { StaffTitleDetailPage } from './staff-title-detail-page';

function buildAuthor(overrides: Partial<AuthorResponse> = {}): AuthorResponse {
  return { id: 1, fullName: 'Victor Hugo', birthDate: null, deathDate: null, nationality: null, biography: null, ...overrides };
}

function buildGenre(overrides: Partial<GenreResponse> = {}): GenreResponse {
  return { id: 1, code: 'CLASSIC', label: 'Classique', description: null, ...overrides };
}

function buildTitleDetail(overrides: Partial<TitleDetailResponse> = {}): TitleDetailResponse {
  return {
    id: 10,
    isbn: null,
    title: 'Les Misérables',
    subtitle: null,
    summary: null,
    publicationYear: 1862,
    language: 'FR',
    pageCount: null,
    publisher: null,
    coverImageUrl: null,
    titleStatus: 'ACTIVE',
    authors: [buildAuthor()],
    genres: [buildGenre()],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function buildCopy(overrides: Partial<CopyResponse> = {}): CopyResponse {
  return {
    id: 1,
    titleId: 10,
    inventoryCode: 'INV-001',
    location: null,
    copyCondition: 'GOOD',
    availabilityStatus: 'AVAILABLE',
    ...overrides,
  };
}

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { timestamp: new Date().toISOString(), status, error: 'Error', code, message, path: '/api/v1/staff/titles/10', fieldErrors: [] },
  });
}

describe('StaffTitleDetailPage', () => {
  let fixture: ComponentFixture<StaffTitleDetailPage>;
  let component: StaffTitleDetailPage;
  let staffCatalogueApiServiceMock: {
    getTitleById: ReturnType<typeof vi.fn>;
    updateTitle: ReturnType<typeof vi.fn>;
    updateTitleStatus: ReturnType<typeof vi.fn>;
    searchAuthors: ReturnType<typeof vi.fn>;
    searchGenres: ReturnType<typeof vi.fn>;
  };
  let copyApiServiceMock: {
    listCopies: ReturnType<typeof vi.fn>;
    createCopy: ReturnType<typeof vi.fn>;
    updateCopy: ReturnType<typeof vi.fn>;
    updateAvailability: ReturnType<typeof vi.fn>;
  };
  let authServiceMock: { hasPermission: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let confirmationServiceMock: { confirm: ReturnType<typeof vi.fn> };
  let paramMap$: BehaviorSubject<ParamMap>;

  function configure(rawId: string | null = '10'): void {
    paramMap$ = new BehaviorSubject<ParamMap>(convertToParamMap(rawId === null ? {} : { id: rawId }));

    staffCatalogueApiServiceMock = {
      getTitleById: vi.fn().mockReturnValue(of(buildTitleDetail())),
      updateTitle: vi.fn().mockReturnValue(of(buildTitleDetail())),
      updateTitleStatus: vi.fn().mockReturnValue(of(buildTitleDetail({ titleStatus: 'WITHDRAWN' }))),
      searchAuthors: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
      searchGenres: vi.fn().mockReturnValue(of({ content: [buildGenre()], page: 0, size: 100, totalElements: 1, totalPages: 1 })),
    };
    copyApiServiceMock = {
      listCopies: vi.fn().mockReturnValue(of([buildCopy()])),
      createCopy: vi.fn().mockReturnValue(of(buildCopy({ id: 2, inventoryCode: 'INV-002' }))),
      updateCopy: vi.fn().mockReturnValue(of(buildCopy())),
      updateAvailability: vi.fn().mockReturnValue(of(buildCopy({ availabilityStatus: 'UNAVAILABLE' }))),
    };
    authServiceMock = { hasPermission: vi.fn().mockReturnValue(true) };
    messageServiceMock = { add: vi.fn() };
    confirmationServiceMock = { confirm: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffTitleDetailPage],
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$ } },
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
        { provide: CopyApiService, useValue: copyApiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffTitleDetailPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function accept(): void {
    const calls = confirmationServiceMock.confirm.mock.calls;
    calls[calls.length - 1][0].accept();
  }

  beforeEach(() => configure());

  // ---------------------------------------------------------------
  // Chargement
  // ---------------------------------------------------------------

  it('should load the Title by the numeric route id', () => {
    createComponent();

    expect(staffCatalogueApiServiceMock.getTitleById).toHaveBeenCalledWith(10);
    expect(component.title()).toEqual(buildTitleDetail());
  });

  it('should not call the API when the route id is not numeric', () => {
    configure('abc');
    createComponent();

    expect(staffCatalogueApiServiceMock.getTitleById).not.toHaveBeenCalled();
    expect(component.titleError()?.message).toBe('Identifiant de titre invalide.');
  });

  it('should load Copies when COPY_READ is granted', () => {
    createComponent();

    expect(copyApiServiceMock.listCopies).toHaveBeenCalledWith(10);
    expect(component.copies()).toEqual([buildCopy()]);
  });

  it('should not load Copies when COPY_READ is not granted', () => {
    authServiceMock.hasPermission.mockImplementation((permission: string) => permission !== 'COPY_READ');
    createComponent();

    expect(copyApiServiceMock.listCopies).not.toHaveBeenCalled();
  });

  it('should show a business error message when the Title fails to load', () => {
    staffCatalogueApiServiceMock.getTitleById.mockReturnValue(throwError(() => apiHttpError(404, 'TITLE_NOT_FOUND', 'Aucun titre pour cet identifiant.')));
    createComponent();

    expect(component.titleError()?.message).toBe('Aucun titre pour cet identifiant.');
  });

  // ---------------------------------------------------------------
  // PATCH sparse Title
  // ---------------------------------------------------------------

  it('should send no request when nothing changed', () => {
    createComponent();

    component.submitUpdate();

    expect(staffCatalogueApiServiceMock.updateTitle).not.toHaveBeenCalled();
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'info' }));
  });

  it('should build a sparse request with only the changed field', () => {
    createComponent();
    component.form.controls.publisher.setValue('Gallimard');

    component.submitUpdate();

    const [titleId, request] = staffCatalogueApiServiceMock.updateTitle.mock.calls[0] as [number, UpdateTitleRequest];
    expect(titleId).toBe(10);
    expect(request).toEqual({ publisher: 'Gallimard' });
  });

  it('should send null explicitly when an optional field is cleared', () => {
    staffCatalogueApiServiceMock.getTitleById.mockReturnValue(of(buildTitleDetail({ isbn: '9780140449266' })));
    createComponent();
    component.form.controls.isbn.setValue('');

    component.submitUpdate();

    const request = staffCatalogueApiServiceMock.updateTitle.mock.calls[0][1] as UpdateTitleRequest;
    expect(request).toEqual({ isbn: null });
  });

  it('should diff authorIds/genreIds as sets, ignoring pure reordering', () => {
    createComponent();
    const author = buildAuthor();
    component.onAuthorsChange([author]);
    component.onGenresChange([buildGenre()]);

    component.submitUpdate();

    expect(staffCatalogueApiServiceMock.updateTitle).not.toHaveBeenCalled();
  });

  it('should include authorIds when the author selection actually changes', () => {
    createComponent();
    component.onAuthorsChange([buildAuthor({ id: 2 }), buildAuthor({ id: 3 })]);

    component.submitUpdate();

    const request = staffCatalogueApiServiceMock.updateTitle.mock.calls[0][1] as UpdateTitleRequest;
    expect(request.authorIds).toEqual([2, 3]);
  });

  it('should require at least one author before submitting', () => {
    createComponent();
    component.onAuthorsChange([]);

    component.submitUpdate();

    expect(component.authorsInvalid).toBe(true);
    expect(staffCatalogueApiServiceMock.updateTitle).not.toHaveBeenCalled();
  });

  it('should show a success toast and refresh the Title after a successful update', () => {
    staffCatalogueApiServiceMock.updateTitle.mockReturnValue(of(buildTitleDetail({ publisher: 'Gallimard' })));
    createComponent();
    component.form.controls.publisher.setValue('Gallimard');

    component.submitUpdate();

    expect(component.title()?.publisher).toBe('Gallimard');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show the backend error message on a business error', () => {
    staffCatalogueApiServiceMock.updateTitle.mockReturnValue(throwError(() => apiHttpError(409, 'ISBN_ALREADY_EXISTS', 'Un titre existe déjà avec cet ISBN.')));
    createComponent();
    component.form.controls.publisher.setValue('Gallimard');

    component.submitUpdate();

    expect(component.updateErrorMessage()).toBe('Un titre existe déjà avec cet ISBN.');
  });

  // ---------------------------------------------------------------
  // Title status
  // ---------------------------------------------------------------

  it('should label the status action "Retirer du catalogue" when ACTIVE', () => {
    createComponent();

    expect(component.statusActionLabel).toBe('Retirer du catalogue');
  });

  it('should label the status action "Réintégrer au catalogue" when WITHDRAWN', () => {
    staffCatalogueApiServiceMock.getTitleById.mockReturnValue(of(buildTitleDetail({ titleStatus: 'WITHDRAWN' })));
    createComponent();

    expect(component.statusActionLabel).toBe('Réintégrer au catalogue');
  });

  it('should require confirmation before changing the Title status', () => {
    createComponent();

    component.confirmToggleStatus();

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(staffCatalogueApiServiceMock.updateTitleStatus).not.toHaveBeenCalled();
  });

  it('should PATCH the status and show a success toast once confirmed', () => {
    createComponent();

    component.confirmToggleStatus();
    accept();

    expect(staffCatalogueApiServiceMock.updateTitleStatus).toHaveBeenCalledWith(10, { status: 'WITHDRAWN' });
    expect(component.title()?.titleStatus).toBe('WITHDRAWN');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should show an error toast when the status change fails', () => {
    staffCatalogueApiServiceMock.updateTitleStatus.mockReturnValue(throwError(() => apiHttpError(404, 'TITLE_NOT_FOUND', 'Aucun titre.')));
    createComponent();

    component.confirmToggleStatus();
    accept();

    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });

  // ---------------------------------------------------------------
  // Copies
  // ---------------------------------------------------------------

  it('should add a newly created Copy to the list', () => {
    createComponent();

    component.onCopyDialogSaved(buildCopy({ id: 2, inventoryCode: 'INV-002' }));

    expect(component.copies().map((c) => c.id)).toEqual([1, 2]);
  });

  it('should update an edited Copy in place, not duplicate it', () => {
    createComponent();

    component.onCopyDialogSaved(buildCopy({ inventoryCode: 'INV-001-CORRECTED' }));

    expect(component.copies()).toHaveLength(1);
    expect(component.copies()[0].inventoryCode).toBe('INV-001-CORRECTED');
  });

  it('should require confirmation before toggling availability', () => {
    createComponent();
    const copy = component.copies()[0];

    component.confirmToggleAvailability(copy);

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(copyApiServiceMock.updateAvailability).not.toHaveBeenCalled();
  });

  it('should toggle AVAILABLE -> UNAVAILABLE once confirmed', () => {
    createComponent();
    const copy = component.copies()[0];

    component.confirmToggleAvailability(copy);
    accept();

    expect(copyApiServiceMock.updateAvailability).toHaveBeenCalledWith(10, 1, { status: 'UNAVAILABLE' });
    expect(component.copies()[0].availabilityStatus).toBe('UNAVAILABLE');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should toggle UNAVAILABLE -> AVAILABLE once confirmed', () => {
    copyApiServiceMock.listCopies.mockReturnValue(of([buildCopy({ availabilityStatus: 'UNAVAILABLE' })]));
    copyApiServiceMock.updateAvailability.mockReturnValue(of(buildCopy({ availabilityStatus: 'AVAILABLE' })));
    createComponent();
    const copy = component.copies()[0];

    component.confirmToggleAvailability(copy);
    accept();

    expect(copyApiServiceMock.updateAvailability).toHaveBeenCalledWith(10, 1, { status: 'AVAILABLE' });
  });

  it('should never offer an availability action for ON_LOAN Copies', () => {
    copyApiServiceMock.listCopies.mockReturnValue(of([buildCopy({ availabilityStatus: 'ON_LOAN' })]));
    createComponent();

    expect(component.canToggleAvailability(component.copies()[0])).toBe(false);
  });

  it('should never offer an availability action for RESERVED Copies', () => {
    copyApiServiceMock.listCopies.mockReturnValue(of([buildCopy({ availabilityStatus: 'RESERVED' })]));
    createComponent();

    expect(component.canToggleAvailability(component.copies()[0])).toBe(false);
  });

  it('should discourage (but not forbid) offering AVAILABLE for a LOST Copy — UX only, backend remains authoritative', () => {
    copyApiServiceMock.listCopies.mockReturnValue(of([buildCopy({ copyCondition: 'LOST', availabilityStatus: 'UNAVAILABLE' })]));
    createComponent();

    expect(component.canOfferAvailable(component.copies()[0])).toBe(false);
    expect(component.canToggleAvailability(component.copies()[0])).toBe(true);
  });

  it('should show an error toast when the availability change fails', () => {
    copyApiServiceMock.updateAvailability.mockReturnValue(throwError(() => apiHttpError(409, 'COPY_AVAILABILITY_WORKFLOW_MANAGED', 'ON_LOAN/RESERVED refusé.')));
    createComponent();
    const copy = component.copies()[0];

    component.confirmToggleAvailability(copy);
    accept();

    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });
});
