# PRIMATIS — Audit documentaire complet de `data-seeding/`

## 1. Résumé exécutif

`data-seeding/` est un sous-projet Python autonome (`primatis-data-seeding`) qui acquiert, normalise, valide, déduplique, mappe, génère et charge le catalogue bibliographique et les données de démonstration de PRIMATIS dans PostgreSQL. Il ne fait partie ni du runtime Angular/Spring Boot ni du schéma géré par Flyway.

Le projet a connu **deux générations distinctes**, un fait qui structure tout cet audit :

```text
Génération 1 (« DEV-13 ») — commitée dans Git (6 commits, 2026-09-02 → 09-04)
  Profils small (100 Titles) et medium (1000 Titles), chargés dans primatis_dev.

Génération 2 (« DEV-16 ») — PRÉSENTE dans l'arbre de travail mais AUCUN COMMIT GIT
  Profils large (5000 Titles, primatis_dev) et full (15000 Titles/24000 Copies/
  1500 Users/scénarios métier, primatis_preview), réellement construits ET
  chargés en base — confirmé par des requêtes SQL réelles documentées dans
  `.claude/logs/DEV-16.8...md` — mais dont le code source (batch/large/full
  build, quarantaine, provenance, socle éditorial...) et les données restent
  uniquement locaux.
```

506 tests automatisés passent (`pytest -q` → `506 passed in 6.81s`, reproduit pendant cet audit le 2026-09-15). Aucun de ces tests n'ouvre de connexion PostgreSQL réelle : toute validation du chargement en base a été effectuée **manuellement**, hors suite automatisée.

Trois zones représentant environ 740 Mo de données (`data/audit/`, `data/cache/`, `data/covers/final/`) ne correspondent à **aucun** code source actuel ni à aucun rapport `.claude/logs/` — leur origine reste non déterminée.

## 2. Objectif du projet

Préparer un jeu de données bibliographique **crédible et réel** (jamais d'ISBN inventé, auteurs et titres réels issus d'Open Library) pour peupler PRIMATIS à quatre paliers de volumétrie croissants, plus un jeu de comptes et de scénarios métier synthétiques (jamais de données personnelles réelles) pour la démonstration et les tests fonctionnels.

## 3. Périmètre

```text
DANS le périmètre : catalogue (Title/Author/Genre/Copy), users synthétiques,
  scénarios métier (Loan/Reservation/Fine/Notification), chargement PostgreSQL
  CHECK/APPLY, rapports de génération.
HORS périmètre : création/évolution du schéma PostgreSQL (Flyway reste
  l'unique autorité), intégration runtime d'une API externe dans PRIMATIS,
  toute donnée personnelle réelle.
```

## 4. Architecture générale

```text
Sources externes (Open Library, Wikidata, Bpost)
        │  acquisition/*
        v
RAW (data/raw/, data/validated/*.jsonl)
        │  normalization/ + validation/
        v
deduplication/catalogue.py
        │
        v
mapping/catalogue.py (+ genres.py, documentary_categories.py)
        │  (génération 2) quality/quarantine.py
        v
generation/ (copies, users, scenarios) + reference/bpost.py
        │  export/*_csv.py
        v
data/bundles/<profile>/*.csv
        │  load/postgres.py, load/users_scenarios.py
        v
PostgreSQL (primatis_dev / primatis_preview)
```

Détail complet : [01-architecture.md](./01-architecture.md).

## 5. Technologies

```text
Python 3.14.x (pyproject.toml : >=3.14,<3.15)
Dépendances de production : bcrypt 5.0, openpyxl 3.1.5, psycopg[binary] 3.3, xlrd 2.0.2
Dépendances de dev : pytest 9.1, xlwt 1.3
PostgreSQL 17 (primatis_dev, primatis_preview — jamais primatis_test)
Aucun framework HTTP tiers (urllib.request de la stdlib), aucun ORM
```

Détail : [23-dependances.md](./23-dependances.md).

## 6. Arborescence principale

```text
data-seeding/
├── src/primatis_data_seeding/   (62 fichiers .py, ~10 281 lignes)
├── tests/                        (51 fichiers, 506 tests)
├── scripts/                      (3 scripts one-shot documentés)
├── config/profiles.toml
├── reference/catalogue/*.toml    (socle éditorial, 7 langues)
└── data/                         (~5,9 Go : raw, validated, bundles, covers,
                                    cache, audit, backups)
```

Détail : [02-arborescence-et-fichiers.md](./02-arborescence-et-fichiers.md).

## 7. Fonctionnement général

Quatre profils (small/medium/large/full), deux mécanismes d'acquisition (quotas globaux small/medium vs lots langue×catégorie documentaire large/full), un pipeline linéaire commun (acquisition → normalisation → validation → déduplication → mapping → [quarantaine] → génération → export → chargement). Chaque étape produit un artefact intermédiaire tracé (sha256, manifest JSON, rapport JSON) permettant un rejeu sans réseau — prouvé réellement pour le profil `full` (reconstruction bit-à-bit identique en 5,4 s, 0 appel réseau, DEV-16.8 §D).

## 8. Principaux pipelines

| Pipeline | Fichier | Profils | Git |
|---|---|---|---|
| Acquisition small/medium | `pipeline/acquisition.py` | small, medium | commité |
| Bundle small/medium | `pipeline/bundle.py` | small, medium | commité |
| Lot (batch) | `pipeline/batch.py` | brique de large/full | non commité |
| Agrégation Large | `pipeline/large_build.py` | large | non commité |
| Agrégation Full + covers | `pipeline/full_build.py` | full | non commité |
| Users + scénarios Full | `pipeline/full_scenarios.py` | full | non commité |

Détail : [03-points-entree-et-cli.md](./03-points-entree-et-cli.md), [22-flux-complet.md](./22-flux-complet.md).

## 9. Sources de données

Open Library (Search API, dumps bulk Authors/Works, Editions Details, Covers API) — source primaire documentée et active. Wikidata (Author.nationality uniquement, chaîne d'identifiants exacts). Bpost (référentiel officiel des codes postaux belges). Trois sources supplémentaires détectées mais **non documentées** : Google Books, Europeana, une variante de l'API Wikidata — visibles uniquement via des noms de fichiers dans `data/audit/`/`data/cache/`, sans code ni log associé.

Détail : [05-sources-de-donnees.md](./05-sources-de-donnees.md).

## 10. Modèle de données produit

`author`, `genre` (26 codes fixes), `title`, `title_author`, `title_genre`, `copy` (catalogue) ; `app_user`, `address`, `residence` (users, avec `city`/`country` créés à la demande côté chargeur) ; `loan`, `reservation`, `fine`, `notification` (scénarios, `full` uniquement). Aucune table hors de la baseline PRIMATIS à 23 tables n'est jamais créée par ce sous-projet.

## 11. Organisation des fichiers

`src/` en organisation package-by-feature (acquisition/normalization/validation/deduplication/mapping/quality/provenance/generation/reference/export/load/pipeline/audit). `pipeline/bundle.py` est le module pivot réutilisé par les trois autres pipelines de génération 2. Détail : [02](./02-arborescence-et-fichiers.md).

## 12. Profils disponibles

| Profil | Titles | Copies | Users | Scénarios | Base | Statut réel |
|---|---|---|---|---|---|---|
| small | 100 | 160 | 25 | non | primatis_dev | chargé, clos (DEV-13) |
| medium | 1 000 | 1 600 | 100 | non | primatis_dev | chargé, clos (DEV-13), seul avec audit de complétude exhaustif |
| large | 5 000 | 8 000 | **0 généré** (500 déclaré) | non | primatis_dev | chargé (DEV-16.4), Users jamais produits |
| full | 15 000 | 24 000 | 1 500 | oui (160 Loans/60 Reservations/60 Fines/270 Notifications) | primatis_preview | chargé et vérifié en base (DEV-16.8) |

Détail : [14-profils-et-volumetrie.md](./14-profils-et-volumetrie.md).

## 13. Système de covers (rapport critique)

Trois emplacements physiques distincts et incompatibles :

```text
primatis-web/public/covers/catalogue/  31 fichiers COMMITÉS (30 covers + .gitkeep,
                                         medium, DEV-13.19.E) + 7079 fichiers PRÉSENTS
                                         mais NON commités (copie de data/covers/full/,
                                         mécanisme de copie non retrouvé dans le code)
data-seeding/data/covers/full/          7108 fichiers (ol-cover-<id>.jpg), pipeline
                                         actif (full_build.py) ; SEULS 1728 sont
                                         réellement référencés par titles.csv (full) ;
                                         5380 orphelins présents mais non référencés
data-seeding/data/covers/final/         10578 fichiers (cover-<sha256>.jpg) — AUCUNE
                                         référence dans le code, AUCUN log — origine
                                         non déterminée
```

`cover_coverage` réel : 0 (small/large), 3 % (medium), 11,79 % (full, exact : 1768/15000). Détail complet, y compris l'anomalie documentée du champ `covers` de `full_build_report.json` : [11-covers.md](./11-covers.md).

## 14. Système d'auteurs

Toujours dérivé d'un `author_key` Open Library réel (jamais synthétisé), réconcilié entre forme nue et forme préfixée (`canonical_author_key`). Enrichi (dates, biographie) via le dump bulk Authors ; nationalité via Wikidata (P27 exact, jamais dérivée du nom/langue). `biography_coverage` full = 0,0002 (2/11 518) après un filtre linguistique volontairement strict (`is_confident_french`) ; `nationality_coverage` full = 0,0 (pipeline Wikidata apparemment jamais exécuté pour ce profil). Détail : [09-auteurs.md](./09-auteurs.md).

## 15. Système de titres

Toujours dérivé d'une édition Open Library réelle. `page_count` reste à 0 % de couverture sur small/large/full car `pipeline/full_build.py`/`large_build.py` n'exposent structurellement aucun paramètre `edition_records` — la fonctionnalité existe (testée) mais n'est câblée que dans `pipeline/bundle.py` (medium l'utilise, d'où 80,4 % de couverture sur ce seul profil). Détail : [08-titres.md](./08-titres.md), [20-incoherences-et-risques.md](./20-incoherences-et-risques.md).

## 16. Bundles

CSV déterministes (bit-à-bit reproductibles, prouvé pour `full`) : `authors/genres/titles/title_authors/title_genres/copies.csv` (catalogue, tous profils), `users/addresses/residences/bpost_localities.csv` (small/medium/full — **absents pour large**), `loans/reservations/fines/notifications/copy_states.csv` (full uniquement). Détail : [15-bundles.md](./15-bundles.md).

## 17. Chargement en base

CHECK (lecture seule, défaut) / APPLY (`--apply --confirm-database <exact>`). Garde-fous : profil→base autorisée fixe, `primatis_test` interdit sans condition, verrou consultatif PostgreSQL, staging en tables `TEMP`, validation référentielle avant toute écriture, remplacement de l'ancien seed fail-closed (abandon si une donnée manuelle en dépend). Idempotence prouvée réellement (0 dérive après teardown + réapplication complète, DEV-16.8 §Q). Détail : [16-chargement-base-donnees.md](./16-chargement-base-donnees.md).

## 18. Tests

506 tests, 51 fichiers, 0 échec (vérifié pendant cet audit). Couvrent intégralement la logique pure Python. **Aucun test n'ouvre de connexion PostgreSQL réelle** — le chargement en base n'est validé que manuellement. Détail : [17-tests.md](./17-tests.md).

## 19. Configuration

`config/profiles.toml` (4 profils), 2 variables d'environnement obligatoires (`PRIMATIS_OPENLIBRARY_CONTACT`, `PRIMATIS_SEED_USER_PASSWORD`), variables `libpq` standard pour PostgreSQL — aucun identifiant en dur dans le code. Détail : [04-configuration.md](./04-configuration.md).

## 20. État actuel réel

DEV-13 (small/medium) : **clos**. DEV-16 (large/full) : **fonctionnellement complet** selon ses propres logs (DEV-16.8 : tous les hard gates satisfaits, verdict « READY FOR REVIEW »), mais **non formellement clôturé** (le rapport lui-même le précise) et le tableau de suivi central (`development-status.md`) n'a pas encore été mis à jour pour refléter DEV-16.7/16.8. **Aucune partie de la génération 2 n'est commitée dans Git.** Détail : [19-etat-reel-actuel.md](./19-etat-reel-actuel.md).

## 21. Principales incohérences

Voir le tableau complet en [20-incoherences-et-risques.md](./20-incoherences-et-risques.md). Points saillants : génération 2 entièrement non commitée (CRITIQUE) ; ~740 Mo de données orphelines sans code producteur (CRITIQUE) ; `page_count`/`nationality` structurellement non alimentés sur large/full malgré un mécanisme existant et testé (MAJEUR) ; profil `large` sans Users malgré une configuration qui le laisse supposer (MAJEUR) ; `data/README.md` obsolète (MAJEUR) ; écart entre les 4 clés `application_setting` documentées dans la gouvernance et les 6 réellement migrées (MAJEUR).

## 22. Principaux risques

- Perte possible de tout le travail de génération 2 (code + données) en cas d'incident sur l'espace de travail, faute de commit Git.
- Confusion possible entre les trois dossiers de covers si un futur travail s'appuie sur `data/covers/final/` en le croyant actif.
- Absence de test PostgreSQL automatisé : une régression du chargeur ne serait détectée qu'en exécution manuelle.
- Documentation de gouvernance (`development-status.md`, `data/README.md`, liste des 4 clés `application_setting`) en retard sur l'état réel du code et du schéma.

## 23. Éléments actifs / historiques / expérimentaux

```text
ACTIF        : tout src/primatis_data_seeding/, tests/, scripts/, config/,
               reference/, data/raw/, data/validated/, data/bundles/,
               data/covers/full/ (partiellement référencé)
HISTORIQUE   : data/backups/*.dump, guard.py (superseded par load/guard.py),
               data/README.md (obsolète)
EXPÉRIMENTAL / ORIGINE NON DÉTERMINÉE :
               data/audit/, data/cache/*, data/covers/final/
```

## 24. Commandes essentielles

```bash
cd data-seeding && python -m pip install -e ".[dev]"
pytest -q
python -m primatis_data_seeding.pipeline.acquisition --profile medium --bpost-xlsx <fichier>
python -m primatis_data_seeding.pipeline.bundle --profile medium --reference-date 2026-09-04
python -m primatis_data_seeding.load.cli --profile medium --export-dir data/bundles/medium
python -m primatis_data_seeding.audit.completeness --profile medium
```

Liste complète : [21-commandes-utiles.md](./21-commandes-utiles.md).

## 25. Schéma global ASCII

```text
                 ┌───────────────────────────┐
                 │ Open Library · Wikidata ·  │
                 │ Bpost (sources externes)   │
                 └─────────────┬─────────────┘
                               │ acquisition/*
                               v
        data/raw/  ·  data/validated/*.jsonl  ·  data/validated/batches/
                               │
                               v
      normalization/  →  validation/  →  deduplication/  →  mapping/
                               │
                     (génération 2) quality/quarantine.py
                               │
                               v
        generation/ (copies · users · scenarios)  +  reference/bpost.py
                               │ export/*_csv.py
                               v
                data/bundles/<profile>/*.csv + rapports JSON
                               │ load/postgres.py · load/users_scenarios.py
                               v
                  PostgreSQL — primatis_dev / primatis_preview
```

## Rapports détaillés

- [01 — Architecture](./01-architecture.md)
- [02 — Arborescence et fichiers](./02-arborescence-et-fichiers.md)
- [03 — Points d'entrée et CLI](./03-points-entree-et-cli.md)
- [04 — Configuration](./04-configuration.md)
- [05 — Sources de données](./05-sources-de-donnees.md)
- [06 — Acquisition](./06-acquisition.md)
- [07 — Normalisation et validation](./07-normalisation-et-validation.md)
- [08 — Titres](./08-titres.md)
- [09 — Auteurs](./09-auteurs.md)
- [10 — Genres](./10-genres.md)
- [11 — Covers](./11-covers.md)
- [12 — Caches et replay](./12-caches-et-replay.md)
- [13 — Déduplication](./13-deduplication.md)
- [14 — Profils et volumétrie](./14-profils-et-volumetrie.md)
- [15 — Bundles](./15-bundles.md)
- [16 — Chargement en base de données](./16-chargement-base-donnees.md)
- [17 — Tests](./17-tests.md)
- [18 — Audits et rapports existants](./18-audits-et-rapports-existants.md)
- [19 — État réel actuel](./19-etat-reel-actuel.md)
- [20 — Incohérences et risques](./20-incoherences-et-risques.md)
- [21 — Commandes utiles](./21-commandes-utiles.md)
- [22 — Flux complet](./22-flux-complet.md)
- [23 — Dépendances](./23-dependances.md)
- [24 — Historique technique visible](./24-historique-technique-visible.md)
