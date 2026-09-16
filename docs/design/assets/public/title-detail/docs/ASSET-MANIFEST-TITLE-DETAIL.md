# PRIMATIS — Asset Manifest — Détail d’un livre

**Phase :** ASSET-RESET-02.3  
**Date :** 2026-09-08  
**État :** validation technique et visuelle acquises

| id | fichier | besoin | rôle | dimensions | poids | source | status | integrationAllowed |
|---|---|---|---|---:|---:|---|---|---|
| TITLE-HERO-001 | `hero/title-detail-hero-1920.webp` | TITLE-001 | décoratif | 1920×320 | 30 474 o | HOME-HERO-001 | `APPROVED` | yes |
| TITLE-HERO-002 | `hero/title-detail-hero-1280.webp` | TITLE-001 | décoratif | 1280×320 | 33 262 o | HOME-HERO-002 | `APPROVED` | yes |

## Dépendances globales

| Besoin | Source autorisée |
|---|---|
| Couverture principale | `TitleResponse.coverImageUrl` |
| Couverture absente | ASSET-RESET-01 `fallbacks/cover.svg` |
| Image en erreur | ASSET-RESET-01 `fallbacks/image-error.svg` |
| Métadonnées/actions | PrimeIcons ou SVG globaux |
| Footer | ASSET-RESET-02.1 Home |

## Données interdites

- `TITLE-005` : disponibilité et localisations publiques ;
- `TITLE-006` : recommandations et ressources associées.

Ces blocs restent `FORBIDDEN_FAKE` tant qu’un contrat backend réel ne les expose pas.
