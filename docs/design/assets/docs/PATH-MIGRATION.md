# PRIMATIS — Migration vers l’arborescence canonique

## Déplacement structurel

| ancien emplacement | nouvel emplacement |
|---|---|
| `assets/home/` | `assets/public/home/` |
| assets Catalogue isolés | `assets/public/catalogue/` |
| assets Détail livre isolés | `assets/public/title-detail/` |
| assets Articles isolés | `assets/public/articles/` |
| assets Georges Lemaître isolés | `assets/public/georges-lemaitre/` |
| assets Login isolés | `assets/public/login/` |
| socle membre | `assets/member/` |
| socle de gestion | `assets/staff-admin/` |

## Ressources mutualisées

Les portraits, fallbacks éditoriaux et bandeaux dupliqués passent dans `assets/shared/`. Les anciens chemins de page ne doivent plus être utilisés dans Angular ou SCSS.

## Règle d’intégration

La structure de ce pack est l’autorité. Ne pas recopier parallèlement les anciens dossiers : cela réintroduirait les doublons supprimés par le Final Gate.
