import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { TagResponse } from '../../../../articles/models/tag-response';
import { StaffTagApiService } from '../../../../articles/services/staff-tag-api.service';
import { TagFormDialog } from '../../components/tag-form-dialog/tag-form-dialog';

const DEFAULT_PAGE_SIZE = 20;

/**
 * CRUD staff des Tags (`ARTICLE_MANAGE`, DEV-11.9/DEV-11.12,
 * `/staff/articles/tags`) — écran séparé de la gestion des Articles
 * (business-rules.md §7.13 : « Tag is a resource managed independently by
 * staff »), jamais un dialog embarqué dans le formulaire Article
 * (`TagPicker` n'en propose structurellement aucun). Précédent structurel
 * `StaffCataloguePage` pour la liste paginée server-side ; aucun filtre
 * (le backend n'en expose aucun sur `GET /api/v1/staff/tags`, tri fixe
 * `label ASC, id ASC`). La suppression est réelle (contrairement à
 * Genre/Author, DEV-06.5.1) : un `409 TAG_IN_USE` est affiché tel quel
 * (message backend déjà destiné à l'utilisateur), jamais une pré-vérification
 * frontend qui introduirait une fenêtre de course.
 */
@Component({
  selector: 'app-staff-tags-page',
  imports: [RouterLink, ButtonModule, TableModule, LoadingState, EmptyState, ErrorState, TagFormDialog],
  templateUrl: './staff-tags-page.html',
  styleUrl: './staff-tags-page.scss',
})
export class StaffTagsPage {
  private readonly staffTagApiService = inject(StaffTagApiService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);

  readonly rows = signal<TagResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);

  readonly dialogVisible = signal(false);
  readonly dialogTag = signal<TagResponse | null>(null);

  readonly deletingTagId = signal<number | null>(null);

  private lastPage = 0;
  private lastSize = DEFAULT_PAGE_SIZE;

  constructor() {
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

  openCreateDialog(): void {
    this.dialogTag.set(null);
    this.dialogVisible.set(true);
  }

  openEditDialog(tag: TagResponse): void {
    this.dialogTag.set(tag);
    this.dialogVisible.set(true);
  }

  closeDialog(): void {
    this.dialogVisible.set(false);
  }

  onDialogSaved(tag: TagResponse): void {
    this.dialogVisible.set(false);
    this.dialogTag.set(null);
    this.load(this.lastPage, this.lastSize);
  }

  confirmDelete(tag: TagResponse): void {
    this.confirmationService.confirm({
      header: 'Supprimer le tag',
      message: `Voulez-vous vraiment supprimer le tag « ${tag.label} » ? Cette action est définitive.`,
      accept: () => this.performDelete(tag),
    });
  }

  private performDelete(tag: TagResponse): void {
    this.deletingTagId.set(tag.id);
    this.staffTagApiService.deleteTag(tag.id).subscribe({
      next: () => {
        this.deletingTagId.set(null);
        this.messageService.add({ severity: 'success', summary: 'Tag supprimé', detail: tag.label });
        this.load(this.lastPage, this.lastSize);
      },
      error: (err: unknown) => {
        this.deletingTagId.set(null);
        this.messageService.add({ severity: 'error', summary: 'Erreur', detail: toAppError(err).message });
      },
    });
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.lastSize = size;
    this.loading.set(true);
    this.error.set(null);

    this.staffTagApiService.listTags(page, size).subscribe({
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
