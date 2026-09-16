# Points d'entrée et CLI — data-seeding

[Retour au rapport général](./general.md)

Aucune commande n'est installée par `pip install -e .` (`pyproject.toml` ne déclare pas de `[project.scripts]`). Tout point d'entrée s'invoque via `python -m <module>` (répertoire d'exécution : `data-seeding/`, environnement virtuel activé) ou en exécutant directement un fichier de `scripts/`.

## Table des matières

- [1. `main.py` — bootstrap](#1-mainpy--bootstrap)
- [2. `pipeline.acquisition` — acquisition small/medium](#2-pipelineacquisition--acquisition-smallmedium)
- [3. `pipeline.bundle` — bundle small/medium](#3-pipelinebundle--bundle-smallmedium)
- [4. `load.cli` — CHECK/APPLY catalogue](#4-loadcli--checkapply-catalogue)
- [5. `load.users_scenarios_cli` — CHECK/APPLY users+scénarios](#5-loadusers_scenarios_cli--checkapply-usersscénarios)
- [6. `acquisition.openlibrary_covers` — matérialisation manuelle de covers](#6-acquisitionopenlibrary_covers--matérialisation-manuelle-de-covers)
- [7. `audit.completeness` — audit de complétude](#7-auditcompleteness--audit-de-complétude)
- [8. `scripts/dev164_build_large.py`](#8-scriptsdev164_build_largepy)
- [9. `scripts/dev165_build_full.py`](#9-scriptsdev165_build_fullpy)
- [10. `scripts/dev1651_build_full_scenarios.py`](#10-scriptsdev1651_build_full_scenariospy)
- [11. Points d'entrée sans CLI (fonctions de bibliothèque uniquement)](#11-points-dentrée-sans-cli-fonctions-de-bibliothèque-uniquement)

## 1. `main.py` — bootstrap

```bash
python -m primatis_data_seeding.main --profile small [--database primatis_dev]
```

- **Entrées** : `config/profiles.toml` (chemin résolu en dur relatif au fichier).
- **Effets** : valide uniquement que le profil existe et que la base demandée correspond au garde-fou (`guard.py::validate_target`). **Ne charge aucune donnée** (message explicite : *« Bootstrap only: PostgreSQL loading is not implemented in DEV-13.3. »*).
- **Sorties** : une ligne `stdout` récapitulative.

## 2. `pipeline.acquisition` — acquisition small/medium

```bash
python -m primatis_data_seeding.pipeline.acquisition \
  --profile {small,medium} \
  --bpost-xlsx <chemin .xls/.xlsx officiel> \
  [--project-root .] \
  [--refresh-openlibrary] \
  [--authors-dump <dump bulk Authors .txt/.txt.gz>] [--refresh-authors] \
  [--fetch-work-summaries] [--refresh-works] \
  [--fetch-edition-details] [--refresh-editions] \
  [--fetch-wikidata] [--refresh-wikidata]
```

- **Variable d'environnement requise** : `PRIMATIS_OPENLIBRARY_CONTACT` (email, obligatoire dès qu'un appel réseau réel est possible).
- **Fichiers lus** : `data/raw/openlibrary/<profile>/search_<lang>.json` (si déjà présents, réutilisés sans appel réseau), le fichier Bpost fourni.
- **Fichiers générés** : `data/raw/openlibrary/<profile>/manifest.json`, `data/raw/bpost/<sha256>.xlsx`, `data/raw/bpost/provenance.json`, `data/validated/<profile>/openlibrary_selected.jsonl`, `bpost_localities.csv`, `acquisition_report.json`, et selon les options : `authors_selected.jsonl`, `works_selected.jsonl`, `editions_selected.jsonl`, `wikidata_authors_selected.jsonl`, `wikidata_countries_selected.jsonl`.
- **Idempotence** : un snapshot Open Library complet déjà présent est réutilisé (`has_complete_snapshot`) — aucun nouvel appel réseau sans `--refresh-openlibrary`.

## 3. `pipeline.bundle` — bundle small/medium

```bash
python -m primatis_data_seeding.pipeline.bundle \
  --profile {small,medium} --reference-date YYYY-MM-DD \
  [--project-root .] [--seed 13014] \
  [--selected-jsonl ...] [--bpost-csv ...] \
  [--authors-snapshot ...] [--work-snapshot ...] [--edition-snapshot ...] \
  [--covers-assets-dir ...] \
  [--wikidata-authors-snapshot ...] [--wikidata-countries-snapshot ...] \
  [--output-dir ...]
```

- **Variable d'environnement requise** : `PRIMATIS_SEED_USER_PASSWORD` (≥ 12 caractères, jamais loggé).
- **Entrées par défaut** : `data/validated/<profile>/openlibrary_selected.jsonl`, `bpost_localities.csv`.
- **Sorties** : `data/bundles/<profile>/{authors,genres,titles,title_authors,title_genres,copies,users,addresses,residences,bpost_localities,loans,reservations,fines,notifications,copy_states}.csv`, `bundle_report.json`, `deduplication_report.json`.
- **Effets** : échoue explicitement (`ValueError`/`NotImplementedError`) si les comptes ne correspondent pas exactement au profil (`title_target`, distribution de Copies) — jamais de padding silencieux.
- **Limite documentée** : `choices=("small", "medium")` — ce point d'entrée ne construit jamais `large`/`full` (ces profils passent par `large_build.py`/`full_build.py`).

## 4. `load.cli` — CHECK/APPLY catalogue

```bash
python -m primatis_data_seeding.load.cli \
  --profile {small,medium,large,full} \
  --export-dir data/bundles/<profile> \
  [--database <nom>] \
  [--apply --confirm-database <nom exact>]
```

- **Connexion** : `psycopg.connect("")` (chaîne vide → variables d'environnement standard libpq : `PGHOST`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, ...). Aucun mot de passe n'est en dur dans le code.
- **Mode par défaut : CHECK** (lecture seule — les tables `TEMP ... ON COMMIT DROP` disparaissent à la fin de la transaction, aucune écriture persistante).
- **Mode APPLY** : requiert `--apply` **et** `--confirm-database` strictement égal au nom de la base réellement connectée (`load/guard.py::require_apply_confirmation`), sans quoi `ValueError`.
- **Garde-fous** : `expected_database()` interdit toute combinaison profil/DB hors de `{small,medium,large}→primatis_dev, full→primatis_preview` ; `primatis_test` est refusé sans condition (`FORBIDDEN_DATABASES`).
- **Sortie** : une ligne `stdout` (`mode=... database=... authors=... titles=... copies=...`).

## 5. `load.users_scenarios_cli` — CHECK/APPLY users+scénarios

```bash
python -m primatis_data_seeding.load.users_scenarios_cli \
  --profile full --export-dir data/bundles/full \
  [--database primatis_preview] \
  [--apply --confirm-database primatis_preview]
```

- **Restriction** : `choices=("full",)` — ce chargeur n'est câblé que pour le profil `full` (aucun scénario métier n'est généré/chargé pour small/medium/large dans ce point d'entrée).
- **Garde-fou renforcé** : contrairement à `load/postgres.py::load_catalogue_export` (qui ouvre lui-même la connexion), ce module ouvre la connexion **avant** d'appeler `load_users_and_scenarios()` et revalide `_live_database(conn)` contre le profil demandé — corrige un point faible documenté dans son propre docstring (« `load/users_scenarios.py` [...] ne valide jamais [seul] quelle base il est réellement connecté »).

## 6. `acquisition.openlibrary_covers` — matérialisation manuelle de covers

```bash
python -m primatis_data_seeding.acquisition.openlibrary_covers \
  (--cover-ids 258027,258028 | --cover-ids-file ids.txt) \
  --contact <email> \
  [--assets-dir primatis-web/public/covers/catalogue] [--overwrite]
```

- **Conception explicite** : ne matérialise **jamais** automatiquement tout un bundle/profil — l'appelant doit énumérer chaque `cover_id` (docstring : *« this tool never crawls a full bundle/profile automatically »*).
- **Sortie** : un fichier JPEG par `cover_id`, nommé `ol-cover-<id>.jpg`, sous `--assets-dir` (par défaut `primatis-web/public/covers/catalogue`, **hors** de `data-seeding/`).
- **Cible réelle en génération 2** : `pipeline/full_build.py` a sa propre logique de matérialisation en masse, indépendante de ce CLI (voir [11-covers.md](./11-covers.md)).

## 7. `audit.completeness` — audit de complétude

```bash
python -m primatis_data_seeding.audit.completeness \
  --profile medium [--project-root .] \
  [--covers-assets-dir <dossier de covers déjà matérialisées>] \
  [--output data/validated/medium/completeness_audit.csv]
```

- **Lecture seule** (docstring : *« performs no network access and no PostgreSQL access »*).
- **Entrées** : les snapshots déjà acquis sous `data/validated/<profile>/` (`openlibrary_selected.jsonl`, `authors_selected.jsonl`, `works_selected.jsonl`, `editions_selected.jsonl`, `wikidata_*_selected.jsonl` — chacun optionnel, absent = simplement ignoré).
- **Sortie** : `completeness_audit.csv` (une ligne par (Title|Author, champ) — 100 % des enregistrements couverts, y compris ceux qui restent NULL, avec un statut explicite : `PRESENT`, `ENRICHED_FROM_SOURCE`, `SOURCE_CONFIRMED_ABSENT`, `SOURCE_RECORD_MISSING`, `SOURCE_VALUE_INVALID`, `AMBIGUOUS_NOT_USED`, `OUT_OF_SCOPE_BY_POLICY`).
- **Exécuté pour `medium`** (fichier présent : `data/validated/medium/completeness_audit.csv`) ; **jamais exécuté pour `full`** (aucun fichier `data/validated/full/completeness_audit.csv` — voir [18](./18-audits-et-rapports-existants.md)).

## 8. `scripts/dev164_build_large.py`

Script one-shot (pas de `argparse`), documenté dans son propre docstring comme *« Not a permanent CLI [...] a one-shot, documented script run manually during DEV-16.4, kept for reproducibility/traceability »*.

```bash
python scripts/dev164_build_large.py
```

- Définit `BATCHES` (29 lots `BatchCriteria`, langue × catégorie documentaire) et `PLAN` (`LargeBuildPlan(profile="large", target_titles=5000, ...)`).
- Appelle `build_large_bundle(...)` avec un `payload_fetcher` résilient (`RateLimiter(0.3)` + `with_retries(max_attempts=6, backoff_seconds=8.0)`).
- **Sorties** : `data/validated/batches/<batch_id>/` (un par lot) et `data/bundles/large/*.csv` + `large_build_report.json`.

## 9. `scripts/dev165_build_full.py`

```bash
python scripts/dev165_build_full.py [--with-enrichment] [--with-covers] [--max-covers N]
```

- `--with-enrichment` : charge `data/validated/full/authors_selected.jsonl` et `works_selected.jsonl` (dumps bulk déjà extraits) pour enrichir biographie/dates/nationalité/résumé.
- `--with-covers` : active le téléchargement réel de covers vers `data/covers/full/`, avec limite optionnelle `--max-covers`.
- Définit `BATCHES` (46 lots) et `PLAN` (`FullBuildPlan(profile="full", target_titles=15000, ...)`).
- **Sorties** : `data/bundles/full/*.csv` + `full_build_report.json`.

## 10. `scripts/dev1651_build_full_scenarios.py`

```bash
PRIMATIS_SEED_USER_PASSWORD=... python scripts/dev1651_build_full_scenarios.py
```

- **Connexion PostgreSQL en lecture seule** pour lire les 5 réglages métier (`LOAN_DURATION_DAYS`, `RESERVATION_READY_HOLD_HOURS`, `LOAN_DUE_SOON_DAYS`, `FINE_WEEKLY_RATE`, `FINE_MAX_AMOUNT`) directement dans `application_setting` de `primatis_preview` (`psycopg.connect("")`, chaîne vide) — **jamais codés en dur** dans le script.
- Relit `data/bundles/full/copies.csv` **sans jamais reconstruire le catalogue**.
- **Sorties** : `data/bundles/full/{users,addresses,residences,loans,reservations,fines,notifications,copy_states,bpost_localities}.csv`, `scenarios_build_report.json`.

## 11. Points d'entrée sans CLI (fonctions de bibliothèque uniquement)

`pipeline/batch.py::run_batch()` / `acquire_batch_candidates()`, `pipeline/large_build.py::build_large_bundle()`, `pipeline/full_build.py::build_full_bundle()` et `pipeline/full_scenarios.py::build_full_scenarios()` ne définissent **aucun** `argparse` — ce sont des fonctions Python appelées depuis un script (`scripts/dev16*`) ou directement depuis un test. C'est un choix explicite documenté (docstring `batch.py` : *« one (profile, language, documentary category, target count) selection »*), pas un oubli — la README illustre l'appel direct en Python (extrait de `README.md` §« Pipeline par lots »).

## Rapports liés

- [Architecture générale](./01-architecture.md)
- [Configuration](./04-configuration.md)
- [Chargement en base de données](./16-chargement-base-donnees.md)
- [Commandes utiles](./21-commandes-utiles.md)
