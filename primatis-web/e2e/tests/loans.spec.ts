import { expect, test } from '@playwright/test';

import { e2eAccounts } from '../fixtures/e2e-accounts';
import { e2eCatalogue } from '../fixtures/e2e-catalogue';
import { getStoredToken, loginAs } from '../fixtures/auth-helpers';
import { createLoanViaUi, returnLoanViaUi } from '../fixtures/loan-helpers';

/**
 * DEV-14.5 — Loans / Returns E2E.
 *
 * Un seul test principal, transactionnel de bout en bout (§17 du
 * handoff) : Chromium → Angular (formulaire staff réel, LoanCreateDialog)
 * → Spring Boot (LoanService.registerLoan/registerReturn réels) →
 * PostgreSQL primatis_e2e. Aucun Loan/Return préchargé (DEV-DEC-0068) :
 * le Loan est créé puis retourné exclusivement via le workflow applicatif
 * réel (UI staff), jamais par SQL direct ni par contournement API pour
 * l'action principale.
 *
 * Scénario volontairement simple (handoff §11/§12) : aucune Reservation
 * WAITING sur le Title, retour immédiat (jamais tardif) → aucune Fine
 * attendue, Copy revient à AVAILABLE (pas de fulfillment READY).
 *
 * Interactions UI (création/retour) factorisées dans
 * `../fixtures/loan-helpers.ts` (DEV-14.6, réutilisées par
 * `reservations.spec.ts`) — ce test conserve toutes ses assertions
 * propres, seule la mécanique d'interaction est partagée.
 */
test('LIBRARIAN enregistre puis retourne un prêt réel', async ({ page, request }) => {
  await loginAs(page, e2eAccounts.librarian);
  const librarianToken = await getStoredToken(page);

  // --- Register Loan (UI staff réelle, LoanCreateDialog) ---
  await createLoanViaUi(page, {
    memberNumber: e2eAccounts.member.memberNumber,
    titleName: e2eCatalogue.titleA.title,
    copyInventoryCode: e2eCatalogue.titleA.copyInventoryCode,
  });

  // --- Assertions après création : UI ---
  // Colonnes (DEV-15.7) : Emprunteur(0)/N° adhérent(1)/Exemplaire(2)/
  // Échéance(3)/Statut(4)/Date d'emprunt(5)/Date de retour(6)/Action(7) —
  // libellé de statut désormais en français ("En cours"), jamais l'enum brut.
  const activeRow = page.locator('table tbody tr', { hasText: e2eCatalogue.titleA.copyInventoryCode });
  await expect(activeRow).toBeVisible();
  await expect(activeRow.getByText('En cours', { exact: true })).toBeVisible();
  await expect(activeRow.locator('td').nth(6)).toHaveText('—'); // returnDate absent

  // --- Assertions après création : backend réel (pas seulement le toast) ---
  const loansAfterCreate = await request.get('http://localhost:8080/api/v1/loans', {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  expect(loansAfterCreate.status()).toBe(200);
  const loansAfterCreateBody = await loansAfterCreate.json();
  const createdLoan = loansAfterCreateBody.content.find(
    (loan: { copy: { inventoryCode: string } }) => loan.copy.inventoryCode === e2eCatalogue.titleA.copyInventoryCode,
  );
  expect(createdLoan).toBeTruthy();
  expect(createdLoan.loanStatus).toBe('ACTIVE');
  expect(createdLoan.returnDate).toBeNull();
  const loanId: number = createdLoan.id;

  // Copy : AVAILABLE → ON_LOAN (endpoint staff réel, jamais une hypothèse).
  const publicTitles = await request.get('http://localhost:8080/api/v1/titles', {
    params: { q: e2eCatalogue.titleA.title },
  });
  const titleId: number = (await publicTitles.json()).content[0].id;
  const copiesAfterCreate = await request.get(`http://localhost:8080/api/v1/staff/titles/${titleId}/copies`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  expect(copiesAfterCreate.status()).toBe(200);
  const copyAfterCreate = (await copiesAfterCreate.json()).find(
    (copy: { inventoryCode: string }) => copy.inventoryCode === e2eCatalogue.titleA.copyInventoryCode,
  );
  expect(copyAfterCreate.availabilityStatus).toBe('ON_LOAN');

  // --- Due date cohérente avec la configuration réelle (LOAN_DURATION_DAYS,
  // jamais une valeur "21" codée en dur dans ce test) ---
  const adminLogin = await request.post('http://localhost:8080/api/v1/auth/login', {
    data: { email: e2eAccounts.admin.email, password: e2eAccounts.admin.password },
  });
  const { token: adminToken } = await adminLogin.json();
  const settingsResponse = await request.get('http://localhost:8080/api/v1/settings', {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  const settings = await settingsResponse.json();
  const loanDurationDays = Number(
    settings.find((setting: { settingKey: string }) => setting.settingKey === 'LOAN_DURATION_DAYS').settingValue,
  );
  const loanDateUtc = new Date(createdLoan.loanDate);
  const expectedDueDate = new Date(
    Date.UTC(loanDateUtc.getUTCFullYear(), loanDateUtc.getUTCMonth(), loanDateUtc.getUTCDate() + loanDurationDays),
  )
    .toISOString()
    .slice(0, 10);
  expect(createdLoan.dueDate).toBe(expectedDueDate);

  // --- Register Return (UI staff réelle, ConfirmationService PrimeNG) ---
  await returnLoanViaUi(page, e2eCatalogue.titleA.copyInventoryCode);

  // --- Assertions après retour : UI ---
  await expect(activeRow.getByText('Retourné', { exact: true })).toBeVisible();
  await expect(activeRow.locator('td').nth(6)).not.toHaveText('—');

  // --- Assertions après retour : backend réel ---
  const loansAfterReturn = await request.get('http://localhost:8080/api/v1/loans', {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  const returnedLoan = (await loansAfterReturn.json()).content.find(
    (loan: { id: number }) => loan.id === loanId,
  );
  expect(returnedLoan.loanStatus).toBe('RETURNED');
  expect(returnedLoan.returnDate).not.toBeNull();

  // Copy : ON_LOAN → AVAILABLE (aucune Reservation WAITING sur ce Title
  // dans la baseline DEV-14.4 → jamais RESERVED/READY ici, conforme au
  // scénario volontairement simple DEV-14.5).
  const copiesAfterReturn = await request.get(`http://localhost:8080/api/v1/staff/titles/${titleId}/copies`, {
    headers: { Authorization: `Bearer ${librarianToken}` },
  });
  const copyAfterReturn = (await copiesAfterReturn.json()).find(
    (copy: { inventoryCode: string }) => copy.inventoryCode === e2eCatalogue.titleA.copyInventoryCode,
  );
  expect(copyAfterReturn.availabilityStatus).toBe('AVAILABLE');

  // --- Fine : aucune créée pour CE Loan précis (retour non tardif, le
  // jour même). Vérifié par loanId, jamais par un total global : MEMBER
  // est réutilisé par fines.spec.ts (DEV-14.7), qui lui crée légitimement
  // une Fine PAID sur un Loan distinct — un total à 0 casserait donc dès
  // que les deux fichiers s'exécutent dans la même suite. ---
  const memberLogin = await request.post('http://localhost:8080/api/v1/auth/login', {
    data: { email: e2eAccounts.member.email, password: e2eAccounts.member.password },
  });
  const { token: memberToken } = await memberLogin.json();
  const ownFines = await request.get('http://localhost:8080/api/v1/me/fines', {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  expect(ownFines.status()).toBe(200);
  const fineForThisLoan = (await ownFines.json()).content.find(
    (fine: { loan: { id: number } }) => fine.loan.id === loanId,
  );
  expect(fineForThisLoan).toBeUndefined();

  // --- Notification LOAN_RETURNED : au moins persistée (conséquence
  // systématique du retour, LoanService#registerReturn) ---
  const ownNotifications = await request.get('http://localhost:8080/api/v1/me/notifications', {
    headers: { Authorization: `Bearer ${memberToken}` },
  });
  expect(ownNotifications.status()).toBe(200);
  const loanReturnedNotification = (await ownNotifications.json()).content.find(
    (notification: { notificationType: string; originId: number }) =>
      notification.notificationType === 'LOAN_RETURNED' && notification.originId === loanId,
  );
  expect(loanReturnedNotification).toBeTruthy();
});
