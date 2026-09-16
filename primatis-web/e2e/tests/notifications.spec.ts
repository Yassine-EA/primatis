import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';
import { e2eCatalogue } from '../fixtures/e2e-catalogue';
import { loginAs } from '../fixtures/auth-helpers';
import { createLoanViaUi, returnLoanViaUi } from '../fixtures/loan-helpers';

/**
 * DEV-14.8 — Notifications E2E.
 *
 * Objet du test : la fonctionnalité Notifications elle-même (liste,
 * compteur UNREAD, mark-as-read, persistance, ownership) — pas une
 * nouvelle preuve que tel workflow crée telle Notification (déjà
 * démontré réellement par DEV-14.5/14.6/14.7 : LOAN_RETURNED,
 * RESERVATION_CREATED/READY, FINE_ISSUED/PAID). Un seul workflow simple
 * (Loan/Return, sans dépendance temporelle) produit la Notification
 * cible ; un second workflow simple (Reservation self-service, sans
 * Clock) produit une Notification de type différent pour MEMBER2,
 * utilisée uniquement pour la preuve d'ownership.
 *
 * Aucun Clock utilisé (offset reste 0 pendant toute l'étape, §15 du
 * handoff — aucune Notification choisie ici ne dépend du temps).
 *
 * Isolation : réutilise Title B (E2E-COPY-0002, DEV-14.4) — jamais
 * transactionné par un autre fichier de test (loans.spec.ts/
 * reservations.spec.ts/fines.spec.ts utilisent respectivement Title A et
 * un Title dédié) — et Title "sans Copy" (titleWithoutCopy, DEV-14.4)
 * pour la Reservation MEMBER2, qui n'exige aucune précondition de
 * disponibilité à construire (zéro Copy = immédiatement éligible).
 *
 * Assertions robustes (§18 du handoff) : comparaisons relatives
 * (before/after), jamais un total absolu de Notifications ni un ordre
 * global. La ligne ciblée n'est jamais localisée par `.first()`/tri
 * createdAt DESC : fines.spec.ts avance le Clock E2E pour produire un
 * retour tardif, donc sa propre Notification `LOAN_RETURNED` porte un
 * `createdAt` dans le futur simulé (constaté : ~22 jours après la date
 * réelle) — elle trierait avant la mienne si l'ordre de création réel
 * était supposé. La Notification produite par ce test est donc identifiée
 * sans ambiguïté par différence d'ensemble d'identifiants (avant/après le
 * workflow), puis localisée dans le DOM via son `createdAt` exact
 * (rendu tel quel par le template, `{{ notification.createdAt }}`).
 */
test('MEMBER consulte ses notifications réelles, les marque comme lues, et ne voit jamais celles de MEMBER2', async ({
  page,
  request,
}) => {
  // --- 1. Baseline : compteur UNREAD de MEMBER avant toute action de ce test ---
  const memberLogin = await request.post('http://localhost:8080/api/v1/auth/login', {
    data: { email: e2eAccounts.member.email, password: e2eAccounts.member.password },
  });
  const { token: memberToken } = await memberLogin.json();
  const unreadBefore = await request.get('http://localhost:8080/api/v1/me/notifications/unread-count', {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const countBefore: number = (await unreadBefore.json()).count;
  const notificationsBefore = await request.get('http://localhost:8080/api/v1/me/notifications?size=100', {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const idsBefore = new Set(
    (await notificationsBefore.json()).content.map((n: { id: number }) => n.id),
  );

  // --- 2. Workflow réel produisant LOAN_RETURNED pour MEMBER (UI réelle,
  // Title B en isolation) ---
  await loginAs(page, e2eAccounts.librarian);
  await createLoanViaUi(page, {
    memberNumber: e2eAccounts.member.memberNumber,
    titleName: e2eCatalogue.titleB.title,
    copyInventoryCode: e2eCatalogue.titleB.copyInventoryCode,
  });
  await page.goto('/staff/loans');
  await returnLoanViaUi(page, e2eCatalogue.titleB.copyInventoryCode);

  // --- 3. Compteur UNREAD : variation relative (+1), jamais une valeur absolue ---
  const unreadAfterCreate = await request.get('http://localhost:8080/api/v1/me/notifications/unread-count', {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const countAfterCreate: number = (await unreadAfterCreate.json()).count;
  expect(countAfterCreate).toBe(countBefore + 1);

  // Identification non ambiguë de MA Notification (différence d'ensemble
  // d'identifiants, jamais un tri par createdAt — voir commentaire d'en-tête).
  const notificationsAfterCreate = await request.get('http://localhost:8080/api/v1/me/notifications?size=100', {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const myNotification = (await notificationsAfterCreate.json()).content.find(
    (n: { id: number; notificationType: string }) =>
      !idsBefore.has(n.id) && n.notificationType === 'LOAN_RETURNED',
  );
  expect(myNotification).toBeTruthy();

  // --- 4. MEMBER login (UI réelle) → page Notifications ---
  await loginAs(page, e2eAccounts.member);
  await page.goto('/member/notifications');

  // --- 5. Cloche/badge : compteur affiché cohérent avec l'API ---
  await expect(page.locator('.nav-bell')).toContainText(String(countAfterCreate));

  // --- 6. Notification ciblée visible, UNREAD, champs réels (title/message) ---
  const notificationRow = page.locator('table tbody tr', {
    hasText: 'Prêt retourné',
  }).filter({ hasText: myNotification.createdAt });
  await expect(notificationRow).toBeVisible();
  await expect(notificationRow).toHaveCount(1);
  await expect(notificationRow.getByText('Non lue', { exact: true })).toBeVisible();
  await expect(notificationRow).toContainText('Le retour de votre prêt a bien été enregistré.');

  // --- 7. Mark as read — UI réelle ---
  await notificationRow.getByRole('button', { name: /Marquer comme lue/ }).click();
  await expect(notificationRow.getByText('Lue', { exact: true })).toBeVisible();

  // --- 8. Persistance READ après rechargement (pas un état local optimiste seul) ---
  await page.reload();
  const reloadedRow = page.locator('table tbody tr', {
    hasText: 'Prêt retourné',
  }).filter({ hasText: myNotification.createdAt });
  await expect(reloadedRow.getByText('Lue', { exact: true })).toBeVisible();

  // --- 9. Compteur décrémenté (relatif — ne suppose jamais un retour à 0) ---
  const unreadAfterRead = await request.get('http://localhost:8080/api/v1/me/notifications/unread-count', {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const countAfterRead: number = (await unreadAfterRead.json()).count;
  expect(countAfterRead).toBe(countAfterCreate - 1);

  // --- 10. Ownership : MEMBER2 produit sa propre Notification, type différent
  // (RESERVATION_CREATED, self-service, sans précondition à construire —
  // titleWithoutCopy n'a aucun Copy, donc immédiatement éligible) ---
  await loginAs(page, e2eAccounts.member2);
  await page.goto('/member/reservations');
  await page.getByRole('button', { name: 'Nouvelle réservation' }).click();
  await page.locator('#reservation-create-title-search').fill(e2eCatalogue.titleWithoutCopy.title);
  const titleResult = page.locator('li', { hasText: e2eCatalogue.titleWithoutCopy.title });
  await expect(titleResult).toBeVisible();
  await titleResult.getByRole('button', { name: 'Sélectionner' }).click();
  await page.getByRole('button', { name: 'Réserver', exact: true }).click();
  await expect(page.getByText('Réservation créée')).toBeVisible();

  const member2Login = await request.post('http://localhost:8080/api/v1/auth/login', {
    data: { email: e2eAccounts.member2.email, password: e2eAccounts.member2.password },
  });
  const { token: member2Token } = await member2Login.json();
  const member2NotificationsResponse = await request.get('http://localhost:8080/api/v1/me/notifications', {
    headers: { Authorization: `Bearer ${member2Token}` },
  });
  const member2Notifications = (await member2NotificationsResponse.json()).content;
  const member2Reservation = member2Notifications.find(
    (n: { notificationType: string }) => n.notificationType === 'RESERVATION_CREATED',
  );
  expect(member2Reservation).toBeTruthy();

  // --- 11. Ownership croisée : ni l'un ni l'autre ne voit la Notification de l'autre ---
  const memberNotificationsFinalResponse = await request.get('http://localhost:8080/api/v1/me/notifications', {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  const memberSeesMember2Notification = (await memberNotificationsFinalResponse.json()).content.some(
    (n: { id: number }) => n.id === member2Reservation.id,
  );
  expect(memberSeesMember2Notification).toBe(false);

  const member2SeesLoanReturnedType = member2Notifications.some(
    (n: { notificationType: string }) => n.notificationType === 'LOAN_RETURNED',
  );
  expect(member2SeesLoanReturnedType).toBe(false);
});
