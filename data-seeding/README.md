# PRIMATIS — Data Seeding

Sous-projet Python autonome destiné à préparer, valider, générer et, dans les étapes ultérieures, charger les données de démonstration PRIMATIS.

## Statut actuel

DEV-13.3 initialise uniquement le socle technique.

Aucun dataset bibliographique n'est téléchargé.
Aucun chargement PostgreSQL n'est implémenté.
Aucune migration Flyway n'est créée ou modifiée.

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

## Secrets

Aucun mot de passe PostgreSQL, secret ou credential ne doit être versionné.
Les futurs paramètres sensibles devront être fournis via variables d'environnement ou configuration locale ignorée par Git.

## Étapes suivantes

Les dépendances et modules pour l'acquisition de sources, PostgreSQL, ISBN, déduplication et chargement seront ajoutés seulement lorsqu'un besoin concret est traité.
