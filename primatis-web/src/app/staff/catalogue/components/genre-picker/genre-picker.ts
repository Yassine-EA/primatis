import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { MultiSelectModule } from 'primeng/multiselect';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { GenreResponse } from '../../../../catalogue/models/genre-response';
import { StaffCatalogueApiService } from '../../../../catalogue/services/staff-catalogue-api.service';
import { GenreFormDialog } from '../genre-form-dialog/genre-form-dialog';
import { loadAllGenres } from '../../genre-loading';

/**
 * Sélecteur de Genres (DEV-06.9, `CATALOGUE_MANAGE`), réutilisé par
 * `StaffTitleCreatePage` et `StaffTitleDetailPage`. Contrairement à
 * `AuthorPicker`, la liste complète est chargée une fois (`loadAllGenres`,
 * déterministe, sans troncature — Genre est un dataset borné, jamais aussi
 * volumineux qu'Author) puis affichée via `MultiSelect` avec filtre local
 * PrimeNG (aucun nouvel appel réseau à la frappe).
 */
@Component({
  selector: 'app-genre-picker',
  imports: [FormsModule, ButtonModule, MultiSelectModule, LoadingState, ErrorState, GenreFormDialog],
  templateUrl: './genre-picker.html',
  styleUrl: './genre-picker.scss',
})
export class GenrePicker implements OnInit {
  private readonly staffCatalogueApiService = inject(StaffCatalogueApiService);

  readonly initialGenres = input<GenreResponse[]>([]);
  readonly selectionChange = output<GenreResponse[]>();

  readonly allGenres = signal<GenreResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly selectedIds = signal<number[]>([]);

  readonly dialogVisible = signal(false);
  readonly dialogGenre = signal<GenreResponse | null>(null);

  /**
   * Initialisation à un seul coup depuis `initialGenres` — jamais dans le
   * constructeur, même raison qu'`AuthorPicker.ngOnInit` (inputs signal
   * garantis résolus à partir de `ngOnInit` seulement).
   */
  ngOnInit(): void {
    this.selectedIds.set(this.initialGenres().map((genre) => genre.id));
    this.loadGenres();
  }

  onSelectionChange(ids: number[]): void {
    this.selectedIds.set(ids);
    this.emitSelection();
  }

  retry(): void {
    this.loadGenres();
  }

  openCreateDialog(): void {
    this.dialogGenre.set(null);
    this.dialogVisible.set(true);
  }

  openEditDialog(genre: GenreResponse): void {
    this.dialogGenre.set(genre);
    this.dialogVisible.set(true);
  }

  closeDialog(): void {
    this.dialogVisible.set(false);
  }

  onDialogSaved(genre: GenreResponse): void {
    const wasCreate = this.dialogGenre() === null;
    this.dialogVisible.set(false);
    this.dialogGenre.set(null);

    if (wasCreate) {
      this.allGenres.set([...this.allGenres(), genre]);
      this.selectedIds.set([...this.selectedIds(), genre.id]);
      this.emitSelection();
      return;
    }

    this.allGenres.set(this.allGenres().map((candidate) => (candidate.id === genre.id ? genre : candidate)));
    if (this.selectedIds().includes(genre.id)) {
      this.emitSelection();
    }
  }

  private emitSelection(): void {
    const ids = this.selectedIds();
    this.selectionChange.emit(this.allGenres().filter((genre) => ids.includes(genre.id)));
  }

  private loadGenres(): void {
    this.loading.set(true);
    this.error.set(null);
    loadAllGenres(this.staffCatalogueApiService).subscribe({
      next: (genres) => {
        this.allGenres.set(genres);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
