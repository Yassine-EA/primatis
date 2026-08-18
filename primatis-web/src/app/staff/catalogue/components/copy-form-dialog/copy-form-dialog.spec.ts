import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CopyResponse } from '../../../../catalogue/models/copy-response';
import { CreateCopyRequest } from '../../../../catalogue/models/create-copy-request';
import { UpdateCopyRequest } from '../../../../catalogue/models/update-copy-request';
import { CopyApiService } from '../../../../catalogue/services/copy-api.service';
import { CopyFormDialog } from './copy-form-dialog';

function buildCopy(overrides: Partial<CopyResponse> = {}): CopyResponse {
  return {
    id: 1,
    titleId: 10,
    inventoryCode: 'INV-001',
    location: 'Salle A',
    copyCondition: 'GOOD',
    availabilityStatus: 'AVAILABLE',
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
      path: '/api/v1/staff/titles/10/copies',
      fieldErrors: [],
    },
  });
}

describe('CopyFormDialog', () => {
  let fixture: ComponentFixture<CopyFormDialog>;
  let component: CopyFormDialog;
  let copyApiServiceMock: { createCopy: ReturnType<typeof vi.fn>; updateCopy: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  function configure(): void {
    copyApiServiceMock = {
      createCopy: vi.fn().mockReturnValue(of(buildCopy())),
      updateCopy: vi.fn().mockReturnValue(of(buildCopy())),
    };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [CopyFormDialog],
      providers: [
        { provide: CopyApiService, useValue: copyApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });
  }

  function createComponent(copyValue: CopyResponse | null, titleId = 10, visible = true): void {
    fixture = TestBed.createComponent(CopyFormDialog);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('copy', copyValue);
    fixture.componentRef.setInput('titleId', titleId);
    fixture.componentRef.setInput('visible', visible);
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should never propose ON_LOAN or RESERVED as availability options', () => {
    createComponent(null);

    expect(component.availabilityOptions.map((option) => option.value)).toEqual(['AVAILABLE', 'UNAVAILABLE']);
  });

  it('should require inventoryCode, copyCondition and availabilityStatus in create mode', () => {
    createComponent(null);

    component.submit();

    expect(component.form.controls.inventoryCode.touched).toBe(true);
    expect(component.form.controls.copyCondition.touched).toBe(true);
    expect(component.form.controls.availabilityStatus.touched).toBe(true);
    expect(copyApiServiceMock.createCopy).not.toHaveBeenCalled();
  });

  it('should build a create request with titleId taken from input, never from the form', () => {
    createComponent(null, 77);
    component.form.setValue({
      inventoryCode: 'INV-002',
      location: '',
      copyCondition: 'GOOD',
      availabilityStatus: 'AVAILABLE',
    });

    component.submit();

    const [titleId, request] = copyApiServiceMock.createCopy.mock.calls[0] as [number, CreateCopyRequest];
    expect(titleId).toBe(77);
    expect(request).toEqual({ inventoryCode: 'INV-002', copyCondition: 'GOOD', availabilityStatus: 'AVAILABLE' });
    expect(request).not.toHaveProperty('titleId');
  });

  it('should emit saved and show a success toast on create', () => {
    createComponent(null);
    component.form.setValue({
      inventoryCode: 'INV-002',
      location: '',
      copyCondition: 'GOOD',
      availabilityStatus: 'AVAILABLE',
    });
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).toHaveBeenCalledWith(buildCopy());
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should not require or send availabilityStatus in edit mode', () => {
    createComponent(buildCopy());

    expect(component.form.controls.availabilityStatus.validator).toBeNull();

    component.form.controls.inventoryCode.setValue('INV-999');
    component.submit();

    const request = copyApiServiceMock.updateCopy.mock.calls[0][2] as UpdateCopyRequest;
    expect(request).not.toHaveProperty('availabilityStatus');
  });

  it('should build a sparse update request with only changed fields, never titleId', () => {
    createComponent(buildCopy({ inventoryCode: 'INV-001', location: 'Salle A', copyCondition: 'GOOD' }));
    component.form.controls.copyCondition.setValue('DAMAGED');

    component.submit();

    const [titleId, copyId, request] = copyApiServiceMock.updateCopy.mock.calls[0] as [number, number, UpdateCopyRequest];
    expect(titleId).toBe(10);
    expect(copyId).toBe(1);
    expect(request).toEqual({ copyCondition: 'DAMAGED' });
  });

  it('should close without calling the API when nothing changed', () => {
    createComponent(buildCopy());

    component.submit();

    expect(copyApiServiceMock.updateCopy).not.toHaveBeenCalled();
  });

  it('should allow clearing location via null in update mode', () => {
    createComponent(buildCopy({ location: 'Salle A' }));
    component.form.controls.location.setValue('');

    component.submit();

    const request = copyApiServiceMock.updateCopy.mock.calls[0][2] as UpdateCopyRequest;
    expect(request).toEqual({ location: null });
  });

  it('should never anticipate LOST/OUT_OF_SERVICE -> UNAVAILABLE locally, and reflect only the backend response', () => {
    copyApiServiceMock.updateCopy.mockReturnValue(of(buildCopy({ copyCondition: 'LOST', availabilityStatus: 'UNAVAILABLE' })));
    createComponent(buildCopy({ copyCondition: 'GOOD', availabilityStatus: 'AVAILABLE' }));
    component.form.controls.copyCondition.setValue('LOST');
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.submit();

    expect(savedSpy).toHaveBeenCalledWith(expect.objectContaining({ availabilityStatus: 'UNAVAILABLE' }));
  });

  it('should show the backend error message and a toast on failure', () => {
    copyApiServiceMock.createCopy.mockReturnValue(
      throwError(() => apiHttpError('INVENTORY_CODE_ALREADY_EXISTS', 'Un exemplaire existe déjà avec ce inventoryCode.')),
    );
    createComponent(null);
    component.form.setValue({
      inventoryCode: 'INV-001',
      location: '',
      copyCondition: 'GOOD',
      availabilityStatus: 'AVAILABLE',
    });

    component.submit();

    expect(component.errorMessage()).toBe('Un exemplaire existe déjà avec ce inventoryCode.');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });
});
