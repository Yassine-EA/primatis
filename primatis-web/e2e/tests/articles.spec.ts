import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';
import { loginAs } from '../fixtures/auth-helpers';

const API_BASE = 'http://localhost:8080/api/v1';
const ARTICLE_TITLE = 'PRIMATIS E2E Published Article';
// Contenu produit réellement via la toolbar de l'éditeur riche (DEV-ARTICLES-EDITOR,
// DEV-DEC-0078) : un <script> ne peut plus être tapé comme balise interprétée dans un
// éditeur WYSIWYG (il ne s'agit plus d'un textarea acceptant du HTML brut) — la preuve
// de la sanitization backend est donc apportée séparément (§4bis) via un appel API direct,
// qui reste le vecteur réaliste d'un contenu malveillant (le backend reste l'autorité de
// sécurité quel que soit le client, jamais seulement l'UI, .claude/rules/frontend.md).
const ARTICLE_CONTENT_HTML =
  '<h2>Section E2E</h2><p>Texte <strong>important</strong> pour DEV-14.9B.</p>';
// DEV-ARTICLES-MEDIA (DEV-DEC-0079) : la preuve directe du sanitizer (§3bis) est étendue
// à <img> — un src interne légitime doit survivre, javascript:/data: jamais.
const maliciousContentHtml = (validImageSrc: string): string =>
  `${ARTICLE_CONTENT_HTML}<script>alert('xss')</script>` +
  `<img src="${validImageSrc}" alt="${IMAGE_ALT_TEXT}">` +
  '<img src="javascript:alert(1)">' +
  '<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk' +
  'YPhfz0AEYBxVSF+FAAAAAElFTkSuQmCC">';
const IMAGE_FIXTURE_PATH = 'e2e/fixtures/media/article-image.png';
const IMAGE_ALT_TEXT = 'Illustration E2E';

/**
 * DEV-14.9B — Articles E2E.
 *
 * Complète le périmètre original de DEV-14.9 ("Articles / Application
 * Settings", ROADMAP_DEV-14_TO_DEV-22.md) resté non couvert — REQUIRED
 * par development-workflow.md §16/§21 et DEV-DEC-0068 (DEV-14.10A §5).
 *
 * Un seul test transactionnel (§19 du handoff) : LIBRARIAN (ARTICLE_MANAGE
 * + ARTICLE_PUBLISH, V002 — pas besoin d'ADMIN) crée un Article DRAFT via
 * l'UI réelle, le publie via l'action UI réelle, puis un visiteur non
 * authentifié (nouveau contexte navigateur) le consulte sur la surface
 * publique. Aucun Article n'est provisionné directement (DEV-DEC-0068) :
 * DRAFT et PUBLISHED sont exclusivement produits par le workflow
 * applicatif réel.
 *
 * Isolation : titre distinctif et stable (aucun Article n'existe dans la
 * baseline E2E, DEV-DEC-0068 — vérifié, E2eBaselineProvisioner ne
 * provisionne aucun Article), jamais localisé par position/ordre.
 *
 * Aucun Clock utilisé (offset reste 0 — §17 du handoff, publishedAt
 * reflète le temps réel E2E).
 *
 * Mise à jour (DEV-ARTICLES-EDITOR, DEV-DEC-0078) : la saisie du contenu se
 * fait désormais via la toolbar réelle de l'éditeur riche (Quill/PrimeNG
 * Editor), plus via `.fill()` sur un `<textarea>` — un éditeur WYSIWYG
 * n'accepte plus de balise `<script>` tapée comme markup interprété. La
 * preuve de la sanitization backend (§8, déjà existante) est donc désormais
 * amenée par un appel API direct (§3bis), vecteur réaliste d'un contenu
 * malveillant, cohérent avec le principe « le backend reste l'autorité de
 * sécurité, jamais seulement l'UI » (.claude/rules/frontend.md).
 *
 * Mise à jour (DEV-ARTICLES-MEDIA, DEV-DEC-0079) : §1bis insère réellement
 * une image via l'upload backend (fixture locale
 * `e2e/fixtures/media/article-image.png`, jamais une ressource externe) —
 * `setInputFiles` cible directement l'`<input type="file">` caché, jamais un
 * clic sur le bouton Image (ouvrirait un vrai sélecteur OS non pilotable).
 * §2bis vérifie que l'image réellement uploadée est persistée dans `content`
 * avant toute publication. §3bis/§8 étendent la preuve de sanitization à
 * `<img>` (src interne conservé, `javascript:`/`data:` jamais).
 */
test('LIBRARIAN publie un Article via workflow réel, visible publiquement sans JWT', async ({
  page,
  browser,
  request,
}) => {
  // --- 1. LIBRARIAN login (UI réelle) → création DRAFT ---
  // Contenu saisi via la toolbar réelle de l'éditeur riche (DEV-ARTICLES-EDITOR) :
  // sélection du format Titre 2, texte, retour à la normale, puis un mot en gras au
  // milieu d'une phrase — jamais un textarea rempli avec du HTML brut.
  //
  // Le thème "snow" de Quill masque le <select class="ql-header"> natif et le
  // remplace par un picker personnalisé (`.ql-picker` — un <span> cliquable qui
  // déplie une liste d'options `.ql-picker-item[data-value]`) : `selectOption()`
  // ne s'applique donc pas ici (élément réellement affiché ≠ <select>).
  const selectHeaderLevel = async (level: '2' | '3' | '4' | null): Promise<void> => {
    const picker = page.locator('#article-content .ql-header.ql-picker');
    await picker.locator('.ql-picker-label').click();
    const option = level
      ? picker.locator(`.ql-picker-item[data-value="${level}"]`)
      : picker.locator('.ql-picker-item:not([data-value])');
    await option.click();
  };

  await loginAs(page, e2eAccounts.librarian);
  await page.goto('/staff/articles/new');
  await page.locator('#article-title').fill(ARTICLE_TITLE);

  const contentEditor = page.locator('#article-content .ql-editor');
  await contentEditor.click();
  await selectHeaderLevel('2');
  await page.keyboard.type('Section E2E');
  await page.keyboard.press('Enter');
  await selectHeaderLevel(null);
  await page.keyboard.type('Texte ');
  await page.locator('#article-content .ql-bold').click();
  await page.keyboard.type('important');
  await page.locator('#article-content .ql-bold').click();
  await page.keyboard.type(' pour DEV-14.9B.');
  await expect(contentEditor).toHaveText('Section E2ETexte important pour DEV-14.9B.');

  // --- 1bis. Image insérée via l'upload réel backend (DEV-ARTICLES-MEDIA) ---
  // `setInputFiles` cible directement l'<input type="file"> caché : jamais un clic sur
  // le bouton Image, qui ouvrirait un vrai sélecteur de fichier OS que Playwright ne
  // peut piloter (le bouton n'existe que pour l'UX humaine, pas pour ce test).
  await page.locator('#article-content input[type="file"]').setInputFiles(IMAGE_FIXTURE_PATH);
  // `getByLabel`/`getByText` feraient un match par sous-chaîne : « Texte alternatif »
  // apparaît aussi dans le titre du dialogue et dans le label de la case « Image
  // décorative (sans texte alternatif) » — `role: 'textbox'` cible sans ambiguïté
  // le seul champ de saisie.
  await page.getByRole('textbox', { name: 'Texte alternatif', exact: true }).fill(IMAGE_ALT_TEXT);
  await page.getByRole('button', { name: 'Insérer', exact: true }).click();
  const insertedImage = page.locator('#article-content .ql-editor img');
  await expect(insertedImage).toHaveAttribute('src', /^\/media\/articles\/.+\.png$/);
  await expect(insertedImage).toHaveAttribute('alt', IMAGE_ALT_TEXT);
  const uploadedImageSrc = await insertedImage.getAttribute('src');
  expect(uploadedImageSrc).toMatch(/^\/media\/articles\/.+\.png$/);

  await page.getByRole('button', { name: "Créer l'article" }).click();
  await expect(page.getByText('Article créé')).toBeVisible();
  await page.waitForURL(/\/staff\/articles\/\d+$/);

  const articleId = Number(page.url().match(/\/staff\/articles\/(\d+)$/)?.[1]);
  expect(Number.isInteger(articleId)).toBe(true);

  // --- 2. DRAFT confirmé, jamais publié encore ---
  await expect(page.getByText('Brouillon', { exact: true })).toBeVisible();
  const publishButton = page.getByRole('button', { name: 'Publier' });
  await expect(publishButton).toBeVisible();

  const librarianLogin = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.librarian.email, password: e2eAccounts.librarian.password },
  });
  const { token: librarianToken } = await librarianLogin.json();
  const staffArticleBefore = await request.get(`${API_BASE}/staff/articles/${articleId}`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  const articleBefore = await staffArticleBefore.json();
  expect(articleBefore.articleStatus).toBe('DRAFT');
  expect(articleBefore.publishedAt).toBeNull();
  const slug: string = articleBefore.slug;
  expect(slug).toBeTruthy();

  // --- 2bis. Image réellement uploadée et persistée dans content (DEV-ARTICLES-MEDIA) ---
  // Preuve du chemin complet réel : navigateur → POST /staff/articles/media (upload) →
  // URL renvoyée → insertion Quill → soumission du formulaire → persistance backend.
  expect(articleBefore.content).toMatch(
    /<img src="\/media\/articles\/[^"]+\.png" alt="Illustration E2E">/,
  );

  // --- 3. DRAFT non public (§14 du handoff) : 404 sans authentification ---
  const publicBeforePublish = await request.get(`${API_BASE}/articles/${slug}`);
  expect(publicBeforePublish.status()).toBe(404);

  // --- 3bis. Sanitizer backend (DEV-ARTICLES-EDITOR) : preuve via appel API direct.
  // Un éditeur WYSIWYG réel n'accepte plus de <script> tapé comme balise interprétée
  // (contrairement à l'ancien textarea, DEV-11.12) — le backend reste néanmoins la seule
  // autorité de sécurité, quel que soit le client. Un appel PATCH authentifié direct
  // (contournant délibérément l'UI, vecteur réaliste d'un contenu malveillant) prouve que
  // l'allowlist est appliquée indépendamment du frontend.
  const sanitizedPatch = await request.patch(`${API_BASE}/staff/articles/${articleId}`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
    data: { content: maliciousContentHtml(uploadedImageSrc!) },
  });
  expect(sanitizedPatch.status()).toBe(200);
  const sanitizedArticle = await sanitizedPatch.json();
  expect(sanitizedArticle.content).toContain('<h2>Section E2E</h2>');
  expect(sanitizedArticle.content).toContain('<strong>important</strong>');
  expect(sanitizedArticle.content).not.toContain('<script');
  // <img> (DEV-ARTICLES-MEDIA) : src interne légitime conservé avec son alt,
  // javascript:/data: jamais — preuve indépendante de l'UI (mission §15/§28).
  expect(sanitizedArticle.content).toContain(`<img src="${uploadedImageSrc}" alt="${IMAGE_ALT_TEXT}">`);
  expect(sanitizedArticle.content).not.toContain('javascript:');
  expect(sanitizedArticle.content).not.toContain('data:');
  expect(sanitizedArticle.content).not.toContain('base64');
  await page.reload();

  // --- 4. Publication réelle via l'UI (confirmation incluse) ---
  await publishButton.click();
  // Libellés PrimeNG par défaut ("Yes"/"No") francisés entre-temps par un
  // chantier de refonte visuelle distinct (acceptLabel/rejectLabel "Oui"/"Non",
  // confirmPublish) — hors périmètre DEV-ARTICLES-MEDIA, adaptation minimale
  // requise pour garder ce test PASS (mission §30).
  await page.getByRole('button', { name: 'Oui', exact: true }).click();
  await expect(page.getByText('Article publié')).toBeVisible();
  await expect(page.getByText('Publié', { exact: true })).toBeVisible();

  // --- 5. publishedAt réel, persistance API ---
  const staffArticleAfter = await request.get(`${API_BASE}/staff/articles/${articleId}`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  const articleAfter = await staffArticleAfter.json();
  expect(articleAfter.articleStatus).toBe('PUBLISHED');
  expect(articleAfter.publishedAt).toBeTruthy();

  // --- 6. Notification ARTICLE_PUBLISHED (§12 du handoff : une preuve ciblée suffit) ---
  const memberLogin = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.member.email, password: e2eAccounts.member.password },
  });
  const { token: memberToken } = await memberLogin.json();
  const memberNotifications = await request.get(`${API_BASE}/me/notifications?size=100`, {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const articlePublishedNotification = (await memberNotifications.json()).content.find(
    (n: { notificationType: string; originId: number }) =>
      n.notificationType === 'ARTICLE_PUBLISHED' && n.originId === articleId,
  );
  expect(articlePublishedNotification).toBeTruthy();

  // --- 7. Surface publique, sans JWT (nouveau contexte navigateur, §13) ---
  const visitorContext = await browser.newContext();
  const visitorPage = await visitorContext.newPage();

  await visitorPage.goto('/articles');
  // DESIGN-V2-B4 : la liste n'est plus un `<table>` mais une composition
  // vedette + grille de cartes (`a.articles-featured-card`/`a.articles-card`).
  // Seul un Article existe dans cette baseline E2E (aucun autre provisionné,
  // DEV-DEC-0068) : il est donc systématiquement la vedette (premier de la
  // page), jamais dans la grille secondaire.
  const publicCard = visitorPage.locator('.articles-featured-card', { hasText: ARTICLE_TITLE });
  await expect(publicCard).toBeVisible();
  await expect(visitorPage.getByText('Aucun article')).toHaveCount(0);
  // DEV-ARTICLES-MEDIA-THUMBNAIL : la vignette publique reprend la
  // première image réellement persistée dans Article.content.
  const publicCardImage = publicCard.locator('.articles-featured-cover img');
  await expect(publicCardImage).toHaveAttribute('src', uploadedImageSrc!);
  await expect(publicCardImage).toHaveAttribute('alt', `Illustration de l’article « ${ARTICLE_TITLE} »`);

  await publicCard.click();
  await visitorPage.waitForURL(new RegExp(`/articles/${slug}$`));
  await expect(visitorPage.getByRole('heading', { name: ARTICLE_TITLE })).toBeVisible();
  await expect(visitorPage.locator('.article-detail-content')).toContainText('important');
  await expect(visitorPage).toHaveTitle(`${ARTICLE_TITLE} — PRIMATIS`);

  // Fil d'Ariane : retour réel vers la liste des Articles (DESIGN-V2-B4 §19).
  await visitorPage
    .locator('.article-detail-breadcrumb')
    .getByRole('link', { name: 'Articles', exact: true })
    .click();
  await expect(visitorPage).toHaveURL('/articles');

  await visitorContext.close();

  // --- 8. Sanitizer backend (§15) : allowlist conservée, script retiré ---
  const publicAfterPublish = await request.get(`${API_BASE}/articles/${slug}`);
  expect(publicAfterPublish.status()).toBe(200);
  const publicArticle = await publicAfterPublish.json();
  expect(publicArticle.content).toContain('<h2>Section E2E</h2>');
  expect(publicArticle.content).toContain('<strong>important</strong>');
  expect(publicArticle.content).not.toContain('<script');
  expect(publicArticle.content).toContain(`<img src="${uploadedImageSrc}" alt="${IMAGE_ALT_TEXT}">`);
  expect(publicArticle.content).not.toContain('javascript:');
  expect(publicArticle.content).not.toContain('data:');
});
