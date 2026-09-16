# Règles d'iconographie PRIMATIS

## Deux familles seulement

| Famille | Usage | Source |
|---|---|---|
| PRIMATIS custom | identité, accès rapides publics, illustrations éditoriales, motifs | `icons/editorial/`, `brand/`, `patterns/` |
| PrimeIcons | navigation fonctionnelle, formulaires, tableaux, filtres, CRUD, statuts, notifications métier | dépendance PrimeNG/PrimeIcons du frontend |

## Règles

- Ne pas redessiner en SVG custom une action déjà correctement couverte par PrimeIcons.
- Ne pas mélanger une icône custom et PrimeIcons à l'intérieur d'un même groupe d'actions.
- Les pictogrammes custom utilisent un `viewBox` de `24 24`, `currentColor`, un trait de `1.8`, des extrémités arrondies et aucune couleur codée en dur.
- Une icône seule dans un bouton reçoit un nom accessible (`aria-label`) et, si utile, un tooltip.
- Une icône décorative reçoit `aria-hidden="true"` et ne remplace jamais un libellé nécessaire.
- Un statut conserve toujours son texte ; couleur et pictogramme restent secondaires.
- Les fichiers de `social-disabled/` ne sont pas intégrables avant validation des URL officielles.

## Correspondances recommandées

| Besoin | Fichier custom ou PrimeIcon |
|---|---|
| Accueil public | `home.svg` |
| Catalogue public | `catalogue-book.svg` |
| Articles publics | `articles-document.svg` |
| Recherche hero | `search.svg` |
| Compte public | `account.svg` |
| Menu / fermeture mobile | `menu.svg` / `close.svg` |
| CRUD, tri, filtres, formulaires | PrimeIcons |
| États métier | PrimeIcons + libellé textuel |
