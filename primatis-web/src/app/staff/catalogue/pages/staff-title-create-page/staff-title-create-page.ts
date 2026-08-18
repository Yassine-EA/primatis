import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';

import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { AuthorResponse } from '../../../../catalogue/models/author-response';
import { CreateTitleRequest } from '../../../../catalogue/models/create-title-request';
import { GenreResponse } from '../../../../catalogue/models/genre-response';
import { Language } from '../../../../catalogue/models/language';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { AuthorPicker } from '../../components/author-picker/author-picker';
import { GenrePicker } from '../../components/genre-picker/genre-picker';
import { normalizeOptional, parseOptionalInt } from '../../form-value-normalization';
import { LANGUAGE_OPTIONS } from '../../language-options';

/**
 * Page dédiée de création d'un Title (DEV-06.9, `CATALOGUE_MANAGE`),
 * `/staff/catalogue/new` — précédent `AdminUserCreatePage` (DEV-05.12) :
 * jamais un Dialog pour un formulaire aussi long. `authorIds`/`genreIds` ne
 * sont pas des contrôles du `FormGroup` (sélection gérée par
 * `AuthorPicker`/`GenrePicker`, composants dédiés) — seule leur présence
 * (`authorIds.length >= 1`) est validée côté UX, jamais une règle métier
 * dupliquée.
 */
@Component({
  selector: 'app-staff-title-create-page',
  imports: [ReactiveFormsModule, InputTextModule, SelectModule, MessageModule, ButtonModule, AuthorPicker, GenrePicker],
  templateUrl: './staff-title-create-page.html',
  styleUrl: './staff-title-create-page.scss',
})
export class StaffTitleCreatePage {
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);

  readonly languageOptions = LANGUAGE_OPTIONS;

  readonly form = this.formBuilder.group({
    isbn: this.formBuilder.control(''),
    title: this.formBuilder.control('', [Validators.required]),
    subtitle: this.formBuilder.control(''),
    summary: this.formBuilder.control(''),
    publicationYear: this.formBuilder.control(''),
    language: this.formBuilder.control<Language | null>(null, [Validators.required]),
    pageCount: this.formBuilder.control(''),
    publisher: this.formBuilder.control(''),
    coverImageUrl: this.formBuilder.control(''),
  });

  readonly selectedAuthors = signal<AuthorResponse[]>([]);
  readonly selectedGenres = signal<GenreResponse[]>([]);
  readonly authorsTouched = signal(false);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private lastFieldErrors: readonly FieldError[] = [];

  get authorsInvalid(): boolean {
    return this.authorsTouched() && this.selectedAuthors().length === 0;
  }

  fieldError(field: string): string | undefined {
    return this.lastFieldErrors.find((fieldError) => fieldError.field === field)?.message;
  }

  onAuthorsChange(authors: AuthorResponse[]): void {
    this.selectedAuthors.set(authors);
    this.authorsTouched.set(true);
  }

  onGenresChange(genres: GenreResponse[]): void {
    this.selectedGenres.set(genres);
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    this.authorsTouched.set(true);
    if (this.form.invalid || this.selectedAuthors().length === 0) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.lastFieldErrors = [];

    this.staffCatalogueApiService.createTitle(this.buildRequest()).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.messageService.add({ severity: 'success', summary: 'Titre créé', detail: response.title });
        void this.router.navigate(['/staff/catalogue', response.id]);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const appError = toAppError(err);
        this.errorMessage.set(appError.message);
        this.lastFieldErrors = appError.fieldErrors;
      },
    });
  }

  private buildRequest(): CreateTitleRequest {
    const raw = this.form.getRawValue();
    const request: CreateTitleRequest = {
      title: raw.title.trim(),
      language: raw.language as Language,
      authorIds: this.selectedAuthors().map((author) => author.id),
    };

    const normalizedIsbn = normalizeOptional(raw.isbn);
    if (normalizedIsbn !== null) {
      request.isbn = normalizedIsbn;
    }
    const normalizedSubtitle = normalizeOptional(raw.subtitle);
    if (normalizedSubtitle !== null) {
      request.subtitle = normalizedSubtitle;
    }
    const normalizedSummary = normalizeOptional(raw.summary);
    if (normalizedSummary !== null) {
      request.summary = normalizedSummary;
    }
    const parsedPublicationYear = parseOptionalInt(raw.publicationYear);
    if (parsedPublicationYear !== null) {
      request.publicationYear = parsedPublicationYear;
    }
    const parsedPageCount = parseOptionalInt(raw.pageCount);
    if (parsedPageCount !== null) {
      request.pageCount = parsedPageCount;
    }
    const normalizedPublisher = normalizeOptional(raw.publisher);
    if (normalizedPublisher !== null) {
      request.publisher = normalizedPublisher;
    }
    const normalizedCoverImageUrl = normalizeOptional(raw.coverImageUrl);
    if (normalizedCoverImageUrl !== null) {
      request.coverImageUrl = normalizedCoverImageUrl;
    }
    if (this.selectedGenres().length > 0) {
      request.genreIds = this.selectedGenres().map((genre) => genre.id);
    }

    return request;
  }
}
