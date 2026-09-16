# Caches et replay — data-seeding

[Retour au rapport général](./general.md)

## 1. Caches réellement câblés au pipeline (ACTIFS)

### 1.1 Cache Search API par langue (small/medium)

```text
Localisation : data/raw/openlibrary/<profile>/search_<lang>.json
Clé          : (profile, langue)
Format       : payload JSON complet de la Search API, indenté, sha256 calculé
Mécanisme    : has_complete_snapshot() vérifie la présence des N fichiers
               attendus -> si complet, reuse_openlibrary_snapshot() relit
               sans appel réseau ; sinon acquire_openlibrary() télécharge
Manifest     : data/raw/openlibrary/<profile>/manifest.json
               (source, contact, purpose, raw_files[], selected_count,
               language_counts)
```

### 1.2 Cache par clé exacte (Authors dump, Works dump, Editions Details, Wikidata)

```text
Authors  : extraction ciblée d'un dump bulk -> data/validated/<profile>/authors_selected.jsonl
           + authors_selected_manifest.json (requested_keys, matched_keys, missing_keys, source_sha256)
Works    : idem -> works_selected.jsonl / works_selected_manifest.json
Editions : cache PAR CLÉ -> data/raw/openlibrary/<profile>/editions/<edition_key>.json
           + manifest.json (reused_keys / fetched_keys / sha256 par clé)
Wikidata : cache PAR QID -> data/raw/wikidata/<profile>/{authors,countries}/<QID>.json
           + manifest.json
```

Clé d'idempotence : `has_reusable_authors_snapshot()`/`has_reusable_works_snapshot()` comparent le jeu de clés **demandé** (`required_keys`) au jeu déjà **enregistré** dans le manifest — si identique, **0 lecture du dump** (les dumps font 742 Mo à 3,8 Go ; le module le documente explicitement comme motivation de performance). Si le jeu de clés change (nouveau candidat), le dump est relu en un seul passage séquentiel.

### 1.3 Cache de lot (`pipeline/batch.py`, DEV-16.3/16.4)

```text
Localisation : data/validated/batches/<batch_id>/raw_<label>.json
               (label = "generic" ou "socle:<author_key>")
Manifest     : data/validated/batches/<batch_id>/raw_manifest.json
               { criteria, reference_date, queries[{label,url}], sha256{}, acquired_at }
Rejeu        : _manifest_matches() compare EXACTEMENT criteria + reference_date
               + la liste (label,url) des requêtes prévues -> si identique ET
               tous les fichiers raw_*.json présents, reuse_cache=True et
               AUCUN appel réseau n'est fait (payload_fetcher jamais invoqué)
```

Une seule différence dans `criteria` (langue, catégorie, count, `use_editorial_socle`) change le `batch_id` (fonction déterministe, humainement lisible — jamais un UUID opaque) et donc invalide implicitement le cache — pas de collision possible entre deux critères différents pointant vers le même cache.

### 1.4 Cache de couverture (`pipeline/full_build.py`)

Le fichier `ol-cover-<cover_id>.jpg` lui-même **est** le cache : `asset_path.is_file()` est vérifié avant tout appel réseau ; aucun manifest séparé.

## 2. Preuve d'idempotence réelle (pas seulement théorique)

`.claude/logs/DEV-16.8...md` §D documente une reconstruction complète du profil `full` (90 lots, catalogue de 15 000 Titles) dans un répertoire de test isolé, avec un `payload_fetcher`/`cover_fetcher` qui lève une exception bloquante au moindre appel réseau réel :

```text
Exécution : 5,4 s, exit 0, 0 exception levée -> 0 appel réseau réel sur
  les 90 lots (tous cache hit)
SHA-256 (reconstruction vs référence) : titles.csv, authors.csv, copies.csv,
  title_authors.csv, title_genres.csv, genres.csv, quarantine.jsonl -> TOUS IDENTIQUES
```

C'est la preuve la plus forte disponible dans le projet que le mécanisme de cache/replay fonctionne réellement pour le profil le plus volumineux, et qu'il a été vérifié empiriquement (pas simplement supposé) — reproduit une seconde fois avec succès lors de cet audit via `pytest -q` (506 tests, aucun accès réseau, 6,81 s).

## 3. Caches NON câblés au pipeline (orphelins)

```text
data/cache/en-mass-editorial-engine/    1820 fichiers (jpeg+json)
data/cache/fr-mass-editorial-engine/    4264 fichiers (jpeg+json)
data/cache/nl-mass-editorial-engine/    1249 fichiers (jpeg+json)
data/cache/final-master-cover-rescue/       7657 fichiers (json+jpeg)
data/cache/final-master-cover-rescue-v2/    7291 fichiers (json+jpeg)
data/cache/page-count-enrichment/           4574 fichiers (json uniquement)
data/cache/google-books-final-rescue-benchmark/  389 fichiers (json+jpeg+png)
```

Aucun de ces sept dossiers n'est référencé par un chemin en dur ni par un motif de nom dans `src/`, `tests/` ou `scripts/` (recherche `grep` négative pour `mass-editorial-engine`, `page-count-enrichment`, `final-master-cover-rescue`, `google-books`). Leur format (un fichier JSON par clé, parfois accompagné d'une image) **ressemble** structurellement au mécanisme de cache par clé décrit en §1.2, ce qui suggère (**INFÉRÉ**) qu'ils ont été produits par des scripts similaires, aujourd'hui absents du dépôt (jamais commités, potentiellement supprimés après exécution). Voir [24-historique-technique-visible.md](./24-historique-technique-visible.md).

## 4. Reprise / dépendances

Le seul mécanisme de reprise documenté au niveau nom de fichier de log est `.claude/logs/DEV-13.13 — CORRECTIF REPRISE OPEN LIBRARY SNAPSHOT.md` (correctif sur la reprise du snapshot Open Library small/medium — non relu intégralement dans cet audit, cité pour traçabilité). Le test `tests/test_openlibrary_snapshot_resume.py` (4 tests) couvre ce comportement.

## Rapports liés

- [Acquisition](./06-acquisition.md)
- [Arborescence et fichiers](./02-arborescence-et-fichiers.md)
- [Historique technique visible](./24-historique-technique-visible.md)
