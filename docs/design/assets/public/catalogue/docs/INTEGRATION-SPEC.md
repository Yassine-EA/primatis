# Contrat d'intégration — Catalogue

## Hero responsive

| viewport | fichier |
|---|---|
| ≥ 1280 px | `catalogue-hero-1920.webp` |
| 768–1279 px | `catalogue-hero-1280.webp` |
| < 768 px | `catalogue-hero-768-mobile.webp` |

```html
<picture class="catalogue-hero__background" aria-hidden="true">
  <source media="(max-width: 767px)" srcset="/assets/public/catalogue/hero/catalogue-hero-768-mobile.webp">
  <source media="(max-width: 1279px)" srcset="/assets/public/catalogue/hero/catalogue-hero-1280.webp">
  <img src="/assets/public/catalogue/hero/catalogue-hero-1920.webp" alt="">
</picture>
```

Le fond utilise `object-fit: cover` et `object-position: 50% 50%`. Titre, sous-titre et citation restent en HTML. Sur mobile, la citation peut être masquée si la densité devient excessive, mais le titre et la recherche restent accessibles.

## Overlay

```scss
.catalogue-hero::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgb(3 27 43 / 76%) 0%, rgb(3 27 43 / 12%) 42%, rgb(3 27 43 / 22%) 72%, rgb(3 27 43 / 62%) 100%);
  pointer-events: none;
}
```

## Recherche et résultats

- La barre de recherche est un composant HTML/PrimeNG, jamais une image.
- Les couvertures utilisent exclusivement `coverImageUrl`, avec le fallback global si absent.
- Les cartes et la vue liste utilisent les mêmes données ; aucune couverture spécifique n'est produite ici.
- Les filtres et compteurs doivent refléter l'API réelle. Les chiffres de la maquette ne sont que des exemples.
- Ne pas rendre interactive une icône de favori sans fonctionnalité réelle.
- Ne pas afficher de disponibilité publique avant évolution explicite du contrat.

## Destination Design

```text
docs/design/assets/public/catalogue/
```
