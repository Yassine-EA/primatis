/**
 * Comptes de la baseline E2E déterministe (DEV-DEC-0067).
 *
 * Copie texte des identifiants provisionnés côté backend par
 * `E2eBaselineProvisioner` (primatis-api/src/main/java/be/primatis/e2e/
 * E2eBaselineProvisioner.java) sous le profil Spring "e2e" uniquement,
 * contre `primatis_e2e` (DEV-DEC-0066) — jamais primatis_dev/primatis_test.
 *
 * Une seule source de vérité TEXTUELLE : backend et frontend sont deux
 * runtimes distincts sans module partagé, ces valeurs sont donc dupliquées
 * ici intentionnellement (pas une divergence à corriger). Identifiants
 * fixes et non sensibles, valables uniquement contre une base E2E locale
 * jetable — jamais des identifiants réels.
 */
export const e2eAccounts = {
  admin: {
    email: 'admin.e2e@primatis.local',
    password: 'PrimatisE2eAdmin#Local',
  },
  librarian: {
    email: 'librarian.e2e@primatis.local',
    password: 'PrimatisE2eLibrarian#Local',
  },
  member: {
    email: 'member.e2e@primatis.local',
    password: 'PrimatisE2eMember#Local',
    // Déterministe : premier appel à MemberNumberGenerator.generateNext()
    // (nextval member_number_seq) après un Flyway migrate frais — le
    // provisioning catalogue (DEV-14.4) ne consomme jamais cette séquence.
    memberNumber: 'M000000001',
  },
  // Second MEMBER (DEV-14.6) : emprunteur intermédiaire des scénarios
  // Reservation, pour ne jamais dépendre d'un Loan créé par un autre
  // fichier de test (isolation, handoff DEV-14.6 §9). memberNumber
  // déterministe : second appel à generateNext() après "member".
  member2: {
    email: 'member2.e2e@primatis.local',
    password: 'PrimatisE2eMember2#Local',
    memberNumber: 'M000000002',
  },
} as const;
