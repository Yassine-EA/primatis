# Sources de données — data-seeding

[Retour au rapport général](./general.md)

## 1. Vue tabulaire

| Source | Usage | Données | Module | Cache | Retry | Statut |
|---|---|---|---|---|---|---|
| Open Library — Search API | Sélection initiale d'éditions par langue (+ catégorie documentaire, génération 2) | `title`, `subtitle`, `author_key/name`, `subject`, `isbn_10/13`, `publisher`, `publish_date/year`, `number_of_pages`, `cover_i` | `acquisition/openlibrary.py`, `pipeline/batch.py` | payload complet en JSON (`data/raw/openlibrary/<profile>/search_<lang>.json`, sha256) | non (small/medium) ; oui via `with_retries` (génération 2) | ACTIF |
| Open Library — Authors bulk dump | Enrichissement Author (dates, biographie) par `author_key` exact | `name`, `birth_date`, `death_date`, `bio`, `remote_ids.wikidata` | `acquisition/openlibrary_authors.py` | extraction ciblée écrite en JSONL + manifest (idempotent tant que dump+clés inchangés) | non (lecture locale d'un fichier déjà téléchargé manuellement) | ACTIF |
| Open Library — Works bulk dump | Résumé (`Title.summary`) par `work_key` exact | `description` | `acquisition/openlibrary_works.py` | idem | non | ACTIF (ajouté DEV-16.5) |
| Open Library — Editions Details API | `isbn`, `publisher`, `publication_year`, `page_count` par `edition_key` exact | idem champs | `acquisition/openlibrary_details.py` | cache par clé (`data/raw/openlibrary/<profile>/editions/<key>.json`) + manifest sha256 | non par défaut ; `with_retries` en génération 2 | ACTIF |
| Open Library — Covers API | Image de couverture par `cover_id` entier | fichier JPEG/PNG | `acquisition/openlibrary_covers.py`, `pipeline/full_build.py` | fichier matérialisé (présence = cache) | oui (`max_attempts=5`) en Full | ACTIF |
| Wikidata — Special:EntityData | `Author.nationality` (résolution stricte via P27) | entité JSON complète (claims, labels) | `acquisition/wikidata.py` | cache par QID + manifest sha256 | non | ACTIF |
| Bpost — Excel officiel des codes postaux | Référentiel `postal_code`/`locality` belge (Users synthétiques) | 2 colonnes | `reference/bpost.py`, `acquisition/provenance.py` | archivé par sha256 (`data/raw/bpost/<sha256>.xlsx` + `provenance.json`) | n/a (fichier fourni localement) | ACTIF |
| Google Books API | **NON DÉTERMINÉ** — aucune fonction d'acquisition dans `src/` ; seule trace : `data/audit/google-books-*-benchmark*.csv/.txt` et `data/cache/google-books-final-rescue-benchmark/` (389 fichiers JSON/JPEG/PNG) | inconnu (pas de code à inspecter) | aucun module identifié | fichiers présents mais mécanisme de production inconnu | inconnu | EXPÉRIMENTAL / NON DOCUMENTÉ |
| Europeana | **NON DÉTERMINÉ** — seule trace : `data/audit/europeana-cover-benchmark/` (88 Ko) | inconnu | aucun module identifié | idem | inconnu | EXPÉRIMENTAL / NON DOCUMENTÉ |
| Wikidata Action API (variante) | **NON DÉTERMINÉ** — dossier `data/audit/wikidata-action-api-cover-benchmark/` distinct du module `acquisition/wikidata.py` (qui utilise `Special:EntityData`, pas l'Action API `w/api.php`) | inconnu | aucun module identifié | idem | inconnu | EXPÉRIMENTAL / NON DOCUMENTÉ |

## 2. Sources officiellement documentées et actives

### 2.1 Open Library

Choisie et évaluée dès DEV-13.4 (`.claude/logs/DEV-13.4 — ÉVALUATION ET SÉLECTION DES SOURCES.md`). Utilisée sous quatre formes distinctes et complémentaires, toutes exclusivement par **identifiant exact** (jamais de correspondance par nom pour les dumps/details/covers) :

1. **Search API** (`https://openlibrary.org/search.json`) — seule interface interrogée par mots-clés (langue, sujet). C'est elle qui *découvre* les éditions candidates.
2. **Bulk dumps** (Authors, Works) — fichiers plats TSV/JSON compressés téléchargés manuellement une fois, puis interrogés localement en un seul passage séquentiel (`extract_authors_by_key`, `extract_works_by_key`) par correspondance stricte sur la colonne `key`. Confirmé présents : `data/raw/openlibrary/authors` (742 Mo) et `.../works` (3,8 Go), cités par `.claude/logs/DEV-16.8` §C.
3. **Details API** (`/works/<key>.json`, `/books/<key>.json`) — un enregistrement à la fois, par clé exacte, pour affiner `page_count`/`isbn`/`publisher`/`summary` au-delà de ce que renvoie la Search API.
4. **Covers API** (`https://covers.openlibrary.org/b/id/<cover_id>-L.jpg`) — téléchargement d'image par identifiant entier.

Toutes les requêtes portent un User-Agent identifié (`PRIMATIS-Data-Seeding/0.1 (<contact email>)`) — condition imposée par la fonction elle-même (`ValueError` si `contact` ne contient pas `@`).

### 2.2 Wikidata

Utilisée **uniquement** pour `Author.nationality`, via une chaîne d'identifiants exacts documentée dans `normalization/wikidata.py` : `author_key` OL → `remote_ids.wikidata` (champ du record Author déjà acquis) → entité Wikidata exacte → claim `P27` (pays de citoyenneté) → entité pays exacte → label anglais. Résolution volontairement conservatrice : `None` si 0 ou ≥2 valeurs `P27`, ou si le pays référencé n'est pas une entité `P31 ∈ {Q6256, Q3624078}` (pays/état souverain moderne — exclut les entités historiques).

### 2.3 Bpost

Fichier Excel officiel (mentionné `BPOST_SOURCE_PAGE = "https://www.bpost.be/fr/outil-de-validation-de-codes-postaux"`). Archivé par SHA-256 avant toute lecture (`archive_source_file`), jamais modifié en place.

## 3. Sources non documentées détectées dans `data/audit/`

Les noms de fichiers `data/audit/google-books-*-benchmark*.csv`, `data/audit/europeana-cover-benchmark/`, `data/audit/wikidata-action-api-cover-benchmark*/` désignent sans ambiguïté trois sources externes (Google Books API, Europeana, l'API Action de Wikidata — distincte de l'API `Special:EntityData` utilisée par le code actuel) qui **n'apparaissent dans aucun module `src/`** et **dans aucun `.claude/logs/DEV-13*` ou `DEV-16*`** (recherche `grep` négative). Elles semblent avoir servi à un **benchmark de sources de couvertures alternatives** (noms explicites : `*-cover-benchmark*`), probablement dans le cadre d'une tentative d'amélioration du taux de couverture (`cover_coverage` = 0,1179 sur le profil full — voir [11-covers.md](./11-covers.md) et [20-incoherences-et-risques.md](./20-incoherences-et-risques.md)), mais aucune preuve ne permet de dater cette activité, de l'attribuer à une tâche précise, ou de confirmer si elle a été jugée concluante. **NON DÉTERMINÉ.**

data-seeding.md (règle « Source evaluation ») exige que toute source retenue soit documentée ; ces trois sources ne le sont pas et ne semblent, de plus, jamais avoir été *retenues* dans le pipeline exécutable — elles restent un artefact d'exploration, jamais intégré.

## Rapports liés

- [Acquisition](./06-acquisition.md)
- [Covers](./11-covers.md)
- [Caches et replay](./12-caches-et-replay.md)
- [Incohérences et risques](./20-incoherences-et-risques.md)
