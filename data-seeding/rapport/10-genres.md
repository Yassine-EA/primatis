# Genres — data-seeding

[Retour au rapport général](./general.md)

## 1. Origine

La table `genre` est une **taxonomie contrôlée, fixe et interne**, définie en dur dans `mapping/genres.py::GENRES` — 26 entrées (`code`, `label` FR, `description` FR), par exemple :

```python
PrimatisGenreRow("FICTION", "Fiction", "Œuvres de fiction générale."),
PrimatisGenreRow("SCIENCE_FICTION", "Science-fiction", "Fiction scientifique et spéculative."),
...
PrimatisGenreRow("POLITICS", "Politique", "Politique et sciences politiques."),
```

Elle n'est **jamais** une copie du vocabulaire `subject` d'Open Library (non borné, en anglais, très hétérogène) — c'est un vocabulaire propre à PRIMATIS, produit intégralement et identiquement à chaque exécution de `map_catalogue()` (`result.genres = list(GENRES)`, déterministe).

## 2. Mapping subject → genre

`map_subjects_to_genre_codes()` applique une table d'alias **exacte** (après repliement Unicode + casefold, `_fold()`) — jamais de correspondance floue ou par sous-chaîne. Deux strates :

1. **Table originale** (≈35 alias) : `fiction→FICTION`, `mystery→MYSTERY`, `love stories→ROMANCE`, `juvenile literature→CHILDREN`, etc.
2. **Extension DEV-16.3 §E** (10 alias supplémentaires) : ajoutée après **mesure réelle** de la fréquence des sujets sur l'échantillon medium (`data/validated/medium/works_selected.jsonl`, 402/1000 œuvres avec ≥1 sujet, 1223 sujets distincts). Chaque alias ajouté a été observé ≥5 fois **et** a un mapping 1:1 non ambigu vers un genre existant : `histoire→HISTORY` (12 occurrences), `politics and government→POLITICS` (15), `historical fiction→FICTION` (10), `fiction, romance, historical, general→ROMANCE` (14), etc. Des sujets fréquents **sans** mapping sûr (« Civilization », « Folklore », « Criticism and interpretation », « Dictionaries », « Congresses ») ont été délibérément laissés non mappés — jamais devinés (`DEC-16.3-05` documenté dans le code même).

## 3. Association Title/Genre

`title_genres.csv` — une ligne par (`title_source_key`, `genre_code`) matché. Un Title **sans** aucun sujet mappable reçoit un avertissement (`NO_GENRE_MATCH`, jamais un rejet) : la relation Title↔Genre est facultative par construction.

## 4. Couverture mesurée

```text
small  : (non rapporté explicitement — 82 avertissements NO_GENRE_MATCH sur 100 Titles)
medium : (idem — 826 avertissements sur 1000 Titles ; cohérent avec la table courte de l'époque DEV-13)
large  : genre_coverage = 0.6978   (69,78 % des Titles ont ≥1 genre)
full   : genre_coverage = 0.65     (65 %)
```

Les rapports `small`/`medium` (génération 1, avant l'extension DEV-16.3 §E des alias) n'exposent pas de champ `genre_coverage` synthétique dans leur `bundle_report.json` — seul le décompte des avertissements de mapping est disponible ; **INFÉRÉ** que la couverture réelle small/medium est proche de 18 % (100−82)/100 et 17,4 % (1000−826)/1000, bien inférieure à large/full car la table d'alias étendue (DEV-16.3) n'existait pas encore lors de ces deux premiers bundles (les fichiers `data/bundles/small/` et `data/bundles/medium/` n'ont, sauf preuve contraire, jamais été régénérés après l'extension de `genres.py`).

## 5. Validation

Aucune contrainte structurelle propre à `genre_code` au-delà de l'appartenance à la liste fixe de 26 codes — la cohérence est garantie par construction (le code ne peut produire qu'un des 26 codes existants, jamais un code inventé).

## Rapports liés

- [Titres](./08-titres.md)
- [Normalisation et validation](./07-normalisation-et-validation.md)
- [État réel actuel](./19-etat-reel-actuel.md)
