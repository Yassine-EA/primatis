# Audits et rapports existants — data-seeding

[Retour au rapport général](./general.md)

## 1. Méthode et avertissement

Cette section recense les journaux existants sous `.claude/logs/DEV-13*` et `DEV-16*` (les seuls directement liés à `data-seeding/`). Par souci d'exhaustivité, **tous** les titres sont listés ; seuls certains ont été **lus intégralement** pendant cet audit (précisé pour chacun). Pour les autres, seul le titre et la position chronologique sont garantis — leur contenu détaillé n'est pas repris ici et devrait être relu avant toute décision s'appuyant dessus. **Rappel de la règle d'audit : un rapport historique n'est pas nécessairement la vérité actuelle** — voir [19-etat-reel-actuel.md](./19-etat-reel-actuel.md) pour ce qui est confirmé comme actuel.

## 2. Série DEV-13 — Data seeding (initial, small/medium, commitée dans Git)

| Fichier | Lu intégralement | Contenu (résumé) |
|---|---|---|
| `DEV-13.1 — AUDIT INITIAL DATA-SEEDING.md` | non | audit initial du sous-projet |
| `DEV-13.2 — CONSOLIDATION DECISIONS DATA-SEEDING.md` | non | décisions consolidées |
| `DEV-13.3 — INITIALISATION SOUS-PROJET PYTHON.md` | non (mais reflété par README §« Statut actuel ») | init Python 3.14, bootstrap CLI |
| `DEV-13.4 — ÉVALUATION ET SÉLECTION DES SOURCES.md` | non | choix d'Open Library/Wikidata/Bpost |
| `DEV-13.5 — NORMALISATION ET VALIDATION.md` | non | fondations `normalization/`/`validation/` |
| `DEV-13.6 — DEDUPLICATION BIBLIOGRAPHIQUE.md` | non | fondations `deduplication/catalogue.py` |
| `DEV-13.7 — MAPPING CATALOGUE PRIMATIS.md` | non | fondations `mapping/catalogue.py` |
| `DEV-13.8 — GENERATION DES COPIES ET DISTRIBUTION VOLUMETRIQUE.md` | non | fondations `generation/copies.py` |
| `DEV-13.10 — USERS ADRESSES SYNTHETIQUES ET REFERENTIEL BPOST.md` | non | fondations `generation/users.py`, `reference/bpost.py` |
| `DEV-13.11 — SCENARIOS METIER COHERENTS.md` | non | fondations `generation/scenarios.py` |
| `DEV-13.12 — LOADER POSTGRESQL USERS SCENARIOS TEARDOWN IDEMPOTENCE — REVISED.md` / `DEV-13.12 — COMPLEMENT TESTS LOADER.md` | non | fondations `load/users_scenarios.py` |
| `DEV-13.13 — ACQUISITION REELLE OPEN LIBRARY BPOST GENERATION SMALL.md` + 3 correctifs (XLS officiel, codes postaux numériques, reprise snapshot) | non | première acquisition réelle, profil small |
| `DEV-13.14` (6 fichiers : cible dynamique, contrat list, fixture ISBN, qualité password/ISBN, unicité ISBN, normalisation dedup mapping) | non | durcissement de la sélection Open Library |
| `DEV-13.15 — CORRECTIF PSYCOPG EMPTY PARAMS.md` | non | correctif chargeur |
| `DEV-13.16 — APPLY CONTROLE BUNDLE SMALL IDEMPOTENCE.md` | non | 1er chargement réel small, backup `primatis_dev-before-DEV-13.16.dump` |
| `DEV-13.17 — VERIFICATION APPLICATIVE DATASET SMALL.md` | non | vérification post-chargement small |
| `DEV-13.18.A à .F` (audit généralisation, généralisation pipeline, acquisition+bundle medium, CHECK medium, APPLY+idempotence medium, vérification applicative medium) | non | passage à l'échelle vers medium |
| `DEV-13.19.A à .F` + `FINAL GATE` (audit champs incomplets, enrichissement Authors dump, enrichissement Titles+covers locales, copy location déterministe, acquisition/régénération medium, normalisation author_key) | non | enrichissement qualité medium (dates, bio, covers, localisation), backups `primatis_dev-before-DEV-13.19.E.dump` |
| `DEV-13.20 — DATA COMPLETENESS MEDIUM.md` | non (mais `audit/completeness.py` lu intégralement en tant que code) | audit exhaustif de complétude medium (9072 lignes, cf. `development-status.md`), backup `primatis_dev-before-DEV-13.20.dump` |
| `DEV-13 FINAL GATE — DATA SEEDING.md` | non | clôture DEV-13 : small/medium DONE/PASS |

**État confirmé actuel (recoupé avec `claude-workspace/tracking/development-status.md` ligne 466+ et 1994)** : DEV-13 = DONE, clos, small/medium chargés et validés dans `primatis_dev`. Ce statut est cohérent avec l'état constaté dans `data/bundles/small/` et `data/bundles/medium/` (fichiers présents, rapports cohérents) — **toujours valide** au sens où rien n'indique une régression depuis.

## 3. Série DEV-16 — Volumétrie Large/Full

| Fichier | Lu intégralement | Contenu (résumé) |
|---|---|---|
| `DEV-16.1 — AUDIT INITIAL VOLUMETRIE QUALITE CATALOGUE.md` | non | audit initial avant Large/Full |
| `DEV-16.2 — POLITIQUE CATALOGUE ET CONTRAT QUALITE.md` | non (mais largement cité par les docstrings du code : DEC-16.2/16.3, politique de quarantaine §3.3/§6.4, biographie §4.2) | politique qualité fondatrice de la génération 2 |
| `DEV-16.3 — PIPELINE COLLECTE VALIDATION PAR LOTS.md` | non (idem, très cité par le code : `pipeline/batch.py`, alias de genres, marqueurs de langue) | conception du pipeline par lots |
| `DEV-16.4 — VALIDATION PROFIL LARGE.md` | non (mais son contenu réel est vérifiable directement via `data/bundles/large/large_build_report.json` et les commentaires de `scripts/dev164_build_large.py`) | construction + validation réelle du profil `large` (5000/8000, `primatis_dev`) |
| `DEV-16.5 — CONSTRUCTION VALIDATION PROFIL FULL.md` (+ addendum) | non (contenu recoupé via `full_build_report.json` et `scripts/dev165_build_full.py`) | construction du profil `full` (15000/24000) |
| `DEV-16.5.1 — DEBLOCAGE SCENARIOS FULL ET CONFORMITE BIOGRAPHIES.md` | non (contenu recoupé via `normalization/language_detection.py` docstring + `scenarios_build_report.json`) | correction `is_confident_french()`, déblocage des scénarios Full |
| `DEV-16.5 — CHECKPOINT REPRISE.md` | non | point de reprise intermédiaire |
| `DEV-16.6 — VALIDATION BACKEND SOUS VOLUMETRIE FULL.md` | non (résumé disponible via `development-status.md` ligne 1997) | validation backend Spring Boot sous 15000/24000, bug UTF-8 401 corrigé (DEV-DEC-0077) |
| `DEV-16.7 — CORRECTIONS CIBLEES POST VALIDATION FULL.md` | non (résumé via `DEV-16.8` §A qui le cite : « PASS, 0 correction, 2 dettes reportées ») | corrections ciblées, dettes reportées (contention connexions, warning collation) |
| `DEV-16.8 — REPRODUCTIBILITE TESTS ET REBUILD PREVIEW FINAL.md` | **oui, intégralement** | reconstruction complète sans réseau, correction de la contention HikariCP, rebuild `primatis_preview` depuis zéro, tous les hard gates PASS, verdict `READY FOR REVIEW` (pas encore `DEV-16 FINAL PASS`) |
| `ROADMAP POST DEV-13 — CONSOLIDATION TRACKING DEV-14 A DEV-22.md` | non | plan macro DEV-14→DEV-22, place DEV-16 dans la roadmap |

## 4. État déclaré dans le tracking central (`claude-workspace/tracking/development-status.md`)

Ligne 1997 (table roadmap, lue intégralement pendant cet audit) :

```text
DEV-16 | Volumétrie Large / Full | IN PROGRESS
  Large (DEV-16.4) : DONE, PASS (primatis_dev, 5000/8000).
  Full (DEV-16.5+16.5.1+16.6) : DONE, PASS — bundle construit, CHECK/APPLY/
    idempotence/provenance PASS sur primatis_preview (15000/24000/11518
    authors, 2 biographies FR conservées), scénarios métier Full implémentés
    et chargés, backend validé sous volumétrie Full réelle.
  Reste ouvert : hardening perf/index (DEV-16.7+).
```

**Contradiction documentée** : cette entrée de `development-status.md` ne mentionne ni DEV-16.7 ni DEV-16.8 (qui existent pourtant comme fichiers de log et dont DEV-16.8 est daté après cette mise à jour), et le tableau roadmap qualifie encore DEV-16 d'« IN PROGRESS » avec « reste ouvert DEV-16.7+ » alors que `DEV-16.8` lui-même conclut que *tous* les hard gates sont satisfaits (`READY FOR REVIEW`). Ce n'est pas une erreur de fait, mais une preuve que **le tableau de suivi central n'a pas été mis à jour après DEV-16.7/DEV-16.8** — un décalage de fraîcheur documentaire à corriger, sans remettre en cause la validité des logs DEV-16.7/16.8 eux-mêmes. Voir [20-incoherences-et-risques.md](./20-incoherences-et-risques.md).

## 5. Rapports produits par le code lui-même (pas des logs `.claude/`)

| Chemin | Objectif | État |
|---|---|---|
| `data/validated/small/acquisition_report.json`, `data/validated/medium/acquisition_report.json` | métriques d'acquisition | présents, cohérents |
| `data/validated/medium/completeness_audit.csv` | audit exhaustif de complétude (DEV-13.20) | présent, encore valide |
| `data/validated/full/completeness_audit.csv` | équivalent pour `full` | **absent** — jamais généré pour ce profil malgré son existence pour `medium` |
| `data/bundles/*/{bundle,large_build,full_build,scenarios_build}_report.json` | métriques de génération | tous présents et cohérents entre eux |
| `data/bundles/full/full_build_report.json` champ `regeneration_note` | correction manuelle documentée du champ `covers` | valide, transparent — voir [11-covers.md](./11-covers.md) §7 |
| `data/audit/*` (~230 fichiers) | **NON DÉTERMINÉ** — aucun script producteur retrouvé | HISTORIQUE présumé / non rattachable à un rapport `.claude/logs/` connu |

## Rapports liés

- [État réel actuel](./19-etat-reel-actuel.md)
- [Incohérences et risques](./20-incoherences-et-risques.md)
- [Historique technique visible](./24-historique-technique-visible.md)
