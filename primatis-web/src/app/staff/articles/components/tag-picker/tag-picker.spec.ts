import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MultiSelect } from 'primeng/multiselect';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { PageResponse } from '../../../../core/models/page-response';
import { TagResponse } from '../../../../articles/models/tag-response';
import { StaffTagApiService } from '../../../../articles/services/staff-tag-api.service';
import { TagPicker } from './tag-picker';

function buildTag(overrides: Partial<TagResponse> = {}): TagResponse {
  return { id: 1, code: 'NEWS', label: 'Actualités', description: null, ...overrides };
}

function buildPage(content: TagResponse[], page: number, totalPages: number): PageResponse<TagResponse> {
  return { content, page, size: 100, totalElements: totalPages * 100, totalPages };
}

describe('TagPicker', () => {
  let fixture: ComponentFixture<TagPicker>;
  let component: TagPicker;
  let staffTagApiServiceMock: { listTags: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffTagApiServiceMock = {
      listTags: vi.fn().mockReturnValue(of(buildPage([buildTag()], 0, 1))),
    };

    TestBed.configureTestingModule({
      imports: [TagPicker],
      providers: [{ provide: StaffTagApiService, useValue: staffTagApiServiceMock }],
    });
  }

  function createComponent(initialTags: TagResponse[] = [], disabled = false): void {
    fixture = TestBed.createComponent(TagPicker);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('initialTags', initialTags);
    fixture.componentRef.setInput('disabled', disabled);
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should load all tags on init with page=0/size=100', () => {
    createComponent();

    expect(staffTagApiServiceMock.listTags).toHaveBeenCalledWith(0, 100);
    expect(component.allTags()).toEqual([buildTag()]);
    expect(component.loading()).toBe(false);
  });

  it('should walk every page deterministically when totalPages > 1, without truncation', () => {
    const pageZero = buildTag({ id: 1, code: 'A' });
    const pageOne = buildTag({ id: 2, code: 'B' });
    const pageTwo = buildTag({ id: 3, code: 'C' });
    staffTagApiServiceMock.listTags.mockImplementation((page: number) => {
      if (page === 0) return of(buildPage([pageZero], 0, 3));
      if (page === 1) return of(buildPage([pageOne], 1, 3));
      return of(buildPage([pageTwo], 2, 3));
    });

    createComponent();

    expect(staffTagApiServiceMock.listTags).toHaveBeenCalledTimes(3);
    expect(component.allTags().map((t) => t.id)).toEqual([1, 2, 3]);
  });

  it('should initialize selectedIds from initialTags', () => {
    createComponent([buildTag({ id: 7 })]);

    expect(component.selectedIds()).toEqual([7]);
  });

  it('should emit the resolved TagResponse objects on selection change', () => {
    createComponent();
    const emitted: TagResponse[][] = [];
    component.selectionChange.subscribe((value) => emitted.push(value));

    component.onSelectionChange([1]);

    expect(emitted.at(-1)).toEqual([buildTag()]);
  });

  it('should never expose an inline Tag creation/edit affordance (business-rules.md §7.13, DEV-DEC-0060)', () => {
    createComponent();

    expect(fixture.nativeElement.querySelector('app-tag-form-dialog')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Nouveau tag');
    expect(fixture.nativeElement.textContent).not.toContain('Modifier');
  });

  it('should disable the underlying multiselect when disabled=true (ARCHIVED read-only)', () => {
    createComponent([], true);

    const multiselect = fixture.debugElement.query(By.directive(MultiSelect)).componentInstance as MultiSelect;
    expect(multiselect.disabled()).toBe(true);
  });

  it('should keep the underlying multiselect enabled by default', () => {
    createComponent();

    const multiselect = fixture.debugElement.query(By.directive(MultiSelect)).componentInstance as MultiSelect;
    expect(multiselect.disabled()).toBeFalsy();
  });

  it('should show an error state when loading fails, with a retry that reloads', () => {
    staffTagApiServiceMock.listTags.mockReturnValue(
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
              path: '/api/v1/staff/tags',
              fieldErrors: [],
            },
          }),
      ),
    );
    createComponent();

    expect(component.error()?.message).toBe('Erreur serveur.');

    staffTagApiServiceMock.listTags.mockReturnValue(of(buildPage([buildTag()], 0, 1)));
    component.retry();

    expect(component.error()).toBeNull();
    expect(component.allTags()).toEqual([buildTag()]);
  });
});
