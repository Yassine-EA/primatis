# PRIMATIS — ASSET-RESET-02.1 — Rapport de validation

**Périmètre :** assets spécifiques Home  
**Intégration Angular :** aucune  
**Sources PRIMATIS modifiées :** aucune

## Contrôles

| Contrôle | Résultat |
|---|---|
| 16 assets runtime attendus | PASS |
| Dimensions conformes au manifeste | PASS |
| Tous les assets runtime en WebP | PASS |
| Transparence portrait conservée | PASS — 3/3 WebP `srgba`, non opaques |
| Hero desktop ≤ 500 Ko | PASS — 86 226 o maximum |
| Hero mobile ≤ 250 Ko | PASS — 63 984 o |
| Carte Article ≤ 180 Ko | PASS — 76 650 o maximum |
| Bandeau/footer ≤ 300 Ko | PASS — 43 904 o maximum |
| Aucun texte incorporé | PASS par inspection des sources |
| Nouvelle génération sémantique | NONE — normalisation déterministe seulement |
| Correspondance globale avec la maquette | PASS — validation propriétaire reçue le 2026-09-08 |
| Poids cumulé des 16 assets runtime | PASS — 786 734 o |
| Doublons binaires runtime | PASS — 0 |

## Gate

Validation visuelle reçue de **Yassine ELABOUBI** le **2026-09-08**.

```text
ASSET-RESET-02.1 — PASS FINAL — APPROVED
```
