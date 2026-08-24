import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, Router, convertToParamMap, provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '../../../../auth/services/auth.service';
import { ArticleResponse } from '../../../../articles/models/article-response';
import { TagResponse } from '../../../../articles/models/tag-response';
import { UpdateArticleRequest } from '../../../../articles/models/update-article-request';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';
import { StaffTagApiService } from '../../../../articles/services/staff-tag-api.service';
import { StaffArticleDetailPage } from './staff-article-detail-page';

function buildTag(overrides: Partial<TagResponse> = {}): TagResponse {
  return { id: 1, code: 'NEWS', label: 'Actualités', description: null, ...overrides };
}

function buildArticle(overrides: Partial<ArticleResponse> = {}): ArticleResponse {
  return {
    id: 10,
    title: 'Nouvelle acquisition',
    content: 'Contenu initial',
    summary: 'Résumé',
    slug: 'nouvelle-acquisition',
    articleStatus: 'DRAFT',
    author: { id: 1, firstName: 'Prénom', lastName: 'Nom' },
    lastModifiedBy: null,
    tags: [buildTag()],
    publishedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { timestamp: new Date().toISOString(), status, error: 'Error', code, message, path: '/api/v1/staff/articles/10', fieldErrors: [] },
  });
}

describe('StaffArticleDetailPage', () => {
  let fixture: ComponentFixture<StaffArticleDetailPage>;
  let component: StaffArticleDetailPage;
  let staffArticleApiServiceMock: {
    getStaffArticleById: ReturnType<typeof vi.fn>;
    updateArticle: ReturnType<typeof vi.fn>;
    publishArticle: ReturnType<typeof vi.fn>;
    archiveArticle: ReturnType<typeof vi.fn>;
    deleteArticle: ReturnType<typeof vi.fn>;
    updateArticleTags: ReturnType<typeof vi.fn>;
  };
  let authServiceMock: { hasPermission: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let confirmationServiceMock: { confirm: ReturnType<typeof vi.fn> };
  let router: Router;
  let paramMap$: BehaviorSubject<ParamMap>;

  function configure(rawId: string | null = '10'): void {
    paramMap$ = new BehaviorSubject<ParamMap>(convertToParamMap(rawId === null ? {} : { id: rawId }));

    staffArticleApiServiceMock = {
      getStaffArticleById: vi.fn().mockReturnValue(of(buildArticle())),
      updateArticle: vi.fn().mockReturnValue(of(buildArticle())),
      publishArticle: vi.fn().mockReturnValue(of(buildArticle({ articleStatus: 'PUBLISHED', publishedAt: '2026-08-01T10:00:00Z' }))),
      archiveArticle: vi.fn().mockReturnValue(
        of(buildArticle({ articleStatus: 'ARCHIVED', publishedAt: '2026-08-01T10:00:00Z' })),
      ),
      deleteArticle: vi.fn().mockReturnValue(of(undefined)),
      updateArticleTags: vi.fn().mockReturnValue(of(buildArticle({ tags: [buildTag({ id: 2, code: 'X', label: 'X' })] }))),
    };
    authServiceMock = { hasPermission: vi.fn().mockReturnValue(true) };
    messageServiceMock = { add: vi.fn() };
    confirmationServiceMock = { confirm: vi.fn() };
    const staffTagApiServiceMock = {
      listTags: vi.fn().mockReturnValue(of({ content: [buildTag()], page: 0, size: 100, totalElements: 1, totalPages: 1 })),
    };

    TestBed.configureTestingModule({
      imports: [StaffArticleDetailPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$ } },
        { provide: StaffArticleApiService, useValue: staffArticleApiServiceMock },
        { provide: StaffTagApiService, useValue: staffTagApiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffArticleDetailPage);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  }

  function accept(): void {
    const calls = confirmationServiceMock.confirm.mock.calls;
    calls[calls.length - 1][0].accept();
  }

  beforeEach(() => configure());

  // ---------------------------------------------------------------
  // Chargement / reload-safe
  // ---------------------------------------------------------------

  it('should load the Article by the numeric route id (reload-safe, DEV-11.12A)', () => {
    createComponent();

    expect(staffArticleApiServiceMock.getStaffArticleById).toHaveBeenCalledWith(10);
    expect(component.article()).toEqual(buildArticle());
  });

  it('should not call the API when the route id is not numeric', () => {
    configure('abc');
    createComponent();

    expect(staffArticleApiServiceMock.getStaffArticleById).not.toHaveBeenCalled();
    expect(component.articleError()?.message).toBe('Identifiant d’article invalide.');
  });

  it('should load a DRAFT Article via the staff detail (tous statuts confondus, DEV-11.12A)', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(of(buildArticle({ articleStatus: 'DRAFT' })));
    createComponent();

    expect(component.article()?.articleStatus).toBe('DRAFT');
  });

  it('should load an ARCHIVED Article via the staff detail', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(of(buildArticle({ articleStatus: 'ARCHIVED' })));
    createComponent();

    expect(component.article()?.articleStatus).toBe('ARCHIVED');
  });

  it('should show the error state on load failure, with a retry that reloads', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(throwError(() => apiHttpError(404, 'ARTICLE_NOT_FOUND', 'Aucun article.')));
    createComponent();

    expect(component.articleError()?.message).toBe('Aucun article.');

    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(of(buildArticle()));
    component.retry();

    expect(component.articleError()).toBeNull();
    expect(component.article()).toEqual(buildArticle());
  });

  // ---------------------------------------------------------------
  // Édition DRAFT
  // ---------------------------------------------------------------

  it('should prefill the form from the loaded DRAFT Article', () => {
    createComponent();

    expect(component.form.controls.title.value).toBe('Nouvelle acquisition');
    expect(component.form.controls.content.value).toBe('Contenu initial');
    expect(component.form.controls.summary.value).toBe('Résumé');
  });

  it('should build a sparse update request with only changed fields', () => {
    createComponent();
    component.form.controls.title.setValue('Titre édité');

    component.submitUpdate();

    const [articleId, request] = staffArticleApiServiceMock.updateArticle.mock.calls[0] as [number, UpdateArticleRequest];
    expect(articleId).toBe(10);
    expect(request).toEqual({ title: 'Titre édité' });
  });

  it('should send an explicit null summary when cleared (PATCH sparse)', () => {
    createComponent();
    component.form.controls.summary.setValue('');

    component.submitUpdate();

    const [, request] = staffArticleApiServiceMock.updateArticle.mock.calls[0] as [number, UpdateArticleRequest];
    expect(request).toEqual({ summary: null });
  });

  it('should show an info toast and never call the API when nothing changed', () => {
    createComponent();

    component.submitUpdate();

    expect(staffArticleApiServiceMock.updateArticle).not.toHaveBeenCalled();
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'info' }));
  });

  it('should replace local state with the server ArticleResponse after save', () => {
    const serverResponse = buildArticle({ title: 'Titre serveur' });
    staffArticleApiServiceMock.updateArticle.mockReturnValue(of(serverResponse));
    createComponent();
    component.form.controls.title.setValue('Titre local');

    component.submitUpdate();

    expect(component.article()).toEqual(serverResponse);
    expect(component.form.controls.title.value).toBe('Titre serveur');
  });

  it('should show the backend error message on update failure', () => {
    staffArticleApiServiceMock.updateArticle.mockReturnValue(throwError(() => apiHttpError(409, 'ARTICLE_NOT_EDITABLE', 'Non modifiable.')));
    createComponent();
    component.form.controls.title.setValue('Titre édité');

    component.submitUpdate();

    expect(component.updateErrorMessage()).toBe('Non modifiable.');
  });

  // ---------------------------------------------------------------
  // Édition PUBLISHED
  // ---------------------------------------------------------------

  it('should allow editing a PUBLISHED Article and keep it editable, no publish/delete button', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'PUBLISHED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    createComponent();

    expect(component.isEditable).toBe(true);
    expect(fixture.nativeElement.textContent).not.toContain('Publier');
    expect(fixture.nativeElement.textContent).not.toContain('Supprimer');
    expect(fixture.nativeElement.textContent).toContain('Archiver');
  });

  it('should update a PUBLISHED Article via the same sparse PATCH, without touching publishedAt', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'PUBLISHED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    createComponent();
    component.form.controls.title.setValue('Titre édité');

    component.submitUpdate();

    const [, request] = staffArticleApiServiceMock.updateArticle.mock.calls[0] as [number, UpdateArticleRequest];
    expect(request).toEqual({ title: 'Titre édité' });
    expect(request).not.toHaveProperty('articleStatus');
    expect(request).not.toHaveProperty('publishedAt');
  });

  // ---------------------------------------------------------------
  // ARCHIVED — lecture seule
  // ---------------------------------------------------------------

  it('should disable the entire form for an ARCHIVED Article', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'ARCHIVED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    createComponent();

    expect(component.form.disabled).toBe(true);
    expect(component.isEditable).toBe(false);
  });

  it('should show no save/publish/archive/delete action for an ARCHIVED Article', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'ARCHIVED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    createComponent();

    expect(fixture.nativeElement.textContent).not.toContain('Enregistrer');
    expect(fixture.nativeElement.textContent).not.toContain('Publier');
    expect(fixture.nativeElement.textContent).not.toContain('Archiver');
    expect(fixture.nativeElement.textContent).not.toContain('Supprimer');
  });

  it('should disable Tag association for an ARCHIVED Article', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'ARCHIVED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    createComponent();

    expect(fixture.nativeElement.textContent).not.toContain('Enregistrer les tags');
  });

  it('should never call the update/tags API for an ARCHIVED Article even if submit is invoked programmatically', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'ARCHIVED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    createComponent();

    component.submitUpdate();

    expect(staffArticleApiServiceMock.updateArticle).not.toHaveBeenCalled();
  });

  // ---------------------------------------------------------------
  // Tags
  // ---------------------------------------------------------------

  it('should initialize the Tag selection from the loaded Article', () => {
    createComponent();

    expect(component.selectedTags()).toEqual([buildTag()]);
  });

  it('should send the full tagIds selection on submitTags', () => {
    createComponent();
    component.onTagsChange([buildTag(), buildTag({ id: 2, code: 'X', label: 'X' })]);

    component.submitTags();

    expect(staffArticleApiServiceMock.updateArticleTags).toHaveBeenCalledWith(10, { tagIds: [1, 2] });
  });

  it('should send an empty tagIds array to dissociate all Tags', () => {
    createComponent();
    component.onTagsChange([]);

    component.submitTags();

    expect(staffArticleApiServiceMock.updateArticleTags).toHaveBeenCalledWith(10, { tagIds: [] });
  });

  it('should replace local Tags with the server ArticleResponse after saving Tags', () => {
    createComponent();

    component.submitTags();

    expect(component.article()?.tags).toEqual([buildTag({ id: 2, code: 'X', label: 'X' })]);
    expect(component.selectedTags()).toEqual([buildTag({ id: 2, code: 'X', label: 'X' })]);
  });

  // ---------------------------------------------------------------
  // Publication (ARTICLE_PUBLISH)
  // ---------------------------------------------------------------

  it('should show the Publish button only for a DRAFT Article with ARTICLE_PUBLISH', () => {
    authServiceMock.hasPermission.mockReturnValue(true);
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Publier');
  });

  it('should hide the Publish button when the user lacks ARTICLE_PUBLISH (ARTICLE_MANAGE alone)', () => {
    authServiceMock.hasPermission.mockReturnValue(false);
    createComponent();

    expect(fixture.nativeElement.textContent).not.toContain('Publier');
  });

  it('should hide the Publish button for a PUBLISHED Article even with ARTICLE_PUBLISH', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'PUBLISHED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    authServiceMock.hasPermission.mockReturnValue(true);
    createComponent();

    expect(fixture.nativeElement.textContent).not.toContain('Publier');
  });

  it('should ask for confirmation before publishing', () => {
    createComponent();

    component.confirmPublish();

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(staffArticleApiServiceMock.publishArticle).not.toHaveBeenCalled();
  });

  it('should publish after confirmation is accepted and replace state with the server response', () => {
    createComponent();

    component.confirmPublish();
    accept();

    expect(staffArticleApiServiceMock.publishArticle).toHaveBeenCalledWith(10);
    expect(component.article()?.articleStatus).toBe('PUBLISHED');
    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('should never call publish twice while a publish request is pending', () => {
    const pending = new Subject<ReturnType<typeof buildArticle>>();
    staffArticleApiServiceMock.publishArticle.mockReturnValue(pending);
    createComponent();

    component.confirmPublish();
    accept();
    component.confirmPublish();
    accept();

    expect(staffArticleApiServiceMock.publishArticle).toHaveBeenCalledTimes(1);
    pending.next(buildArticle({ articleStatus: 'PUBLISHED', publishedAt: '2026-08-01T10:00:00Z' }));
    pending.complete();
  });

  // ---------------------------------------------------------------
  // Archivage
  // ---------------------------------------------------------------

  it('should show the Archive button only for a PUBLISHED Article', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'PUBLISHED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Archiver');
  });

  it('should hide the Archive button for a DRAFT Article', () => {
    createComponent();

    expect(fixture.nativeElement.textContent).not.toContain('Archiver');
  });

  it('should ask for confirmation before archiving', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'PUBLISHED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    createComponent();

    component.confirmArchive();

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(staffArticleApiServiceMock.archiveArticle).not.toHaveBeenCalled();
  });

  it('should archive after confirmation is accepted and replace state with the server response', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'PUBLISHED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    createComponent();

    component.confirmArchive();
    accept();

    expect(staffArticleApiServiceMock.archiveArticle).toHaveBeenCalledWith(10);
    expect(component.article()?.articleStatus).toBe('ARCHIVED');
  });

  // ---------------------------------------------------------------
  // Hard-delete DRAFT
  // ---------------------------------------------------------------

  it('should show the Delete button only for a DRAFT Article', () => {
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Supprimer');
  });

  it('should hide the Delete button for a PUBLISHED Article', () => {
    staffArticleApiServiceMock.getStaffArticleById.mockReturnValue(
      of(buildArticle({ articleStatus: 'PUBLISHED', publishedAt: '2026-01-02T00:00:00Z' })),
    );
    createComponent();

    expect(fixture.nativeElement.textContent).not.toContain('Supprimer');
  });

  it('should ask for confirmation before deleting', () => {
    createComponent();

    component.confirmDelete();

    expect(confirmationServiceMock.confirm).toHaveBeenCalledTimes(1);
    expect(staffArticleApiServiceMock.deleteArticle).not.toHaveBeenCalled();
  });

  it('should delete after confirmation is accepted and navigate back to the staff list', () => {
    createComponent();

    component.confirmDelete();
    accept();

    expect(staffArticleApiServiceMock.deleteArticle).toHaveBeenCalledWith(10);
    expect(router.navigate).toHaveBeenCalledWith(['/staff/articles']);
  });

  it('should show the backend error message on delete failure and not navigate', () => {
    staffArticleApiServiceMock.deleteArticle.mockReturnValue(throwError(() => apiHttpError(409, 'ARTICLE_NOT_DELETABLE', 'Non supprimable.')));
    createComponent();

    component.confirmDelete();
    accept();

    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', detail: 'Non supprimable.' }));
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
