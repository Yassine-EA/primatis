import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';
import { getStoredToken, loginAs, logoutViaUi } from '../fixtures/auth-helpers';

/**
 * DEV-14.3 — Authentification / session / RBAC E2E.
 *
 * Chaîne réelle à chaque test : Chromium → Angular (ng serve réel) →
 * Spring Boot (profil "e2e" réel) → PostgreSQL primatis_e2e. Aucun mock
 * HTTP. Les comptes utilisés proviennent exclusivement de la baseline
 * E2eBaselineProvisioner (DEV-DEC-0067) — aucun compte manuel ajouté.
 *
 * Ne duplique pas le smoke test DEV-14.2 (login MEMBER minimal, conservé
 * séparément dans smoke.spec.ts) : ici, chaque login vérifie en plus un
 * accès réel à une zone protégée effectivement autorisée pour le rôle.
 */

test.describe('Login et accès RBAC réellement autorisé', () => {
  test('LIBRARIAN se connecte et accède réellement à /staff/loans (LOAN_READ)', async ({ page, request }) => {
    await loginAs(page, e2eAccounts.librarian);

    await page.goto('/staff/loans');
    await expect(page).toHaveURL(/\/staff\/loans$/);
    await expect(page.getByRole('heading', { name: 'Prêts' })).toBeVisible();

    // Preuve backend directe (pas seulement l'affichage de la page) :
    // le même JWT autorise réellement GET /api/v1/loans (LOAN_READ).
    const token = await getStoredToken(page);
    const apiResponse = await request.get('http://localhost:8080/api/v1/loans', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(apiResponse.status()).toBe(200);
  });

  test('ADMIN se connecte et accède réellement à /admin/settings (SETTING_READ)', async ({ page, request }) => {
    await loginAs(page, e2eAccounts.admin);

    await page.goto('/admin/settings');
    await expect(page).toHaveURL(/\/admin\/settings$/);
    await expect(page.getByRole('heading', { name: 'Paramètres applicatifs' })).toBeVisible();

    const token = await getStoredToken(page);
    const apiResponse = await request.get('http://localhost:8080/api/v1/settings', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(apiResponse.status()).toBe(200);
  });
});

test.describe('Guards de routing (UX uniquement — jamais l\'autorité de sécurité)', () => {
  test('visiteur non authentifié redirigé vers /login avec returnUrl', async ({ page }) => {
    await page.goto('/member/loans');

    await expect(page).toHaveURL(/\/login\?/);
    const returnUrl = new URL(page.url()).searchParams.get('returnUrl');
    expect(returnUrl).toBe('/member/loans');
  });

  test('MEMBER authentifié redirigé vers /forbidden sur une route staff (LOAN_READ absente)', async ({ page }) => {
    await loginAs(page, e2eAccounts.member);

    await page.goto('/staff/loans');

    await expect(page).toHaveURL(/\/forbidden$/);
    await expect(page.getByRole('heading', { name: 'Accès interdit' })).toBeVisible();
  });

  test('LIBRARIAN authentifié redirigé vers /forbidden sur une route admin (USER_MANAGE absente)', async ({
    page,
  }) => {
    await loginAs(page, e2eAccounts.librarian);

    await page.goto('/admin/users');

    await expect(page).toHaveURL(/\/forbidden$/);
    await expect(page.getByRole('heading', { name: 'Accès interdit' })).toBeVisible();
  });
});

test.describe('Contrat HTTP backend 401/403 (ApiErrorResponse)', () => {
  test('401 AUTHENTICATION_REQUIRED sans token sur un endpoint protégé', async ({ request }) => {
    const response = await request.get('http://localhost:8080/api/v1/loans');

    expect(response.status()).toBe(401);
    const body = await response.json();
    expect(body.status).toBe(401);
    expect(body.code).toBe('AUTHENTICATION_REQUIRED');
    expect(body.path).toBe('/api/v1/loans');
    expect(Array.isArray(body.fieldErrors)).toBe(true);
  });

  test('403 ACCESS_DENIED pour un MEMBER authentifié sur un endpoint LOAN_READ', async ({ request }) => {
    const loginResponse = await request.post('http://localhost:8080/api/v1/auth/login', {
      data: { email: e2eAccounts.member.email, password: e2eAccounts.member.password },
    });
    expect(loginResponse.status()).toBe(200);
    const { token } = await loginResponse.json();

    const response = await request.get('http://localhost:8080/api/v1/loans', {
      headers: { Authorization: `Bearer ${token}` },
    });

    expect(response.status()).toBe(403);
    const body = await response.json();
    expect(body.status).toBe(403);
    expect(body.code).toBe('ACCESS_DENIED');
    expect(body.path).toBe('/api/v1/loans');
    expect(Array.isArray(body.fieldErrors)).toBe(true);
  });
});

test.describe('Logout et session', () => {
  test('logout supprime le JWT et referme réellement l\'accès à une route protégée', async ({ page }) => {
    await loginAs(page, e2eAccounts.member);
    expect(await getStoredToken(page)).toBeTruthy();
    await expect(page.locator('.nav-logout')).toBeVisible();

    await logoutViaUi(page);

    // État local réinitialisé.
    expect(await getStoredToken(page)).toBeNull();
    await expect(page.locator('.nav-logout')).toBeHidden();
    await expect(page.getByRole('link', { name: 'Connexion' })).toBeVisible();

    // Preuve comportementale, pas seulement un état local : une route
    // protégée redemande désormais une authentification réelle.
    await page.goto('/member/loans');
    await expect(page).toHaveURL(/\/login\?/);
  });
});
