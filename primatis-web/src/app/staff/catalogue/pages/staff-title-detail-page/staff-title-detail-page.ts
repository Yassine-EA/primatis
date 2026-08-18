import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';

import { AuthService } from '../../../../auth/services/auth.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { AuthorResponse } from '../../../../catalogue/models/author-response';
import { AvailabilityStatus } from '../../../../catalogue/models/availability-status';
import { CopyResponse } from '../../../../catalogue/models/copy-response';
import { GenreResponse } from '../../../../catalogue/models/genre-response';
import { Language } from '../../../../catalogue/models/language';
import { TitleDetailResponse } from '../../../../catalogue/models/title-detail-response';
import { TitleStatus } from '../../../../catalogue/models/title-status';
import { UpdateTitleRequest } from '../../../../catalogue/models/update-title-request';
import { CopyApiService } from '../../../../catalogue/services/copy-api.service';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { AuthorPicker } from '../../components/author-picker/author-picker';
import { CopyFormDialog } from '../../components/copy-form-dialog/copy-form-dialog';
import { GenrePicker } from '../../components/genre-picker/genre-picker';
import { normalizeOptional, parseOptionalInt } from '../../form-value-normalization';
import { sameIdSet } from '../../id-set';
import { LANGUAGE_OPTIONS } from '../../language-options';

const INVALID_TITLE_ID_ERROR: AppError = { message: 'Identifiant de titre invalide.', fieldErrors: [] };

function parseTitleId(rawId: string | null): number | null {
  if (rawId === null) {
    return null;
  }
  const id = Number(rawId);
  return Number.isInteger(id) ? id : null;
}

/**
 * Détail staff d'un Title (DEV-06.9, `CATALOGUE_MANAGE`) : édition PATCH
 * sparse inline (précédent `AdminUserDetailPage`, DEV-05.12), statut
 * `ACTIVE ⇄ WITHDRAWN` (bouton + confirmation), section Copies
 * conditionnée à `COPY_READ`/`COPY_MANAGE` — permissions distinctes de
 * `CATALOGUE_MANAGE`, vérifiées via `AuthService.hasPermission` (UX
 * uniquement, jamais l'autorité — le backend revalide systématiquement).
 */
@Component({
  selector: 'app-staff-title-detail-page',
  imports: [
    ReactiveFormsModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ButtonModule,
    TagModule,
    LoadingState,
    EmptyState,
    ErrorState,
    AuthorPicker,
    GenrePicker,
    CopyFormDialog,
  ],
  templateUrl: './staff-title-detail-page.html',
  styleUrl: './staff-title-detail-page.scss',
})
export class StaffTitleDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);
  private readonly copyApiService = inject(CopyApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);

  private titleId: number | null = null;

  readonly languageOptions = LANGUAGE_OPTIONS;

  readonly title = signal<TitleDetailResponse | null>(null);
  readonly titleLoading = signal(false);
  readonly titleError = signal<AppError | null>(null);

  readonly copies = signal<CopyResponse[]>([]);
  readonly copiesLoading = signal(false);
  readonly copiesError = signal<AppError | null>(null);

  readonly selectedAuthors = signal<AuthorResponse[]>([]);
  readonly selectedGenres = signal<GenreResponse[]>([]);
  readonly authorsTouched = signal(false);

  readonly updateSubmitting = signal(false);
  readonly updateErrorMessage = signal<string | null>(null);
  private lastUpdateFieldErrors: readonly FieldError[] = [];

  readonly statusSubmitting = signal(false);
  readonly availabilitySubmittingCopyId = signal<number | null>(null);

  readonly copyDialogVisible = signal(false);
  readonly copyDialogCopy = signal<CopyResponse | null>(null);

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

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = parseTitleId(params.get('id'));
      if (id === null) {
        this.titleId = null;
        this.title.set(null);
        this.titleLoading.set(false);
        this.titleError.set(INVALID_TITLE_ID_ERROR);
        return;
      }
      this.titleId = id;
      this.loadTitle(id);
      if (this.canReadCopies) {
        this.loadCopies(id);
      }
    });
  }

  get canReadCopies(): boolean {
    return this.authService.hasPermission('COPY_READ');
  }

  get canManageCopies(): boolean {
    return this.authService.hasPermission('COPY_MANAGE');
  }

  get authorsInvalid(): boolean {
    return this.authorsTouched() && this.selectedAuthors().length === 0;
  }

  get statusActionLabel(): string {
    return this.title()?.titleStatus === 'ACTIVE' ? 'Retirer du catalogue' : 'Réintégrer au catalogue';
  }

  fieldError(field: string): string | undefined {
    return this.lastUpdateFieldErrors.find((fieldError) => fieldError.field === field)?.message;
  }

  titleStatusSeverity(status: TitleStatus): 'success' | 'warn' {
    return status === 'ACTIVE' ? 'success' : 'warn';
  }

  onAuthorsChange(authors: AuthorResponse[]): void {
    this.selectedAuthors.set(authors);
    this.authorsTouched.set(true);
  }

  onGenresChange(genres: GenreResponse[]): void {
    this.selectedGenres.set(genres);
  }

  // ---------------------------------------------------------------
  // Chargement
  // ---------------------------------------------------------------

  private loadTitle(id: number): void {
    this.titleLoading.set(true);
    this.titleError.set(null);
    this.staffCatalogueApiService.getTitleById(id).subscribe({
      next: (value) => {
        this.title.set(value);
        this.resetFormFromTitle(value);
        this.titleLoading.set(false);
      },
      error: (err: unknown) => {
        this.titleLoading.set(false);
        this.titleError.set(toAppError(err));
      },
    });
  }

  private loadCopies(id: number): void {
    this.copiesLoading.set(true);
    this.copiesError.set(null);
    this.copyApiService.listCopies(id).subscribe({
      next: (value) => {
        this.copies.set(value);
        this.copiesLoading.set(false);
      },
      error: (err: unknown) => {
        this.copiesLoading.set(false);
        this.copiesError.set(toAppError(err));
      },
    });
  }

  retryTitle(): void {
    if (this.titleId !== null) {
      this.loadTitle(this.titleId);
    }
  }

  retryCopies(): void {
    if (this.titleId !== null) {
      this.loadCopies(this.titleId);
    }
  }

  private resetFormFromTitle(title: TitleDetailResponse): void {
    this.form.reset({
      isbn: title.isbn ?? '',
      title: title.title,
      subtitle: title.subtitle ?? '',
      summary: title.summary ?? '',
      publicationYear: title.publicationYear !== null ? String(title.publicationYear) : '',
      language: title.language,
      pageCount: title.pageCount !== null ? String(title.pageCount) : '',
      publisher: title.publisher ?? '',
      coverImageUrl: title.coverImageUrl ?? '',
    });
    this.selectedAuthors.set(title.authors);
    this.selectedGenres.set(title.genres);
    this.authorsTouched.set(false);
  }

  // ---------------------------------------------------------------
  // PATCH sparse Title
  // ---------------------------------------------------------------

  submitUpdate(): void {
    if (this.updateSubmitting() || this.titleId === null) {
      return;
    }
    this.authorsTouched.set(true);
    if (this.form.invalid || this.selectedAuthors().length === 0) {
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

    this.staffCatalogueApiService.updateTitle(this.titleId, request).subscribe({
      next: (response) => {
        this.updateSubmitting.set(false);
        this.title.set(response);
        this.resetFormFromTitle(response);
        this.messageService.add({ severity: 'success', summary: 'Titre modifié', detail: response.title });
      },
      error: (err: unknown) => {
        this.updateSubmitting.set(false);
        const appError = toAppError(err);
        this.updateErrorMessage.set(appError.message);
        this.lastUpdateFieldErrors = appError.fieldErrors;
      },
    });
  }

  private buildUpdateRequest(): UpdateTitleRequest {
    const current = this.title();
    if (current === null) {
      return {};
    }
    const raw = this.form.getRawValue();
    const request: UpdateTitleRequest = {};

    const normalizedIsbn = normalizeOptional(raw.isbn);
    if (normalizedIsbn !== current.isbn) {
      request.isbn = normalizedIsbn;
    }
    const trimmedTitle = raw.title.trim();
    if (trimmedTitle !== current.title) {
      request.title = trimmedTitle;
    }
    const normalizedSubtitle = normalizeOptional(raw.subtitle);
    if (normalizedSubtitle !== current.subtitle) {
      request.subtitle = normalizedSubtitle;
    }
    const normalizedSummary = normalizeOptional(raw.summary);
    if (normalizedSummary !== current.summary) {
      request.summary = normalizedSummary;
    }
    const parsedPublicationYear = parseOptionalInt(raw.publicationYear);
    if (parsedPublicationYear !== current.publicationYear) {
      request.publicationYear = parsedPublicationYear;
    }
    if (raw.language !== null && raw.language !== current.language) {
      request.language = raw.language;
    }
    const parsedPageCount = parseOptionalInt(raw.pageCount);
    if (parsedPageCount !== current.pageCount) {
      request.pageCount = parsedPageCount;
    }
    const normalizedPublisher = normalizeOptional(raw.publisher);
    if (normalizedPublisher !== current.publisher) {
      request.publisher = normalizedPublisher;
    }
    const normalizedCoverImageUrl = normalizeOptional(raw.coverImageUrl);
    if (normalizedCoverImageUrl !== current.coverImageUrl) {
      request.coverImageUrl = normalizedCoverImageUrl;
    }
    if (!sameIdSet(current.authors, this.selectedAuthors())) {
      request.authorIds = this.selectedAuthors().map((author) => author.id);
    }
    if (!sameIdSet(current.genres, this.selectedGenres())) {
      request.genreIds = this.selectedGenres().map((genre) => genre.id);
    }

    return request;
  }

  // ---------------------------------------------------------------
  // Title status
  // ---------------------------------------------------------------

  confirmToggleStatus(): void {
    const current = this.title();
    if (current === null) {
      return;
    }
    const next: TitleStatus = current.titleStatus === 'ACTIVE' ? 'WITHDRAWN' : 'ACTIVE';
    this.confirmationService.confirm({
      header: this.statusActionLabel,
      message:
        next === 'WITHDRAWN'
          ? 'Voulez-vous vraiment retirer ce titre du catalogue ?'
          : 'Voulez-vous réintégrer ce titre au catalogue ?',
      accept: () => this.performToggleStatus(next),
    });
  }

  private performToggleStatus(next: TitleStatus): void {
    if (this.titleId === null) {
      return;
    }
    this.statusSubmitting.set(true);
    this.staffCatalogueApiService.updateTitleStatus(this.titleId, { status: next }).subscribe({
      next: (response) => {
        this.statusSubmitting.set(false);
        this.title.set(response);
        this.resetFormFromTitle(response);
        this.messageService.add({
          severity: 'success',
          summary: 'Statut modifié',
          detail: response.titleStatus === 'ACTIVE' ? 'Le titre est de nouveau actif.' : 'Le titre a été retiré du catalogue.',
        });
      },
      error: (err: unknown) => {
        this.statusSubmitting.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  // ---------------------------------------------------------------
  // Copies
  // ---------------------------------------------------------------

  openCreateCopyDialog(): void {
    this.copyDialogCopy.set(null);
    this.copyDialogVisible.set(true);
  }

  openEditCopyDialog(copy: CopyResponse): void {
    this.copyDialogCopy.set(copy);
    this.copyDialogVisible.set(true);
  }

  closeCopyDialog(): void {
    this.copyDialogVisible.set(false);
  }

  onCopyDialogSaved(copy: CopyResponse): void {
    this.copyDialogVisible.set(false);
    this.copyDialogCopy.set(null);
    const existingIndex = this.copies().findIndex((candidate) => candidate.id === copy.id);
    if (existingIndex === -1) {
      this.copies.set([...this.copies(), copy]);
    } else {
      this.copies.set(this.copies().map((candidate) => (candidate.id === copy.id ? copy : candidate)));
    }
  }

  /** `ON_LOAN`/`RESERVED` n'ont jamais d'action manuelle — lecture seule. */
  canToggleAvailability(copy: CopyResponse): boolean {
    return copy.availabilityStatus === 'AVAILABLE' || copy.availabilityStatus === 'UNAVAILABLE';
  }

  /**
   * Protection UX uniquement (miroir non contraignant de
   * `COPY_CONDITION_REQUIRES_UNAVAILABLE`) : le backend reste le dernier
   * arbitre, ce bouton n'est qu'une aide, jamais une garantie.
   */
  canOfferAvailable(copy: CopyResponse): boolean {
    return copy.copyCondition !== 'LOST' && copy.copyCondition !== 'OUT_OF_SERVICE';
  }

  availabilityActionLabel(copy: CopyResponse): string {
    return copy.availabilityStatus === 'AVAILABLE' ? 'Rendre indisponible' : 'Rendre disponible';
  }

  confirmToggleAvailability(copy: CopyResponse): void {
    const next: AvailabilityStatus = copy.availabilityStatus === 'AVAILABLE' ? 'UNAVAILABLE' : 'AVAILABLE';
    this.confirmationService.confirm({
      header: this.availabilityActionLabel(copy),
      message:
        next === 'UNAVAILABLE'
          ? `Voulez-vous rendre l'exemplaire ${copy.inventoryCode} indisponible ?`
          : `Voulez-vous rendre l'exemplaire ${copy.inventoryCode} disponible ?`,
      accept: () => this.performToggleAvailability(copy, next),
    });
  }

  private performToggleAvailability(copy: CopyResponse, next: AvailabilityStatus): void {
    if (this.titleId === null) {
      return;
    }
    this.availabilitySubmittingCopyId.set(copy.id);
    this.copyApiService.updateAvailability(this.titleId, copy.id, { status: next }).subscribe({
      next: (response) => {
        this.availabilitySubmittingCopyId.set(null);
        this.copies.set(this.copies().map((candidate) => (candidate.id === response.id ? response : candidate)));
        this.messageService.add({
          severity: 'success',
          summary: 'Disponibilité modifiée',
          detail: `${response.inventoryCode} est maintenant ${response.availabilityStatus === 'AVAILABLE' ? 'disponible' : 'indisponible'}.`,
        });
      },
      error: (err: unknown) => {
        this.availabilitySubmittingCopyId.set(null);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }
}
