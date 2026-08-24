import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TextareaModule } from 'primeng/textarea';

import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { CreateArticleRequest } from '../../../../articles/models/create-article-request';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';
import { normalizeOptional } from '../../form-value-normalization';

/**
 * Page dédiée de création d'un Article `DRAFT` (`ARTICLE_MANAGE`, DEV-11.12,
 * `POST /api/v1/staff/articles`) — précédent structurel exact
 * `StaffTitleCreatePage` (page dédiée, jamais un dialog, DEV-06.9). Aucune
 * association de Tags ici (`CreateArticleRequest` ne porte structurellement
 * aucun `tagIds`, business-rules.md §7.13/DEV-DEC-0060) : l'association se
 * fait après création, depuis le détail. `content` : `<textarea pTextarea>`
 * natif — aucun éditeur riche tiers, aucune nouvelle dépendance (mission
 * §27, IMPLEMENTATION FREEDOM, aucune source n'impose Quill/CKEditor/etc.).
 */
@Component({
  selector: 'app-staff-article-create-page',
  imports: [ReactiveFormsModule, InputTextModule, TextareaModule, MessageModule, ButtonModule],
  templateUrl: './staff-article-create-page.html',
  styleUrl: './staff-article-create-page.scss',
})
export class StaffArticleCreatePage {
  private readonly staffArticleApiService = inject(StaffArticleApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);

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
        this.messageService.add({ severity: 'success', summary: 'Article créé', detail: response.title });
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
