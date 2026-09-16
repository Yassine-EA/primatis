import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';
import { ArticleContentEditor } from './article-content-editor';

function fileChangeEvent(file: File | null): Event {
  const input = document.createElement('input');
  input.type = 'file';
  if (file) {
    Object.defineProperty(input, 'files', { value: [file], writable: false });
  }
  return { target: input } as unknown as Event;
}

describe('ArticleContentEditor', () => {
  let fixture: ComponentFixture<ArticleContentEditor>;
  let component: ArticleContentEditor;
  let staffArticleApiServiceMock: { uploadArticleMedia: ReturnType<typeof vi.fn> };

  function configure(): void {
    staffArticleApiServiceMock = { uploadArticleMedia: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: StaffArticleApiService, useValue: staffArticleApiServiceMock }],
    });
  }

  async function createComponent(content = '<p>Contenu initial</p>'): Promise<void> {
    fixture = TestBed.createComponent(ArticleContentEditor);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('content', content);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /**
   * L'initialisation réelle de Quill (`afterNextRender` + `import('quill')`
   * dynamique dans PrimeNG) n'est pas garantie terminée par un seul
   * `whenStable()` sous le harnais de test (le rendu statique du gabarit
   * `pTemplate="header"` l'est, lui, immédiatement — sans dépendre de
   * Quill). Sondage borné explicite pour les assertions qui dépendent
   * réellement de l'instance Quill (ex. focus/blur natifs, `quill.root`,
   * insertion d'image réelle).
   */
  async function waitForQuillReady(): Promise<void> {
    for (let attempt = 0; attempt < 50; attempt++) {
      if (fixture.nativeElement.querySelector('.ql-editor')) {
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, 10));
      fixture.detectChanges();
    }
    throw new Error("Quill n'a pas été initialisé dans le délai attendu.");
  }

  function jpegFile(name = 'photo.jpg', size = 1024): File {
    return new File([new Uint8Array(size)], name, { type: 'image/jpeg' });
  }

  beforeEach(() => configure());

  it('should render the rich editor (p-editor / Quill), never a plain textarea', async () => {
    await createComponent();

    expect(fixture.nativeElement.querySelector('.p-editor')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('textarea')).toBeNull();
  });

  it('should never expose more than one editor instance', async () => {
    await createComponent();

    expect(fixture.nativeElement.querySelectorAll('.p-editor').length).toBe(1);
  });

  it('should never use the native Quill image button, only the custom one (DEV-ARTICLES-MEDIA)', async () => {
    await createComponent();

    expect(fixture.nativeElement.querySelector('.ql-image')).toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label="Insert Image"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.article-content-editor-image-btn')).not.toBeNull();
  });

  it('should render only the required formatting controls (bold/italic/underline/list/blockquote/link/clean/header/undo/redo/image)', async () => {
    await createComponent();

    const toolbar: HTMLElement = fixture.nativeElement.querySelector('.p-editor-toolbar');
    expect(toolbar.querySelector('.ql-bold')).not.toBeNull();
    expect(toolbar.querySelector('.ql-italic')).not.toBeNull();
    expect(toolbar.querySelector('.ql-underline')).not.toBeNull();
    expect(toolbar.querySelector('.ql-list[value="ordered"]')).not.toBeNull();
    expect(toolbar.querySelector('.ql-list[value="bullet"]')).not.toBeNull();
    expect(toolbar.querySelector('.ql-blockquote')).not.toBeNull();
    expect(toolbar.querySelector('.ql-link')).not.toBeNull();
    expect(toolbar.querySelector('.ql-clean')).not.toBeNull();
    const headerOptions = Array.from(toolbar.querySelectorAll('.ql-header option')).map((option) =>
      option.getAttribute('value'),
    );
    expect(headerOptions.sort()).toEqual(['', '2', '3', '4']);
    expect(
      toolbar.querySelector('.article-content-editor-history-btn[aria-label="Annuler"]'),
    ).not.toBeNull();
    expect(
      toolbar.querySelector('.article-content-editor-history-btn[aria-label="Rétablir"]'),
    ).not.toBeNull();
    expect(
      toolbar.querySelector('.article-content-editor-image-btn[aria-label="Insérer une image"]'),
    ).not.toBeNull();
    // Ni palette de couleurs/fond, ni police, ni bloc de code — non requis (mission §5).
    expect(toolbar.querySelector('.ql-color')).toBeNull();
    expect(toolbar.querySelector('.ql-background')).toBeNull();
    expect(toolbar.querySelector('.ql-font')).toBeNull();
    expect(toolbar.querySelector('.ql-code-block')).toBeNull();
  });

  it('should reflect the content input into the editor (initial value)', async () => {
    await createComponent('<h2>Titre</h2><p>Texte</p>');

    expect(component.internalControl.value).toBe('<h2>Titre</h2><p>Texte</p>');
  });

  it('should emit contentChange when the internal control value changes (user edit)', async () => {
    await createComponent('<p>Initial</p>');
    const emitted: string[] = [];
    component.contentChange.subscribe((value) => emitted.push(value));

    component.internalControl.setValue('<p>Modifié</p>');

    expect(emitted).toEqual(['<p>Modifié</p>']);
  });

  it('should update the internal control when the content input changes from the parent', async () => {
    await createComponent('<p>Initial</p>');

    fixture.componentRef.setInput('content', '<p>Rechargé depuis le parent</p>');
    fixture.detectChanges();

    expect(component.internalControl.value).toBe('<p>Rechargé depuis le parent</p>');
  });

  it('should emit editorBlur when the editor loses focus', async () => {
    await createComponent();
    await waitForQuillReady();
    let blurred = false;
    component.editorBlur.subscribe(() => (blurred = true));

    fixture.nativeElement.querySelector('.ql-editor')?.dispatchEvent(new FocusEvent('blur'));

    expect(blurred).toBe(true);
  });

  // ---------------------------------------------------------------
  // Image (DEV-ARTICLES-MEDIA)
  // ---------------------------------------------------------------

  it('should reject an oversized file without calling the upload API', async () => {
    await createComponent();
    const tooLarge = jpegFile('big.jpg', 5 * 1024 * 1024 + 1);

    component.onImageFileSelected(fileChangeEvent(tooLarge));

    expect(staffArticleApiServiceMock.uploadArticleMedia).not.toHaveBeenCalled();
    expect(component.uploadError()).toContain('5 Mio');
    expect(component.altDialogVisible()).toBe(false);
  });

  it('should accept a file exactly at 5 MiB without frontend rejection', async () => {
    await createComponent();
    const exactSize = jpegFile('exact.jpg', 5 * 1024 * 1024);

    component.onImageFileSelected(fileChangeEvent(exactSize));

    expect(component.uploadError()).toBeNull();
    expect(component.altDialogVisible()).toBe(true);
  });

  it('should reject a non-image file type without calling the upload API', async () => {
    await createComponent();
    const pdf = new File([new Uint8Array(10)], 'doc.pdf', { type: 'application/pdf' });

    component.onImageFileSelected(fileChangeEvent(pdf));

    expect(staffArticleApiServiceMock.uploadArticleMedia).not.toHaveBeenCalled();
    expect(component.uploadError()).toContain('non autorisé');
    expect(component.altDialogVisible()).toBe(false);
  });

  it('should open the alt-text dialog after selecting a valid file, without uploading yet', async () => {
    await createComponent();

    component.onImageFileSelected(fileChangeEvent(jpegFile()));

    expect(component.altDialogVisible()).toBe(true);
    expect(staffArticleApiServiceMock.uploadArticleMedia).not.toHaveBeenCalled();
  });

  it('should require alt text unless the image is explicitly marked decorative', async () => {
    await createComponent();
    component.onImageFileSelected(fileChangeEvent(jpegFile()));

    component.confirmImageInsertion();

    expect(component.altDialogError()).toContain('alternatif');
    expect(staffArticleApiServiceMock.uploadArticleMedia).not.toHaveBeenCalled();
  });

  it('should allow confirming with no alt text when marked decorative', async () => {
    staffArticleApiServiceMock.uploadArticleMedia.mockReturnValue(
      of({ url: '/media/articles/dec.jpg' }),
    );
    await createComponent();
    await waitForQuillReady();
    component.onImageFileSelected(fileChangeEvent(jpegFile()));
    component.decorativeImage.set(true);

    component.confirmImageInsertion();

    expect(staffArticleApiServiceMock.uploadArticleMedia).toHaveBeenCalledTimes(1);
  });

  it('should upload the selected file and insert the returned URL into the editor content with the given alt', async () => {
    staffArticleApiServiceMock.uploadArticleMedia.mockReturnValue(
      of({ url: '/media/articles/550e8400.webp' }),
    );
    await createComponent('<p>Texte</p>');
    await waitForQuillReady();

    component.onImageFileSelected(fileChangeEvent(jpegFile()));
    component.altText.set('Illustration décrivant le contenu');
    component.confirmImageInsertion();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(staffArticleApiServiceMock.uploadArticleMedia).toHaveBeenCalledTimes(1);
    expect(component.internalControl.value).toContain('src="/media/articles/550e8400.webp"');
    expect(component.internalControl.value).toContain('alt="Illustration décrivant le contenu"');
    expect(component.internalControl.value).not.toContain('base64');
    expect(component.altDialogVisible()).toBe(false);
  });

  it('should propagate the image insertion to contentChange (parent form control)', async () => {
    staffArticleApiServiceMock.uploadArticleMedia.mockReturnValue(
      of({ url: '/media/articles/abc.png' }),
    );
    await createComponent('<p>Texte</p>');
    await waitForQuillReady();
    const emitted: string[] = [];
    component.contentChange.subscribe((value) => emitted.push(value));

    component.onImageFileSelected(fileChangeEvent(jpegFile()));
    component.decorativeImage.set(true);
    component.confirmImageInsertion();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(emitted.some((value) => value.includes('/media/articles/abc.png'))).toBe(true);
  });

  it('should show a clear error message and never insert anything when the upload fails', async () => {
    staffArticleApiServiceMock.uploadArticleMedia.mockReturnValue(
      throwError(() => new Error('network error')),
    );
    await createComponent('<p>Texte</p>');
    await waitForQuillReady();

    component.onImageFileSelected(fileChangeEvent(jpegFile()));
    component.decorativeImage.set(true);
    component.confirmImageInsertion();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.uploadError()).toBeTruthy();
    expect(component.altDialogVisible()).toBe(false);
    expect(component.internalControl.value).toBe('<p>Texte</p>');
  });

  it('should do nothing when cancelling the alt-text dialog', async () => {
    await createComponent('<p>Texte</p>');
    component.onImageFileSelected(fileChangeEvent(jpegFile()));

    component.cancelImageInsertion();

    expect(component.altDialogVisible()).toBe(false);
    expect(staffArticleApiServiceMock.uploadArticleMedia).not.toHaveBeenCalled();
    expect(component.internalControl.value).toBe('<p>Texte</p>');
  });

  it('should disable the image button while an upload is in progress', async () => {
    await createComponent();
    component.uploading.set(true);
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '.article-content-editor-image-btn',
    );
    expect(button.disabled).toBe(true);
    expect(
      fixture.nativeElement.querySelector('.article-content-editor-image-btn .pi-spinner'),
    ).not.toBeNull();
  });

  it('should never generate a base64 data URI at any point of the image workflow', async () => {
    const file = jpegFile();
    staffArticleApiServiceMock.uploadArticleMedia.mockReturnValue(
      of({ url: '/media/articles/abc.png' }),
    );
    await createComponent('<p>Texte</p>');
    await waitForQuillReady();

    component.onImageFileSelected(fileChangeEvent(file));
    component.decorativeImage.set(true);
    component.confirmImageInsertion();
    await fixture.whenStable();
    fixture.detectChanges();

    // Le fichier brut (jamais une chaîne base64) est passé tel quel à l'API.
    expect(staffArticleApiServiceMock.uploadArticleMedia).toHaveBeenCalledWith(file);
    expect(component.internalControl.value).not.toContain('data:image');
  });
});
