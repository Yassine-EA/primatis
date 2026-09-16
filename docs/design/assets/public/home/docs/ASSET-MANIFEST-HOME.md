# PRIMATIS — Asset Manifest Home

**Phase :** ASSET-RESET-02.1  
**Date :** 2026-09-08  
**État :** validation technique et visuelle acquise ; prêt pour intégration lors du Visual Reset Home

## Assets runtime candidats

| id | fichier | besoin | rôle | dimensions | poids | point focal | source | status | integrationAllowed |
|---|---|---|---|---:|---:|---|---|---|---|
| HOME-HERO-001 | `hero/hero-background-1920.webp` | HOME-001 | décoratif | 1920×768 | 86 226 o | observatoire 91%/63%, zone texte à gauche | source SRC-001, crop/resize/compression | `APPROVED` | yes |
| HOME-HERO-002 | `hero/hero-background-1280.webp` | HOME-001 | décoratif | 1280×720 | 71 782 o | observatoire 91%/65% | source SRC-001, crop/resize/compression | `APPROVED` | yes |
| HOME-HERO-003 | `hero/hero-background-768-mobile.webp` | HOME-001 | décoratif | 768×960 | 63 984 o | observatoire 78%/62% | source SRC-001, crop mobile est | `APPROVED` | yes |
| HOME-PORTRAIT-001 | `/assets/shared/portraits/georges-lemaitre-1024.webp` | HOME-002 | informatif | 1024×1365 | 101 208 o | visage 52%/31% | source SRC-002, transparence conservée | `APPROVED` | yes |
| HOME-PORTRAIT-002 | `/assets/shared/portraits/georges-lemaitre-768.webp` | HOME-002 | informatif | 768×1024 | 57 820 o | visage 52%/31% | variante responsive | `APPROVED` | yes |
| HOME-PORTRAIT-003 | `/assets/shared/portraits/georges-lemaitre-480.webp` | HOME-002 | informatif | 480×640 | 24 812 o | visage 52%/31% | variante responsive | `APPROVED` | yes |
| HOME-ARTICLE-001 | `/assets/shared/editorial/editorial-cosmos-960.webp` | HOME-008/009 | décoratif | 960×540 | 76 650 o | galaxie 36%/42% | source SRC-003 | `APPROVED` | yes |
| HOME-ARTICLE-002 | `/assets/shared/editorial/editorial-cosmos-640.webp` | HOME-008/009 | décoratif | 640×360 | 36 458 o | galaxie 36%/42% | variante responsive | `APPROVED` | yes |
| HOME-ARTICLE-003 | `/assets/shared/editorial/editorial-library-960.webp` | HOME-008/009 | décoratif | 960×540 | 25 164 o | sphère armillaire 38%/68% | crop sans portrait de SRC-004 | `APPROVED` | yes |
| HOME-ARTICLE-004 | `/assets/shared/editorial/editorial-library-640.webp` | HOME-008/009 | décoratif | 640×360 | 16 672 o | sphère armillaire 38%/68% | variante responsive | `APPROVED` | yes |
| HOME-ARTICLE-005 | `/assets/shared/editorial/editorial-observatory-960.webp` | HOME-008/009 | décoratif | 960×540 | 63 470 o | observatoire 78%/53% | source SRC-005 | `APPROVED` | yes |
| HOME-ARTICLE-006 | `/assets/shared/editorial/editorial-observatory-640.webp` | HOME-008/009 | décoratif | 640×360 | 31 654 o | observatoire 78%/53% | variante responsive | `APPROVED` | yes |
| HOME-EDITORIAL-001 | `/assets/shared/editorial/lemaitre-observatory-banner-1920.webp` | HOME-012 | décoratif | 1920×480 | 22 358 o | portrait 14%/48%, texte au centre | source SRC-006, crop dédié | `APPROVED` | yes |
| HOME-EDITORIAL-002 | `/assets/shared/editorial/lemaitre-observatory-banner-960-mobile.webp` | HOME-012 | décoratif | 960×720 | 27 986 o | portrait 22%/46% | variante mobile | `APPROVED` | yes |
| HOME-FOOTER-001 | `footer/footer-observatory-1920.webp` | HOME-014 | décoratif | 1920×360 | 36 586 o | observatoire 8%/50%, zone contenu à droite | source SRC-001, crop miroir décoratif | `APPROVED` | yes |
| HOME-FOOTER-002 | `footer/footer-observatory-960-mobile.webp` | HOME-014 | décoratif | 960×540 | 43 904 o | observatoire 18%/57% | source SRC-001, crop mobile miroir | `APPROVED` | yes |

Tous les fichiers runtime sont sous les budgets fixés : hero ≤ 500 Ko, bandeau ≤ 300 Ko, cartes ≤ 180 Ko. Aucun fichier n'incorpore de texte.

## Sources

Les identifiants SRC-001 à SRC-006 et leurs empreintes sont documentés dans `PROVENANCE.md`. Les sources graphiques d'origine ne sont pas dupliquées dans ce pack.

## Interdictions

- Ne pas présenter HOME-PORTRAIT comme photographie historique.
- Ne pas calculer une association sémantique entre fallback Article et titre/tags inexistants.
- Ne pas afficher les données institutionnelles, statistiques ou liens fictifs de la maquette.
- Ne pas fusionner le texte du hero dans le fond.

## Validation humaine

```text
validatedBy: Yassine ELABOUBI
validatedAt: 2026-09-08
validationScope: preview/home-assets-board.png et pack Home ASSET-RESET-02.1
```
