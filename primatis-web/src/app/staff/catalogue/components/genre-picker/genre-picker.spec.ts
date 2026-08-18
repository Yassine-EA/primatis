import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { GenreListParams } from '../../../../catalogue/models/genre-list-params';
import { GenreResponse } from '../../../../catalogue/models/genre-response';
import { PageResponse } from '../../../../core/models/page-response';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { GenrePicker } from './genre-picker';

function buildGenre(overrides: Partial<GenreResponse> = {}): GenreResponse {
  return { id: 1, code: 'CLASSIC', label: 'Classique', description: null, ...overrides };
}

function buildPage(content: GenreResponse[], page: number, totalPages: number): PageResponse<GenreResponse> {
  return { content, page, size: 100, totalElements: totalPages * 100, totalPages };
}

describe('GenrePicker', () => {
  let fixture: ComponentFixture<GenrePicker>;
  let component: GenrePicker;
  let staffCatalogueApiServiceMock: { searchGenres: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffCatalogueApiServiceMock = {
      searchGenres: vi.fn().mockReturnValue(of(buildPage([buildGenre()], 0, 1))),
    };

    TestBed.configureTestingModule({
      imports: [GenrePicker],
      providers: [
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
        { provide: MessageService, useValue: { add: vi.fn() } },
      ],
    });
  }

  function createComponent(initialGenres: GenreResponse[] = []): void {
    fixture = TestBed.createComponent(GenrePicker);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('initialGenres', initialGenres);
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should load all genres on init with page=0/size=100', () => {
    createComponent();

    const params = staffCatalogueApiServiceMock.searchGenres.mock.calls[0][0] as GenreListParams;
    expect(params).toEqual({ page: 0, size: 100 });
    expect(component.allGenres()).toEqual([buildGenre()]);
    expect(component.loading()).toBe(false);
  });

  it('should walk every page deterministically when totalPages > 1, without truncation', () => {
    const pageZero = buildGenre({ id: 1, code: 'A' });
    const pageOne = buildGenre({ id: 2, code: 'B' });
    const pageTwo = buildGenre({ id: 3, code: 'C' });
    staffCatalogueApiServiceMock.searchGenres.mockImplementation((params: GenreListParams) => {
      if (params.page === 0) return of(buildPage([pageZero], 0, 3));
      if (params.page === 1) return of(buildPage([pageOne], 1, 3));
      return of(buildPage([pageTwo], 2, 3));
    });

    createComponent();

    expect(staffCatalogueApiServiceMock.searchGenres).toHaveBeenCalledTimes(3);
    expect(component.allGenres().map((g) => g.id)).toEqual([1, 2, 3]);
  });

  it('should initialize selectedIds from initialGenres', () => {
    createComponent([buildGenre({ id: 7 })]);

    expect(component.selectedIds()).toEqual([7]);
  });

  it('should emit the resolved GenreResponse objects on selection change', () => {
    createComponent();
    const emitted: GenreResponse[][] = [];
    component.selectionChange.subscribe((value) => emitted.push(value));

    component.onSelectionChange([1]);

    expect(emitted.at(-1)).toEqual([buildGenre()]);
  });

  it('should append and select a newly created genre', () => {
    createComponent();
    component.openCreateDialog();

    component.onDialogSaved(buildGenre({ id: 99, label: 'Nouveau genre' }));

    expect(component.allGenres().map((g) => g.id)).toContain(99);
    expect(component.selectedIds()).toContain(99);
    expect(component.dialogVisible()).toBe(false);
  });

  it('should update an edited genre in place without duplicating it', () => {
    createComponent();
    const existing = component.allGenres()[0];
    component.openEditDialog(existing);

    component.onDialogSaved({ ...existing, label: 'Classique corrigé' });

    expect(component.allGenres()).toHaveLength(1);
    expect(component.allGenres()[0].label).toBe('Classique corrigé');
  });

  it('should show an error state when loading fails, with a retry that reloads', () => {
    staffCatalogueApiServiceMock.searchGenres.mockReturnValue(
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
              path: '/api/v1/staff/genres',
              fieldErrors: [],
            },
          }),
      ),
    );
    createComponent();

    expect(component.error()?.message).toBe('Erreur serveur.');

    staffCatalogueApiServiceMock.searchGenres.mockReturnValue(of(buildPage([buildGenre()], 0, 1)));
    component.retry();

    expect(component.error()).toBeNull();
    expect(component.allGenres()).toEqual([buildGenre()]);
  });
});
