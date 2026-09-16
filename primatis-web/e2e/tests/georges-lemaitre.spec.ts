import { expect, test } from '@playwright/test';

/**
 * DESIGN-V2-B5 — Page publique Georges Lemaître.
 *
 * Page strictement statique (aucun appel réseau, aucune règle métier,
 * aucun état d'authentification) : un seul test suffit pour prouver que la
 * route réelle sert bien le contenu réel dans le navigateur, sans mock.
 * Ne duplique pas les nombreuses garanties de contenu déjà couvertes en
 * unitaire (`georges-lemaitre.spec.ts`) — se limite à ce qu'un test
 * unitaire ne peut pas prouver : la route est réellement servie et
 * accessible publiquement, sans JWT.
 */
test('la page publique Georges Lemaître est accessible sans authentification et affiche son contenu réel', async ({
  page,
}) => {
  await page.goto('/georges-lemaitre');

  await expect(page).toHaveTitle('Georges Lemaître — PRIMATIS');
  await expect(page.getByRole('heading', { level: 1, name: 'Georges Lemaître' })).toBeVisible();
  await expect(page.locator('.lemaitre-hero-portrait')).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: 'Repères biographiques' })).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'UCLouvain — Archives Georges Lemaître' }),
  ).toHaveAttribute('href', 'https://archives.uclouvain.be/exhibits/show/georges-lemaitre');

  // CTA réel vers le Catalogue public (pas un lien fictif).
  await page.getByRole('link', { name: 'Découvrir le catalogue' }).click();
  await expect(page).toHaveURL('/catalogue');
});
