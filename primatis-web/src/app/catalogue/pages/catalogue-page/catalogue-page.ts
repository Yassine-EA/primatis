import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { PaginatorState } from 'primeng/types/paginator';
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
 * backend, jamais reconstruit ici). Filtres limités à `q` (titre, partiel —
 * jamais sous-titre/auteur/sujet, cf. `TitleSpecifications`) et `language` —
 * `authorId`/`genreCode` ne sont volontairement pas exposés : aucun endpoint
 * public Author/Genre n'existe pour peupler ces contrôles (audit DEV-06.8
 * §3). Aucune disponibilité/Copy affichée : `CopyApiService` reste
 * staff-only (`COPY_READ`/`COPY_MANAGE`). Aucun tri client : le backend
 * impose un tri fixe (`title ASC, id ASC`).
 *
 * `q` synchronisé avec l'URL (`/catalogue?q=...`, DESIGN-V2-B2 §5/§28,
 * révise DEV-15.5 §9) : contrat minimal pour permettre une future recherche
 * déclenchée depuis la Home. La synchronisation ne relit `route.
 * queryParamMap` que pour appliquer un `q` *externe* (navigation directe ou
 * lien entrant) — jamais notre propre écriture, cf. garde `!==` ci-dessous.
 * Combinée à `distinctUntilChanged()` (déjà en place, DEV-15.7/15.8), une
 * recherche identique reste toujours relançable après réinitialisation ou
 * navigation : le flux `q.valueChanges` ne voit jamais deux émissions
 * consécutives strictement égales sans qu'une valeur différente s'intercale
 * (typé, effacé, ou navigation externe) — même précédent audité que le bug
 * réel DEV-15 (`resetState()` + `distinctUntilChanged()` sur un Subject
 * *persistant* de dialog), non reproductible ici (page recréée à chaque
 * navigation, garde explicite sur la resynchronisation externe).
 * `Title` (`@angular/platform-browser`) met à jour l'onglet du navigateur
 * (DEV-15.5 §30).
 */
@Component({
  selector: 'app-catalogue-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    InputTextModule,
    SelectModule,
    PaginatorModule,
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
  private readonly titleService = inject(Title);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly languageOptions = LANGUAGE_OPTIONS;

  private readonly initialQuery = this.route.snapshot.queryParamMap.get('q')?.trim() ?? '';

  readonly filtersForm = this.formBuilder.group({
    q: this.formBuilder.control(this.initialQuery),
    language: this.formBuilder.control<Language | null>(null),
  });

  // `FormControl.value` n'est pas un Signal : le lire directement dans le
  // template (ex. affichage conditionnel du bouton "Effacer") provoquerait
  // un NG0100 (ExpressionChangedAfterItHasBeenCheckedError) dès que la
  // valeur change sans passer par un nouveau cycle de détection déclenché
  // par un Signal. `toSignal` republie `valueChanges` comme un Signal réel.
  readonly searchTerm = toSignal(this.filtersForm.controls.q.valueChanges, {
    initialValue: this.filtersForm.controls.q.value,
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
    this.titleService.setTitle('Catalogue — PRIMATIS');

    // Recherche texte débouncée : évite un appel HTTP à chaque frappe.
    // Un changement de langue reste un choix discret (pas de saisie
    // continue) — pas de debounce nécessaire pour ce contrôle.
    this.filtersForm.controls.q.valueChanges
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => {
        this.load(0, this.lastSize);
        this.syncUrlQuery(value);
      });

    this.filtersForm.controls.language.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load(0, this.lastSize));

    // Applique un `q` externe (navigation directe `/catalogue?q=...`, lien
    // entrant) : ne réagit jamais à notre propre écriture (`syncUrlQuery`
    // ci-dessous), qui produit toujours une URL déjà égale à la valeur du
    // formulaire au moment où elle est émise ici.
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const urlQuery = (params.get('q') ?? '').trim();
      if (urlQuery !== this.filtersForm.controls.q.value.trim()) {
        this.filtersForm.controls.q.setValue(urlQuery);
      }
    });

    this.load(0, DEFAULT_PAGE_SIZE);
  }

  /** Déclenche une recherche immédiate (soumission du formulaire), sans attendre le debounce. */
  onSubmit(): void {
    this.load(0, this.lastSize);
    this.syncUrlQuery(this.filtersForm.controls.q.value);
  }

  clearSearch(): void {
    this.filtersForm.controls.q.setValue('');
  }

  resetFilters(): void {
    this.filtersForm.reset({ q: '', language: null });
  }

  hasActiveFilters(): boolean {
    const raw = this.filtersForm.getRawValue();
    return raw.q.trim().length > 0 || raw.language !== null;
  }

  onPageChange(event: PaginatorState): void {
    const rows = event.rows ?? DEFAULT_PAGE_SIZE;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.lastSize);
  }

  /** Position (0-based) du premier résultat de la page courante — reflète toujours la dernière page réellement chargée. */
  currentFirst(): number {
    return this.lastPage * this.lastSize;
  }

  currentRows(): number {
    return this.lastSize;
  }

  languageLabel(language: Language): string {
    return this.languageOptions.find((option) => option.value === language)?.label ?? language;
  }

  private syncUrlQuery(rawValue: string): void {
    const trimmed = rawValue.trim();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { q: trimmed.length > 0 ? trimmed : null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
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
