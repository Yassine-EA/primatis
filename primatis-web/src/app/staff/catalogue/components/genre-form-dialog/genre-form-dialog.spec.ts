import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CreateGenreRequest } from '../../../../catalogue/models/create-genre-request';
import { GenreResponse } from '../../../../catalogue/models/genre-response';
import { UpdateGenreRequest } from '../../../../catalogue/models/update-genre-request';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { GenreFormDialog } from './genre-form-dialog';

function buildGenre(overrides: Partial<GenreResponse> = {}): GenreResponse {
  return { id: 1, code: 'CLASSIC', label: 'Classique', description: null, ...overrides };
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
      path: '/api/v1/staff/genres',
      fieldErrors: [],
    },
  });
}

describe('GenreFormDialog', () => {
  let fixture: ComponentFixture<GenreFormDialog>;
  let component: GenreFormDialog;
  let staffCatalogueApiServiceMock: { createGenre: ReturnType<typeof vi.fn>; updateGenre: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffCatalogueApiServiceMock = {
      createGenre: vi.fn().mockReturnValue(of(buildGenre())),
      updateGenre: vi.fn().mockReturnValue(of(buildGenre())),
    };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [GenreFormDialog],
      providers: [
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });
  }

  function createComponent(genreValue: GenreResponse | null, visible = true): void {
    fixture = TestBed.createComponent(GenreFormDialog);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('genre', genreValue);
    fixture.componentRef.setInput('visible', visible);
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should require code and label', () => {
    createComponent(null);

    component.submit();

    expect(component.form.controls.code.touched).toBe(true);
    expect(component.form.controls.label.touched).toBe(true);
    expect(staffCatalogueApiServiceMock.createGenre).not.toHaveBeenCalled();
  });

  it('should build a create request with only the provided description', () => {
    createComponent(null);
    component.form.setValue({ code: 'SCIFI', label: 'Science-fiction', description: '' });

    component.submit();

    const request = staffCatalogueApiServiceMock.createGenre.mock.calls[0][0] as CreateGenreRequest;
    expect(request).toEqual({ code: 'SCIFI', label: 'Science-fiction' });
  });

  it('should emit saved and show a success toast on create', () => {
    createComponent(null);
    component.form.setValue({ code: 'SCIFI', label: 'Science-fiction', description: '' });
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).toHaveBeenCalledWith(buildGenre());
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should prefill the form in edit mode', () => {
    createComponent(buildGenre({ code: 'CLASSIC', label: 'Classique' }));

    expect(component.form.controls.code.value).toBe('CLASSIC');
    expect(component.form.controls.label.value).toBe('Classique');
  });

  it('should build a sparse update request with only changed fields', () => {
    createComponent(buildGenre());
    component.form.controls.label.setValue('Classique français');

    component.submit();

    const [genreId, request] = staffCatalogueApiServiceMock.updateGenre.mock.calls[0] as [number, UpdateGenreRequest];
    expect(genreId).toBe(1);
    expect(request).toEqual({ label: 'Classique français' });
  });

  it('should close without calling the API when nothing changed', () => {
    createComponent(buildGenre());

    component.submit();

    expect(staffCatalogueApiServiceMock.updateGenre).not.toHaveBeenCalled();
  });

  it('should never validate code/label uniqueness locally', () => {
    createComponent(null);
    component.form.setValue({ code: 'CLASSIC', label: 'Classique', description: '' });

    component.submit();

    expect(staffCatalogueApiServiceMock.createGenre).toHaveBeenCalled();
  });

  it('should show the backend error message and a toast on failure', () => {
    staffCatalogueApiServiceMock.createGenre.mockReturnValue(
      throwError(() => apiHttpError('GENRE_CODE_ALREADY_EXISTS', 'Un genre existe déjà avec ce code.')),
    );
    createComponent(null);
    component.form.setValue({ code: 'CLASSIC', label: 'Classique', description: '' });

    component.submit();

    expect(component.errorMessage()).toBe('Un genre existe déjà avec ce code.');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });
});
