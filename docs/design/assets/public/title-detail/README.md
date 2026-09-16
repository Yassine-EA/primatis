# PRIMATIS — ASSET-RESET-02.3 — Détail d’un livre

Pack spécifique à la page publique de détail d’un titre, produit depuis `docs/design/public/title-detail.png`.

## Contenu

- `hero/` : deux crops WebP pour desktop et tablette ;
- `docs/` : manifeste, provenance et contrat d’intégration ;
- `preview/` : planche de validation visuelle.

## Dépendances non dupliquées

- couverture réelle : `TitleResponse.coverImageUrl` ;
- couverture absente : ASSET-RESET-01 `fallbacks/cover.svg` ;
- icônes de métadonnées et d’actions : PrimeIcons / socle global ;
- logo, navigation et footer : socle global et pack Home.

## Règles métier

- aucune disponibilité ou localisation inventée ;
- aucune recommandation ou ressource associée sans contrat réel ;
- aucun texte incorporé dans les images ;
- le hero est masqué en mobile conformément à la maquette.

```text
ASSET-RESET-02.3 — PASS FINAL — APPROVED
```
