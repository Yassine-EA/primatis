# Provenance et prompt — Hero Catalogue

## Génération

| champ | valeur |
|---|---|
| id | `CAT-GEN-001` |
| date | 2026-09-08 |
| outil | OpenAI ImageGen intégré |
| type | nouvelle image guidée par référence |
| référence | `docs/design/public/catalogue.png`, direction artistique uniquement |
| master | `source/catalogue-library-armillary-master.png` |
| SHA-256 master | `1e482a9a46233e12b72c22946d75929bb37f71bdc4f9fe54e904ef8c0f8e434e` |

## Prompt final

```text
Use case: historical-scene
Asset type: responsive website hero background for the PRIMATIS public catalogue
Input image: the supplied Catalogue mockup is a composition and art-direction reference only; do not reproduce any UI, text, logo, navigation, cards, filters, buttons, or book covers from it.
Primary request: create a clean ultra-wide editorial background showing a dignified old public-library wall of dark wooden bookshelves filled with antique books, with one elegant brass armillary sphere centered in the composition.
Style/medium: refined cinematic photographic illustration, institutional cultural heritage, realistic materials, premium but restrained.
Composition/framing: wide symmetrical panorama; armillary sphere centered; shelves span the frame; deliberately darker low-detail negative space on the left for a large title and on the right for a short quotation; no people.
Lighting/mood: deep navy shadows, subtle warm copper highlights on the brass and book spines, soft museum lighting, serious and timeless.
Color palette: PRIMATIS navy #082B40, copper #B87333 and #D7A16E, muted ivory highlights only.
Constraints: background image only; no typography; no signs; no labels; no logos; no watermark; no embedded interface; no readable book titles; no purple neon; no fantasy magic effects; preserve enough dark contrast for white HTML text overlays.
```

## Transformations

Les variantes runtime résultent uniquement de recadrages centrés, redimensionnements, suppression de métadonnées et compression WebP. Aucune seconde génération ni retouche sémantique n'a été appliquée.

Le master est une illustration générée. Il ne doit pas être décrit comme la photographie d'une bibliothèque ou d'un objet historique réel.
