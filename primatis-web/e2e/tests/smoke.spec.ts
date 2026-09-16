import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';

/**
 * Smoke test DEV-14.2 — preuve d'intégration réelle, pas un test
 * frontend mocké :
 *
 *   Browser (Chromium réel)
 *   → Angular (ng serve réel, proxy /api → :8080)
 *   → Spring Boot (profil "e2e" réel)
 *   → PostgreSQL primatis_e2e (DEV-DEC-0066)
 *
 * Le compte MEMBER utilisé provient exclusivement de la baseline
 * déterministe provisionnée par E2eBaselineProvisioner (DEV-DEC-0067),
 * jamais d'un compte primatis_dev. Un login réussi ne peut se produire
 * que si : le formulaire Angular soumet réellement une requête HTTP, le
 * backend Spring Security valide réellement le mot de passe (BCrypt) et
 * l'AppUser réellement lu dans primatis_e2e, et un JWT réel est émis et
 * stocké en sessionStorage — aucune étape n'est simulée/mockée ici.
 *
 * Volontairement le seul scénario de DEV-14.2 : DEV-14.3+ couvrira les
 * parcours métier (voir DEV-14-integration-e2e.md §16, "NE PAS FAIRE").
 */
test('un compte MEMBER de la baseline E2E peut se connecter de bout en bout', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();

  await page.locator('#login-email').fill(e2eAccounts.member.email);
  await page.locator('#login-password').fill(e2eAccounts.member.password);
  await page.getByRole('button', { name: 'Se connecter' }).click();

  // Navigation réelle hors de /login : preuve que le backend a répondu
  // avec succès (un échec laisserait l'utilisateur sur /login avec un
  // p-message d'erreur, jamais de redirection).
  await expect(page).not.toHaveURL(/\/login/);

  // État authentifié reflété par l'UI (navigation réelle, pas un état
  // local simulé) : le bouton de déconnexion n'apparaît que si
  // AuthService.authenticated() lit un JWT réel et non expiré.
  await expect(page.locator('.nav-logout')).toBeVisible();

  // Preuve directe qu'un JWT a été émis par le backend et persisté par le
  // frontend, exactement comme le décrit AuthService (sessionStorage,
  // jamais localStorage).
  const storedToken = await page.evaluate(() => sessionStorage.getItem('primatis.accessToken'));
  expect(storedToken).toBeTruthy();
});
