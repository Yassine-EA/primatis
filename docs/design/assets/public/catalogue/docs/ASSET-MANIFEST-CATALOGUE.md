# PRIMATIS — Asset Manifest Catalogue

**Phase :** ASSET-RESET-02.2  
**Date :** 2026-09-08  
**État :** validation technique et visuelle acquises

| id | fichier | besoin | rôle | dimensions | poids | point focal | source | status | integrationAllowed |
|---|---|---|---|---:|---:|---|---|---|---|
| CAT-SOURCE-001 | `source/catalogue-library-armillary-master.png` | conservation source | source seulement | 2172×724 | 1 782 700 o | sphère 50%/50% | génération CAT-GEN-001 | `SOURCE_ONLY` | no |
| CAT-HERO-001 | `hero/catalogue-hero-1920.webp` | CAT-001 | décoratif | 1920×640 | 75 124 o | sphère 50%/50% | CAT-SOURCE-001 | `APPROVED` | yes |
| CAT-HERO-002 | `hero/catalogue-hero-1280.webp` | CAT-001 | décoratif | 1280×512 | 50 342 o | sphère 50%/50% | CAT-SOURCE-001 | `APPROVED` | yes |
| CAT-HERO-003 | `hero/catalogue-hero-768-mobile.webp` | CAT-001 | décoratif | 768×512 | 45 892 o | sphère 50%/50% | CAT-SOURCE-001 | `APPROVED` | yes |

Les trois fichiers runtime sont largement sous le budget hero de 500 Ko. Ils ne contiennent aucun texte ni élément d'interface.

## Dépendances globales

| Besoin | Source |
|---|---|
| Couverture réelle | `TitleResponse.coverImageUrl` |
| Couverture absente | ASSET-RESET-01 `fallbacks/cover.svg` |
| État vide | ASSET-RESET-01 `fallbacks/empty-states/catalogue.svg` |
| Recherche/menu/home/compte | ASSET-RESET-01 `icons/editorial/` |
| Actions de filtre/tri/grille | PrimeIcons |

## Données interdites

`CAT-005` disponibilité publique et `CAT-006` favoris restent `FORBIDDEN_FAKE` tant que leurs contrats fonctionnels ne sont pas disponibles.
