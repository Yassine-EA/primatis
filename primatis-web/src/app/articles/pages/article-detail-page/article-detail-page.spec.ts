import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ArticleResponse } from '../../models/article-response';
import { ArticleApiService } from '../../services/article-api.service';
import { ArticleDetailPage } from './article-detail-page';

function buildArticle(overrides: Partial<ArticleResponse> = {}): ArticleResponse {
  return {
    id: 1,
    title: 'Les horaires d’été',
    content: '<p>Texte <strong>important</strong></p>',
    summary: 'La bibliothèque adapte ses horaires pour la période estivale.',
    slug: 'les-horaires-dete',
    articleStatus: 'PUBLISHED',
    author: { id: 10, firstName: 'Prénom', lastName: 'Nom' },
    lastModifiedBy: null,
    tags: [{ id: 1, code: 'horaires', label: 'Horaires', description: null }],
    publishedAt: '2026-08-01T10:00:00Z',
    createdAt: '2026-07-01T08:00:00Z',
    updatedAt: '2026-08-01T10:00:00Z',
    ...overrides,
  };
}

function apiHttpError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: {
      timestamp: new Date().toISOString(),
      status,
      error: status === 404 ? 'Not Found' : 'Internal Server Error',
      code,
      message,
      path: '/api/v1/articles/does-not-exist',
      fieldErrors: [],
    },
  });
}

describe('ArticleDetailPage', () => {
  let fixture: ComponentFixture<ArticleDetailPage>;
  let component: ArticleDetailPage;
  let articleApiServiceMock: { getPublishedArticleBySlug: ReturnType<typeof vi.fn> };
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  function configure(initialSlug: string | null = 'les-horaires-dete'): void {
    paramMap$ = new BehaviorSubject(convertToParamMap(initialSlug === null ? {} : { slug: initialSlug }));
    articleApiServiceMock = { getPublishedArticleBySlug: vi.fn().mockReturnValue(of(buildArticle())) };

    TestBed.configureTestingModule({
      imports: [ArticleDetailPage],
      providers: [
        { provide: ArticleApiService, useValue: articleApiServiceMock },
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$ } },
      ],
    });
  }

  function createComponent(): void {
    fixture = TestBed.createComponent(ArticleDetailPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // ---------------------------------------------------------------
  // Chargement / paramètre de route
  // ---------------------------------------------------------------

  it('should call getPublishedArticleBySlug with the slug from the route', () => {
    configure('mon-article');
    createComponent();

    expect(articleApiServiceMock.getPublishedArticleBySlug).toHaveBeenCalledWith('mon-article');
  });

  it('should not call the API when the route has no slug param', () => {
    configure(null);
    createComponent();

    expect(articleApiServiceMock.getPublishedArticleBySlug).not.toHaveBeenCalled();
  });

  it('should show an ErrorState when the route has no slug param', () => {
    configure(null);
    createComponent();

    expect(component.error()?.message).toBe('Slug d’article invalide.');
    expect(fixture.nativeElement.textContent).toContain('Slug d’article invalide.');
  });

  it('should reload when the route slug param changes while the component stays mounted', () => {
    configure('premier-slug');
    createComponent();
    articleApiServiceMock.getPublishedArticleBySlug.mockClear();
    articleApiServiceMock.getPublishedArticleBySlug.mockReturnValue(of(buildArticle({ title: 'Second article' })));

    paramMap$.next(convertToParamMap({ slug: 'second-slug' }));

    expect(articleApiServiceMock.getPublishedArticleBySlug).toHaveBeenCalledWith('second-slug');
    expect(component.article()?.title).toBe('Second article');
  });

  it('should show the loading state before the response arrives', () => {
    configure();
    // Observable never emits synchronously: keeps the component in its initial loading state.
    articleApiServiceMock.getPublishedArticleBySlug.mockReturnValue({ subscribe: () => ({ unsubscribe: () => {} }) });
    createComponent();

    expect(component.loading()).toBe(true);
    expect(fixture.nativeElement.querySelector('app-loading-state')).not.toBeNull();
  });

  // ---------------------------------------------------------------
  // Rendu
  // ---------------------------------------------------------------

  it('should render the title, author and publishedAt', () => {
    configure();
    createComponent();

    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Les horaires d’été');
    expect(text).toContain('Prénom Nom');
    expect(text).toContain('2026-08-01T10:00:00Z');
  });

  it('should render the summary when present', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('La bibliothèque adapte ses horaires');
  });

  it('should omit the summary block when null', () => {
    configure();
    articleApiServiceMock.getPublishedArticleBySlug.mockReturnValue(of(buildArticle({ summary: null })));
    createComponent();

    expect(fixture.nativeElement.querySelector('.article-detail-summary')).toBeNull();
  });

  it('should render Tag labels', () => {
    configure();
    createComponent();

    expect(fixture.nativeElement.textContent).toContain('Horaires');
  });

  it('should not render an empty Tags container when there are no Tags', () => {
    configure();
    articleApiServiceMock.getPublishedArticleBySlug.mockReturnValue(of(buildArticle({ tags: [] })));
    createComponent();

    expect(fixture.nativeElement.querySelector('.article-detail-tags')).toBeNull();
  });

  // ---------------------------------------------------------------
  // Rendu HTML / sécurité (mission DEV-11.11 §16-19)
  // ---------------------------------------------------------------

  it('should render content as real HTML, not escaped text', () => {
    configure();
    articleApiServiceMock.getPublishedArticleBySlug.mockReturnValue(
      of(buildArticle({ content: '<p>Texte <strong>important</strong></p>' })),
    );
    createComponent();

    const contentEl: HTMLElement = fixture.nativeElement.querySelector('.article-detail-content');
    expect(contentEl.querySelector('strong')?.textContent).toBe('important');
    // Si le HTML avait été échappé plutôt que rendu, ce marqueur littéral
    // apparaîtrait dans le texte affiché.
    expect(contentEl.textContent).not.toContain('<strong>');
  });

  it('should let Angular apply its own DOM sanitization on [innerHTML] (no bypass)', () => {
    configure();
    articleApiServiceMock.getPublishedArticleBySlug.mockReturnValue(
      of(buildArticle({ content: '<p>Texte</p><script>window.__xss = true;</script>' })),
    );
    createComponent();

    const contentEl: HTMLElement = fixture.nativeElement.querySelector('.article-detail-content');
    expect(contentEl.querySelector('script')).toBeNull();
    expect((window as unknown as Record<string, unknown>)['__xss']).toBeUndefined();
  });

  // ---------------------------------------------------------------
  // Erreurs
  // ---------------------------------------------------------------

  it('should show an ErrorState (not an empty state) on a 404', () => {
    configure('does-not-exist');
    articleApiServiceMock.getPublishedArticleBySlug.mockReturnValue(
      throwError(() => apiHttpError(404, 'ARTICLE_NOT_FOUND', 'Aucun article publié pour le slug does-not-exist.')),
    );
    createComponent();

    expect(component.error()?.message).toBe('Aucun article publié pour le slug does-not-exist.');
    expect(fixture.nativeElement.querySelector('.error-state')).not.toBeNull();
  });

  it('should show a generic ErrorState message on a network error', () => {
    configure();
    articleApiServiceMock.getPublishedArticleBySlug.mockReturnValue(throwError(() => new Error('boom')));
    createComponent();

    expect(component.error()?.message).toBe('Une erreur est survenue. Veuillez réessayer.');
  });
});
