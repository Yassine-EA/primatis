import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';
import { advanceE2eTime, getE2eTime, resetE2eTime } from '../fixtures/e2e-time-helpers';

/**
 * DEV-14.7 (reprise) — Preuve technique du Clock E2E (DEV-DEC-0070),
 * indépendante de tout scénario métier (§9 du handoff : "ne modifie pas
 * un domaine métier uniquement pour observer le Clock"). Écrit et validé
 * AVANT le scénario Fine, conformément à la démarche imposée.
 *
 * `test.afterEach` garantit le reset même si une assertion échoue en
 * cours de test — aucun test suivant ne doit hériter d'un temps avancé
 * (§8/§19 du handoff).
 */
test.afterEach(async ({ request }) => {
  await resetE2eTime(request);
});

test('le Clock E2E peut être avancé puis réinitialisé au temps réel', async ({ request }) => {
  const before = await getE2eTime(request);
  expect(before.offsetSeconds).toBe(0);
  expect(Math.abs(new Date(before.currentTime).getTime() - Date.now())).toBeLessThan(60_000);

  const advanced = await advanceE2eTime(request, 48);
  expect(advanced.offsetSeconds).toBe(48 * 3600);
  const deltaMs = new Date(advanced.currentTime).getTime() - new Date(before.currentTime).getTime();
  expect(deltaMs).toBeGreaterThanOrEqual(48 * 3600 * 1000 - 5_000);
  expect(deltaMs).toBeLessThanOrEqual(48 * 3600 * 1000 + 60_000);

  const afterReset = await resetE2eTime(request);
  expect(afterReset.offsetSeconds).toBe(0);
  expect(Math.abs(new Date(afterReset.currentTime).getTime() - Date.now())).toBeLessThan(60_000);
});

test('la surface e2e/time est protégée (ADMIN autorisé, MEMBER refusé, sans token refusé)', async ({ request }) => {
  const noToken = await request.get('http://localhost:8080/api/v1/e2e/time');
  expect(noToken.status()).toBe(401);

  const memberLogin = await request.post('http://localhost:8080/api/v1/auth/login', {
    data: { email: e2eAccounts.member.email, password: e2eAccounts.member.password },
  });
  const { token: memberToken } = await memberLogin.json();
  const memberAttempt = await request.get('http://localhost:8080/api/v1/e2e/time', {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  expect(memberAttempt.status()).toBe(403);

  const adminState = await getE2eTime(request);
  expect(adminState.offsetSeconds).toBe(0);
});
