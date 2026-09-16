# Dépendances — data-seeding

[Retour au rapport général](./general.md)

## 1. Python

```text
requires-python = ">=3.14,<3.15"     (pyproject.toml)
Interpréteur réellement installé dans .venv/ : Python 3.14.x (confirmé par
  la présence de .venv/lib/python3.14/)
```

## 2. Dépendances de production (`[project.dependencies]`)

| Paquet | Contrainte | Version installée | Rôle | Import principal |
|---|---|---|---|---|
| `bcrypt` | `>=5.0,<6` | 5.0.0 | hachage des mots de passe synthétiques | `generation/users.py` (`bcrypt.hashpw`) |
| `openpyxl` | `>=3.1.5,<4` | 3.1.5 | lecture des fichiers `.xlsx`/`.xlsm` Bpost | `reference/bpost.py::_load_xlsx` |
| `psycopg[binary]` | `>=3.3,<4` | 3.3.4 (+ `psycopg-binary` 3.3.4) | connexion PostgreSQL (chargement, lecture des réglages métier) | `load/postgres.py`, `load/users_scenarios.py`, `scripts/dev1651_build_full_scenarios.py` |
| `xlrd` | `>=2.0.2,<3` | 2.0.2 | lecture des fichiers `.xls` legacy Bpost | `reference/bpost.py::_load_xls` |

## 3. Dépendances de développement (`[project.optional-dependencies].dev`)

| Paquet | Contrainte | Version installée | Rôle |
|---|---|---|---|
| `pytest` | `>=9.0,<10` | 9.1.1 | suite de tests (506 tests) |
| `xlwt` | `>=1.3,<2` | 1.3.0 | **écriture** de fichiers `.xls` — utilisée uniquement dans les fixtures de test (aucun usage en dehors de `tests/`, confirmé par `grep -rn xlwt src/` → 0 résultat) |

## 4. Bibliothèque standard largement utilisée (sans dépendance externe)

```text
hashlib (sha256)         acquisition/provenance.py, generation/copies.py
urllib.request           acquisition/openlibrary*.py, wikidata.py, pipeline/batch.py
                          (aucun `requests`/`httpx` — choix délibéré de rester sur la stdlib)
tomllib                   config.py, reference/editorial_selection.py (TOML natif Python 3.11+)
csv, json, gzip           export/*, acquisition/*
dataclasses               modèles internes partout
decimal (Decimal)         generation/scenarios.py (montants de Fine, jamais de float)
random (random.Random)    generation/users.py, seed explicite — jamais random global
```

## 5. Analyse — conformité à la politique de dépendances (`data-seeding.md`)

Conforme à la règle *« Préférer quelques bibliothèques ciblées à un framework lourd inutile »* : 4 dépendances de production seulement, chacune répondant à un besoin précis et non substituable par la stdlib (BCrypt n'existe pas en stdlib, lecture Excel nécessite un parseur dédié, PostgreSQL nécessite un driver). Aucun framework HTTP (`requests`), aucun ORM, aucune bibliothèque de fuzzy-matching, aucun framework de tâches — cohérent avec les interdictions explicites de `data-seeding.md` (« pas de multiprocessing/async complexe/framework distribué sans besoin mesuré »).

## Rapports liés

- [Configuration](./04-configuration.md)
- [Architecture générale](./01-architecture.md)
