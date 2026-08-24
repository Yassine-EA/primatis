import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { ArticleResponse } from '../../../../articles/models/article-response';
import { ArticleStatus } from '../../../../articles/models/article-status';
import { TagResponse } from '../../../../articles/models/tag-response';
import { UpdateArticleRequest } from '../../../../articles/models/update-article-request';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';
import { AuthService } from '../../../../auth/services/auth.service';
import { TagPicker } from '../../components/tag-picker/tag-picker';
import { normalizeOptional } from '../../form-value-normalization';

const INVALID_ARTICLE_ID_ERROR: AppError = { message: 'Identifiant d’article invalide.', fieldErrors: [] };

function parseArticleId(rawId: string | null): number | null {
  if (rawId === null) {
    return null;
  }
  const id = Number(rawId);
  return Number.isInteger(id) ? id : null;
}

/**
 * Détail staff d'un Article (`ARTICLE_MANAGE`, DEV-11.12,
 * `GET /api/v1/staff/articles/{id}`, DEV-11.12A) : édition PATCH sparse
 * inline (précédent `StaffTitleDetailPage`, DEV-06.9), tous statuts
 * confondus (`DRAFT`/`PUBLISHED`/`ARCHIVED`, contrairement au détail
 * public structurellement `PUBLISHED`-only). `ARCHIVED` est terminal
 * (business-rules.md §7.1) : l'écran devient intégralement lecture seule —
 * aucun `save`/`publish`/`archive`/`delete`/association de Tags.
 *
 * <p>Publication (`ARTICLE_PUBLISH`, jamais `ARTICLE_MANAGE` en plus) et
 * archivage/hard-delete (`ARTICLE_MANAGE`) restent des permissions
 * distinctes — la page entière n'exige que `ARTICLE_MANAGE` (guard de
 * route), le bouton Publier est en plus conditionné par
 * `AuthService.hasPermission('ARTICLE_PUBLISH')` (UX uniquement, le
 * backend revalide systématiquement, `.claude/rules/frontend.md`).
 * Publication/archivage/suppression utilisent `ConfirmationService` — même
 * précédent que `confirmToggleStatus`/`confirmToggleAvailability`
 * (`StaffTitleDetailPage`) et exigence explicite `.claude/rules/frontend.md`
 * (« archiver un Article » cité nommément parmi les actions nécessitant une
 * confirmation).
 */
@Component({
  selector: 'app-staff-article-detail-page',
  imports: [
    ReactiveFormsModule,
    InputTextModule,
    TextareaModule,
    MessageModule,
    ButtonModule,
    TagModule,
    LoadingState,
    EmptyState,
    ErrorState,
    TagPicker,
  ],
  templateUrl: './staff-article-detail-page.html',
  styleUrl: './staff-article-detail-page.scss',
})
export class StaffArticleDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly staffArticleApiService = inject(StaffArticleApiService);
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);

  private articleId: number | null = null;

  readonly article = signal<ArticleResponse | null>(null);
  readonly articleLoading = signal(false);
  readonly articleError = signal<AppError | null>(null);

  readonly selectedTags = signal<TagResponse[]>([]);

  readonly updateSubmitting = signal(false);
  readonly updateErrorMessage = signal<string | null>(null);
  private lastUpdateFieldErrors: readonly FieldError[] = [];

  readonly tagsSubmitting = signal(false);

  readonly publishSubmitting = signal(false);
  readonly archiveSubmitting = signal(false);
  readonly deleteSubmitting = signal(false);

  readonly form = this.formBuilder.group({
    title: this.formBuilder.control('', [Validators.required, Validators.maxLength(255)]),
    content: this.formBuilder.control('', [Validators.required]),
    summary: this.formBuilder.control(''),
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = parseArticleId(params.get('id'));
      if (id === null) {
        this.articleId = null;
        this.article.set(null);
        this.articleLoading.set(false);
        this.articleError.set(INVALID_ARTICLE_ID_ERROR);
        return;
      }
      this.articleId = id;
      this.loadArticle(id);
    });
  }

  get canPublish(): boolean {
    return this.authService.hasPermission('ARTICLE_PUBLISH');
  }

  get isArchived(): boolean {
    return this.article()?.articleStatus === 'ARCHIVED';
  }

  get isEditable(): boolean {
    const status = this.article()?.articleStatus;
    return status === 'DRAFT' || status === 'PUBLISHED';
  }

  articleStatusSeverity(status: ArticleStatus): 'success' | 'warn' | 'secondary' {
    if (status === 'PUBLISHED') {
      return 'success';
    }
    if (status === 'DRAFT') {
      return 'warn';
    }
    return 'secondary';
  }

  fieldError(field: string): string | undefined {
    return this.lastUpdateFieldErrors.find((fieldError) => fieldError.field === field)?.message;
  }

  onTagsChange(tags: TagResponse[]): void {
    this.selectedTags.set(tags);
  }

  // ---------------------------------------------------------------
  // Chargement
  // ---------------------------------------------------------------

  private loadArticle(id: number): void {
    this.articleLoading.set(true);
    this.articleError.set(null);
    this.staffArticleApiService.getStaffArticleById(id).subscribe({
      next: (value) => {
        this.article.set(value);
        this.resetFormFromArticle(value);
        this.articleLoading.set(false);
      },
      error: (err: unknown) => {
        this.articleLoading.set(false);
        this.articleError.set(toAppError(err));
      },
    });
  }

  retry(): void {
    if (this.articleId !== null) {
      this.loadArticle(this.articleId);
    }
  }

  private resetFormFromArticle(article: ArticleResponse): void {
    this.form.reset({
      title: article.title,
      content: article.content,
      summary: article.summary ?? '',
    });
    this.selectedTags.set(article.tags);
    if (article.articleStatus === 'ARCHIVED') {
      this.form.disable();
    } else {
      this.form.enable();
    }
  }

  // ---------------------------------------------------------------
  // PATCH sparse Article
  // ---------------------------------------------------------------

  submitUpdate(): void {
    if (this.updateSubmitting() || this.articleId === null) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const request = this.buildUpdateRequest();
    if (Object.keys(request).length === 0) {
      this.messageService.add({ severity: 'info', summary: 'Aucune modification', detail: "Aucun champ n'a été modifié." });
      return;
    }

    this.updateSubmitting.set(true);
    this.updateErrorMessage.set(null);
    this.lastUpdateFieldErrors = [];

    this.staffArticleApiService.updateArticle(this.articleId, request).subscribe({
      next: (response) => {
        this.updateSubmitting.set(false);
        this.article.set(response);
        this.resetFormFromArticle(response);
        this.messageService.add({ severity: 'success', summary: 'Article modifié', detail: response.title });
      },
      error: (err: unknown) => {
        this.updateSubmitting.set(false);
        const appError = toAppError(err);
        this.updateErrorMessage.set(appError.message);
        this.lastUpdateFieldErrors = appError.fieldErrors;
      },
    });
  }

  private buildUpdateRequest(): UpdateArticleRequest {
    const current = this.article();
    if (current === null) {
      return {};
    }
    const raw = this.form.getRawValue();
    const request: UpdateArticleRequest = {};

    const trimmedTitle = raw.title.trim();
    if (trimmedTitle !== current.title) {
      request.title = trimmedTitle;
    }
    if (raw.content !== current.content) {
      request.content = raw.content;
    }
    const normalizedSummary = normalizeOptional(raw.summary);
    if (normalizedSummary !== current.summary) {
      request.summary = normalizedSummary;
    }

    return request;
  }

  // ---------------------------------------------------------------
  // Tags
  // ---------------------------------------------------------------

  submitTags(): void {
    if (this.tagsSubmitting() || this.articleId === null) {
      return;
    }
    this.tagsSubmitting.set(true);
    this.staffArticleApiService.updateArticleTags(this.articleId, { tagIds: this.selectedTags().map((tag) => tag.id) }).subscribe({
      next: (response) => {
        this.tagsSubmitting.set(false);
        this.article.set(response);
        this.selectedTags.set(response.tags);
        this.messageService.add({ severity: 'success', summary: 'Tags mis à jour', detail: response.title });
      },
      error: (err: unknown) => {
        this.tagsSubmitting.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  // ---------------------------------------------------------------
  // Publication
  // ---------------------------------------------------------------

  confirmPublish(): void {
    this.confirmationService.confirm({
      header: 'Publier l’article',
      message: 'Voulez-vous vraiment publier cet article ? Une notification sera envoyée à chaque membre actif.',
      accept: () => this.performPublish(),
    });
  }

  private performPublish(): void {
    if (this.publishSubmitting() || this.articleId === null) {
      return;
    }
    this.publishSubmitting.set(true);
    this.staffArticleApiService.publishArticle(this.articleId).subscribe({
      next: (response) => {
        this.publishSubmitting.set(false);
        this.article.set(response);
        this.resetFormFromArticle(response);
        this.messageService.add({ severity: 'success', summary: 'Article publié', detail: response.title });
      },
      error: (err: unknown) => {
        this.publishSubmitting.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  // ---------------------------------------------------------------
  // Archivage
  // ---------------------------------------------------------------

  confirmArchive(): void {
    this.confirmationService.confirm({
      header: 'Archiver l’article',
      message: 'Voulez-vous vraiment archiver cet article ? Cette action est définitive.',
      accept: () => this.performArchive(),
    });
  }

  private performArchive(): void {
    if (this.archiveSubmitting() || this.articleId === null) {
      return;
    }
    this.archiveSubmitting.set(true);
    this.staffArticleApiService.archiveArticle(this.articleId).subscribe({
      next: (response) => {
        this.archiveSubmitting.set(false);
        this.article.set(response);
        this.resetFormFromArticle(response);
        this.messageService.add({ severity: 'success', summary: 'Article archivé', detail: response.title });
      },
      error: (err: unknown) => {
        this.archiveSubmitting.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  // ---------------------------------------------------------------
  // Hard-delete DRAFT
  // ---------------------------------------------------------------

  confirmDelete(): void {
    this.confirmationService.confirm({
      header: 'Supprimer l’article',
      message: 'Voulez-vous vraiment supprimer définitivement ce brouillon ? Cette action est irréversible.',
      accept: () => this.performDelete(),
    });
  }

  private performDelete(): void {
    if (this.deleteSubmitting() || this.articleId === null) {
      return;
    }
    this.deleteSubmitting.set(true);
    this.staffArticleApiService.deleteArticle(this.articleId).subscribe({
      next: () => {
        this.deleteSubmitting.set(false);
        this.messageService.add({ severity: 'success', summary: 'Article supprimé' });
        void this.router.navigate(['/staff/articles']);
      },
      error: (err: unknown) => {
        this.deleteSubmitting.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }
}
