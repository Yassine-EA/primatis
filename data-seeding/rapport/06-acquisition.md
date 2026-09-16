# Acquisition — data-seeding

[Retour au rapport général](./general.md)

## 1. Deux mécanismes d'acquisition distincts

```text
MÉCANISME A — quotas globaux fixes (small/medium)
  acquisition/openlibrary.py::acquire_openlibrary()
  1 requête Search API par langue, quota fixe par langue

MÉCANISME B — lots (batch), langue × catégorie documentaire (large/full)
  pipeline/batch.py::acquire_batch_candidates()
  N requêtes Search API par lot, quota fixe par lot, agrégées sur
  plusieurs lots pour atteindre le total du profil
```

Le mécanisme A ne construit jamais `large`/`full` ; le mécanisme B ne redéfinit jamais `small`/`medium` — les deux coexistent sans jamais se substituer l'un à l'autre (confirmé par les `choices` d'argparse et l'absence d'import croisé).

## 2. Mécanisme A — quotas globaux (small/medium)

### 2.1 Construction de la recherche

```python
q = f"language:{language_code}"   # ex. "language:fre"
fields = "key,title,author_name,author_key,subject,cover_i,editions,
          editions.key,editions.title,...,editions.number_of_pages"
sort = "key"
limit = min(1000, max(quota * 4, quota + 20))   # surcollecte (overfetch_factor=4)
```

### 2.2 Critères, quotas, langues

| Langue | small | medium |
|---|---|---|
| FR | 75 | 750 |
| EN | 10 | 100 |
| NL | 8 | 80 |
| DE | 3 | 30 |
| ES | 2 | 20 |
| IT | 1 | 15 |
| LA | 1 | 5 |

Ces proportions (~75 % FR) sont réutilisées telles quelles comme référence pour dimensionner `large`/`full` (voir [14-profils-et-volumetrie.md](./14-profils-et-volumetrie.md)).

### 2.3 Sélection

`select_candidates()` — pour chaque langue, parcourt les `docs` renvoyés, extrait un candidat via `_extract_candidate()` (rejette si : pas de `work_key`, pas d'`edition_key` commençant par `/books/`, pas de `title`, langue de l'édition ne contient pas le code demandé, 0 auteur apparié nom+clé), déduplique à la volée par `edition_key` **et** par tout ISBN valide déjà vu (toutes langues confondues), s'arrête au quota exact. **Échoue explicitement** (`ValueError`) si le quota n'est pas atteint — jamais de résultat partiel silencieux.

### 2.4 Exclusions

- Édition sans auteur apparié (nom **et** clé requis, appariés positionnellement, tronqués au plus petit des deux tableaux).
- Édition dont l'`edition_key` ou un ISBN valide a déjà été retenu (même langue ou langue différente).
- Toute langue de l'édition qui ne contient pas le code exact demandé (`languages` est un ensemble, pas une correspondance floue).

### 2.5 Erreurs et retries

Mécanisme A : **aucun retry, aucun throttle** par défaut (`sleep_seconds=0.4` entre langues seulement, pas entre tentatives). Une erreur réseau fait échouer tout l'appel (`urlopen` propage l'exception).

## 3. Mécanisme B — lots (`pipeline/batch.py`, DEV-16.3/16.4)

### 3.1 Construction de la recherche

Requête générique :

```text
q = "language:<code> subject:<mot-clé catégorie documentaire>"
```

Requête ciblée « socle éditorial » (si `use_editorial_socle=True`), une par auteur du socle, **prioritaire** dans le plan (`build_query_plan` les place en premier) :

```text
q = "author_key:<clé OL vérifiée> language:<code>"
```

9 catégories documentaires (`DOCUMENTARY_CATEGORY_KEYWORDS`) : `literature→fiction`, `youth→juvenile`, `history→history`, `science→science`, `philosophy_religion→philosophy`, `social_sciences→social sciences`, `arts→art`, `comics→comics`, `documentary→travel`.

### 3.2 Bug historique corrigé (DEV-16.4 §4.1)

Le module documente lui-même que la version DEV-16.3 de `run_batch()` définissait `build_batch_search_url()` (avec filtre `subject:`) mais **ne l'appelait jamais** — le `payload_fetcher` par défaut construisait sa propre URL (langue seule, sans catégorie). Les deux lots pilotes DEV-16.3 ont donc été filtrés par langue uniquement. Corrigé en DEV-16.4 ; documenté sans dissimulation dans le docstring du module.

### 3.3 Sélection, exclusions

Identique en substance au mécanisme A (`select_batch_candidates`), mais l'état de déduplication (`seen_editions`, `seen_valid_isbns`) est **partagé entre tous les lots** d'un même plan (`already_seen_editions`/`already_seen_valid_isbns` passés en paramètre, mutés en place) — un même livre ne peut jamais être compté deux fois même s'il apparaît dans deux lots différents (ex. FR/littérature et FR/histoire).

### 3.4 Erreurs, retries, résultats réels mesurés

`with_retries(fetch_batch_payload, max_attempts=3..10, backoff_seconds=5..10)` selon le script. Rendements réels très hétérogènes, mesurés et documentés (pas supposés) :

```text
Large (2026-09-11), FR/literature : demandé 1800 → reçu 390 (net après dédup)
Large, FR/history                 : demandé 1200 → reçu 867
Large, FR/science                 : demandé  840 → reçu 447
Large, FR/arts                    : demandé  600 → reçu  51  (pool quasi épuisé)
Full,  FR/literature (round 2)    : demandé 7000 → reçu  484 net contribué
Full,  FR/comics                  : demandé 1800 → reçu  903
```

(Sources : `data/bundles/large/large_build_report.json`, `data/bundles/full/full_build_report.json`, commentaires de `scripts/dev165_build_full.py`.) La chute de rendement est documentée comme un phénomène réel de recouvrement inter-catégories (les mêmes classiques français ressortent sous plusieurs filtres `subject:`), pas une hypothèse — cf. `pipeline/full_build.py`/scripts docstrings citant des taux de rendement mesurés 8,5 %–88 % selon la catégorie.

### 3.5 Résultat agrégé réel (Full)

```text
source_records_received        198 801   (docs Search API bruts, tous lots)
unique_candidates_inter_batch   15 187   (après filtrage + dédup inter-lots)
normalized                      15 187
validated (après dédup catalogue) 15 187   (0 perdu à cette étape — merged_losers=0)
rejected (map_catalogue)             0
quarantined (text quality)          16
accepted_pre_trim                15 171
trimmed (surplus déterministe)      171
accepted_final                   15 000   (= target_titles exact)
```

(Source : `data/bundles/full/full_build_report.json`, bloc `funnel`.)

## Rapports liés

- [Sources de données](./05-sources-de-donnees.md)
- [Normalisation et validation](./07-normalisation-et-validation.md)
- [Déduplication](./13-deduplication.md)
- [Flux complet](./22-flux-complet.md)
