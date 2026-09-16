import { Page } from '@playwright/test';

import { e2eAccounts } from './e2e-accounts';

type E2eAccount = (typeof e2eAccounts)[keyof typeof e2eAccounts];

const SESSION_STORAGE_KEY = 'primatis.accessToken';

/**
 * Connexion réelle via le formulaire Angular (DEV-14.3) — mêmes sélecteurs
 * que le smoke test DEV-14.2, factorisés ici car réutilisés par plusieurs
 * scénarios Auth/RBAC. Attend une navigation réelle hors de /login (preuve
 * d'un succès backend), jamais une simple temporisation.
 */
export async function loginAs(page: Page, account: E2eAccount): Promise<void> {
  await page.goto('/login');
  await page.locator('#login-email').fill(account.email);
  await page.locator('#login-password').fill(account.password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

/** Lit le JWT réel stocké par AuthService (jamais localStorage — DEV-DEC baseline sécurité). */
export function getStoredToken(page: Page): Promise<string | null> {
  return page.evaluate((key) => sessionStorage.getItem(key), SESSION_STORAGE_KEY);
}

/** Déclenche le logout réel via la navigation (bouton ".nav-logout", jamais un appel direct à AuthService depuis le test). */
export async function logoutViaUi(page: Page): Promise<void> {
  await page.locator('.nav-logout').click();
}
