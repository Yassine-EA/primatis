# PRIMATIS — Bibliothèque d’assets consolidée

Version canonique issue d’ASSET-RESET-01 à ASSET-RESET-04.

## Organisation

- `brand/` : identité et favicons ;
- `patterns/` : motifs astronomiques ;
- `icons/` : pictogrammes éditoriaux ;
- `fallbacks/` : couvertures, erreur image et états vides ;
- `shared/` : ressources réellement mutualisées entre plusieurs pages ;
- `public/` : assets spécifiques aux six pages publiques ;
- `member/` : socle commun Prêts, Réservations, Amendes et Notifications ;
- `staff-admin/` : socle commun des écrans de gestion ;
- `source/` : masters d’identité conservés hors runtime ;
- `docs/` : manifeste global, accessibilité, migration et Final Gate ;
- `preview/` : planches de validation.

## Principes définitifs

1. Une seule occurrence binaire de chaque asset runtime.
2. WebP pour les scènes raster, SVG pour logos, icônes et états vides.
3. Texte, tableaux, graphiques, avatars et statuts restent des composants.
4. Aucune donnée métier absente n’est simulée par un visuel.
5. Tout nouveau fichier doit être ajouté au manifeste et contrôlé avant intégration.

Les dossiers `source/` et `preview/` sont documentaires : ils ne doivent jamais être copiés dans les assets Angular de production.

```text
ASSET-RESET-05 — PASS TECHNIQUE — READY FOR OWNER FINAL APPROVAL
```
