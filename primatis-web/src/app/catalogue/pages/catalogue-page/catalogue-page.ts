import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { AppError } from '../../../core/errors/api-error';
import { toAppError } from '../../../core/errors/api-error.util';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import { Language } from '../../models/language';
import { TitleResponse } from '../../models/title-response';
import { TitleSearchParams } from '../../models/title-search-params';
import { CatalogueApiService } from '../../services/catalogue-api.service';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface LanguageOption {
  readonly label: string;
  readonly value: Language | null;
}

const LANGUAGE_OPTIONS: LanguageOption[] = [
  { label: 'Toutes les langues', value: null },
  { label: 'Français', value: 'FR' },
  { label: 'Anglais', value: 'EN' },
  { label: 'Néerlandais', value: 'NL' },
  { label: 'Allemand', value: 'DE' },
  { label: 'Espagnol', value: 'ES' },
  { label: 'Italien', value: 'IT' },
  { label: 'Latin', value: 'LA' },
];

/**
 * Catalogue public (DEV-06.8, `GET /api/v1/titles`, surface `permitAll`) :
 * liste paginée server-side des Titles `ACTIVE` uniquement (imposé par le
 * backend, jamais reconstruit ici). Filtres limités à `q` (titre, partiel)
 * et `language` — `authorId`/`genreCode` ne sont volontairement pas exposés
 * : aucun endpoint public Author/Genre n'existe pour peupler ces contrôles
 * (audit DEV-06.8 §3). Aucune disponibilité/Copy affichée : `CopyApiService`
 * reste staff-only (`COPY_READ`/`COPY_MANAGE`).
 */
@Component({
  selector: 'app-catalogue-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    InputTextModule,
    SelectModule,
    TableModule,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './catalogue-page.html',
  styleUrl: './catalogue-page.scss',
})
export class CataloguePage {
  private readonly catalogueApiService = inject(CatalogueApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly languageOptions = LANGUAGE_OPTIONS;

  readonly filtersForm = this.formBuilder.group({
    q: this.formBuilder.control(''),
    language: this.formBuilder.control<Language | null>(null),
  });

  readonly rows = signal<TitleResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur), même précédent que StaffUsersPage (DEV-05.11).
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
    // Recherche texte débouncée : évite un appel HTTP à chaque frappe.
    // Un changement de langue reste un choix discret (pas de saisie
    // continue) — pas de debounce nécessaire pour ce contrôle.
    this.filtersForm.controls.q.valueChanges
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.load(0, this.lastSize));

    this.filtersForm.controls.language.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load(0, this.lastSize));

    this.load(0, DEFAULT_PAGE_SIZE);
  }

  /**
   * `event.first`/`event.rows` sont optionnels dans le typage PrimeNG :
   * toujours retomber sur des valeurs par défaut explicites plutôt que de
   * propager `undefined` vers le backend (même précédent StaffUsersPage).
   */
  onLazyLoad(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    const raw = this.filtersForm.getRawValue();
    const params: TitleSearchParams = { page, size };
    const trimmedQ = raw.q.trim();
    if (trimmedQ.length > 0) {
      params.q = trimmedQ;
    }
    if (raw.language !== null) {
      params.language = raw.language;
    }

    this.catalogueApiService.searchTitles(params).subscribe({
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
