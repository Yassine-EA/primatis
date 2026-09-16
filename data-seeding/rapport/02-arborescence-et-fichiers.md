# Arborescence et fichiers — data-seeding

[Retour au rapport général](./general.md)

## Table des matières

- [1. Méthode](#1-méthode)
- [2. Dossiers de premier niveau](#2-dossiers-de-premier-niveau)
- [3. `src/primatis_data_seeding/`](#3-srcprimatis_data_seeding)
- [4. `data/`](#4-data)
- [5. Fichiers critiques](#5-fichiers-critiques)
- [6. Statut Git par zone](#6-statut-git-par-zone)

## 1. Méthode

Le statut (ACTIF/HISTORIQUE/EXPÉRIMENTAL/OBSOLÈTE/INCONNU) est déterminé par recoupement de trois preuves : (a) le code source référence-t-il ce chemin (`grep` sur `src/`, `tests/`, `scripts/`) ; (b) un rapport `.claude/logs/DEV-13*` ou `DEV-16*` en parle-t-il ; (c) le fichier est-il suivi par Git (`git status`, `git ls-files`). Un dossier peut être ACTIF (produit/consommé par le code actuel) sans être commité — les deux dimensions sont indépendantes et documentées séparément (voir [6](#6-statut-git-par-zone)).

## 2. Dossiers de premier niveau

| Chemin | Rôle | Statut |
|---|---|---|
| `src/primatis_data_seeding/` | Code source du package Python | ACTIF |
| `tests/` | Suite de tests (51 fichiers, 506 tests) | ACTIF |
| `scripts/` | 3 scripts one-shot documentés (Large, Full, scénarios Full) | ACTIF (historique de commande, pas des CLI génériques) |
| `config/` | `profiles.toml` (4 profils) | ACTIF |
| `reference/catalogue/` | Socle éditorial par langue (7 fichiers `.toml`) | ACTIF (génération 2) |
| `data/raw/` | Payloads bruts + dumps bulk Open Library/Wikidata/Bpost | ACTIF, gitignored |
| `data/validated/` | JSONL/CSV validés par profil + lots (`batches/`) | ACTIF, gitignored |
| `data/bundles/` | CSV finaux prêts au chargement, par profil | ACTIF, gitignored |
| `data/covers/` | Fichiers image de couverture (2 sous-dossiers, `full/` et `final/`) | MIXTE — voir [11-covers.md](./11-covers.md) |
| `data/cache/` | Caches d'un pipeline non retrouvé dans le code actuel | EXPÉRIMENTAL / INCONNU |
| `data/audit/` | ~230 fichiers CSV/TXT d'un pipeline non retrouvé dans le code actuel | EXPÉRIMENTAL / INCONNU |
| `data/backups/` | 5 dumps `pg_dump -Fc` (`.dump`), horodatés par étape DEV | HISTORIQUE (artefacts de sécurité avant opération destructive) |
| `.pytest_cache/`, `__pycache__/`, `*.egg-info/`, `.venv/` | Artefacts d'exécution locaux | ACTIF (généré), non pertinent pour l'audit fonctionnel |

## 3. `src/primatis_data_seeding/`

```text
src/primatis_data_seeding/
├── __init__.py            (1 ligne)
├── config.py               profils (SeedProfile, load_profiles)
├── guard.py                garde-fou historique profil/DB (superseded par load/guard.py)
├── main.py                 bootstrap CLI (--profile), ne charge rien
├── models.py                NormalizedAuthor / NormalizedEdition / NormalizedPostalLocality
├── acquisition/
│   ├── openlibrary.py            Search API, sélection par quotas linguistiques (small/medium)
│   ├── openlibrary_authors.py    extraction ciblée du dump bulk Authors
│   ├── openlibrary_works.py      extraction ciblée du dump bulk Works
│   ├── openlibrary_details.py    fetch exact par clé (Work/Edition), cache par clé + manifest
│   ├── openlibrary_covers.py     Covers API + CLI de matérialisation manuelle explicite
│   ├── wikidata.py               fetch exact par QID (Author/Country), pour nationality
│   ├── rate_limit.py             RateLimiter séquentiel + with_retries (DEV-16.4)
│   └── provenance.py             sha256_file, archive_source_file (Bpost)
├── normalization/
│   ├── isbn.py                   normalisation + validation ISBN-10/13
│   ├── language.py                mapping code langue -> enum Language (7 langues)
│   ├── language_detection.py     is_confident_french() — biographie uniquement
│   ├── text.py                    normalize_text (NFC + whitespace), truncate_or_none
│   ├── text_quality.py           détection mojibake/contrôle/HTML/espace pathologique
│   ├── openlibrary.py             normalize_*_record (Author/Edition), dates, pagination
│   ├── wikidata.py                résolution nationality (P27 exact, état souverain moderne)
│   ├── bpost.py                   normalize_postal_locality
│   └── cover_validation.py       validation bytes image (dimensions/format/poids)
├── validation/
│   ├── catalogue.py               validate_author / validate_edition
│   ├── postal.py                  (ré-exporté par normalization/bpost.py)
│   └── result.py                  ValidationResult/ValidationIssue génériques
├── deduplication/catalogue.py     deduplicate_authors / deduplicate_editions
├── mapping/
│   ├── catalogue.py                map_catalogue() — pivot Title/Author/Genre
│   ├── genres.py                   taxonomie fixe (26 genres) + alias exacts
│   ├── documentary_categories.py  signal de précision (9 catégories), mesure seule
│   └── models.py                   PrimatisTitleRow / PrimatisAuthorRow / ... / résultats
├── quality/quarantine.py          apply_text_quality_policy() + cascade Author→Title
├── provenance/record_provenance.py  RecordProvenance, FieldDecision, JSONL
├── generation/
│   ├── copies.py                   distribution de Copies + inventoryCode + location
│   ├── users.py                    membres synthétiques (bcrypt, seed déterministe)
│   └── scenarios.py                 Loan/Reservation/Fine/Notification cohérents
├── reference/
│   ├── bpost.py                    lecture .xls/.xlsx localités belges
│   └── editorial_selection.py     lecture reference/catalogue/<lang>.toml
├── export/
│   ├── catalogue_csv.py, users_csv.py, scenarios_csv.py
├── load/
│   ├── cli.py, guard.py, postgres.py, users_scenarios.py, users_scenarios_cli.py
├── pipeline/
│   ├── acquisition.py    (CLI acquisition small/medium)
│   ├── bundle.py          (build_bundle small/medium — module pivot réutilisé partout)
│   ├── batch.py           (DEV-16.3, un lot = langue+catégorie+quota)
│   ├── large_build.py     (DEV-16.4, agrégation multi-lots -> profil large)
│   ├── full_build.py      (DEV-16.5, idem + covers + enrichissement -> profil full)
│   └── full_scenarios.py  (DEV-16.5.1, users+scénarios pour full)
└── audit/completeness.py  (DEV-13.20, matrice de complétude, lecture seule)
```

## 4. `data/`

| Dossier | Taille | Producteur | Consommateur | Statut |
|---|---|---|---|---|
| `data/raw/openlibrary/` | 4,6 Go | `acquisition/openlibrary*.py` | `pipeline/*` | ACTIF |
| `data/raw/wikidata/` | 42 Mo | `acquisition/wikidata.py` | `pipeline/bundle.py`, `full_build.py` | ACTIF |
| `data/raw/bpost/` | 428 Ko | `acquisition/provenance.py::archive_source_file` | `reference/bpost.py` | ACTIF |
| `data/validated/small/`, `medium/`, `full/` | 100 Ko / 27 Mo / 16 Mo | `pipeline/acquisition.py`, acquisition manuelle Full | `pipeline/bundle.py`, `full_build.py`, `full_scenarios.py` | ACTIF |
| `data/validated/batches/` | 181 Mo, 91 dossiers | `pipeline/batch.py::acquire_batch_candidates` | rejoué par `large_build.py`/`full_build.py` (cache RAW par lot) | ACTIF |
| `data/bundles/small/`, `medium/`, `large/`, `full/` | 144 Ko / 624 Ko / 14 Mo / 28 Mo | `pipeline/bundle.py`, `large_build.py`, `full_build.py`, `full_scenarios.py` | `load/cli.py`, `load/users_scenarios_cli.py` | ACTIF |
| `data/covers/full/` | 214 Mo, 7108 fichiers | `pipeline/full_build.py` (cover pipeline DEV-16.5) | référencé par `titles.csv` du profil full (1728/7108 fichiers réellement utilisés) | ACTIF |
| `data/covers/final/` | 330 Mo, 10578 fichiers | **NON DÉTERMINÉ** (aucune référence dans `src/`, aucun log `.claude/logs/DEV-1[36]*` ne le mentionne) | aucun (0 référence trouvée dans les CSV de bundle) | EXPÉRIMENTAL / OBSOLÈTE |
| `data/cache/*-mass-editorial-engine/`, `final-master-cover-rescue*/`, `page-count-enrichment/`, `google-books-final-rescue-benchmark/` | 321 Mo au total | **NON DÉTERMINÉ** | aucun | EXPÉRIMENTAL |
| `data/audit/` | 87 Mo, ~230 fichiers | **NON DÉTERMINÉ** | aucun (hors le `data/audit/` produit par `audit/completeness.py`, qui écrit en réalité dans `data/validated/<profile>/completeness_audit.csv`, PAS dans `data/audit/`) | EXPÉRIMENTAL |
| `data/backups/` | 2,7 Mo, 5 dumps | opérations manuelles `pg_dump` (DEV-13.16, 13.19.E, 13.20, 16.4, 16.8) | aucun automatisme ; restauration manuelle uniquement | HISTORIQUE |

Voir [12-caches-et-replay.md](./12-caches-et-replay.md) pour le détail du mécanisme de cache réellement utilisé par le pipeline, et [20-incoherences-et-risques.md](./20-incoherences-et-risques.md) pour l'analyse de `data/audit/`, `data/cache/` et `data/covers/final/`.

## 5. Fichiers critiques

| Fichier | Rôle | Produit par | Consommé par | Statut |
|---|---|---|---|---|
| `config/profiles.toml` | Définit les 4 profils (cible, DB) | manuel | `config.py::load_profiles`, tous les pipelines | ACTIF |
| `data/bundles/<profile>/titles.csv` | Table `title` prête à charger | `export/catalogue_csv.py` | `load/postgres.py` | ACTIF |
| `data/bundles/<profile>/copies.csv` | Table `copy` prête à charger | `generation/copies.py` + export | `load/postgres.py`, `pipeline/full_scenarios.py` (relecture) | ACTIF |
| `data/bundles/<profile>/*_report.json` | Rapport de génération (métriques) | `pipeline/bundle.py` / `large_build.py` / `full_build.py` | lecture humaine, audit | ACTIF |
| `data/bundles/full/provenance.jsonl` | Traçabilité par Title (source, batch, décisions) | `pipeline/full_build.py` | audit uniquement (jamais lu par `primatis-api`) | ACTIF |
| `data/validated/full/authors_selected.jsonl`, `works_selected.jsonl` | Snapshots d'enrichissement Full (4,7 Mo / 10 Mo) | acquisition manuelle (dump bulk) | `full_build.py --with-enrichment` | ACTIF |
| `reference/catalogue/*.toml` | Socle éditorial vérifié par langue | manuel (vérification humaine réelle sur Open Library) | `reference/editorial_selection.py`, `pipeline/batch.py` | ACTIF |
| `data/README.md` | Documentation locale de `data/` | manuel, DEV-13.3 | lecture humaine | **OBSOLÈTE** — affirme encore *« Aucune donnée externe n'est acquise pendant DEV-13.3 »* alors que ~5,9 Go de données externes sont désormais acquises (contradiction documentée, voir [20](./20-incoherences-et-risques.md)) |

## 6. Statut Git par zone

Preuve : `git status --porcelain data-seeding` (racine du dépôt `/home/yassine/workspace/projects/primatis`) et `git log --oneline -- data-seeding`.

```text
COMMITÉ (6 commits, 2026-09-02 → 2026-09-04) :
  README.md, pyproject.toml (implicite, présent depuis le 1er commit),
  la quasi-totalité des modules « génération 1 » listés en §2 de
  01-architecture.md, config/profiles.toml, tests DEV-13.

NON COMMITÉ (« ?? » dans git status, présent uniquement dans l'arbre de travail) :
  reference/ (dossier entier), scripts/ (dossier entier),
  data/audit/, data/cache/, data/covers/ (dossiers entiers, non gitignorés),
  pipeline/batch.py, large_build.py, full_build.py, full_scenarios.py,
  quality/, provenance/, mapping/documentary_categories.py,
  normalization/{cover_validation,language_detection,text_quality}.py,
  acquisition/{openlibrary_works,rate_limit}.py,
  load/users_scenarios*.py,
  + une quinzaine de fichiers de tests correspondants.

MODIFIÉ NON COMMITÉ :
  README.md, deduplication/catalogue.py, mapping/catalogue.py,
  mapping/genres.py, + 4 fichiers de tests associés.
```

Conséquence directe : la totalité de la « génération 2 » (§2 de [01-architecture.md](./01-architecture.md)), qui a pourtant été réellement exécutée et chargée dans `primatis_dev`/`primatis_preview` (voir [19-etat-reel-actuel.md](./19-etat-reel-actuel.md)), n'existe **que localement**, sans trace dans l'historique Git. `data/raw/`, `data/normalized/`, `data/validated/`, `data/bundles/`, `data/backups/` sont explicitement gitignorés (`.gitignore` racine, lignes 13-19) ; `data/audit/`, `data/cache/`, `data/covers/` et `reference/` **ne sont pas gitignorés** mais restent non commités — ce n'est pas une exclusion volontaire du `.gitignore`, simplement un `git add`/`git commit` jamais exécuté.

## Rapports liés

- [Architecture générale](./01-architecture.md)
- [Caches et replay](./12-caches-et-replay.md)
- [Covers](./11-covers.md)
- [Historique technique visible](./24-historique-technique-visible.md)
- [Incohérences et risques](./20-incoherences-et-risques.md)
