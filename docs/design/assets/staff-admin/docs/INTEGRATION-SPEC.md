# PRIMATIS — Contrat d’intégration — Staff/Admin

## Destination

```text
docs/design/assets/staff-admin/
```

## Sidebar

La sidebar utilise un fond navy CSS et place le décor WebP en bas :

- ≥ 1280 px : `shell/staff-sidebar-observatory-480.webp` ;
- 769–1279 px : `shell/staff-sidebar-observatory-320.webp` ;
- ≤ 768 px : sidebar masquée au profit de la navigation mobile ; aucun raster chargé.

Le décor est purement décoratif. Le logo clair provient d’ASSET-RESET-01 et reste une image informative avec un nom accessible.

## État vide

`empty-states/management-empty.svg` convient aux listes et tableaux Staff/Admin sans résultat. Le titre et l’explication restent en HTML. Adapter le texte au domaine réel ; ne pas modifier l’illustration pour simuler une fonctionnalité.

## Avatar

Utiliser un composant CSS d’initiales, calculées depuis le nom réellement disponible. Prévoir un nom accessible. Aucune image de personne générique ou générée.

## Données et graphiques

- aucune courbe ou jauge n’est fournie comme raster ;
- un graphique n’existe que si une API réelle fournit les valeurs ;
- tableau et liste restent utilisables sans vignette ;
- `coverImageUrl` est utilisé seulement lorsque le Title correspondant est réellement chargé.

## Fonctions interdites à simuler

- dashboard statistique sans contrat réel ;
- photos utilisateurs ;
- groupes, fuseau, notes ou préférences absents du DTO ;
- image de couverture Article absente du modèle ;
- Rapports avancés hors V1 ;
- upload de logo non supporté ;
- notifications Staff non supportées.
