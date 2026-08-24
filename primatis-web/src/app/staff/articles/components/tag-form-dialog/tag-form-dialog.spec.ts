import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CreateTagRequest } from '../../../../articles/models/create-tag-request';
import { TagResponse } from '../../../../articles/models/tag-response';
import { UpdateTagRequest } from '../../../../articles/models/update-tag-request';
import { StaffTagApiService } from '../../../../articles/services/staff-tag-api.service';
import { TagFormDialog } from './tag-form-dialog';

function buildTag(overrides: Partial<TagResponse> = {}): TagResponse {
  return { id: 1, code: 'NEWS', label: 'Actualités', description: null, ...overrides };
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
      path: '/api/v1/staff/tags',
      fieldErrors: [],
    },
  });
}

describe('TagFormDialog', () => {
  let fixture: ComponentFixture<TagFormDialog>;
  let component: TagFormDialog;
  let staffTagApiServiceMock: { createTag: ReturnType<typeof vi.fn>; updateTag: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffTagApiServiceMock = {
      createTag: vi.fn().mockReturnValue(of(buildTag())),
      updateTag: vi.fn().mockReturnValue(of(buildTag())),
    };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [TagFormDialog],
      providers: [
        { provide: StaffTagApiService, useValue: staffTagApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });
  }

  function createComponent(tagValue: TagResponse | null, visible = true): void {
    fixture = TestBed.createComponent(TagFormDialog);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('tag', tagValue);
    fixture.componentRef.setInput('visible', visible);
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should require code and label on create', () => {
    createComponent(null);

    component.submit();

    expect(component.form.controls.code.touched).toBe(true);
    expect(component.form.controls.label.touched).toBe(true);
    expect(staffTagApiServiceMock.createTag).not.toHaveBeenCalled();
  });

  it('should build a create request with only the provided description', () => {
    createComponent(null);
    component.form.setValue({ code: 'NEWS', label: 'Actualités', description: '' });

    component.submit();

    const request = staffTagApiServiceMock.createTag.mock.calls[0][0] as CreateTagRequest;
    expect(request).toEqual({ code: 'NEWS', label: 'Actualités' });
  });

  it('should emit saved and show a success toast on create', () => {
    createComponent(null);
    component.form.setValue({ code: 'NEWS', label: 'Actualités', description: '' });
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).toHaveBeenCalledWith(buildTag());
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should prefill the form in edit mode', () => {
    createComponent(buildTag({ code: 'NEWS', label: 'Actualités' }));

    expect(component.form.controls.code.value).toBe('NEWS');
    expect(component.form.controls.label.value).toBe('Actualités');
  });

  it('should disable the code field in edit mode', () => {
    createComponent(buildTag());

    expect(component.form.controls.code.disabled).toBe(true);
  });

  it('should keep the code field enabled in create mode', () => {
    createComponent(null);

    expect(component.form.controls.code.disabled).toBe(false);
  });

  it('should build a sparse update request with only changed fields, never including code', () => {
    createComponent(buildTag());
    component.form.controls.label.setValue('Actus');

    component.submit();

    const [tagId, request] = staffTagApiServiceMock.updateTag.mock.calls[0] as [number, UpdateTagRequest];
    expect(tagId).toBe(1);
    expect(request).toEqual({ label: 'Actus' });
    expect(Object.prototype.hasOwnProperty.call(request, 'code')).toBe(false);
  });

  it('should close without calling the API when nothing changed', () => {
    createComponent(buildTag());

    component.submit();

    expect(staffTagApiServiceMock.updateTag).not.toHaveBeenCalled();
  });

  it('should show the backend error message and a toast on failure (TAG_CODE_ALREADY_EXISTS)', () => {
    staffTagApiServiceMock.createTag.mockReturnValue(
      throwError(() => apiHttpError('TAG_CODE_ALREADY_EXISTS', 'Un Tag existe déjà avec ce code.')),
    );
    createComponent(null);
    component.form.setValue({ code: 'NEWS', label: 'Actualités', description: '' });

    component.submit();

    expect(component.errorMessage()).toBe('Un Tag existe déjà avec ce code.');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });
});
