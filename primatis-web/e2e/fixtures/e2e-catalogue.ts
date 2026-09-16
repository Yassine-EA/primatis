/**
 * Baseline catalogue E2E déterministe (DEV-14.4, DEV-DEC-0068).
 *
 * Copie texte des valeurs provisionnées côté backend par
 * `E2eBaselineProvisioner#provisionCatalogueBaseline`
 * (primatis-api/src/main/java/be/primatis/e2e/E2eBaselineProvisioner.java)
 * sous le profil Spring "e2e" uniquement, contre `primatis_e2e`
 * (DEV-DEC-0066). Identifiants fonctionnels stables (ISBN, inventoryCode),
 * jamais un ID technique — cohérent avec `database-model.md` §25.3.
 */
export const e2eCatalogue = {
  searchPrefix: 'PRIMATIS E2E',
  titleA: {
    isbn: '9782000000001',
    title: 'PRIMATIS E2E Available Title',
    language: 'FR',
    author: 'E2E Author Alpha',
    genre: 'Roman E2E',
    copyInventoryCode: 'E2E-COPY-0001',
  },
  titleB: {
    isbn: '9782000000002',
    title: 'PRIMATIS E2E Second Title',
    language: 'EN',
    authors: ['E2E Author Alpha', 'E2E Author Beta'],
    genre: 'Essai E2E',
    copyInventoryCode: 'E2E-COPY-0002',
  },
  titleWithoutCopy: {
    isbn: '9782000000003',
    title: 'PRIMATIS E2E Title Without Copy',
  },
  titleWithdrawn: {
    isbn: '9782000000004',
    title: 'PRIMATIS E2E Withdrawn Title',
  },
  // Dédié au scénario Fine (DEV-14.7) — isolation totale par rapport à
  // titleA (déjà utilisé par loans.spec.ts/reservations.spec.ts).
  titleFine: {
    isbn: '9782000000005',
    title: 'PRIMATIS E2E Fine Scenario Title',
    copyInventoryCode: 'E2E-COPY-0003',
  },
} as const;
