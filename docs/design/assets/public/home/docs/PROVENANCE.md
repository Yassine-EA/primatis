# Provenance des sources Home

Les six sources sont des fichiers fournis dans le dossier Design PRIMATIS. Leur README les décrit comme des créations visuelles générées pour la maquette. Aucun contenu tiers identifiable n'a été ajouté pendant ASSET-RESET-02.1.

| id | source Design | SHA-256 | nature | dérivés |
|---|---|---|---|---|
| SRC-001 | `source/hero/hero-observatory-milky-way.png` | `ede02af8c7caf6ed6c31b1e41c93953b8a10477c2fa556afe0283f1d4d7b7ce1` | scène générée observatoire/ciel | fonds hero et footer |
| SRC-002 | `source/hero/georges-lemaitre-cutout.png` | `968adf9904cd1234f5abdb8e8cf90f580855b5d567b64ef5f35feefaa4f1beb5` | représentation générée détourée d'une personne réelle | portraits hero |
| SRC-003 | `source/cards/article-origins-universe.png` | `3328fcffb8cb6545280b3d8a430ae6f20d01e92984864d326e9633382b84bb99` | scène cosmologique générée | fallbacks cosmos |
| SRC-004 | `source/cards/article-lemaitre-library.png` | `eddb15228b61c79d63c086ef893ca4f8ed010d89b185ff63c790b8ab06da5323` | scène de bibliothèque générée | crop générique bibliothèque, sans visage |
| SRC-005 | `source/cards/article-night-observatory.png` | `8dfb22c8a22925face3edb01b0c4e492b3905669a6c7a9c32702d36aede82c70` | scène nocturne générée | fallbacks observatoire |
| SRC-006 | `source/editorial/lemaitre-editorial-light.png` | `354338b0b9b81f568c33d00e23f17710fdf20b274089944f55cbd9063158a3db` | composition éditoriale générée | bandeaux Lemaître |

## Traitements appliqués

Uniquement des opérations déterministes : recadrage, redimensionnement, miroir du crop footer, suppression des métadonnées et compression WebP. **Aucune nouvelle génération d'image ni retouche sémantique** n'a été effectuée pendant cette phase.

## Règles de présentation

- SRC-002 et SRC-006 sont des illustrations et non des archives photographiques.
- Les observatoires et paysages générés sont décoratifs ; ils ne doivent pas être présentés comme une photographie de la Bibliothèque Georges Lemaître ou d'un lieu réel précis.
- Les fallbacks Article reçoivent un `alt=""` lorsque le titre et le résumé de l'Article sont déjà présents dans le DOM.
- Toute future photographie authentique doit recevoir une source, un auteur et une licence distincts dans le manifeste.
