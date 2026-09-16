# Cycle de vie d'un Title — data-seeding

[Retour au rapport général](./general.md)

## 1. Origine

Un `Title` provient toujours, in fine, d'une **édition** Open Library (`OpenLibraryEditionCandidate` → `SelectedEdition` → `NormalizedEdition` → `PrimatisTitleRow`). Il n'existe aucun chemin de création de `Title` sans passer par cette chaîne — pas de génération synthétique de titres.

```text
Open Library Search API (docs[].editions.docs[0])
        │ acquisition/openlibrary.py::_extract_candidate / pipeline/batch.py::select_batch_candidates
        v
OpenLibraryEditionCandidate  /  SelectedEdition (pipeline/bundle.py, même contrat JSONL)
        │ pipeline/bundle.py::normalize_selected_catalogue
        v
NormalizedEdition (models.py)
        │ deduplication/catalogue.py::deduplicate_editions
        v
NormalizedEdition (survivant unique)
        │ mapping/catalogue.py::map_catalogue
        v
PrimatisTitleRow (mapping/models.py)
        │ (génération 2 uniquement) quality/quarantine.py::apply_text_quality_policy
        v
PrimatisTitleRow (final, éventuellement nettoyé de subtitle/summary/publisher)
        │ export/catalogue_csv.py
        v
titles.csv  →  load/postgres.py  →  table `title`
```

## 2. Champs et origine exacte

| Champ | Origine | Peut être NULL | Détail |
|---|---|---|---|
| `source_key` | `edition_key` Open Library (ex. `/books/OL12623748M`) | non | identifiant technique de rapprochement CSV, **jamais chargé en base** (la table `title` n'a pas cette colonne — sert uniquement de clé de jointure pendant le pipeline) |
| `title` | `edition.title` (Search API) | non (champ bloquant) | tronqué à 500 ; rejeté (`TITLE_REQUIRED`) si vide après normalisation |
| `subtitle` | `edition.subtitle` | oui | tronqué à 500 ; mis à `NULL` si qualité texte suspecte |
| `summary` | `Work.description` (dump/API Works), uniquement si `work_key` exact résolu | oui (39/1000 medium, 0,17 en full) | **jamais** un extrait de `title`/`subtitle`, jamais généré |
| `language` | `edition.language`, mappé via `normalize_language_code` | non (bloque le candidat si absent) | un des 7 codes PRIMATIS (FR/EN/NL/DE/ES/IT/LA) |
| `isbn` | priorité : Edition Detail exact (`isbn_13`/`isbn_10`) puis fallback Search API | oui (26,6 % en Full) | jamais fabriqué ; conflits ISBN entre éditions → groupe entier exclu (voir [13-deduplication.md](./13-deduplication.md)) |
| `publisher` | priorité : Edition Detail puis Search API, premier élément non vide | oui (1,9 % absent en Full) | — |
| `publication_year` | priorité : Edition Detail `publish_date` puis Search API `publish_date`/`publish_year` | oui (1,4 % absent en Full) | seulement si une **unique** année est extractible |
| `page_count` | priorité : Edition Detail `number_of_pages`, puis `pagination` (forme stricte), puis Search API `number_of_pages` | oui (**100 % absent** dans tous les rapports consultés — 0,0 dans small/medium/large/full) | voir remarque ci-dessous |
| `cover_image_url` | résolu **uniquement** si le fichier local existe réellement (`resolve_cover_image_url`, fail-closed) | oui (88,2 % absent en Full) | voir [11-covers.md](./11-covers.md) |
| `title_status` | constante `"ACTIVE"` | non | aucune autre valeur n'est jamais produite par le seeding |

**Remarque `page_count` :** malgré un mécanisme de normalisation dédié (`normalize_page_count`, `normalize_pagination`) et un audit de complétude qui documente précisément les cas `SOURCE_CONFIRMED_ABSENT`/`AMBIGUOUS_NOT_USED`, **tous** les rapports de bundle consultés (`small`, `medium`, `large`, `full`) affichent `page_count_coverage: 0.0`. Ce point est développé dans [20-incoherences-et-risques.md](./20-incoherences-et-risques.md).

## 3. Auteurs et genres

Chaque `Title` est rattaché à ≥1 `Author` via `title_authors.csv` (`TITLE_WITHOUT_AUTHOR` sinon — rejet, jamais de Title orphelin). Chaque `Title` **peut** n'avoir aucun genre (`NO_GENRE_MATCH` = avertissement, jamais un rejet) — la taxonomie est fixe et exacte, voir [10-genres.md](./10-genres.md).

## 4. Statuts possibles dans le pipeline (avant chargement)

```text
ACCEPTED     -> présent dans mapping.titles / quality_result.titles (chemins normaux)
REJECTED     -> mapping.rejections (TITLE_WITHOUT_AUTHOR, UNRESOLVED_AUTHOR_REFERENCE) — jamais chargé
QUARANTINED  -> quality.quarantined, entity="title" (TEXT_ENCODING_SUSPECT ou
                NO_VALID_AUTHOR_AFTER_QUARANTINE, cascade depuis un Author quarantiné) — jamais chargé
MERGED       -> deduplication.duplicates (SAME_SOURCE_KEY, SAME_VALID_ISBN) — le
                perdant disparaît, seul le survivant (le plus complet) continue
```

`title_status` en base PRIMATIS (`ACTIVE`/autres valeurs métier éventuelles côté backend) est une notion **distincte** de ces statuts pipeline — le seeding n'écrit jamais que `ACTIVE`.

## Rapports liés

- [Normalisation et validation](./07-normalisation-et-validation.md)
- [Auteurs](./09-auteurs.md)
- [Genres](./10-genres.md)
- [Déduplication](./13-deduplication.md)
- [Covers](./11-covers.md)
