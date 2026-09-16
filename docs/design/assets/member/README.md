# PRIMATIS — ASSET-RESET-03 — Espace membre

Pack mutualisé pour les pages Prêts, Réservations, Amendes et Notifications.

## Contenu

- `shared/` : hero membre commun en trois tailles WebP ;
- `docs/` : manifeste, provenance, contrat d’intégration et validation ;
- `preview/` : planche de validation visuelle.

## Dépendances déjà livrées par ASSET-RESET-01

- `assets/fallbacks/cover-compact.svg` ;
- `assets/fallbacks/empty-states/loans.svg` ;
- `assets/fallbacks/empty-states/reservations.svg` ;
- `assets/fallbacks/empty-states/fines.svg` ;
- `assets/fallbacks/empty-states/notifications.svg`.

Ces fichiers ne sont pas dupliqués dans ce pack. Les icônes fonctionnelles courantes utilisent PrimeIcons selon la table d’intégration.

## Règles

- le hero est décoratif et reçoit `alt=""` si rendu avec `<img>` ;
- titres, citations, compteurs et statuts restent en HTML ;
- aucune couverture spécifique n’est inventée à partir d’un simple `titleId` ;
- aucun indicateur métier absent des DTO n’est simulé ;
- couleur et icône ne remplacent jamais un libellé de statut.

```text
ASSET-RESET-03 — APPROVED — TECHNICAL AND OWNER VISUAL VALIDATION PASSED
```
