import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { CreateArticleRequest } from '../../../../articles/models/create-article-request';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';
import { normalizeOptional } from '../../form-value-normalization';
import { ArticleContentEditor } from '../../components/article-content-editor/article-content-editor';

/**
 * Page dédiée de création d'un Article `DRAFT` (`ARTICLE_MANAGE`, DEV-11.12,
 * `POST /api/v1/staff/articles`) — précédent structurel exact
 * `StaffTitleCreatePage` (page dédiée, jamais un dialog, DEV-06.9). Aucune
 * association de Tags ici (`CreateArticleRequest` ne porte structurellement
 * aucun `tagIds`, business-rules.md §7.13/DEV-DEC-0060) : l'association se
 * fait après création, depuis le détail.
 *
 * `content` : éditeur riche WYSIWYG (`ArticleContentEditor`,
 * `DEV-ARTICLES-EDITOR`, `DEV-DEC-0078`). Historique : DEV-11.12 avait
 * retenu un `<textarea pTextarea>` natif ici (aucun éditeur riche tiers,
 * IMPLEMENTATION FREEDOM) — choix **supersédé** par `DEV-DEC-0078` après
 * validation visuelle humaine ayant montré son insuffisance éditoriale
 * réelle. Le contrat `CreateArticleRequest`/`content` (string HTML) et la
 * sanitization backend (`ArticleSanitizer`) restent inchangés.
 */
@Component({
  selector: 'app-staff-article-create-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    InputTextModule,
    MessageModule,
    ButtonModule,
    ArticleContentEditor,
  ],
  templateUrl: './staff-article-create-page.html',
  styleUrl: './staff-article-create-page.scss',
})
export class StaffArticleCreatePage {
  private readonly staffArticleApiService = inject(StaffArticleApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly titleService = inject(Title);

  constructor() {
    this.titleService.setTitle('Créer un article — PRIMATIS');
  }

  readonly form = this.formBuilder.group({
    title: this.formBuilder.control('', [Validators.required, Validators.maxLength(255)]),
    content: this.formBuilder.control('', [Validators.required]),
    summary: this.formBuilder.control(''),
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private lastFieldErrors: readonly FieldError[] = [];

  fieldError(field: string): string | undefined {
    return this.lastFieldErrors.find((fieldError) => fieldError.field === field)?.message;
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.lastFieldErrors = [];

    this.staffArticleApiService.createArticle(this.buildRequest()).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Article créé',
          detail: response.title,
        });
        void this.router.navigate(['/staff/articles', response.id]);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const appError = toAppError(err);
        this.errorMessage.set(appError.message);
        this.lastFieldErrors = appError.fieldErrors;
      },
    });
  }

  private buildRequest(): CreateArticleRequest {
    const raw = this.form.getRawValue();
    const request: CreateArticleRequest = { title: raw.title.trim(), content: raw.content };

    const normalizedSummary = normalizeOptional(raw.summary);
    if (normalizedSummary !== null) {
      request.summary = normalizedSummary;
    }

    return request;
  }
}
