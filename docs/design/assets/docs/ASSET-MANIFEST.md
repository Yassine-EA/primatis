# PRIMATIS — Asset Manifest initial

**Phase :** ASSET-RESET-01  
**Date :** 2026-09-08  
**État général :** validé techniquement et visuellement par le propriétaire ; prêt pour intégration Angular lors de la phase prévue

## Statuts

- `APPROVED` : fichier produit, validé techniquement et accepté visuellement par le propriétaire.
- `APPROVED_SOURCE` : source globale conservée sans modification substantielle.
- `DISABLED` : présent pour archivage contrôlé, intégration interdite.

## Identité

| id | file | runtimePath recommandé | needIds | pages | role | format / taille | poids | source et droits | status | integrationAllowed |
|---|---|---|---|---|---|---|---:|---|---|---|
| BRAND-001 | `brand/primatis-symbol-dark.svg` | `/assets/brand/primatis-symbol-dark.svg` | GLOBAL-004 | toutes | informatif ou décoratif selon contexte | SVG 64×64 | 825 o | asset PRIMATIS fourni ; usage interne projet | `APPROVED_SOURCE` | yes |
| BRAND-002 | `brand/primatis-symbol-light.svg` | `/assets/brand/primatis-symbol-light.svg` | GLOBAL-003/004 | footer, sidebar | informatif ou décoratif selon contexte | SVG 64×64 | 763 o | dérivé BRAND-001 par Codex ; aucune source tierce | `APPROVED` | yes |
| BRAND-003 | `brand/primatis-logo-horizontal-dark.svg` | `/assets/brand/primatis-logo-horizontal-dark.svg` | GLOBAL-001 | header clair | informatif | SVG 360×72 | 24 095 o | dérivé du logo PRIMATIS fourni ; texte converti en tracés DejaVu | `APPROVED` | yes |
| BRAND-004 | `brand/primatis-logo-horizontal-light.svg` | `/assets/brand/primatis-logo-horizontal-light.svg` | GLOBAL-003 | footer/fond navy | informatif | SVG 360×72 | 24 095 o | dérivé BRAND-003 par Codex | `APPROVED` | yes |
| BRAND-005 | `brand/primatis-logo-vertical-dark.svg` | `/assets/brand/primatis-logo-vertical-dark.svg` | GLOBAL-002 | surface claire | informatif | SVG 180×128 | 23 935 o | dérivé du logo PRIMATIS fourni ; texte converti en tracés DejaVu | `APPROVED` | yes |
| BRAND-006 | `brand/primatis-logo-vertical-light.svg` | `/assets/brand/primatis-logo-vertical-light.svg` | GLOBAL-003 | sidebar/fond navy | informatif | SVG 180×128 | 23 935 o | dérivé BRAND-005 par Codex | `APPROVED` | yes |
| BRAND-007 | `brand/favicon.svg` | `/favicon.svg` | GLOBAL-004 | navigateur | informatif | SVG 64×64 | 518 o | dérivé du symbole PRIMATIS par Codex | `APPROVED` | yes |
| BRAND-008 | `brand/favicon-32.png`, `favicon-48.png`, `favicon.ico`, `apple-touch-icon-180.png` | racine runtime / métadonnées HTML | GLOBAL-004 | navigateur/PWA | informatif | PNG/ICO 32, 48, 180 px | 34 248 o | rendus de BRAND-007 | `APPROVED` | yes |

Les logos finaux ne contiennent aucune balise `<text>` et ne dépendent donc d'aucune police installée au runtime.

## Motifs

| id | file | runtimePath recommandé | needIds | pages | role | format / taille | poids | source et droits | status | integrationAllowed |
|---|---|---|---|---|---|---|---:|---|---|---|
| PATTERN-001 | `patterns/orbit-copper.svg` | `/assets/patterns/orbit-copper.svg` | GLOBAL-005 | public, member | décoratif | SVG 200×200 | 427 o | normalisation de l'orbite PRIMATIS fournie | `APPROVED` | yes |
| PATTERN-002 | `patterns/constellation-light.svg` | `/assets/patterns/constellation-light.svg` | GLOBAL-005 | fonds navy | décoratif | SVG 300×200 | 696 o | normalisation de la constellation PRIMATIS fournie | `APPROVED` | yes |
| PATTERN-003 | `patterns/constellation-dark.svg` | `/assets/patterns/constellation-dark.svg` | GLOBAL-005 | fonds clairs | décoratif | SVG 300×200 | 696 o | dérivé PATTERN-002 par Codex | `APPROVED` | yes |
| PATTERN-004 | `patterns/star-copper.svg` | `/assets/patterns/star-copper.svg` | GLOBAL-005 | toutes | décoratif | SVG 24×24 | 192 o | normalisation de l'étoile PRIMATIS fournie | `APPROVED` | yes |
| PATTERN-005 | `patterns/starfield.webp` | `/assets/patterns/starfield.webp` | GLOBAL-006 | fonds navy | décoratif | WebP 1920×1080 | 10 498 o | asset génératif PRIMATIS fourni ; aucune source tierce identifiée | `APPROVED_SOURCE` | yes |

`focalPoint` : sans objet pour les SVG ; centre 50%/50% pour `starfield.webp`. Les motifs sont adaptables par recadrage CSS et n'exigent pas de variante responsive.

## Pictogrammes publics

Tous les fichiers ci-dessous sont des SVG 24×24, `currentColor`, sans texte, entre 194 et 331 octets. Runtime recommandé : `/assets/icons/editorial/{file}`.

| id | fichiers | needIds | pages | role | source et droits | status | integrationAllowed |
|---|---|---|---|---|---|---|---|
| ICON-001 | `account.svg`, `arrow-right.svg`, `articles-document.svg`, `calendar.svg`, `catalogue-book.svg`, `chevron-left.svg`, `chevron-right.svg`, `external-link.svg`, `globe.svg`, `info-circle.svg`, `location.svg`, `menu.svg`, `search.svg`, `users-group.svg` | GLOBAL-008 | public | décoratif avec libellé ou informatif avec nom accessible | assets PRIMATIS fournis | `APPROVED_SOURCE` | yes |
| ICON-002 | `home.svg`, `close.svg` | GLOBAL-008, CAT-008 | public/mobile | décoratif avec libellé ou informatif avec nom accessible | créés par Codex selon la grammaire graphique existante | `APPROVED` | yes |
| ICON-003 | `icons/social-disabled/social-facebook.svg`, `social-instagram.svg`, `social-linkedin.svg`, `social-youtube.svg` | GLOBAL-009 | aucune actuellement | informatif | assets PRIMATIS fournis ; marques tierces représentées | `DISABLED` | no |

Les actions métier, CRUD, formulaires, statuts et tableaux utilisent PrimeIcons : aucun fichier n'est dupliqué dans ce pack.

## Fallbacks et états vides

| id | file | runtimePath recommandé | needIds | pages | role | format / taille | poids | source et droits | status | integrationAllowed |
|---|---|---|---|---|---|---|---:|---|---|---|
| FALLBACK-001 | `fallbacks/cover.svg` | `/assets/fallbacks/cover.svg` | HOME-011, CAT-004, TITLE-003 | public | informatif | SVG 360×520 | 855 o | créé par Codex avec primitives PRIMATIS | `APPROVED` | yes |
| FALLBACK-002 | `fallbacks/cover-compact.svg` | `/assets/fallbacks/cover-compact.svg` | MLOAN-005, SCAT-003 | member/staff | informatif | SVG 120×168 | 652 o | déclinaison FALLBACK-001 | `APPROVED` | yes |
| FALLBACK-003 | `fallbacks/image-error.svg` | `/assets/fallbacks/image-error.svg` | GLOBAL-010 | toutes | informatif | SVG 320×200 | 602 o | créé par Codex | `APPROVED` | yes |
| EMPTY-001 | `fallbacks/empty-states/catalogue.svg` | `/assets/fallbacks/empty-states/catalogue.svg` | GLOBAL-012 | catalogue | décoratif si texte HTML voisin | SVG 240×180 | 477 o | créé par Codex | `APPROVED` | yes |
| EMPTY-002 | `fallbacks/empty-states/loans.svg` | `/assets/fallbacks/empty-states/loans.svg` | GLOBAL-012 | prêts | décoratif si texte HTML voisin | SVG 240×180 | 552 o | créé par Codex | `APPROVED` | yes |
| EMPTY-003 | `fallbacks/empty-states/reservations.svg` | `/assets/fallbacks/empty-states/reservations.svg` | GLOBAL-012 | réservations | décoratif si texte HTML voisin | SVG 240×180 | 503 o | créé par Codex | `APPROVED` | yes |
| EMPTY-004 | `fallbacks/empty-states/fines.svg` | `/assets/fallbacks/empty-states/fines.svg` | GLOBAL-012 | amendes | décoratif si texte HTML voisin | SVG 240×180 | 653 o | créé par Codex | `APPROVED` | yes |
| EMPTY-005 | `fallbacks/empty-states/notifications.svg` | `/assets/fallbacks/empty-states/notifications.svg` | GLOBAL-012, MNOTIF-005 | notifications | décoratif si texte HTML voisin | SVG 240×180 | 546 o | créé par Codex | `APPROVED` | yes |

Tous les fallbacks runtime sont sans texte éditorial incorporé. L'application fournit le libellé et l'alternative dans le DOM.

## Éléments explicitement non produits

| Besoin | Décision |
|---|---|
| Avatar neutre | initiales CSS documentées dans `ACCESSIBILITY-AND-CSS.md` |
| Icônes métier | PrimeIcons existant dans le frontend |
| Observatoire legacy | exclu du pack ; ne pas réintégrer |
| Images Home et pages | hors périmètre, traitées dans ASSET-RESET-02.x |

## Validation humaine

```text
validatedBy: Yassine ELABOUBI
validatedAt: 2026-09-08
validationScope: preview/asset-reset-01-board.png et socle global ASSET-RESET-01
```

Les quatre icônes sociales restent volontairement `DISABLED` : la validation visuelle du socle ne remplace pas la validation future des URL officielles.
