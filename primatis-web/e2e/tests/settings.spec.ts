import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';
import { loginAs } from '../fixtures/auth-helpers';

/**
 * DEV-14.9 — Application Settings E2E.
 *
 * Un seul setting représentatif : `LOAN_DUE_SOON_DAYS` (INTEGER, valeur
 * initiale "3", V001). Choisi (§6 du handoff) car sa valeur n'est utilisée
 * par aucun autre scénario transactionnel de la suite — contrairement à
 * `LOAN_DURATION_DAYS` (dueDate, loans.spec.ts/fines.spec.ts) et
 * `RESERVATION_READY_HOLD_HOURS` (expirationDate, reservations.spec.ts),
 * dont la modification temporaire casserait leurs assertions si les
 * fichiers s'exécutent dans la même base pendant le même run. Vérifié par
 * lecture du code réel : aucun test de la suite ne produit de Loan proche
 * de l'échéance (aucune Notification LOAN_DUE_SOON déclenchée nulle part),
 * `LOAN_DUE_SOON_DAYS` peut donc être temporairement modifié sans effet de
 * bord sur les autres fichiers.
 *
 * Cleanup à deux niveaux (§11 du handoff) : restauration principale via
 * l'UI réelle en fin de test (preuve du workflow complet, vérifiée), plus
 * un filet de sécurité `afterEach` via API ADMIN (idempotent — PATCH vers
 * la valeur déjà restaurée ne change rien) garantissant qu'aucune valeur
 * modifiée ne fuite vers un autre fichier même en cas d'échec intermédiaire.
 */
const SETTING_KEY = 'LOAN_DUE_SOON_DAYS';
const API_BASE = 'http://localhost:8080/api/v1';

let originalValue: string | undefined;

test.afterEach(async ({ request }) => {
  if (originalValue === undefined) {
    return;
  }
  const adminLogin = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.admin.email, password: e2eAccounts.admin.password },
  });
  const { token } = await adminLogin.json();
  await request.patch(`${API_BASE}/settings/${SETTING_KEY}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { settingValue: originalValue },
  });
  originalValue = undefined;
});

test('ADMIN consulte puis modifie réellement un Application Setting, restauré avant la fin du test', async ({
  page,
  request,
}) => {
  // --- 1. Valeur initiale (API, jamais supposée) ---
  const adminLoginForBaseline = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.admin.email, password: e2eAccounts.admin.password },
  });
  const { token: adminToken } = await adminLoginForBaseline.json();
  const settingsBefore = await request.get(`${API_BASE}/settings`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  const settingBefore = (await settingsBefore.json()).find(
    (s: { settingKey: string }) => s.settingKey === SETTING_KEY,
  );
  expect(settingBefore).toBeTruthy();
  expect(settingBefore.valueType).toBe('INTEGER');
  originalValue = settingBefore.settingValue;
  const testValue = String(Number(originalValue) + 1);

  // --- 2. RBAC : LIBRARIAN ne peut pas modifier (SETTING_MANAGE absente,
  // seul ROLE_ADMIN la détient — V002) ---
  const librarianLogin = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.librarian.email, password: e2eAccounts.librarian.password },
  });
  const { token: librarianToken } = await librarianLogin.json();
  const forbiddenPatch = await request.patch(`${API_BASE}/settings/${SETTING_KEY}`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
    data: { settingValue: testValue },
  });
  expect(forbiddenPatch.status()).toBe(403);
  expect((await forbiddenPatch.json()).code).toBe('ACCESS_DENIED');

  const settingsAfterForbidden = await request.get(`${API_BASE}/settings`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  const settingAfterForbidden = (await settingsAfterForbidden.json()).find(
    (s: { settingKey: string }) => s.settingKey === SETTING_KEY,
  );
  expect(settingAfterForbidden.settingValue).toBe(originalValue);

  // --- 3. ADMIN login (UI réelle) → /admin/settings ---
  await loginAs(page, e2eAccounts.admin);
  await page.goto('/admin/settings');

  const row = page.locator('table tbody tr', { hasText: SETTING_KEY });
  await expect(row).toBeVisible();
  await expect(row).toContainText(originalValue);

  // --- 4. Ouverture du dialog de modification ---
  await row.getByRole('button', { name: `Modifier ${SETTING_KEY}` }).click();
  const valueInput = page.locator('#setting-value');
  await expect(valueInput).toHaveValue(originalValue);

  // --- 5. Validation négative (UI réelle, §13 du handoff) : valeur non
  // strictement positive bloquée côté client, aucune soumission envoyée ---
  await valueInput.fill('0');
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText('La valeur doit être strictement positive.')).toBeVisible();
  await expect(valueInput).toHaveValue('0');

  // --- 6. Modification réelle avec une valeur valide (testValue = originalValue + 1) ---
  await valueInput.fill(testValue);
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText(`${SETTING_KEY} a été mis à jour.`)).toBeVisible();
  await expect(row).toContainText(testValue);

  // --- 7. Persistance API ---
  const settingsAfterUpdate = await request.get(`${API_BASE}/settings`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  const settingAfterUpdate = (await settingsAfterUpdate.json()).find(
    (s: { settingKey: string }) => s.settingKey === SETTING_KEY,
  );
  expect(settingAfterUpdate.settingValue).toBe(testValue);

  // --- 8. Persistance après rechargement complet (pas un état Angular local) ---
  await page.reload();
  const reloadedRow = page.locator('table tbody tr', { hasText: SETTING_KEY });
  await expect(reloadedRow).toContainText(testValue);

  // --- 9. Restauration via l'UI réelle ---
  await reloadedRow.getByRole('button', { name: `Modifier ${SETTING_KEY}` }).click();
  const restoreInput = page.locator('#setting-value');
  await expect(restoreInput).toHaveValue(testValue);
  await restoreInput.fill(originalValue);
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText(`${SETTING_KEY} a été mis à jour.`)).toBeVisible();
  await expect(reloadedRow).toContainText(originalValue);

  // --- 10. Preuve de restauration (API, §12 du handoff) ---
  const settingsAfterRestore = await request.get(`${API_BASE}/settings`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  const settingAfterRestore = (await settingsAfterRestore.json()).find(
    (s: { settingKey: string }) => s.settingKey === SETTING_KEY,
  );
  expect(settingAfterRestore.settingValue).toBe(originalValue);

  // Déjà restauré via l'UI et vérifié ci-dessus : afterEach devient un
  // no-op sûr (garde active uniquement en cas d'échec avant ce point).
  originalValue = undefined;
});
