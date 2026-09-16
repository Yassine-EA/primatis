import { Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { EditorInitEvent, EditorModule } from 'primeng/editor';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { toAppError } from '../../../../core/errors/api-error.util';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';

interface QuillSelectionRange {
  index: number;
  length: number;
}

interface QuillInstance {
  root: HTMLElement;
  history?: { undo: () => void; redo: () => void };
  getSelection(focus?: boolean): QuillSelectionRange | null;
  getLength(): number;
  getSemanticHTML(): string;
  insertEmbed(index: number, type: string, value: unknown, source?: string): void;
  setSelection(index: number, length?: number, source?: string): void;
}

const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_IMAGE_FILE_SIZE_BYTES = 5 * 1024 * 1024;

/**
 * Éditeur riche WYSIWYG pour `Article.content` (`DEV-ARTICLES-EDITOR`,
 * `DEV-DEC-0078` — supersède, sur ce point précis, le textarea natif retenu
 * par DEV-11.12), réutilisé par `StaffArticleCreatePage` et
 * `StaffArticleDetailPage`. Composant de présentation pur, `input()`/
 * `output()` — même précédent structurel exact que `TagPicker`
 * (`staff/articles/components/tag-picker`) : aucun `ControlValueAccessor`
 * (aucun précédent de ce mécanisme dans le projet). Le parent reste seul
 * propriétaire du `FormControl` `content` ; ce composant se contente de
 * refléter sa valeur et d'émettre les modifications.
 *
 * PrimeNG `p-editor` (Quill 2) n'expose sa valeur qu'au travers d'un
 * accesseur de formulaire Angular interne — un `FormControl` local
 * (`internalControl`) sert uniquement à le piloter, synchronisé dans les
 * deux sens avec `content()`/`contentChange`.
 *
 * Toolbar personnalisée (gabarit `pTemplate="header"`) volontairement
 * restreinte au sous-ensemble réellement supporté par
 * `be.primatis.article.ArticleSanitizer` (allowlist jsoup, seule autorité
 * de sécurité — jamais dupliquée ici) : titres `h2`/`h3`/`h4` (jamais
 * `h1`, réservé au titre d'Article), gras/italique/souligné, listes,
 * citation, lien, suppression de mise en forme, undo/redo (module
 * `history` natif de Quill), et — depuis DEV-ARTICLES-MEDIA/DEV-DEC-0079 —
 * image (upload réel backend, jamais de base64).
 *
 * <p>Image (mission DEV-ARTICLES-MEDIA) : bouton dédié → `<input
 * type="file">` caché → validation frontend légère (type/taille, feedback
 * immédiat, jamais une protection de sécurité — le backend
 * revalide systématiquement, `.claude/rules/frontend.md`) → petit dialogue
 * de texte alternatif (§19 : jamais un système d'édition d'image complet)
 * → upload (`StaffArticleApiService.uploadArticleMedia`) → insertion via
 * l'API Quill réelle (`insertEmbed`, jamais une concaténation manuelle de
 * HTML). `alt` : le build Quill 2 embarqué par PrimeNG Editor n'a pas
 * {@code alt} enregistré comme format Parchment ({@code formatText}
 * échoue silencieusement, vérifié empiriquement — avertissement console
 * « Cannot register "alt" » — jamais un format inventé/enregistré ici,
 * mission §19 : pas de système d'édition d'image complet) — posé
 * directement sur le nœud DOM `<img>` réellement inséré par Quill
 * (fallback explicitement autorisé par la mission), puis {@code
 * getSemanticHTML()} relu et repoussé manuellement dans
 * {@link #internalControl} pour que ce {@code alt} atteigne bien
 * `contentChange` (le flux normal de {@code text-change} de PrimeNG a déjà
 * capturé le HTML sans {@code alt} un instant plus tôt, avant la pose de
 * l'attribut).
 */
@Component({
  selector: 'app-article-content-editor',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    EditorModule,
    ButtonModule,
    DialogModule,
    CheckboxModule,
    InputTextModule,
    MessageModule,
  ],
  templateUrl: './article-content-editor.html',
  styleUrl: './article-content-editor.scss',
})
export class ArticleContentEditor {
  private readonly destroyRef = inject(DestroyRef);
  private readonly staffArticleApiService = inject(StaffArticleApiService);

  readonly content = input.required<string>();
  readonly disabled = input(false);
  readonly placeholder = input('Rédigez le contenu de l’article…');

  readonly contentChange = output<string>();
  readonly editorBlur = output<void>();

  readonly formats = [
    'header',
    'bold',
    'italic',
    'underline',
    'list',
    'blockquote',
    'link',
    'image',
  ];

  readonly internalControl = new FormControl('', { nonNullable: true });

  private quill: QuillInstance | null = null;
  private pendingFile: File | null = null;
  private pendingSelection: QuillSelectionRange | null = null;

  readonly uploadError = signal<string | null>(null);
  readonly uploading = signal(false);

  readonly altDialogVisible = signal(false);
  readonly altText = signal('');
  readonly decorativeImage = signal(false);
  readonly altDialogError = signal<string | null>(null);

  constructor() {
    effect(() => {
      const value = this.content();
      if (value !== this.internalControl.value) {
        this.internalControl.setValue(value, { emitEvent: false });
      }
    });

    this.internalControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        this.contentChange.emit(value ?? '');
      });
  }

  onEditorInit(event: EditorInitEvent): void {
    this.quill = event.editor as QuillInstance;
  }

  undo(): void {
    this.quill?.history?.undo();
  }

  redo(): void {
    this.quill?.history?.redo();
  }

  // ---------------------------------------------------------------
  // Image (DEV-ARTICLES-MEDIA)
  // ---------------------------------------------------------------

  triggerImageSelection(fileInput: HTMLInputElement): void {
    if (this.disabled() || this.uploading()) {
      return;
    }
    this.uploadError.set(null);
    // Capturé ici, avant l'ouverture du sélecteur de fichier natif du
    // système : par la suite, l'éditeur n'a structurellement plus le focus
    // (bascule OS), `getSelection()` non forcé y renverrait `null`.
    // `getSelection(true)` force Quill à (re)focus — sans effet notable en
    // navigateur réel, mais peut lever si l'environnement ne peut pas
    // calculer de géométrie réelle (ex. jsdom en test) : ne doit jamais
    // bloquer la sélection du fichier pour autant, dégrade proprement vers
    // une insertion en fin de document (mission §18, IMPLEMENTATION FREEDOM).
    try {
      this.pendingSelection = this.quill?.getSelection(true) ?? null;
    } catch {
      this.pendingSelection = null;
    }
    fileInput.click();
  }

  onImageFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    // Réinitialisé immédiatement : sélectionner à nouveau le même fichier
    // après une erreur doit redéclencher un événement `change`.
    input.value = '';
    if (!file) {
      return;
    }

    const validationError = this.validateImageFile(file);
    if (validationError) {
      this.uploadError.set(validationError);
      return;
    }

    this.uploadError.set(null);
    this.pendingFile = file;
    this.altText.set('');
    this.decorativeImage.set(false);
    this.altDialogError.set(null);
    this.altDialogVisible.set(true);
  }

  private validateImageFile(file: File): string | null {
    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      return 'Format non autorisé (JPEG, PNG ou WebP uniquement).';
    }
    if (file.size > MAX_IMAGE_FILE_SIZE_BYTES) {
      return 'Le fichier dépasse la taille maximale autorisée (5 Mio).';
    }
    return null;
  }

  cancelImageInsertion(): void {
    this.altDialogVisible.set(false);
    this.pendingFile = null;
    this.pendingSelection = null;
  }

  confirmImageInsertion(): void {
    if (this.uploading() || !this.pendingFile) {
      return;
    }
    if (!this.decorativeImage() && this.altText().trim().length === 0) {
      this.altDialogError.set('Indiquez un texte alternatif, ou cochez « image décorative ».');
      return;
    }

    const file = this.pendingFile;
    const alt = this.decorativeImage() ? '' : this.altText().trim();

    this.uploading.set(true);
    this.altDialogError.set(null);
    this.staffArticleApiService.uploadArticleMedia(file).subscribe({
      next: (response) => {
        this.uploading.set(false);
        this.altDialogVisible.set(false);
        this.insertImage(response.url, alt);
        this.pendingFile = null;
        this.pendingSelection = null;
      },
      error: (err: unknown) => {
        this.uploading.set(false);
        this.altDialogVisible.set(false);
        this.uploadError.set(toAppError(err).message);
        this.pendingFile = null;
        this.pendingSelection = null;
      },
    });
  }

  private insertImage(url: string, alt: string): void {
    if (!this.quill) {
      return;
    }
    const range = this.pendingSelection ?? { index: this.quill.getLength(), length: 0 };
    this.quill.insertEmbed(range.index, 'image', url, 'user');
    this.quill.setSelection(range.index + 1, 0, 'user');

    // `alt` posé directement sur le nœud DOM réellement inséré par Quill
    // (voir Javadoc de la classe) — dernier <img src="url"> du document,
    // puisqu'un même fichier peut en théorie apparaître plusieurs fois.
    const insertedImages = this.quill.root.querySelectorAll<HTMLImageElement>(`img[src="${url}"]`);
    const insertedImage = insertedImages[insertedImages.length - 1];
    if (insertedImage) {
      insertedImage.setAttribute('alt', alt);
    }

    // Le flux normal (`text-change` interne à p-editor) a déjà propagé le
    // HTML sans `alt` un instant plus tôt — relecture explicite et
    // réémission manuelle pour que `contentChange` reflète bien `alt`.
    this.internalControl.setValue(this.quill.getSemanticHTML());
  }
}
