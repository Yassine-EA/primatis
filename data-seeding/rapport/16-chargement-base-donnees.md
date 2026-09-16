# Chargement en base de données — data-seeding

[Retour au rapport général](./general.md)

Deux chargeurs indépendants, même discipline (CHECK/APPLY, verrou consultatif, garde-fous de cible) mais tables différentes : `load/postgres.py` (catalogue) et `load/users_scenarios.py` (users + scénarios métier).

## 1. Garde-fous de cible (`load/guard.py`, réutilisés par les deux CLI)

```text
expected_database(profile)          small/medium/large -> primatis_dev, full -> primatis_preview
validate_requested_target()         lève si base demandée != base attendue par le profil,
                                      lève TOUJOURS si base == "primatis_test"
validate_live_database()             en APPLY, revalide que la base RÉELLEMENT connectée
                                      (SELECT current_database()) correspond, et qu'elle
                                      appartient à l'ensemble autorisé
require_apply_confirmation()        --apply exige --confirm-database == nom exact de la base
```

`primatis_test` est **structurellement inatteignable** en écriture par ce mécanisme, quel que soit l'argument fourni — confirmé par `tests/test_load_guard.py`.

## 2. `load/postgres.py::load_catalogue_export()` — catalogue

### 2.1 Ordre des opérations (une seule transaction)

```text
1. require_apply_confirmation()
2. lecture des 6 CSV requis (échoue si l'un manque)
3. psycopg.connect("") -> _live_database() -> validate_live_database()
4. _validate_schema() : vérifie la présence des 6 tables PRIMATIS requises
   + flyway_schema_history existe + 0 migration en échec
5. BEGIN
   pg_advisory_xact_lock(1374139009)      -- sérialise tout chargement concurrent
   CREATE TEMP TABLE seed_*_stage (... ON COMMIT DROP)
   COPY ... FROM STDIN                     -- staging, 6 tables
   _validate_stage()                       -- intégrité référentielle DANS le stage
   _prepare_old_seed_graph()               -- détecte l'ancien seed (Copy LIKE 'PRI-C-%')
   si apply=True : _replace_catalogue()    -- DELETE ancien seed puis INSERT nouveau
   si apply=False : rien (les TEMP tables disparaissent au COMMIT)
   COMMIT
```

### 2.2 Contrôles d'intégrité du stage (`_validate_stage`)

```text
title_author référencé -> title ET author existent dans le stage
title_genre référencé   -> title ET genre existent dans le stage
copy.title_source_key   -> title existe dans le stage
tout title a >=1 title_author dans le stage (redondant avec map_catalogue,
   mais revérifié côté base — défense en profondeur)
tout inventory_code COMMENCE PAR 'PRI-C-'  -- namespace réservé
```

### 2.3 Remplacement sûr (`_prepare_old_seed_graph` + `_replace_catalogue`)

```text
Ancien seed = Copies dont inventory_code LIKE 'PRI-C-%' -> title_id distincts
              -> authors distincts via title_author

Abandon (ValueError, AVANT toute écriture) si :
  - un Loan référence une Copy de l'ancien seed
  - une Reservation référence un Title de l'ancien seed
  - un Author de l'ancien seed est partagé avec un Title NON-seed

Sinon : DELETE copy -> title_genre -> title_author -> title -> author
        (uniquement l'ancien seed, uniquement les Author non réutilisés),
        puis INSERT genre (skip si code déjà présent, jamais de doublon),
        author, title, title_author, title_genre, copy (nextval sur les
        séquences réelles : author_seq, title_seq — jamais un ID choisi
        manuellement).
```

Conforme à `.claude/rules/database.md` (« Loan active uniqueness », séquences PostgreSQL explicites) et à `data-seeding.md` (« Production guard », « Idempotence »).

## 3. `load/users_scenarios.py::load_users_and_scenarios()` — users + scénarios

Même architecture (stage TEMP → validation → transaction), avec en plus :

```text
_ensure_country_and_cities()   INSERT country 'Belgique'/'BE' si absent,
                                 INSERT city (locality, postal_code) manquantes,
                                 résout resolved_city_id pour chaque adresse stagée
_teardown_previous_seed()      identifie les anciens seed users (email LIKE
                                 '%@seed.primatis.invalid' OR member_number LIKE 'M8%'),
                                 refuse (ValueError) si un utilisateur NON-seed a un
                                 Loan/Reservation sur une ressource seed, refuse si
                                 un utilisateur seed a reçu une Notification liée à un
                                 Article (donnée éditoriale manuelle, jamais supprimée
                                 silencieusement), puis supprime Notification -> Fine
                                 -> Reservation -> Loan -> remet les Copy seed à
                                 AVAILABLE -> supprime user_role/residence/address
                                 (uniquement les rues synthétiques connues,
                                 "Rue Démo %", "Avenue Exemple %", etc.) -> app_user
_insert_seed_users()             nextval('app_user_seq'), rôle ROLE_MEMBER (erreur
                                 explicite si le rôle n'existe pas)
_insert_scenarios()             loan -> reservation -> fine -> copy.availability_status
                                 -> notification, dans cet ordre exact (respecte les FK)
```

### 3.1 Contrôles du stage (`_validate_stage`)

```text
namespace users     : email LIKE '%@seed.primatis.invalid' ET member_number LIKE 'M8%'
                       ET role_code = 'ROLE_MEMBER' — sinon rejet en bloc
résidences           : user ET address doivent exister dans le stage
adresses             : locality Bpost doit exister dans le référentiel stagé
Loan                 : user existe, Copy existe ET porte le préfixe 'PRI-C-'
Reservation          : user existe, Copy ancre existe (préfixe 'PRI-C-'), Copy assignée
                        (si présente) appartient au MÊME title que l'ancre, READY exige
                        une Copy assignée
Fine                  : loan_source_key doit exister dans le stage
```

## 4. Idempotence — preuve réelle (pas seulement documentée)

`.claude/logs/DEV-16.8...md` §Q, séquence complète rejouée réellement contre `primatis_preview` :

```text
1. teardown scénarios seul       -> catalogue intact (15000/24000), app_user=loan=0
2. APPLY#2 catalogue              -> previous_seed_titles=15000 (détection correcte),
                                      volumes finaux identiques (0 dérive)
3. APPLY scénarios (post-catalogue)-> volumes identiques (1500/160/60/60/270)
```

Une tentative d'APPLY#2 catalogue **sans** teardown préalable échoue explicitement (`ValueError` : *« Existing seeded Copies are referenced by Loan rows. Scenario teardown must run before catalogue replacement. »*) — confirmé comme comportement attendu du garde-fou, pas un bug.

## 5. Ce qui n'est PAS automatisé par pytest

Aucun test du dépôt n'ouvre de connexion PostgreSQL réelle (`grep -l psycopg tests/*.py` → 0 résultat). Toute la validation CHECK/APPLY décrite ci-dessus a été exécutée **manuellement** lors des sessions DEV-13/DEV-16 et n'est **pas rejouable** par `pytest`. Voir [17-tests.md](./17-tests.md) et [20-incoherences-et-risques.md](./20-incoherences-et-risques.md).

## Rapports liés

- [Bundles](./15-bundles.md)
- [Tests](./17-tests.md)
- [État réel actuel](./19-etat-reel-actuel.md)
