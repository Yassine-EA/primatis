# PRIMATIS — Data Seeding

Sous-projet Python autonome destiné à préparer, valider, générer et, dans les étapes ultérieures, charger les données de démonstration PRIMATIS.

## Statut actuel

> Note (DEV-16.1/DEV-16.3) : cette section date de DEV-13.3 et était
> restée obsolète depuis (signalé DEV-13.20 §22, corrigé ici a minima —
> pas de refonte documentaire complète, hors périmètre DEV-16.3 §26).

Le pipeline `small`/`medium` (acquisition Open Library réelle,
normalisation, déduplication, mapping, chargement PostgreSQL
CHECK/APPLY, idempotence) est opérationnel et validé de bout en bout
(DEV-13, `.claude/logs/DEV-13 FINAL GATE — DATA SEEDING.md`). Un second
pipeline par lots (`pipeline.batch`, DEV-16.3, voir plus bas) permet une
collecte pilote traçable par (langue, catégorie documentaire, taille) —
il ne remplace pas le pipeline `small`/`medium`, il le complète en vue
des profils `large`/`full`.

Aucune migration Flyway n'est créée ou modifiée par ce sous-projet
(Flyway reste l'unique autorité de schéma, `.claude/rules/database.md`).

## Python

Le projet cible Python 3.14.x.

## Installation locale

```bash
cd data-seeding
python -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -e ".[dev]"
```

## Tests

```bash
pytest
```

## Profils

```text
small  -> primatis_dev     -> ~100 Titles
medium -> primatis_dev     -> ~1 000 Titles
large  -> primatis_dev     -> ~5 000 Titles
full   -> primatis_preview -> ~15 000 Titles / 24 000 Copies / scénarios
```

`primatis_test` n'est pas une cible de bulk seeding permanent.

## Bootstrap CLI

Après installation editable :

```bash
python -m primatis_data_seeding.main --profile small
```

La commande ne charge aucune donnée. Elle valide uniquement le profil et la base cible configurée.

## Pipeline par lots (DEV-16.3)

Collecte pilote traçable, indépendante du pipeline `small`/`medium` —
voir `.claude/logs/DEV-16.3 — PIPELINE COLLECTE VALIDATION PAR LOTS.md`
pour le détail complet (politique qualité, mojibake, biography,
provenance).

```python
from datetime import date
from pathlib import Path
from primatis_data_seeding.pipeline.batch import BatchCriteria, run_batch

criteria = BatchCriteria(
    profile="large",  # small/medium/large/full — cible DB (config/profiles.toml)
    language="IT",
    documentary_category="history",
    count=12,
)
report = run_batch(
    criteria,
    contact="<email>",
    output_dir=Path("data/validated/batches"),
    reference_date=date.today(),
)
```

Produit, sous `data/validated/batches/<batch_id>/` : `raw_search.json`,
`selected.jsonl`, `provenance.jsonl`, `quarantine.jsonl` (jamais chargé
en base — DEC-16.3-03) et `batch_report.json`. `large`/`full` sont
reconnus au niveau `BatchCriteria`/`config/profiles.toml`/
`load/guard.py` ; leurs quotas linguistiques/documentaires définitifs
restent une décision DEV-16.4+ après mesure (DEC-16.3-01) — ce module ne
prétend pas encore produire les volumes cibles 5000/15000 Titles.

## Secrets

Aucun mot de passe PostgreSQL, secret ou credential ne doit être versionné.
Les futurs paramètres sensibles devront être fournis via variables d'environnement ou configuration locale ignorée par Git.

## Étapes suivantes

Les dépendances et modules pour l'acquisition de sources, PostgreSQL, ISBN, déduplication et chargement seront ajoutés seulement lorsqu'un besoin concret est traité.
