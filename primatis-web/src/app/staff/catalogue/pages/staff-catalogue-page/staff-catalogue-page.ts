import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { Language } from '../../../../catalogue/models/language';
import { StaffTitleSearchParams } from '../../../../catalogue/models/title-search-params';
import { TitleResponse } from '../../../../catalogue/models/title-response';
import { TitleStatus } from '../../../../catalogue/models/title-status';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { LANGUAGE_OPTIONS } from '../../language-options';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface LanguageFilterOption {
  label: string;
  value: Language | null;
}

interface TitleStatusFilterOption {
  label: string;
  value: TitleStatus | null;
}

const LANGUAGE_FILTER_OPTIONS: LanguageFilterOption[] = [
  { label: 'Toutes les langues', value: null },
  ...LANGUAGE_OPTIONS,
];

const TITLE_STATUS_FILTER_OPTIONS: TitleStatusFilterOption[] = [
  { label: 'Tous les statuts', value: null },
  { label: 'Actif', value: 'ACTIVE' },
  { label: 'Retiré', value: 'WITHDRAWN' },
];

/**
 * Liste staff paginée server-side du catalogue (DEV-06.9, `CATALOGUE_MANAGE`).
 * Filtres V1 strictement limités à `q`/`language`/`titleStatus` (K.7,
 * FIGÉ) — `authorId`/`genreCode` volontairement différés (nécessiteraient
 * le même contrôle de recherche live qu'`AuthorPicker`, hors scope liste).
 * `titleStatus` absent = `ACTIVE` et `WITHDRAWN` tous deux visibles
 * (contrat backend réel, `StaffTitleController`).
 */
@Component({
  selector: 'app-staff-catalogue-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    InputTextModule,
    SelectModule,
    TableModule,
    TagModule,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './staff-catalogue-page.html',
  styleUrl: './staff-catalogue-page.scss',
})
export class StaffCataloguePage {
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly languageOptions = LANGUAGE_FILTER_OPTIONS;
  readonly titleStatusOptions = TITLE_STATUS_FILTER_OPTIONS;

  readonly filtersForm = this.formBuilder.group({
    q: this.formBuilder.control(''),
    language: this.formBuilder.control<Language | null>(null),
    titleStatus: this.formBuilder.control<TitleStatus | null>(null),
  });

  readonly rows = signal<TitleResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    this.filtersForm.controls.q.valueChanges
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.load(0, this.lastSize));

    this.filtersForm.controls.language.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.load(0, this.lastSize));
    this.filtersForm.controls.titleStatus.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.load(0, this.lastSize));

    this.load(0, DEFAULT_PAGE_SIZE);
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  titleStatusSeverity(status: TitleStatus): 'success' | 'warn' {
    return status === 'ACTIVE' ? 'success' : 'warn';
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    const raw = this.filtersForm.getRawValue();
    const params: StaffTitleSearchParams = { page, size };
    const trimmedQ = raw.q.trim();
    if (trimmedQ.length > 0) {
      params.q = trimmedQ;
    }
    if (raw.language !== null) {
      params.language = raw.language;
    }
    if (raw.titleStatus !== null) {
      params.titleStatus = raw.titleStatus;
    }

    this.staffCatalogueApiService.searchTitles(params).subscribe({
      next: (response) => {
        this.rows.set(response.content);
        this.totalRecords.set(response.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
