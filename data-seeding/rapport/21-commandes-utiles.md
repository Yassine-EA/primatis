# Commandes utiles — data-seeding

[Retour au rapport général](./general.md)

Répertoire d'exécution par défaut : `data-seeding/` (racine du sous-projet), environnement virtuel activé, sauf mention contraire.

## Installation

```bash
cd data-seeding
python -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -e ".[dev]"
```

**Objectif** : installer le package en mode éditable + dépendances de développement (`pytest`, `xlwt`). **Résultat attendu** : `pip show primatis-data-seeding` liste le package ; aucune commande CLI n'est créée (pas de `[project.scripts]`).

## Tests

```bash
pytest                      # équivaut à `pytest -q` (addopts pyproject.toml)
pytest tests/test_bundle.py -v
pytest -k "cover"            # filtrer par nom de test
```

**Objectif** : valider l'ensemble de la logique pure Python (normalisation, dédup, mapping, génération, garde-fous). **Résultat attendu (constaté)** : `506 passed in 6.81s`, aucun accès réseau ni PostgreSQL.

## Bootstrap (vérification de profil, ne charge rien)

```bash
python -m primatis_data_seeding.main --profile small
```

## Acquisition small/medium

```bash
export PRIMATIS_OPENLIBRARY_CONTACT="votre-email@example.org"
python -m primatis_data_seeding.pipeline.acquisition \
  --profile medium --bpost-xlsx /chemin/vers/bpost.xlsx --refresh-openlibrary
```

**Objectif** : acquérir (ou réutiliser) le snapshot Open Library + Bpost pour un profil. **Résultat attendu** : `data/validated/<profil>/openlibrary_selected.jsonl`, `bpost_localities.csv`, `acquisition_report.json`.

## Construction du bundle small/medium

```bash
export PRIMATIS_SEED_USER_PASSWORD="MotDePasseDemo!2026"
python -m primatis_data_seeding.pipeline.bundle --profile medium --reference-date 2026-09-04
```

**Objectif** : produire le bundle CSV complet. **Résultat attendu** : `data/bundles/medium/*.csv` + `bundle_report.json` + `deduplication_report.json`.

## CHECK (lecture seule) du catalogue en base

```bash
python -m primatis_data_seeding.load.cli \
  --profile medium --export-dir data/bundles/medium
```

**Objectif** : valider intégrité/contraintes sans écrire. **Résultat attendu** : ligne `mode=CHECK database=primatis_dev authors=... titles=...`.

## APPLY (écriture réelle) du catalogue

```bash
python -m primatis_data_seeding.load.cli \
  --profile medium --export-dir data/bundles/medium \
  --apply --confirm-database primatis_dev
```

**Attention** : opération destructive au sens PRIMATIS (remplace l'ancien seed) — à réserver à un environnement dev/preview autorisé, jamais `primatis_test` (refusé structurellement).

## CHECK/APPLY Users + Scénarios (profil `full` uniquement)

```bash
python -m primatis_data_seeding.load.users_scenarios_cli \
  --profile full --export-dir data/bundles/full
python -m primatis_data_seeding.load.users_scenarios_cli \
  --profile full --export-dir data/bundles/full \
  --apply --confirm-database primatis_preview
```

## Audit de complétude (lecture seule, réseau et DB non requis)

```bash
python -m primatis_data_seeding.audit.completeness --profile medium
```

**Résultat attendu** : `data/validated/medium/completeness_audit.csv` + un résumé `stdout` par statut (`PRESENT`, `ENRICHED_FROM_SOURCE`, ...).

## Matérialisation manuelle de covers (liste explicite)

```bash
python -m primatis_data_seeding.acquisition.openlibrary_covers \
  --cover-ids 258027,258028 --contact votre-email@example.org
```

## Construction Large / Full (scripts one-shot documentés)

```bash
python scripts/dev164_build_large.py
python scripts/dev165_build_full.py --with-enrichment --with-covers --max-covers 500
export PRIMATIS_SEED_USER_PASSWORD="MotDePasseDemo!2026"
python scripts/dev1651_build_full_scenarios.py     # requiert une connexion PostgreSQL réelle à primatis_preview
```

**Avertissement** : ces trois scripts déclenchent potentiellement des appels réseau réels vers Open Library (des heures d'exécution possibles à froid) — vérifier d'abord que le cache (`data/validated/batches/`, `data/raw/openlibrary/`) est déjà complet pour éviter tout appel réseau (comportement par défaut si le cache est intact, `refresh=False`).

## Diagnostic / comptage (lecture seule, utilisés pendant cet audit)

```bash
git status --porcelain data-seeding | wc -l
git log --oneline -- data-seeding
du -sh data/*
find data/covers/full -type f | wc -l
python3 -c "import csv; rows=list(csv.DictReader(open('data/bundles/full/titles.csv'))); \
  print(sum(1 for r in rows if r['cover_image_url']))"
```

## Rapports liés

- [Points d'entrée et CLI](./03-points-entree-et-cli.md)
- [Tests](./17-tests.md)
- [Chargement en base de données](./16-chargement-base-donnees.md)
