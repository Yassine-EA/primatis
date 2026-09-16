# Auteurs — data-seeding

[Retour au rapport général](./general.md)

## 1. Génération et identification

Un `Author` est toujours dérivé d'un `author_key` Open Library réel — jamais synthétisé. Chaque édition sélectionnée porte un ou plusieurs `author_keys` (Search API, champ `author_key`, apparié positionnellement à `author_name`). Deux représentations coexistent et sont réconciliées explicitement (`canonical_author_key()`, `normalization/openlibrary.py`) :

```text
Search API                : "OL1098039A"           (forme nue)
Dump bulk Authors (`key`) : "/authors/OL1098039A"   (forme préfixée)
```

Seul le strip exact du préfixe `/authors/`, suivi d'une validation par regex `^OL\d+A$`, est utilisé — jamais un split générique sur `/`, jamais une correspondance par nom.

## 2. Relation Title/Author

`title_authors.csv` (table d'association `title_author`) — une ligne par (`title_source_key`, `author_source_key`), dédupliquée (`sorted(set(...))`). Un `Title` sans auteur résolu est **rejeté avant même d'exister** (`TITLE_WITHOUT_AUTHOR` si `author_keys` vide, `UNRESOLVED_AUTHOR_REFERENCE` si une clé référencée n'a pas de record `Author` correspondant dans l'ensemble validé).

## 3. Champs et origine

| Champ | Origine | NULL possible | Détail |
|---|---|---|---|
| `full_name` | dump bulk Authors (`name`), sinon `author_name` de la Search API | non | jamais scindé sur `/` (Open Library concatène parfois des formes alternatives à cet endroit) |
| `birth_date` / `death_date` | dump bulk Authors, format exact uniquement (`normalize_exact_date`) | oui (large majorité — voir §4) | une année seule ou une forme ambiguë reste `NULL` (`AMBIGUOUS_NOT_USED` dans l'audit) |
| `biography` | dump bulk Authors (`bio`), filtrée par `is_confident_french()` | oui (quasi-totalité — voir [07](./07-normalisation-et-validation.md) §3) | seule une biographie **confidemment française** est conservée |
| `nationality` | Wikidata, chaîne exacte `author_key → remote_ids.wikidata → P27 → pays moderne → label anglais` | oui (0,0 % en Full — voir remarque) | jamais dérivée du nom, de la langue ou de la biographie |

**Remarque `nationality` :** le rapport `full_build_report.json` affiche `"nationality_coverage": 0.0` malgré l'existence complète du mécanisme de résolution (`normalization/wikidata.py`, testé par `tests/test_wikidata_normalization.py`/`test_wikidata_acquisition.py`) et malgré `work_enrichment_applied`/`author_enrichment_applied` à `true`. Analysé dans [20-incoherences-et-risques.md](./20-incoherences-et-risques.md) : soit le pipeline Wikidata n'a pas été exécuté pour Full (aucun fichier `data/validated/full/wikidata_*_selected.jsonl` trouvé, contrairement à `medium` qui les possède), soit aucun auteur Full n'a de `remote_ids.wikidata` exact exploitable — les deux hypothèses restent **NON DÉTERMINÉ** faute de log dédié.

## 4. Enrichissement mesuré (medium, seul profil avec un audit de complétude exhaustif)

D'après `data/bundles/medium/bundle_report.json` :

```text
authors_kept              : 768
authors_enriched          :  22   (birth_date, death_date ou biography présent)
authors_with_nationality  :  25
author_candidates         :   7   (AUTHOR_IDENTITY_CANDIDATE, jamais fusionnés)
author_duplicates         : 247   (SAME_SOURCE_KEY — même clé Open Library citée plusieurs fois)
```

## 5. Déduplication

Voir [13-deduplication.md](./13-deduplication.md) — résumé : dédup **exacte** par `source_key` (garde le record avec le plus de dates renseignées), puis détection de **candidats** par empreinte `(nom folded, birth_date, death_date)` — jamais de fusion automatique de deux `author_key` Open Library distincts, même si leur nom et leurs dates coïncident.

## 6. Œuvres anonymes/collectives

Aucun mécanisme dédié trouvé dans le code pour une œuvre sans auteur identifiable autrement que par le rejet `TITLE_WITHOUT_AUTHOR` — data-seeding.md exige une stratégie de traitement des œuvres anonymes/collectives « conforme au modèle PRIMATIS », mais aucune règle métier spécifique (pseudo-auteur « Anonyme », etc.) n'a été retrouvée dans `src/` ni dans les logs DEV consultés. **NON DÉTERMINÉ.**

## Rapports liés

- [Titres](./08-titres.md)
- [Déduplication](./13-deduplication.md)
- [Normalisation et validation](./07-normalisation-et-validation.md)
