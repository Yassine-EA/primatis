# Contrat d’intégration — Articles

## Destination documentaire

```text
docs/design/assets/public/articles/
```

## Hero

- ≥ 1440 px : `hero/articles-hero-1920.webp`
- 769–1439 px : `hero/articles-hero-1280.webp`
- ≤ 768 px : `hero/articles-hero-768-mobile.webp`

Titre, sous-titre et citation restent du HTML. Le hero est décoratif.

## Fallbacks de cartes

Les trois familles peuvent être distribuées de manière stable à partir de l’identifiant réel de l’article :

```ts
const fallbackIndex = stableHash(article.id) % 3;
```

Cette sélection doit rester strictement visuelle et déterministe. Elle ne doit jamais prétendre décrire le sujet de l’article. Utiliser `/assets/shared/editorial/editorial-{cosmos|library|observatory}-960.webp` pour une vedette large et les variantes 640 px pour les cartes.

## Contenu autorisé

- liste PUBLISHED réelle ;
- titre, résumé, date et auteur réellement exposés ;
- liste « derniers articles » dérivée de la réponse réelle déjà chargée.

## Contenu interdit

- catégories et compteurs fictifs ;
- tags non exposés dans le résumé public ;
- images présentées comme spécifiques à l’article ;
- sidebar ou filtres alimentés par des valeurs codées en dur.

## Bandeau Lemaître

Le portrait est décoratif et éditorial, avec texte HTML séparé. Utiliser `/assets/shared/editorial/lemaitre-observatory-banner-1920.webp` et sa variante `960-mobile` sous 768 px.
