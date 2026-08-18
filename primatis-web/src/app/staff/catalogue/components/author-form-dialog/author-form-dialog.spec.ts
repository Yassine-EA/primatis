import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthorResponse } from '../../../../catalogue/models/author-response';
import { CreateAuthorRequest } from '../../../../catalogue/models/create-author-request';
import { UpdateAuthorRequest } from '../../../../catalogue/models/update-author-request';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { AuthorFormDialog } from './author-form-dialog';

function buildAuthor(overrides: Partial<AuthorResponse> = {}): AuthorResponse {
  return {
    id: 1,
    fullName: 'Victor Hugo',
    birthDate: '1802-02-26',
    deathDate: '1885-05-22',
    nationality: 'Française',
    biography: null,
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
      path: '/api/v1/staff/authors',
      fieldErrors: [],
    },
  });
}

describe('AuthorFormDialog', () => {
  let fixture: ComponentFixture<AuthorFormDialog>;
  let component: AuthorFormDialog;
  let staffCatalogueApiServiceMock: { createAuthor: ReturnType<typeof vi.fn>; updateAuthor: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffCatalogueApiServiceMock = {
      createAuthor: vi.fn().mockReturnValue(of(buildAuthor())),
      updateAuthor: vi.fn().mockReturnValue(of(buildAuthor())),
    };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [AuthorFormDialog],
      providers: [
        { provide: StaffCatalogueApiService, useValue: staffCatalogueApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });
  }

  function createComponent(authorValue: AuthorResponse | null, visible = true): void {
    fixture = TestBed.createComponent(AuthorFormDialog);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('author', authorValue);
    fixture.componentRef.setInput('visible', visible);
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should be in create mode when author is null', () => {
    createComponent(null);

    expect(component.isCreate).toBe(true);
  });

  it('should require fullName', () => {
    createComponent(null);

    component.submit();

    expect(component.form.controls.fullName.touched).toBe(true);
    expect(staffCatalogueApiServiceMock.createAuthor).not.toHaveBeenCalled();
  });

  it('should build a create request with only the provided optional fields', () => {
    createComponent(null);
    component.form.setValue({
      fullName: '  Émile Zola  ',
      birthDate: '1840-04-02',
      deathDate: '',
      nationality: '',
      biography: '',
    });

    component.submit();

    const request = staffCatalogueApiServiceMock.createAuthor.mock.calls[0][0] as CreateAuthorRequest;
    expect(request).toEqual({ fullName: 'Émile Zola', birthDate: '1840-04-02' });
  });

  it('should emit saved and show a success toast on create', () => {
    createComponent(null);
    component.form.controls.fullName.setValue('Émile Zola');
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).toHaveBeenCalledWith(buildAuthor());
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should prefill the form from the author input in edit mode', () => {
    createComponent(buildAuthor({ fullName: 'Victor Hugo', nationality: 'Française' }));

    expect(component.form.controls.fullName.value).toBe('Victor Hugo');
    expect(component.form.controls.nationality.value).toBe('Française');
  });

  it('should build a sparse update request with only changed fields', () => {
    createComponent(buildAuthor({ fullName: 'Victor Hugo', nationality: 'Française' }));
    component.form.controls.nationality.setValue('Belge');

    component.submit();

    const [authorId, request] = staffCatalogueApiServiceMock.updateAuthor.mock.calls[0] as [number, UpdateAuthorRequest];
    expect(authorId).toBe(1);
    expect(request).toEqual({ nationality: 'Belge' });
  });

  it('should close without calling the API when nothing changed', () => {
    createComponent(buildAuthor());

    component.submit();

    expect(staffCatalogueApiServiceMock.updateAuthor).not.toHaveBeenCalled();
  });

  it('should allow clearing an optional field via null in update mode', () => {
    createComponent(buildAuthor({ nationality: 'Française' }));
    component.form.controls.nationality.setValue('');

    component.submit();

    const [, request] = staffCatalogueApiServiceMock.updateAuthor.mock.calls[0] as [number, UpdateAuthorRequest];
    expect(request).toEqual({ nationality: null });
  });

  it('should never validate fullName uniqueness or birth/death coherence locally', () => {
    createComponent(null);
    component.form.setValue({
      fullName: 'Doublon',
      birthDate: '2000-01-01',
      deathDate: '1900-01-01',
      nationality: '',
      biography: '',
    });

    component.submit();

    expect(staffCatalogueApiServiceMock.createAuthor).toHaveBeenCalled();
  });

  it('should show the backend error message and a toast on failure', () => {
    staffCatalogueApiServiceMock.createAuthor.mockReturnValue(
      throwError(() => apiHttpError('AUTHOR_BIRTH_DATE_AFTER_DEATH_DATE', 'birthDate ne peut pas être postérieure à deathDate.')),
    );
    createComponent(null);
    component.form.controls.fullName.setValue('Doublon');

    component.submit();

    expect(component.errorMessage()).toBe('birthDate ne peut pas être postérieure à deathDate.');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });

  it('should emit closed when cancel is called', () => {
    createComponent(null);
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.cancel();

    expect(closedSpy).toHaveBeenCalledTimes(1);
  });
});
