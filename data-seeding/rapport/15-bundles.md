# Bundles — data-seeding

[Retour au rapport général](./general.md)

## 1. Rôle

Un « bundle » est le résultat final, prêt à charger, d'un profil — un ensemble de fichiers CSV déterministes (mêmes entrées ⇒ mêmes fichiers, bit à bit, prouvé pour `full` en DEV-16.8) plus un ou plusieurs rapports JSON. Produit par `export/catalogue_csv.py`, `export/users_csv.py`, `export/scenarios_csv.py`, appelés depuis `pipeline/bundle.py`, `large_build.py`, `full_build.py`, `full_scenarios.py`.

## 2. Fichiers du catalogue (tous profils, `export/catalogue_csv.py`)

| Fichier | Colonnes | Producteur | Consommateur | Rôle |
|---|---|---|---|---|
| `authors.csv` | source_key, full_name, birth_date, death_date, nationality, biography | `export_catalogue_csv()` | `load/postgres.py::_stage_export` | table `author` |
| `genres.csv` | code, label, description | idem | idem | table `genre` (26 lignes fixes, identiques à chaque profil) |
| `titles.csv` | source_key, isbn, title, subtitle, summary, publication_year, language, page_count, publisher, cover_image_url, title_status | idem | idem | table `title` |
| `title_authors.csv` | title_source_key, author_source_key | idem | idem | table `title_author` |
| `title_genres.csv` | title_source_key, genre_code | idem | idem | table `title_genre` |
| `copies.csv` | title_source_key, inventory_code, location, copy_condition, availability_status | `generate_copies()` + export | `load/postgres.py` ; **relu tel quel** par `pipeline/full_scenarios.py::load_copies_csv()` | table `copy` |

Toutes les valeurs vides sont représentées par une chaîne vide `""` dans le CSV (jamais `NULL` littéral) — converties en `NULL` SQL uniquement au chargement (`_none()` dans `load/postgres.py`).

## 3. Fichiers Users (profils avec `include_demo_scenarios=False` aussi — small/medium/full, jamais large)

| Fichier | Colonnes | Rôle |
|---|---|---|
| `bpost_localities.csv` | postal_code, locality | référentiel localités belges (rechargé aussi par `load/users_scenarios.py`) |
| `users.csv` | source_key, email, password_hash, first_name, last_name, phone_number, account_status, member_number, member_status, registration_date, member_expiration_date, blocked_reason, failed_login_count, role_code | table `app_user` (+ `user_role`) |
| `addresses.csv` | source_key, postal_code, locality, street, street_number, box_number, additional_info | table `address` |
| `residences.csv` | user_source_key, address_source_key, start_date, end_date | table `residence` |

**Absents de `data/bundles/large/`** : aucun `users.csv`/`addresses.csv`/`residences.csv` n'a jamais été produit pour `large`, malgré `user_target=500` dans `config/profiles.toml` — voir [19](./19-etat-reel-actuel.md) et [20](./20-incoherences-et-risques.md).

## 4. Fichiers Scénarios (uniquement `full`, via `full_scenarios.py`/`export/scenarios_csv.py`)

| Fichier | Colonnes | Rôle |
|---|---|---|
| `loans.csv` | source_key, user_source_key, inventory_code, loan_date, due_date, return_date, loan_status, notes | table `loan` |
| `reservations.csv` | source_key, user_source_key, title_source_key, title_inventory_code, assigned_inventory_code, fulfilled_by_loan_source_key, reservation_date, expiration_date, reservation_status | table `reservation` |
| `fines.csv` | source_key, loan_source_key, amount, reason, issued_at, fine_status, paid_at, cancelled_at | table `fine` |
| `notifications.csv` | source_key, recipient_user_source_key, loan_source_key, reservation_source_key, fine_source_key, article_source_key, notification_type, title, message, notification_status, created_at, read_at | table `notification` |
| `copy_states.csv` | inventory_code, availability_status | mise à jour de `copy.availability_status` (ON_LOAN/RESERVED) après génération des scénarios |

## 5. Fichiers annexes / traçabilité

```text
bundle_report.json / large_build_report.json / full_build_report.json  -> métriques
deduplication_report.json      -> détail des doublons/candidats/conflits/rejets
scenarios_build_report.json    -> métriques scénarios
provenance.jsonl               -> une ligne JSON par Title accepté (source, batch,
                                   décisions de champ) — DEV-16.3+ uniquement
quarantine.jsonl                -> une ligne par enregistrement quarantiné (source_key,
                                   entity, reason_code, detail) — DEV-16.3+ uniquement
selected_large.jsonl / selected_full.jsonl / openlibrary_selected.jsonl
                                -> snapshot des SelectedEdition retenus (rejoué par
                                   d'autres modules, ex. audit de complétude)
```

## 6. Ordre et dépendances de construction

```text
1. acquisition (RAW)
2. sélection déterministe (SelectedEdition, sha256 du fichier JSONL)
3. normalisation -> déduplication -> mapping -> (quarantaine)
4. generate_copies() — EXIGE le compte exact de Titles du profil (sinon ValueError)
5. generate_synthetic_members() — indépendant du catalogue (Bpost + seed)
6. (full uniquement) generate_demo_scenarios() — EXIGE copies.csv déjà écrit
   (relu à l'identique par pipeline/full_scenarios.py, jamais régénéré)
7. export_catalogue_csv() / export_users_csv() / export_scenarios_csv()
```

## 7. Invariants vérifiés au moment de l'export/construction (pas seulement a posteriori)

```text
distribution.title_count == profile.title_target                (large_build.py, full_build.py)
len(copy_result.copies) == distribution.copy_count                (generate_copies, AssertionError sinon)
edition_dedup.conflicts vide                                       (build_bundle, ValueError sinon)
len(edition_dedup.kept) == profile.title_target                    (idem)
len(mapping.titles) == profile.title_target                        (idem)
password ≥ 12 caractères                                            (build_bundle, ValueError sinon)
```

## 8. Profils

Le contenu exact par profil (nombre de fichiers, présence/absence de Users/Scénarios) est repris en détail dans [14-profils-et-volumetrie.md](./14-profils-et-volumetrie.md).

## Rapports liés

- [Profils et volumétrie](./14-profils-et-volumetrie.md)
- [Chargement en base de données](./16-chargement-base-donnees.md)
- [Caches et replay](./12-caches-et-replay.md)
