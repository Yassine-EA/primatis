import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ArticleResponse } from '../../../../articles/models/article-response';
import { CreateArticleRequest } from '../../../../articles/models/create-article-request';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';
import { StaffArticleCreatePage } from './staff-article-create-page';

function buildArticle(overrides: Partial<ArticleResponse> = {}): ArticleResponse {
  return {
    id: 99,
    title: 'Nouvelle acquisition',
    content: 'Contenu',
    summary: null,
    slug: 'nouvelle-acquisition',
    articleStatus: 'DRAFT',
    author: { id: 10, firstName: 'Prénom', lastName: 'Nom' },
    lastModifiedBy: null,
    tags: [],
    publishedAt: null,
    createdAt: '2026-08-01T08:00:00Z',
    updatedAt: '2026-08-01T08:00:00Z',
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
      path: '/api/v1/staff/articles',
      fieldErrors: [],
    },
  });
}

describe('StaffArticleCreatePage', () => {
  let fixture: ComponentFixture<StaffArticleCreatePage>;
  let component: StaffArticleCreatePage;
  let staffArticleApiServiceMock: { createArticle: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };
  let router: Router;

  function configure(): void {
    staffArticleApiServiceMock = { createArticle: vi.fn().mockReturnValue(of(buildArticle())) };
    messageServiceMock = { add: vi.fn() };

    TestBed.configureTestingModule({
      imports: [StaffArticleCreatePage],
      providers: [
        provideRouter([]),
        { provide: StaffArticleApiService, useValue: staffArticleApiServiceMock },
        { provide: MessageService, useValue: messageServiceMock },
      ],
    });

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(StaffArticleCreatePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => configure());

  it('should require title and content', () => {
    createComponent();

    component.submit();

    expect(component.form.controls.title.touched).toBe(true);
    expect(component.form.controls.content.touched).toBe(true);
    expect(staffArticleApiServiceMock.createArticle).not.toHaveBeenCalled();
  });

  it('should reject a title longer than 255 characters', () => {
    createComponent();
    component.form.setValue({ title: 'a'.repeat(256), content: 'Contenu', summary: '' });

    expect(component.form.controls.title.invalid).toBe(true);
  });

  it('should build a request with only the provided summary', () => {
    createComponent();
    component.form.setValue({ title: 'Nouvel article', content: 'Contenu', summary: '' });

    component.submit();

    const request = staffArticleApiServiceMock.createArticle.mock.calls[0][0] as CreateArticleRequest;
    expect(request).toEqual({ title: 'Nouvel article', content: 'Contenu' });
  });

  it('should include a trimmed summary when provided', () => {
    createComponent();
    component.form.setValue({ title: 'Nouvel article', content: 'Contenu', summary: '  Résumé  ' });

    component.submit();

    const request = staffArticleApiServiceMock.createArticle.mock.calls[0][0] as CreateArticleRequest;
    expect(request.summary).toBe('Résumé');
  });

  it('should trim the title before sending it', () => {
    createComponent();
    component.form.setValue({ title: '  Nouvel article  ', content: 'Contenu', summary: '' });

    component.submit();

    const request = staffArticleApiServiceMock.createArticle.mock.calls[0][0] as CreateArticleRequest;
    expect(request.title).toBe('Nouvel article');
  });

  it('should never send tagIds on creation (association happens after, from the detail)', () => {
    createComponent();
    component.form.setValue({ title: 'Nouvel article', content: 'Contenu', summary: '' });

    component.submit();

    const request = staffArticleApiServiceMock.createArticle.mock.calls[0][0] as CreateArticleRequest;
    expect(request).not.toHaveProperty('tagIds');
  });

  it('should show a success toast and navigate to the created Article detail page', () => {
    createComponent();
    component.form.setValue({ title: 'Nouvel article', content: 'Contenu', summary: '' });

    component.submit();

    expect(messageServiceMock.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    expect(router.navigate).toHaveBeenCalledWith(['/staff/articles', 99]);
  });

  it('should prevent a double submit while a request is pending', () => {
    const pending = new Subject<ReturnType<typeof buildArticle>>();
    staffArticleApiServiceMock.createArticle.mockReturnValue(pending);
    createComponent();
    component.form.setValue({ title: 'Nouvel article', content: 'Contenu', summary: '' });

    component.submit();
    component.submit();

    expect(staffArticleApiServiceMock.createArticle).toHaveBeenCalledTimes(1);
    pending.next(buildArticle());
    pending.complete();
  });

  it('should show the backend error message and field errors on failure', () => {
    staffArticleApiServiceMock.createArticle.mockReturnValue(
      throwError(() => apiHttpError('ARTICLE_CONTENT_EMPTY', 'Le contenu de l’Article est vide après sanitization.')),
    );
    createComponent();
    component.form.setValue({ title: 'Nouvel article', content: '<script></script>', summary: '' });

    component.submit();

    expect(component.errorMessage()).toBe('Le contenu de l’Article est vide après sanitization.');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
