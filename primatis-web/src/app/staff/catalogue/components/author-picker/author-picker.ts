import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { AuthorResponse } from '../../../../catalogue/models/author-response';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { AuthorFormDialog } from '../author-form-dialog/author-form-dialog';

const SEARCH_DEBOUNCE_MS = 300;
const SEARCH_PAGE_SIZE = 20;

/**
 * Sélecteur d'Authors par recherche live (DEV-06.9, `CATALOGUE_MANAGE`),
 * réutilisé par `StaffTitleCreatePage` et `StaffTitleDetailPage`. Jamais un
 * `p-select` chargeant tous les Authors : la volumétrie n'est pas bornée
 * (contrairement à Genre) — recherche `searchAuthors({ q })` débouncée,
 * comme `CataloguePage` (DEV-06.8). Homonymes explicitement autorisés :
 * aucune déduplication par `fullName`, uniquement par `id`.
 */
@Component({
  selector: 'app-author-picker',
  imports: [FormsModule, ButtonModule, InputTextModule, ErrorState, AuthorFormDialog],
  templateUrl: './author-picker.html',
  styleUrl: './author-picker.scss',
})
export class AuthorPicker implements OnInit {
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);

  readonly initialAuthors = input<AuthorResponse[]>([]);
  readonly selectionChange = output<AuthorResponse[]>();

  readonly selected = signal<AuthorResponse[]>([]);
  readonly searchTerm = signal('');
  readonly searchResults = signal<AuthorResponse[]>([]);
  readonly searching = signal(false);
  readonly searchError = signal<AppError | null>(null);

  readonly dialogVisible = signal(false);
  readonly dialogAuthor = signal<AuthorResponse | null>(null);

  private readonly searchInput$ = new Subject<string>();

  constructor() {
    this.searchInput$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.runSearch(value));
  }

  /**
   * Initialisation à un seul coup depuis `initialAuthors` — jamais dans le
   * constructeur : les inputs signal ne sont garantis résolus qu'à partir
   * de `ngOnInit` (`fixture.componentRef.setInput()` en test, comme la
   * liaison de template réelle, s'applique après la construction).
   * Volontairement non réactif ensuite : un changement ultérieur de
   * `initialAuthors` n'écrase jamais une sélection utilisateur en cours.
   */
  ngOnInit(): void {
    this.selected.set(this.initialAuthors());
  }

  onSearchInput(value: string): void {
    this.searchTerm.set(value);
    this.searchInput$.next(value);
  }

  isSelected(author: AuthorResponse): boolean {
    return this.selected().some((candidate) => candidate.id === author.id);
  }

  add(author: AuthorResponse): void {
    if (this.isSelected(author)) {
      return;
    }
    const next = [...this.selected(), author];
    this.selected.set(next);
    this.selectionChange.emit(next);
  }

  remove(author: AuthorResponse): void {
    const next = this.selected().filter((candidate) => candidate.id !== author.id);
    this.selected.set(next);
    this.selectionChange.emit(next);
  }

  openCreateDialog(): void {
    this.dialogAuthor.set(null);
    this.dialogVisible.set(true);
  }

  openEditDialog(author: AuthorResponse): void {
    this.dialogAuthor.set(author);
    this.dialogVisible.set(true);
  }

  closeDialog(): void {
    this.dialogVisible.set(false);
  }

  onDialogSaved(author: AuthorResponse): void {
    const wasCreate = this.dialogAuthor() === null;
    this.dialogVisible.set(false);
    this.dialogAuthor.set(null);

    if (wasCreate) {
      this.add(author);
      return;
    }

    this.searchResults.set(this.searchResults().map((candidate) => (candidate.id === author.id ? author : candidate)));
    if (this.isSelected(author)) {
      const next = this.selected().map((candidate) => (candidate.id === author.id ? author : candidate));
      this.selected.set(next);
      this.selectionChange.emit(next);
    }
  }

  private runSearch(value: string): void {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      this.searchResults.set([]);
      this.searching.set(false);
      this.searchError.set(null);
      return;
    }
    this.searching.set(true);
    this.searchError.set(null);
    this.staffCatalogueApiService.searchAuthors({ q: trimmed, page: 0, size: SEARCH_PAGE_SIZE }).subscribe({
      next: (response) => {
        this.searchResults.set(response.content);
        this.searching.set(false);
      },
      error: (err: unknown) => {
        this.searching.set(false);
        this.searchError.set(toAppError(err));
      },
    });
  }
}
