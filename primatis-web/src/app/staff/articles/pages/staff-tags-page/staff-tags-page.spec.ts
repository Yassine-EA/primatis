import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../../core/models/page-response';
import { TagResponse } from '../../../../articles/models/tag-response';
import { StaffTagApiService } from '../../../../articles/services/staff-tag-api.service';
import { StaffTagsPage } from './staff-tags-page';

function buildTag(overrides: Partial<TagResponse> = {}): TagResponse {
  return { id: 1, code: 'NEWS', label: 'Actualités', description: null, ...overrides };
}

function buildPage(content: TagResponse[]): PageResponse<TagResponse> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { timestamp: new Date().toISOString(), status, error: 'Error', code, message, path: '/api/v1/staff/tags/1', fieldErrors: [] },
  });
}

describe('StaffTagsPage', () => {
  let fixture: ComponentFixture<StaffTagsPage>;
  let component: StaffTagsPage;
  let staffTagApiServiceMock: {
    listTags: ReturnType<typeof vi.fn>;
    deleteTag: ReturnType<typeof vi.fn>;
  };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let confirmationServiceMock: { confirm: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffTagApiServiceMock = {
      listTags: vi.fn().mockReturnValue(of(buildPage([buildTag()]))),
      deleteTag: vi.fn().mockReturnValue(of(undefined)),
    };
    messageServiceMock = { add: vi.fn() };
    confirmationServiceMock = { confirm: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffTagsPage],
      providers: [
        provideRouter([]),
        { provide: StaffTagApiService, useValue: staffTagApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffTagsPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function accept(): void {
    const calls = confirmationServiceMock.confirm.mock.calls;
    calls[calls.length - 1][0].accept();
  }

  beforeEach(() => configure());

  it('should load the first page of Tags on init', () => {
    createComponent();

    expect(staffTagApiServiceMock.listTags).toHaveBeenCalledWith(0, 20);
    expect(component.rows()).toEqual([buildTag()]);
    expect(component.loading()).toBe(false);
  });

  it('should show the empty state when no Tag exists', () => {
    staffTagApiServiceMock.listTags.mockReturnValue(of(buildPage([])));
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Aucun tag');
  });

  it('should show the error state on load failure, with a retry that reloads', () => {
    staffTagApiServiceMock.listTags.mockReturnValue(throwError(() => apiHttpError(500, 'INTERNAL_ERROR', 'Erreur serveur.')));
    createComponent();

    expect(component.error()?.message).toBe('Erreur serveur.');

    staffTagApiServiceMock.listTags.mockReturnValue(of(buildPage([buildTag()])));
    component.retry();

    expect(component.error()).toBeNull();
    expect(component.rows()).toEqual([buildTag()]);
  });

  it('should convert a lazy-load event to a 0-based page/size call', () => {
    createComponent();
    staffTagApiServiceMock.listTags.mockClear();

    component.onLazyLoad({ first: 40, rows: 20 });

    expect(staffTagApiServiceMock.listTags).toHaveBeenCalledWith(2, 20);
  });

  it('should open the create dialog with no Tag preselected', () => {
    createComponent();

    component.openCreateDialog();

    expect(component.dialogVisible()).toBe(true);
    expect(component.dialogTag()).toBeNull();
  });

  it('should open the edit dialog with the selected Tag', () => {
    createComponent();

    component.openEditDialog(buildTag());

    expect(component.dialogVisible()).toBe(true);
    expect(component.dialogTag()).toEqual(buildTag());
  });

  it('should close the dialog and reload the current page after a save', () => {
    createComponent();
    staffTagApiServiceMock.listTags.mockClear();
    component.openCreateDialog();

    component.onDialogSaved(buildTag({ id: 99 }));

    expect(component.dialogVisible()).toBe(false);
    expect(staffTagApiServiceMock.listTags).toHaveBeenCalledTimes(1);
  });

  it('should ask for confirmation before deleting a Tag', () => {
    createComponent();

    component.confirmDelete(buildTag());

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(staffTagApiServiceMock.deleteTag).not.toHaveBeenCalled();
  });

  it('should delete the Tag and reload after confirmation is accepted', () => {
    createComponent();
    staffTagApiServiceMock.listTags.mockClear();

    component.confirmDelete(buildTag());
    accept();

    expect(staffTagApiServiceMock.deleteTag).toHaveBeenCalledWith(1);
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    expect(staffTagApiServiceMock.listTags).toHaveBeenCalledTimes(1);
  });

  it('should show the backend TAG_IN_USE message as a toast, never a silent auto-dissociation', () => {
    staffTagApiServiceMock.deleteTag.mockReturnValue(
      throwError(() => apiHttpError(409, 'TAG_IN_USE', 'Ce Tag est encore associé à au moins un Article et ne peut pas être supprimé.')),
    );
    createComponent();

    component.confirmDelete(buildTag());
    accept();

    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        detail: 'Ce Tag est encore associé à au moins un Article et ne peut pas être supprimé.',
      }),
    );
  });

  it('should clear deletingTagId after a failed delete so the row is actionable again', () => {
    staffTagApiServiceMock.deleteTag.mockReturnValue(throwError(() => apiHttpError(409, 'TAG_IN_USE', 'En usage.')));
    createComponent();

    component.confirmDelete(buildTag());
    accept();

    expect(component.deletingTagId()).toBeNull();
  });
});
