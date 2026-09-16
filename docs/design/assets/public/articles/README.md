# PRIMATIS — ASSET-RESET-02.4 — Articles

Pack spécifique à la page publique Articles, produit depuis `docs/design/public/articles.png`. Il complète le socle global et le pack Home sans modifier Angular.

## Contenu

- `hero/` : trois variantes WebP ;
- `../../shared/editorial/` : trois familles de fallbacks et bandeau Georges Lemaître mutualisés ;
- `docs/` : manifeste, provenance et contrat d’intégration ;
- `preview/` : planche de validation visuelle.

## Principe essentiel

Le contrat Article ne contient pas de champ image public. Les visuels de cartes sont donc des fallbacks décoratifs de marque, jamais la représentation prétendument réelle du contenu.

## Interdictions

- aucune catégorie ou quantité inventée ;
- aucune correspondance sémantique artificielle entre un titre et une image ;
- aucun texte incorporé dans les assets ;
- aucun nouveau champ frontend simulant une image backend.

```text
ASSET-RESET-02.4 — PASS FINAL — APPROVED
```
