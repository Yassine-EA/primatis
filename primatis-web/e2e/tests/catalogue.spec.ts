import { expect, test } from '@playwright/test';

import { e2eCatalogue } from '../fixtures/e2e-catalogue';

/**
 * DEV-14.4 — Catalogue E2E (consultation publique uniquement — le plan
 * DEV-14 §3 "Expected outcome" ne liste que "consultation du catalogue
 * public" parmi les parcours critiques ; aucune mutation staff
 * CATALOGUE_MANAGE n'est inventée ici, conforme au handoff §9).
 *
 * Chaîne réelle : Chromium → Angular (ng serve réel) → Spring Boot (profil
 * "e2e" réel) → PostgreSQL primatis_e2e. Baseline catalogue provisionnée
 * par E2eBaselineProvisioner (DEV-DEC-0068) : 3 Titles ACTIVE + 1 Title
 * WITHDRAWN, identifiés uniquement par leurs identifiants fonctionnels
 * (isbn/title) — aucun ID technique hardcodé dans ces tests.
 */

test.describe('Consultation publique du catalogue', () => {
  test('affiche les Titles ACTIVE de la baseline, jamais le Title WITHDRAWN', async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.searchPrefix);

    // DESIGN-V2-B2 : la liste de résultats n'est plus un `<table>` mais une
    // grille de cartes (`<a class="catalogue-card">`, toute la carte est le
    // lien vers le détail, `aria-label="Voir le détail de <titre>"`).
    const cardA = page.locator('.catalogue-card', { hasText: e2eCatalogue.titleA.title });
    const cardB = page.locator('.catalogue-card', { hasText: e2eCatalogue.titleB.title });
    const cardWithoutCopy = page.locator('.catalogue-card', { hasText: e2eCatalogue.titleWithoutCopy.title });
    const cardWithdrawn = page.locator('.catalogue-card', { hasText: e2eCatalogue.titleWithdrawn.title });

    await expect(cardA).toBeVisible();
    await expect(cardB).toBeVisible();
    await expect(cardWithoutCopy).toBeVisible();
    // Le Title WITHDRAWN partage le même préfixe de recherche mais ne doit
    // jamais apparaître : preuve que le filtrage ACTIVE-only est réel côté
    // backend, pas seulement une convention de présentation frontend.
    await expect(cardWithdrawn).toHaveCount(0);
  });

  test('recherche sans résultat affiche un état vide réel (pas un tableau silencieux)', async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill('ZZZ_AUCUN_TITRE_E2E_XYZ');

    await expect(page.getByRole('heading', { name: 'Aucun ouvrage trouvé' })).toBeVisible();
  });

  test("détail d'un Title affiche des données réelles cohérentes avec la baseline", async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.titleA.title);

    const card = page.getByRole('link', { name: `Voir le détail de ${e2eCatalogue.titleA.title}` });
    await expect(card).toBeVisible();
    await card.click();

    await expect(page).toHaveURL(/\/catalogue\/\d+$/);
    await expect(page.getByRole('heading', { name: e2eCatalogue.titleA.title })).toBeVisible();

    const definitionList = page.locator('dl');
    await expect(definitionList.getByText(e2eCatalogue.titleA.author)).toBeVisible();
    await expect(definitionList.getByText(e2eCatalogue.titleA.genre)).toBeVisible();
    // DESIGN-V2-B3 : la langue est affichée en toutes lettres ("Français"),
    // jamais le code brut ("FR") — cf. `TitleDetailPage.languageLabel`.
    // `e2eCatalogue.titleA.language` vaut 'FR' (baseline provisionnée).
    await expect(definitionList.getByText('Français', { exact: true })).toBeVisible();
    await expect(definitionList.getByText(e2eCatalogue.titleA.isbn)).toBeVisible();
  });

  test("détail d'un Title à deux auteurs affiche les deux noms dans l'ordre alphabétique", async ({ page }) => {
    await page.goto('/catalogue');
    await page.locator('#catalogue-search').fill(e2eCatalogue.titleB.title);

    const card = page.getByRole('link', { name: `Voir le détail de ${e2eCatalogue.titleB.title}` });
    await expect(card).toBeVisible();
    await card.click();

    await expect(page.getByRole('heading', { name: e2eCatalogue.titleB.title })).toBeVisible();
    await expect(
      page.locator('dl').getByText(e2eCatalogue.titleB.authors.join(', ')),
    ).toBeVisible();
  });

  test('visiteur anonyme : CTA "Se connecter pour réserver" avec returnUrl réel, puis retour au Catalogue via le fil d\'Ariane (DESIGN-V2-B3)', async ({
    page,
  }) => {
    await page.goto('/catalogue');
    await page.getByRole('link', { name: `Voir le détail de ${e2eCatalogue.titleA.title}` }).click();
    await expect(page).toHaveURL(/\/catalogue\/\d+$/);
    const detailUrl = page.url();

    // Aucune disponibilité inventée, aucun bouton "Réserver" pour un visiteur anonyme.
    await expect(page.getByRole('button', { name: 'Réserver', exact: true })).toHaveCount(0);
    const loginCta = page.getByRole('link', { name: 'Se connecter pour réserver' });
    await expect(loginCta).toBeVisible();
    await loginCta.click();

    await expect(page).toHaveURL(/\/login\?returnUrl=/);
    expect(decodeURIComponent(page.url())).toContain(new URL(detailUrl).pathname);

    // Fil d'Ariane : retour réel vers le Catalogue (DESIGN-V2-B3 §16).
    // Scope explicite : "Catalogue" apparaît aussi dans la navigation du header.
    await page.goto(detailUrl);
    await page.locator('.title-detail-breadcrumb').getByRole('link', { name: 'Catalogue', exact: true }).click();
    await expect(page).toHaveURL('/catalogue');
  });

  test('une navigation directe avec ?q=... initialise le champ, filtre réellement, et le reset fonctionne (DESIGN-V2-B2)', async ({
    page,
  }) => {
    await page.goto(`/catalogue?q=${encodeURIComponent(e2eCatalogue.titleA.title)}`);

    await expect(page.locator('#catalogue-search')).toHaveValue(e2eCatalogue.titleA.title);
    await expect(page.locator('.catalogue-card', { hasText: e2eCatalogue.titleA.title })).toBeVisible();
    await expect(page.locator('.catalogue-card', { hasText: e2eCatalogue.titleB.title })).toHaveCount(0);

    await page.getByRole('button', { name: 'Effacer la recherche' }).click();

    await expect(page.locator('#catalogue-search')).toHaveValue('');
    await expect(page).toHaveURL('/catalogue');
    await expect(page.locator('.catalogue-card', { hasText: e2eCatalogue.titleB.title })).toBeVisible();
  });
});

test.describe('API publique du catalogue (contrat HTTP réel, sans JWT)', () => {
  test('GET /api/v1/titles répond 200 sans authentification et ne contient jamais un WITHDRAWN', async ({
    request,
  }) => {
    const response = await request.get('http://localhost:8080/api/v1/titles', {
      params: { q: e2eCatalogue.searchPrefix },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();

    const isbns: string[] = body.content.map((title: { isbn: string }) => title.isbn);
    expect(isbns).toContain(e2eCatalogue.titleA.isbn);
    expect(isbns).toContain(e2eCatalogue.titleB.isbn);
    expect(isbns).toContain(e2eCatalogue.titleWithoutCopy.isbn);
    expect(isbns).not.toContain(e2eCatalogue.titleWithdrawn.isbn);

    const statuses: string[] = body.content.map((title: { titleStatus: string }) => title.titleStatus);
    expect(statuses.every((status) => status === 'ACTIVE')).toBe(true);
  });
});
