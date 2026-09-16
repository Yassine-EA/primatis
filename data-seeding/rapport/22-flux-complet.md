# Flux complet — data-seeding

[Retour au rapport général](./general.md)

## 1. Diagramme générique

```text
SOURCE EXTERNE (Open Library Search/Details/Covers, dumps bulk Authors/Works, Wikidata, Bpost)
      │
      v
ACQUISITION (acquisition/*.py) — User-Agent identifié, cache par clé/langue, sha256
      │
      v
CACHE RAW (data/raw/, data/validated/<profile>/*.jsonl, data/validated/batches/<id>/)
      │
      v
NORMALISATION (normalization/*.py) — NFC, ISBN, langue, dates, mojibake, pagination
      │
      v
VALIDATION (validation/catalogue.py) — structure/longueur/enum/checksum
      │
      v
DÉDUPLICATION (deduplication/catalogue.py) — exact source_key, exact ISBN, candidats jamais fusionnés
      │
      v
MAPPING (mapping/catalogue.py, genres.py) — PrimatisTitleRow/AuthorRow, taxonomie Genre fixe
      │
      v
ACCEPTED ──────┬────────── REJECTED (mapping.rejections, jamais chargé)
      │        └────────── (candidats de dédup, jamais chargé ni fusionné)
      v
QUALITÉ/QUARANTAINE (quality/quarantine.py, génération 2 uniquement)
      │
      ├──> QUARANTINED (jamais chargé, cascade Author→Title)
      v
ENRICHISSEMENT (optionnel : Authors/Works dump, Wikidata, Editions Detail — selon le pipeline)
      │
      v
SÉLECTION / TRIM déterministe (large/full : tri par source_key, coupe au compte exact du profil)
      │
      v
GÉNÉRATION (generation/copies.py, users.py, scenarios.py) — déterministe, seed explicite
      │
      v
BUNDLE (export/*_csv.py → data/bundles/<profile>/*.csv + rapports JSON)
      │
      v
LOAD DB (load/postgres.py, load/users_scenarios.py) — CHECK puis APPLY, garde-fous, verrou, idempotent
```

## 2. Scénario 1 — `small`/`medium` (mécanisme A, `pipeline/bundle.py`)

```text
1. pipeline/acquisition.py --profile medium --bpost-xlsx ...
     -> data/validated/medium/openlibrary_selected.jsonl (+ authors/works/editions/wikidata
        selon les options --fetch-*/--authors-dump activées)
2. pipeline/bundle.py --profile medium --reference-date ...
     normalize_selected_catalogue() -> deduplicate_* -> map_catalogue()
     (pas de quality/quarantine.py à cette étape — chemin DEV-13 non modifié)
     generate_copies() + generate_synthetic_members()
     export_catalogue_csv() + export_users_csv() + export_scenarios_csv(vide, scénarios désactivés)
     -> data/bundles/medium/*.csv + bundle_report.json
3. load/cli.py --profile medium --export-dir data/bundles/medium [--apply --confirm-database primatis_dev]
     -> table title/author/genre/title_author/title_genre/copy dans primatis_dev
```

## 3. Scénario 2 — `large` (mécanisme B agrégé, `pipeline/large_build.py`)

```text
1. scripts/dev164_build_large.py définit 29+8 BatchCriteria (langue × catégorie documentaire,
   avec socle éditorial pour amorcer chaque langue)
2. build_large_bundle() : pour chaque lot, acquire_batch_candidates() (cache par lot,
   dédup GLOBALE via seen_editions/seen_valid_isbns partagés entre lots)
   -> total_received=5413 (mesuré)
3. normalize_selected_catalogue() SANS enrichissement dump (pas de author_records/work_records
   passés dans ce script) -> deduplicate_* -> map_catalogue() -> apply_text_quality_policy()
   -> accepted_pre_trim=5402, trim déterministe -> accepted_final=5000 EXACT
4. generate_copies(profile="large") -> 8000 Copies (distribution exacte)
5. export_catalogue_csv() UNIQUEMENT (pas de Users/Scénarios pour ce profil — voir 20-incoherences.md)
   -> data/bundles/large/*.csv + large_build_report.json + provenance.jsonl + quarantine.jsonl
6. load/cli.py --profile large --export-dir data/bundles/large --apply --confirm-database primatis_dev
   -> chargé dans primatis_dev (confirmé DEV-16.4/16.8 : title=5000)
```

## 4. Scénario 3 — `full` (mécanisme B agrégé + covers + enrichissement + scénarios)

```text
1. scripts/dev165_build_full.py --with-enrichment --with-covers définit 46 BatchCriteria
   (FR sur 9 catégories dès le départ, headroom pour compenser le recouvrement mesuré sur large)
2. build_full_bundle() :
   a. 90 lots agrégés (dédup globale) -> source_records_received=198801,
      unique_candidates_inter_batch=15187
   b. normalize_selected_catalogue(author_records=<dump Authors>, work_records=<dump Works>)
      -> enrichissement dates/bio/summary réel
   c. deduplicate_* -> validated=15187, merged_losers=0
   d. map_catalogue() -> rejected=0 ; apply_text_quality_policy() -> quarantined=16
   e. tri déterministe -> accepted_pre_trim=15171, trimmed=171, accepted_final=15000 EXACT
   f. pipeline covers (cover_id -> téléchargement/réutilisation -> validate_cover_candidate())
      -> 1728 covers valides référencées sur 7661 candidates
   g. generate_copies(profile="full") -> 24000 Copies (distribution exacte)
   h. export_catalogue_csv() + provenance.jsonl (origin_batch_id exact par Title) + quarantine.jsonl
      -> data/bundles/full/*.csv + full_build_report.json
3. scripts/dev1651_build_full_scenarios.py :
   a. lit les réglages métier EN DIRECT depuis primatis_preview.application_setting
   b. load_copies_csv(data/bundles/full/copies.csv) -- relit, ne régénère JAMAIS le catalogue
   c. generate_synthetic_members(count=1500, seed=1651) + generate_demo_scenarios(...)
   d. export_users_csv() + export_scenarios_csv()
      -> data/bundles/full/{users,addresses,residences,loans,reservations,fines,
         notifications,copy_states}.csv + scenarios_build_report.json
4. load/cli.py --profile full --apply --confirm-database primatis_preview   (catalogue)
   load/users_scenarios_cli.py --profile full --apply --confirm-database primatis_preview (users+scénarios)
   -> chargé et vérifié dans primatis_preview (DEV-16.8 §N/§O/§P : tous PASS, 0 dérive)
```

## 5. Scénario 4 — reconstruction sans réseau (preuve d'idempotence, DEV-16.8 §D/§E)

```text
1. Rejeu de scripts/dev165_build_full.py dans un répertoire scratch, avec un
   payload_fetcher/cover_fetcher qui lève immédiatement une exception au moindre
   appel réseau réel (NetworkAccessForbidden, sous-classe d'AssertionError)
2. Exécution : 5,4 s, exit 0, 0 exception levée -> tous les 90 lots servis depuis
   data/validated/batches/<batch_id>/raw_*.json (cache hit total)
3. Comparaison SHA-256 scratch vs référence : titles/authors/copies/title_authors/
   title_genres/genres.csv + quarantine.jsonl -> TOUS IDENTIQUES
4. Rejeu de scripts/dev1651_build_full_scenarios.py -> loans/reservations/fines/
   notifications/addresses/residences/copy_states.csv -> TOUS IDENTIQUES ;
   users.csv identique hors password_hash (salage BCrypt aléatoire, attendu)
```

## Rapports liés

- [Architecture générale](./01-architecture.md)
- [Acquisition](./06-acquisition.md)
- [Profils et volumétrie](./14-profils-et-volumetrie.md)
- [Caches et replay](./12-caches-et-replay.md)
