# Tests — data-seeding

[Retour au rapport général](./general.md)

## 1. Exécution réelle (reproduite pendant cet audit)

```bash
cd data-seeding && .venv/bin/python -m pytest
```

```text
506 passed in 6.81s
```

Résultat obtenu le 2026-09-15 pendant la rédaction de ce rapport (commande non destructive, aucun accès réseau, aucune écriture hors `.pytest_cache/`). Correspond exactement au chiffre déjà annoncé par `.claude/logs/DEV-16.8...md` §F (*« 506 tests exécutés [...], 0 échec, 0 erreur [...] confirme le total connu depuis DEV-16.5.1 »*) — reproductibilité confirmée indépendamment.

`grep -c "^def test_"` sur `tests/*.py` compte 490 fonctions — l'écart avec 506 s'explique par des tests paramétrés (`@pytest.mark.parametrize`) ou des méthodes de classes de test non capturées par l'ancre `^def`.

## 2. Inventaire (51 fichiers, 7 282 lignes)

Aucun `conftest.py` dans `tests/` — pas de fixture partagée, chaque fichier est autonome.

| Groupe fonctionnel | Fichiers | But |
|---|---|---|
| Normalisation générique | `test_normalization_text.py`, `test_isbn.py`, `test_language.py`, `test_language_detection.py`, `test_text_quality.py` | garantit les règles de nettoyage/validation de bas niveau |
| Open Library — acquisition | `test_openlibrary_acquisition.py`, `test_openlibrary_authors_acquisition.py`, `test_openlibrary_details_acquisition.py`, `test_openlibrary_works_acquisition.py`, `test_openlibrary_covers.py`, `test_openlibrary_snapshot_resume.py`, `test_openlibrary_isbn_uniqueness.py`, `test_openlibrary_all_isbns_uniqueness.py`, `test_acquisition_contract.py` | contrat des fonctions `fetch_*`/`acquire_*`/`load_*` (fetchers injectés en test, jamais de vrai réseau) |
| Open Library — normalisation | `test_openlibrary_normalization.py` (38 tests, le plus fourni de cette catégorie) | `normalize_*_record`, dates, pagination, année |
| Wikidata | `test_wikidata_acquisition.py`, `test_wikidata_normalization.py` | résolution `nationality`, cas ambigus/historiques exclus |
| Bpost | `test_bpost_normalization.py`, `test_bpost_reference.py` | lecture .xls/.xlsx, résolution de colonnes, dédup |
| Mapping / genres | `test_catalogue_mapping.py`, `test_genre_mapping.py`, `test_documentary_categories.py` | contrat `map_catalogue`, alias exacts |
| Déduplication | `test_deduplication_editions.py`, `test_deduplication_authors.py` | scénarios de doublon/candidat/conflit |
| Qualité / quarantaine | `test_quarantine.py`, `test_record_provenance.py`, `test_source_provenance.py` | cascade Author→Title, décisions de champ tracées |
| Génération | `test_copy_generation.py`, `test_user_generation.py`, `test_scenario_generation.py`, `test_scenario_title_anchor.py` | distribution déterministe, invariants métier (READY/assignation, etc.) |
| Export CSV | `test_export_catalogue_csv.py`, `test_users_export.py`, `test_scenario_export.py` | forme exacte des fichiers produits |
| Bundle (small/medium) | `test_bundle.py` (1 136 lignes, 53 tests — le plus gros fichier du dépôt) | bout-en-bout du profil small/medium |
| Pipelines DEV-16 | `test_batch_pipeline.py` (16), `test_large_build.py` (7), `test_full_build.py` (11), `test_full_scenarios_build.py` (6) | bout-en-bout des générations de lots/large/full |
| Chargement / garde-fous | `test_load_contract.py`, `test_load_guard.py`, `test_guard.py`, `test_users_scenarios_cli.py`, `test_users_scenarios_loader_contract.py`, `test_users_scenarios_loader_stage.py`, `test_users_scenarios_loader_summary.py` | contrats/garde-fous — **jamais** de connexion PostgreSQL réelle |
| Audit / complétude | `test_completeness_audit.py` (30 tests) | matrice de complétude, tous les statuts possibles |
| Divers infra | `test_rate_limit.py`, `test_profiles.py`, `test_editorial_selection.py`, `test_cover_validation.py` | `RateLimiter`/`with_retries`, chargement de `profiles.toml`, socle éditorial, validation d'octets image |

## 3. Ce qui N'EST PAS testé automatiquement

```text
grep -l psycopg tests/*.py    -> AUCUN résultat
```

Aucun test n'ouvre de connexion PostgreSQL réelle. Les fichiers `test_load_contract.py`/`test_users_scenarios_loader_contract.py` ne vérifient que des **constantes** (`SEED_INVENTORY_PREFIX == "PRI-C-"`, `ADVISORY_LOCK_KEY > 0`, `SEED_USER_EMAIL_SUFFIX`, `SEED_MEMBER_PREFIX`) — pas un comportement de base réelle. Toute validation CHECK/APPLY contre `primatis_dev`/`primatis_preview` documentée dans les logs DEV-13/DEV-16 (contraintes, séquences, transactions, verrou, idempotence — voir [16](./16-chargement-base-donnees.md)) a été effectuée **manuellement**, hors de la suite automatisée. C'est en tension directe avec `data-seeding.md` (« Tests » : *« Tester selon le besoin [...] génération inventoryCode, génération Copy [...] relations, invariants métier »* et « Dataset validation » : *« Avant chargement [...] contrôler au minimum [...] »*) — voir [20-incoherences-et-risques.md](./20-incoherences-et-risques.md).

## 4. Commande

```bash
cd data-seeding
python -m pip install -e ".[dev]"
pytest                 # équivaut à: pytest -q (addopts dans pyproject.toml)
pytest tests/test_bundle.py -v     # cibler un fichier
```

## Rapports liés

- [Chargement en base de données](./16-chargement-base-donnees.md)
- [État réel actuel](./19-etat-reel-actuel.md)
- [Incohérences et risques](./20-incoherences-et-risques.md)
