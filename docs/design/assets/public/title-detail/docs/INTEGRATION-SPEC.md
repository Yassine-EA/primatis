# Contrat d’intégration — Détail d’un livre

## Destination documentaire

```text
docs/design/assets/public/title-detail/
```

## Hero

- ≥ 1440 px : `hero/title-detail-hero-1920.webp`
- 769–1439 px : `hero/title-detail-hero-1280.webp`
- ≤ 768 px : aucun hero raster ; masquer la bannière comme dans la maquette mobile

Le titre, le sous-titre, le fil d’Ariane et la citation restent du HTML. L’image est décorative : `alt=""` si elle est rendue via `img`, ou background CSS.

Exemple :

```css
.title-detail-hero {
  background:
    linear-gradient(90deg, rgba(3, 25, 47, 0.18), rgba(3, 25, 47, 0.04)),
    url('/assets/public/title-detail/hero/title-detail-hero-1920.webp')
      center / cover no-repeat;
}

@media (max-width: 1439px) {
  .title-detail-hero {
    background-image:
      linear-gradient(90deg, rgba(3, 25, 47, 0.18), rgba(3, 25, 47, 0.04)),
      url('/assets/public/title-detail/hero/title-detail-hero-1280.webp');
  }
}

@media (max-width: 768px) {
  .title-detail-hero {
    display: none;
  }
}
```

## Couverture

Utiliser exclusivement `coverImageUrl`. En absence d’URL, utiliser le fallback global. Ne jamais intégrer la couverture fictive visible dans la maquette.

## Blocs conditionnels

Ne pas intégrer « Disponible », « Où trouver ce livre ? », « Ressources associées » ou « Vous aimerez aussi » tant que les données correspondantes ne sont pas réellement disponibles.
