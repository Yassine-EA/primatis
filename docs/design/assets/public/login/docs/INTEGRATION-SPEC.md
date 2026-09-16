# Contrat d’intégration — Login

## Destination

```text
docs/design/assets/public/login/
```

## Background

- ≥ 1600 px : `login-background-1920.webp`
- 769–1599 px : `login-background-1440.webp`
- ≤ 768 px : `login-background-768-mobile.webp`

Le fond est décoratif. Les textes et la citation restent du HTML.

## Portrait

- desktop large : `/assets/shared/portraits/georges-lemaitre-1024.webp`
- desktop compact/tablette : `/assets/shared/portraits/georges-lemaitre-768.webp`
- mobile : portrait masqué, conformément à la maquette

Le portrait reste séparé du fond.

## Formulaire

- `email.svg` : décor d’input ;
- `lock.svg` : décor d’input ;
- `eye.svg` / `eye-off.svg` : bouton réel d’affichage du mot de passe, avec `aria-label` dynamique ;
- `shield-lock.svg` : information de sécurité ;
- `settings.svg` : bénéfice desktop.

Les icônes d’input sont décoratives si le label textuel est présent. Le bouton œil doit rester accessible au clavier.

## Bénéfices

Réutiliser les icônes livre et utilisateurs du socle global. Afficher uniquement des bénéfices correspondant réellement au produit.

## Interdictions

- ne pas afficher « Mot de passe oublié ? » tant que le parcours n’existe pas ;
- ne pas inventer de route, de support ou de coordonnées ;
- ne pas cuire les textes dans le fond ;
- ne pas présenter le portrait comme une photographie d’archive authentifiée.
