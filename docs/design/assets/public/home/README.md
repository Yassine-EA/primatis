# PRIMATIS — ASSET-RESET-02.1 — Home

Pack d'assets optimisés pour reconstruire la Home depuis `docs/design/public/home.png`. Il complète le socle global validé dans ASSET-RESET-01 et ne modifie pas Angular.

## Contenu

- `hero/` : fond desktop/tablette/mobile ;
- `articles/` : trois fallbacks éditoriaux génériques en 960×540 et 640×360 ;
- `../../shared/portraits/` : portrait Georges Lemaître mutualisé ;
- `../../shared/editorial/` : fallbacks d’articles et bandeau Lemaître mutualisés ;
- `footer/` : fond observatoire desktop/mobile ;
- `docs/` : manifeste, provenance, contrat d'intégration et rapport de validation ;
- `preview/` : planche de validation visuelle.
- `source/` : masters PNG d’origine et référence Home, exclus du runtime.

## Principes figés

1. Le hero est une composition en couches : fond, overlay CSS, portrait, motifs éventuels, puis contenu HTML.
2. Aucun titre, CTA, citation, formule ou donnée institutionnelle n'est incorporé aux images.
3. Le portrait détouré est une **illustration générée de Georges Lemaître**, jamais une photographie d'archive.
4. Les images d'Article sont des fallbacks décoratifs de marque. Elles ne constituent pas un champ image métier et ne prouvent pas le sujet d'un Article.
5. Couvertures, textes, dates et liens restent pilotés par les données réelles.

## Dépendance

Réutiliser depuis ASSET-RESET-01 les logos, icônes, motifs, fallback de couverture et règles d'accessibilité. Ne pas les dupliquer dans ce pack.

## Statut

```text
ASSET-RESET-02.1 — PASS FINAL — APPROVED
```
