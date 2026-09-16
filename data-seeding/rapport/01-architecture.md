# Architecture générale — data-seeding

[Retour au rapport général](./general.md)

## Table des matières

- [1. Vue d'ensemble](#1-vue-densemble)
- [2. Les deux générations du projet](#2-les-deux-générations-du-projet)
- [3. Modules et responsabilités](#3-modules-et-responsabilités)
- [4. Flux entre composants](#4-flux-entre-composants)
- [5. Dépendances internes (qui importe quoi)](#5-dépendances-internes-qui-importe-quoi)
- [6. Points d'entrée](#6-points-dentrée)
- [7. Composants auxiliaires / hors code](#7-composants-auxiliaires--hors-code)
- [8. Schéma ASCII global](#8-schéma-ascii-global)

## 1. Vue d'ensemble

`data-seeding/` est un sous-projet Python autonome (`primatis-data-seeding`, `pyproject.toml`) dont le rôle est de préparer, valider, dédupliquer, générer et charger le catalogue bibliographique et les données de démonstration de PRIMATIS. Il ne fait partie ni du runtime Angular/Spring Boot, ni du schéma PostgreSQL (Flyway reste l'unique autorité de schéma — `.claude/rules/database.md`).

Le code source vit sous `src/primatis_data_seeding/` (organisation *package-by-feature*, 62 fichiers `.py` hors `__init__.py`, ~10 281 lignes). Les tests vivent sous `tests/` (51 fichiers, ~7 282 lignes, 506 tests exécutés avec succès au moment de l'audit — `pytest -q` → `506 passed in 6.81s`, reproduit le 2026-09-15). Les données (brutes, validées, bundles, covers, caches, audits) vivent sous `data/` (~5,9 Go au total, très majoritairement non versionné par Git — voir [02](./02-arborescence-et-fichiers.md)).

## 2. Les deux générations du projet

Une lecture strictement architecturale ne peut pas ignorer un fait structurant confirmé par `git log` et `git status` (preuve : `git log --oneline -- data-seeding` ne montre que 6 commits, tous datés du 2026-09-02 au 2026-09-04) :

```text
GÉNÉRATION 1 — « DEV-13 », commitée dans Git (6 commits, 2026-09-02 → 2026-09-04)
    pipeline/acquisition.py   (CLI d'acquisition small/medium)
    pipeline/bundle.py        (construction du bundle small/medium)
    generation/copies.py, generation/users.py
    mapping/catalogue.py, mapping/genres.py
    deduplication/catalogue.py
    normalization/{isbn,language,text,openlibrary,bpost}.py
    load/cli.py, load/postgres.py
    → profils small (100 Titles) et medium (1000 Titles), chargés et
      validés dans primatis_dev (`.claude/logs/DEV-13 FINAL GATE`).

GÉNÉRATION 2 — « DEV-16 », NON commitée (fichiers en `??` dans `git status`)
    pipeline/batch.py, pipeline/large_build.py, pipeline/full_build.py,
      pipeline/full_scenarios.py
    quality/quarantine.py, provenance/record_provenance.py
    normalization/{text_quality,language_detection,cover_validation}.py
    mapping/documentary_categories.py
    reference/editorial_selection.py, reference/catalogue/*.toml
    acquisition/openlibrary_works.py, acquisition/rate_limit.py
    load/users_scenarios.py, load/users_scenarios_cli.py
    scripts/dev164_build_large.py, dev165_build_full.py,
      dev1651_build_full_scenarios.py
    data/audit/, data/cache/, data/covers/, reference/ (dossiers entiers)
    → profils large (5000 Titles, primatis_dev) et full (15000 Titles,
      primatis_preview), réellement construits ET chargés en base
      (confirmé par `.claude/logs/DEV-16.8...md` §B/§P : requêtes SQL
      réelles sur les deux bases), mais dont le CODE et les DONNÉES ne
      sont, à la date de l'audit, dans AUCUN commit Git.
```

Voir [19-etat-reel-actuel.md](./19-etat-reel-actuel.md) et [24-historique-technique-visible.md](./24-historique-technique-visible.md) pour le détail et les implications (non-reproductibilité en cas de perte du répertoire de travail, absence de revue de code possible sur GitHub/PR, etc.).

## 3. Modules et responsabilités

| Package | Rôle | Génération |
|---|---|---|
| `acquisition/` | Appels réseau réels (Open Library Search/Details/Works/Authors/Covers, Wikidata), cache par clé exacte, manifestes SHA-256 | 1 + 2 |
| `normalization/` | Nettoyage/typage d'un enregistrement source vers un type interne (`NormalizedAuthor`, `NormalizedEdition`), détection qualité texte, détection langue | 1 + 2 |
| `validation/` | Règles de structure (obligatoire, longueur, enum, checksum ISBN) sur les types normalisés | 1 |
| `deduplication/` | Résolution des doublons (source_key exact, ISBN exact) + détection de candidats (jamais fusionnés) | 1 |
| `mapping/` | Transformation vers le contrat PRIMATIS (`PrimatisTitleRow`, `PrimatisAuthorRow`, ...), taxonomie Genre, catégories documentaires | 1 + 2 |
| `quality/` | Politique de quarantaine texte + cascade Author→Title | 2 |
| `provenance/` | Traçabilité par enregistrement (source, batch, décisions de champ) | 2 |
| `generation/` | Génération déterministe de Copies, Users synthétiques, Scénarios métier | 1 (copies/users) + 2 (scénarios complets) |
| `reference/` | Référentiel Bpost (codes postaux belges), socle éditorial par langue | 1 (bpost) + 2 (socle) |
| `export/` | Sérialisation CSV du bundle final (catalogue, users, scénarios) | 1 |
| `load/` | Chargement PostgreSQL CHECK/APPLY, garde-fous de cible, idempotence | 1 + 2 |
| `pipeline/` | Orchestration bout-en-bout (acquisition.py, bundle.py, batch.py, large_build.py, full_build.py, full_scenarios.py) | 1 + 2 |
| `audit/` | Matrice d'audit de complétude champ-par-champ | 1 (DEV-13.20) |
| `config.py`, `guard.py`, `models.py`, `main.py` | Configuration des profils, garde-fou de base cible, types normalisés partagés, CLI bootstrap | 1 |

## 4. Flux entre composants

```text
acquisition/*  --(payloads bruts + manifest sha256)-->  data/raw/ ou data/validated/<profile>/*.jsonl
      |
      v
pipeline/bundle.py::normalize_selected_catalogue()   (utilisé par bundle.py, batch.py, large_build.py, full_build.py)
      |
      v
deduplication/catalogue.py::deduplicate_authors/editions()
      |
      v
mapping/catalogue.py::map_catalogue()  -->  PrimatisTitleRow / PrimatisAuthorRow / ...
      |
      v
quality/quarantine.py::apply_text_quality_policy()   (uniquement génération 2 : batch/large/full)
      |
      v
generation/copies.py::generate_copies()
generation/users.py::generate_synthetic_members()
generation/scenarios.py::generate_demo_scenarios()   (uniquement full, via full_scenarios.py)
      |
      v
export/*_csv.py  -->  data/bundles/<profile>/*.csv
      |
      v
load/postgres.py / load/users_scenarios.py  -->  PostgreSQL (primatis_dev | primatis_preview)
```

## 5. Dépendances internes (qui importe quoi)

`pipeline/bundle.py` est le module **pivot** : il définit `SelectedEdition`, `load_selected_editions()` et `normalize_selected_catalogue()`, réutilisés tels quels par `pipeline/batch.py`, `pipeline/large_build.py` et `pipeline/full_build.py` (jamais dupliqués — confirmé par les imports explicites en tête de chacun de ces trois fichiers). `pipeline/large_build.py` et `pipeline/full_build.py` réutilisent à leur tour `pipeline/batch.py::acquire_batch_candidates()` pour l'acquisition multi-lots. Aucun de ces modules n'importe `pipeline/acquisition.py` (le CLI d'acquisition small/medium reste isolé, non réutilisé par la génération 2).

`load/users_scenarios_cli.py` (DEV-16.5.1) réutilise explicitement `load/guard.py` (déjà testé pour le catalogue) plutôt que de dupliquer une logique de garde-fou — le fichier le documente lui-même dans son docstring.

## 6. Points d'entrée

Voir le détail complet dans [03-points-entree-et-cli.md](./03-points-entree-et-cli.md). Résumé :

```text
python -m primatis_data_seeding.main                       (bootstrap, ne charge rien)
python -m primatis_data_seeding.pipeline.acquisition        (acquisition small/medium)
python -m primatis_data_seeding.pipeline.bundle             (bundle small/medium)
python -m primatis_data_seeding.load.cli                    (CHECK/APPLY catalogue)
python -m primatis_data_seeding.load.users_scenarios_cli    (CHECK/APPLY users+scenarios, full)
python -m primatis_data_seeding.acquisition.openlibrary_covers  (matérialisation manuelle de covers)
python -m primatis_data_seeding.audit.completeness          (audit de complétude, lecture seule)
scripts/dev164_build_large.py        (script one-shot, plan Large)
scripts/dev165_build_full.py         (script one-shot, plan Full)
scripts/dev1651_build_full_scenarios.py (script one-shot, scénarios Full)
```

`pyproject.toml` ne déclare aucun `[project.scripts]` : il n'existe **aucune commande CLI installée** (`pip install -e .` ne crée pas d'exécutable) — chaque point d'entrée s'invoque via `python -m <module>` ou en exécutant directement le fichier `scripts/*.py`.

## 7. Composants auxiliaires / hors code

```text
config/profiles.toml           configuration des 4 profils (cible, volumétrie)
reference/catalogue/*.toml     socle éditorial vérifié par langue (7 fichiers)
data/                          voir 02-arborescence-et-fichiers.md
.pytest_cache/, __pycache__/, *.egg-info/   artefacts d'exécution locaux (ACTIF, générés, non versionnés)
```

## 8. Schéma ASCII global

```text
                         ┌─────────────────────────────┐
                         │        SOURCES EXTERNES     │
                         │ Open Library (Search/Books/  │
                         │ Authors/Works dumps, Covers) │
                         │ Wikidata (entités exactes)   │
                         │ Bpost (Excel officiel)       │
                         └──────────────┬───────────────┘
                                        │ acquisition/*
                                        v
                         ┌─────────────────────────────┐
                         │  data/raw/  · data/validated/*.jsonl │  (RAW, sha256, manifest)
                         └──────────────┬───────────────┘
                                        │ pipeline/bundle.py::normalize_selected_catalogue
                                        v
                         ┌─────────────────────────────┐
                         │ normalization/ + validation/ │
                         └──────────────┬───────────────┘
                                        v
                         ┌─────────────────────────────┐
                         │ deduplication/catalogue.py   │
                         └──────────────┬───────────────┘
                                        v
                         ┌─────────────────────────────┐
                         │ mapping/catalogue.py         │──> genres.py, documentary_categories.py
                         └──────────────┬───────────────┘
                                        v
                    (génération 2 seulement) quality/quarantine.py
                                        v
                         ┌─────────────────────────────┐
                         │ generation/ (copies, users,  │
                         │ scenarios) + reference/bpost │
                         └──────────────┬───────────────┘
                                        v
                         ┌─────────────────────────────┐
                         │ export/*_csv.py              │──> data/bundles/<profile>/*.csv
                         └──────────────┬───────────────┘
                                        v
                         ┌─────────────────────────────┐
                         │ load/postgres.py             │──> PostgreSQL (primatis_dev/preview)
                         │ load/users_scenarios.py      │
                         └─────────────────────────────┘
```

## Rapports liés

- [Arborescence et fichiers](./02-arborescence-et-fichiers.md)
- [Points d'entrée et CLI](./03-points-entree-et-cli.md)
- [Flux complet](./22-flux-complet.md)
- [État réel actuel](./19-etat-reel-actuel.md)
