# Configuration — data-seeding

[Retour au rapport général](./general.md)

## 1. `config/profiles.toml`

```toml
[profiles.small]
database = "primatis_dev"
title_target = 100
user_target = 25
include_demo_scenarios = false

[profiles.medium]
database = "primatis_dev"
title_target = 1000
user_target = 100
include_demo_scenarios = false

[profiles.large]
database = "primatis_dev"
title_target = 5000
user_target = 500
include_demo_scenarios = false

[profiles.full]
database = "primatis_preview"
title_target = 15000
copy_target = 24000
user_target = 1500
include_demo_scenarios = true
```

Chargé par `config.py::load_profiles()` (chemin résolu en dur : `Path(__file__).resolve().parents[2] / "config" / "profiles.toml"` — donc toujours relatif à l'installation du package, pas au répertoire d'exécution). `copy_target` n'est renseigné que pour `full` dans ce fichier ; pour `small`/`medium`/`large`, la cible réelle de Copies vient de `generation/copies.py::PROFILE_COPY_DISTRIBUTIONS` (voir §5).

## 2. Variables d'environnement

| Variable | Requise par | Contrainte | Usage |
|---|---|---|---|
| `PRIMATIS_OPENLIBRARY_CONTACT` | `pipeline/acquisition.py::main()` (si `--refresh-openlibrary` ou tout `--fetch-*`) | doit contenir `@` | User-Agent HTTP identifié (`PRIMATIS-Data-Seeding/0.1 (<contact>)`) auprès d'Open Library — jamais anonyme |
| `PRIMATIS_SEED_USER_PASSWORD` | `pipeline/bundle.py::main()`, `pipeline/full_scenarios.py` (via `scripts/dev1651...py`) | ≥ 12 caractères | Mot de passe commun des comptes membres synthétiques, haché BCrypt avant export — jamais loggé, jamais en dur |
| variables `libpq` standard (`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, ...) | `load/cli.py`, `load/users_scenarios_cli.py`, `scripts/dev1651_build_full_scenarios.py` | — | `psycopg.connect("")` (chaîne de connexion vide) délègue entièrement à ces variables — aucun identifiant PostgreSQL n'est écrit dans le dépôt |

Contact réellement utilisé en génération 2 (constantes en tête de `scripts/dev164_build_large.py` / `dev165_build_full.py`, PAS une variable d'environnement à cet endroit) : `"dev16.4-large-build@primatis.local"` / `"dev16.5-full-build@primatis.local"` — des adresses fictives `*.local`, jamais une adresse réelle.

## 3. Constantes structurantes (dans le code, pas dans un fichier de config)

| Constante | Valeur | Fichier | Rôle |
|---|---|---|---|
| `PROFILE_LANGUAGE_QUOTAS` | small: FR75/EN10/NL8/DE3/ES2/IT1/LA1 ; medium: ×10 | `acquisition/openlibrary.py` | quotas linguistiques fixes small/medium |
| `PROFILE_COPY_DISTRIBUTIONS` | small 59/28/10/3 ; medium 599/268/100/33 ; large 3001/1332/500/167 ; full 9000/4000/1500/500 | `generation/copies.py` | distribution 1/2/3/5 Copies par Title, par profil |
| `ALLOWED_PROFILE_DATABASES` | small/medium/large→`primatis_dev`, full→`primatis_preview` | `guard.py` **et** `load/guard.py` (dupliqué à l'identique dans les deux fichiers) | garde-fou cible |
| `FORBIDDEN_DATABASES` | `{"primatis_test"}` | idem | interdiction absolue |
| `ADVISORY_LOCK_KEY` | `1_374_139_009` | `load/postgres.py` (réutilisé par `load/users_scenarios.py`) | verrou consultatif PostgreSQL, sérialise les chargements concurrents |
| `SEED_INVENTORY_PREFIX` | `"PRI-C-"` | `load/postgres.py` | espace de noms réservé des `inventoryCode` générés |
| `SEED_USER_EMAIL_SUFFIX` / `SEED_MEMBER_PREFIX` | `"@seed.primatis.invalid"` / `"M8"` | `load/users_scenarios.py` | espace de noms réservé des comptes synthétiques |
| `DOCUMENTARY_CATEGORY_KEYWORDS` / `LANGUAGE_CODES` | 9 catégories / 7 langues | `pipeline/batch.py` | construction des requêtes Search API par lot |
| `DEFAULT_FULL_SCENARIO_COUNTS` | 80 actifs / 20 en retard / 60 rendus tardifs / 40 en attente / 20 prêtes | `generation/scenarios.py` | volumétrie par défaut des scénarios métier |

## 4. Timeouts, tailles de lot, pagination

| Paramètre | Valeur par défaut | Fichier |
|---|---|---|
| `timeout_seconds` (HTTP Open Library / Wikidata) | 30 s | `acquisition/openlibrary.py`, `openlibrary_details.py`, `wikidata.py`, `openlibrary_covers.py` |
| `sleep_seconds` entre requêtes Search small/medium | 0,4 s | `acquisition/openlibrary.py::acquire_openlibrary` |
| `RateLimiter` (génération 2) | intervalle configurable (`0.3` s utilisé dans les scripts Large/Full) | `acquisition/rate_limit.py` |
| `with_retries` | `max_attempts=3, backoff_seconds=5.0` par défaut ; Large `max_attempts=6, backoff_seconds=8.0` ; Full `max_attempts=10, backoff_seconds=10.0` ; covers `max_attempts=5, backoff_seconds=6.0` | `acquisition/rate_limit.py`, scripts |
| `fetch_limit` (docs bruts demandés par requête Search) | `batch.py` défaut 50 ; Large 2000 ; Full 4500 | `pipeline/batch.py`, scripts |
| `socle_fetch_limit` | `batch.py` défaut 20 ; Large 80 ; Full 150 | idem |
| `overfetch_factor` (small/medium) | 4 (surcollecte pour absorber les rejets) | `acquisition/openlibrary.py::acquire_openlibrary` |
| Pagination Search API | `limit` borné à 1000 par appel (`build_search_url` lève sinon) | `acquisition/openlibrary.py` |
| Taille max fichier cover accepté | 5 Mo | `normalization/cover_validation.py::MAX_FILE_SIZE_BYTES` |
| Dimensions cover acceptées | 120×160 à 3000×3000 px | idem |

## 5. Profils — vue consolidée

| Profil | Titles | Copies | Users | Scénarios | Base cible |
|---|---|---|---|---|---|
| `small` | 100 | 160 | 25 | non | `primatis_dev` |
| `medium` | 1 000 | 1 600 | 100 | non | `primatis_dev` |
| `large` | 5 000 | 8 000 | 500 (déclaré ; jamais généré dans les faits — voir [19](./19-etat-reel-actuel.md)) | non | `primatis_dev` |
| `full` | 15 000 | 24 000 | 1 500 | oui | `primatis_preview` |

## 6. Défauts implicites (pas de fichier de config dédié)

- Graine (`seed`) par défaut du bundle small/medium : `13014` (`pipeline/bundle.py::build_parser`, argument `--seed`).
- Graine utilisée pour les scénarios Full : `1651` (constante `SEED` dans `scripts/dev1651_build_full_scenarios.py`).
- Date de référence Large/Full : `2026-09-11` (constante `REFERENCE_DATE` dans les deux scripts DEV-16.4/16.5) ; scénarios Full : `2026-09-12T12:00:00Z`.

## Rapports liés

- [Points d'entrée et CLI](./03-points-entree-et-cli.md)
- [Profils et volumétrie](./14-profils-et-volumetrie.md)
- [Dépendances](./23-dependances.md)
