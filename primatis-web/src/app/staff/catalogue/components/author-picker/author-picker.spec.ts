import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthorResponse } from '../../../../catalogue/models/author-response';
import { AuthorSearchParams } from '../../../../catalogue/models/author-search-params';
import { PageResponse } from '../../../../core/models/page-response';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { AuthorPicker } from './author-picker';

function buildAuthor(overrides: Partial<AuthorResponse> = {}): AuthorResponse {
  return {
    id: 1,
    fullName: 'Victor Hugo',
    birthDate: null,
    deathDate: null,
    nationality: null,
    biography: null,
    ...overrides,
  };
}

function buildPage(content: AuthorResponse[]): PageResponse<AuthorResponse> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe('AuthorPicker', () => {
  let fixture: ComponentFixture<AuthorPicker>;
  let component: AuthorPicker;
  let staffCatalogueApiServiceMock: { searchAuthors: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffCatalogueApiServiceMock = { searchAuthors: vi.fn().mockReturnValue(of(buildPage([]))) };

    TestBed.configureTestingModule({
      imports: [AuthorPicker],
      providers: [
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
        { provide: MessageService, useValue: { add: vi.fn() } },
      ],
    });
  }

  function createComponent(initialAuthors: AuthorResponse[] = []): void {
    fixture = TestBed.createComponent(AuthorPicker);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('initialAuthors', initialAuthors);
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    configure();
  });

  afterEach(() => vi.useRealTimers());

  it('should initialize selected authors from initialAuthors', () => {
    const author = buildAuthor();
    createComponent([author]);

    expect(component.selected()).toEqual([author]);
  });

  it('should not search when the input is empty', () => {
    createComponent();

    component.onSearchInput('');
    vi.advanceTimersByTime(300);

    expect(staffCatalogueApiServiceMock.searchAuthors).not.toHaveBeenCalled();
  });

  it('should debounce the search and call searchAuthors with q/page/size', () => {
    createComponent();

    component.onSearchInput('Hugo');
    vi.advanceTimersByTime(299);
    expect(staffCatalogueApiServiceMock.searchAuthors).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    const params = staffCatalogueApiServiceMock.searchAuthors.mock.calls[0][0] as AuthorSearchParams;
    expect(params).toEqual({ q: 'Hugo', page: 0, size: 20 });
  });

  it('should display search results and allow adding an author by id', () => {
    const author = buildAuthor({ id: 42, fullName: 'Victor Hugo' });
    staffCatalogueApiServiceMock.searchAuthors.mockReturnValue(of(buildPage([author])));
    createComponent();

    component.onSearchInput('Hugo');
    vi.advanceTimersByTime(300);
    component.add(author);

    expect(component.selected()).toEqual([author]);
  });

  it('should allow selecting two distinct authors sharing the same fullName (homonyms)', () => {
    const first = buildAuthor({ id: 1, fullName: 'Jean Dupont' });
    const second = buildAuthor({ id: 2, fullName: 'Jean Dupont' });
    createComponent();

    component.add(first);
    component.add(second);

    expect(component.selected()).toEqual([first, second]);
  });

  it('should deduplicate only by id, never by fullName', () => {
    const author = buildAuthor({ id: 1, fullName: 'Jean Dupont' });
    createComponent([author]);

    component.add({ ...author });

    expect(component.selected()).toHaveLength(1);
  });

  it('should remove a selected author and emit selectionChange', () => {
    const author = buildAuthor();
    createComponent([author]);
    const emitted: AuthorResponse[][] = [];
    component.selectionChange.subscribe((value) => emitted.push(value));

    component.remove(author);

    expect(component.selected()).toEqual([]);
    expect(emitted.at(-1)).toEqual([]);
  });

  it('should add the newly created author to the selection when the dialog was opened in create mode', () => {
    createComponent();
    component.openCreateDialog();

    component.onDialogSaved(buildAuthor({ id: 99, fullName: 'Nouvel Auteur' }));

    expect(component.selected().map((a) => a.id)).toContain(99);
    expect(component.dialogVisible()).toBe(false);
  });

  it('should update the selected author in place when edited (not add a duplicate)', () => {
    const author = buildAuthor({ id: 1, fullName: 'Victor Hugo' });
    createComponent([author]);
    component.openEditDialog(author);

    component.onDialogSaved({ ...author, fullName: 'Victor-Marie Hugo' });

    expect(component.selected()).toEqual([{ ...author, fullName: 'Victor-Marie Hugo' }]);
  });

  it('should show an error state when the search fails', () => {
    staffCatalogueApiServiceMock.searchAuthors.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: {
              timestamp: new Date().toISOString(),
              status: 500,
              error: 'Internal Server Error',
              code: 'INTERNAL_ERROR',
              message: 'Erreur serveur.',
              path: '/api/v1/staff/authors',
              fieldErrors: [],
            },
          }),
      ),
    );
    createComponent();

    component.onSearchInput('Hugo');
    vi.advanceTimersByTime(300);

    expect(component.searchError()?.message).toBe('Erreur serveur.');
  });
});
