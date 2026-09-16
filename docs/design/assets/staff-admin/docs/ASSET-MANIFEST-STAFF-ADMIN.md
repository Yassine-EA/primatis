# PRIMATIS — Asset Manifest — Staff/Admin

**Phase :** ASSET-RESET-04  
**Date :** 2026-09-09  
**État :** APPROVED — validations technique et visuelle acquises

## Nouveaux fichiers runtime

| id | fichier | rôle | dimensions | source | status | integrationAllowed |
|---|---|---|---:|---|---|---|
| STAFF-SHELL-001 | `shell/staff-sidebar-observatory-480.webp` | décoratif | 480×480 RGBA | MEMBER-HERO-001 retraité | `APPROVED` | yes |
| STAFF-SHELL-002 | `shell/staff-sidebar-observatory-320.webp` | décoratif | 320×320 RGBA | STAFF-SHELL-001 | `APPROVED` | yes |
| STAFF-EMPTY-001 | `empty-states/management-empty.svg` | informatif | 420×280 vectoriel | création vectorielle PRIMATIS | `APPROVED` | yes |

## Réemplois obligatoires

| besoin | fichier/système existant |
|---|---|
| logo clair de sidebar | `assets/brand/primatis-logo-horizontal-light.svg` |
| symbole clair compact | `assets/brand/primatis-symbol-light.svg` |
| couverture compacte | `assets/fallbacks/cover-compact.svg` |
| erreur image | `assets/fallbacks/image-error.svg` |
| navigation, CRUD, filtres et statuts | PrimeIcons |
| avatar utilisateur | composant d’initiales CSS |

## Non-assets

Les graphiques, compteurs, tableaux, badges, étapes de formulaire et avatars ne sont jamais des images. Ils restent pilotés par les données et les composants Angular/PrimeNG.
