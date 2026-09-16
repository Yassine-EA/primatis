# PRIMATIS — Installation du dossier consolidé

Depuis `primatis/docs/design`, conserver une sauvegarde récupérable de l’ancien dossier avant remplacement. Les six sous-dossiers actuellement placés dans `public/` sont d’anciens packs d’assets : seules les maquettes PNG restent dans `public/`.

```bash
test ! -e asset-reset-backup
mkdir -p asset-reset-backup/misplaced-public-packs
mv public/articles public/catalogue public/georges-lemaitre public/home public/login public/title-detail asset-reset-backup/misplaced-public-packs/
mv assets asset-reset-backup/assets-before-reset
mkdir -p /tmp/primatis-assets-final-20260909
unzip ~/Téléchargements/PRIMATIS-ASSET-RESET-05-FINAL.zip -d /tmp/primatis-assets-final-20260909
cp -a /tmp/primatis-assets-final-20260909/asset-reset-05-final/assets ./assets
```

Vérification :

```bash
test -f assets/docs/ASSET-RESET-05-FINAL-GATE.md
find assets -type f | wc -l
```

Le nombre attendu est documenté dans le Final Gate. Les fichiers `public/*.png`, `member/*.png` et `staff-admin/*.png` restent à leur place : ce sont les maquettes. Ne supprimer la sauvegarde qu’après vérification du dépôt et de l’intégration future.
