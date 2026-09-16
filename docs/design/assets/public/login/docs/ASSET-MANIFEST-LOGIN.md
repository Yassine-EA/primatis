# PRIMATIS — Asset Manifest — Login

**Phase :** ASSET-RESET-02.6  
**Date :** 2026-09-08  
**État :** APPROVED — validations technique et visuelle acquises

| id | fichier | rôle | dimensions | poids | source | status | integrationAllowed |
|---|---|---|---:|---:|---|---|---|
| LOGIN-BG-001 | `background/login-background-1920.webp` | décoratif | 1920×1080 | 110 562 o | LOGIN-GEN-001 | `APPROVED` | yes |
| LOGIN-BG-002 | `background/login-background-1440.webp` | décoratif | 1440×900 | 97 340 o | LOGIN-GEN-001 | `APPROVED` | yes |
| LOGIN-BG-003 | `background/login-background-768-mobile.webp` | décoratif | 768×640 | 69 660 o | GL-HERO-003 | `APPROVED` | yes |
| LOGIN-PORTRAIT-001 | `/assets/shared/portraits/georges-lemaitre-1024.webp` | éditorial | 1024×1365 | 101 208 o | GL-PORTRAIT-001 | `APPROVED` | yes |
| LOGIN-PORTRAIT-002 | `/assets/shared/portraits/georges-lemaitre-768.webp` | éditorial | 768×1024 | 57 820 o | GL-PORTRAIT-002 | `APPROVED` | yes |
| LOGIN-ICON-001 | `icons/email.svg` | formulaire | 24×24 vectoriel | — | création vectorielle | `APPROVED` | yes |
| LOGIN-ICON-002 | `icons/lock.svg` | formulaire | 24×24 vectoriel | — | création vectorielle | `APPROVED` | yes |
| LOGIN-ICON-003 | `icons/eye.svg` | action | 24×24 vectoriel | — | création vectorielle | `APPROVED` | yes |
| LOGIN-ICON-004 | `icons/eye-off.svg` | action | 24×24 vectoriel | — | création vectorielle | `APPROVED` | yes |
| LOGIN-ICON-005 | `icons/settings.svg` | bénéfice | 24×24 vectoriel | — | création vectorielle | `APPROVED` | yes |
| LOGIN-ICON-006 | `icons/shield-lock.svg` | sécurité | 24×24 vectoriel | — | création vectorielle | `APPROVED` | yes |

Poids raster runtime total : **436 590 octets**.

## Dépendances globales

| Besoin | Source |
|---|---|
| Logo et symbole | ASSET-RESET-01 |
| Icône livre | ASSET-RESET-01 `catalogue-book.svg` |
| Icône utilisateurs | ASSET-RESET-01 `users-group.svg` |
| Flèche CTA | ASSET-RESET-01 `arrow-right.svg` |

## Fonctionnalités interdites

`LOGIN-006` « Mot de passe oublié ? » reste `FORBIDDEN_FAKE` tant qu’aucune route et aucun parcours de récupération ne sont implémentés.
