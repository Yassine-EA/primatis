# Accessibilité et éléments à produire en CSS

## Alternatives textuelles

- Logo complet : `alt="PRIMATIS — Bibliothèque publique Georges Lemaître"`.
- Symbole avec nom adjacent : `alt=""` ou `aria-hidden="true"`.
- Couverture réelle : `alt="Couverture de {titre}"`.
- Couverture absente : `alt="Couverture indisponible pour {titre}"`.
- Image cassée informative : signaler l'indisponibilité dans le texte voisin.
- État vide : le SVG peut être décoratif si un titre et une explication HTML sont présents.
- Motifs : toujours décoratifs (`alt=""`, `aria-hidden="true"`).

## Avatar neutre

Ne créer aucune image de visage. Utiliser un cercle CSS avec les initiales calculées depuis le nom réel :

```scss
.primatis-avatar {
  display: inline-grid;
  width: 2.5rem;
  aspect-ratio: 1;
  place-items: center;
  border-radius: 50%;
  color: #faf9f6;
  background: #082b40;
  font: 600 0.875rem/1 system-ui, sans-serif;
}
```

Le nom complet reste disponible dans le DOM et sert de nom accessible.

## Couleurs

Les assets utilisent uniquement les couleurs canoniques du Design System v2 : navy `#082B40`, cuivre `#B87333`, cuivre clair `#D7A16E`, ivoire `#FAF9F6` / `#F6F1E8`, gris et couleurs métier documentées.
