# Covers — data-seeding (rapport critique)

[Retour au rapport général](./general.md)

## Table des matières

- [1. Vocabulaire — ne jamais confondre](#1-vocabulaire--ne-jamais-confondre)
- [2. Les quatre dossiers de covers](#2-les-quatre-dossiers-de-covers)
- [3. Le pipeline actif (`pipeline/full_build.py`)](#3-le-pipeline-actif-pipelinefull_buildpy)
- [4. Mesures réelles, calculées pendant cet audit](#4-mesures-réelles-calculées-pendant-cet-audit)
- [5. `data/covers/final/` — le dossier orphelin](#5-datacoversfinal--le-dossier-orphelin)
- [6. Historique de `primatis-web/public/covers/catalogue/`](#6-historique-de-primatis-webpubliccoverscatalogue)
- [7. Anomalie documentée dans le rapport lui-même](#7-anomalie-documentée-dans-le-rapport-lui-même)
- [8. Ce qui n'existe PAS](#8-ce-qui-nexiste-pas)

## 1. Vocabulaire — ne jamais confondre

| Notion | Définition précise ici |
|---|---|
| **fichier physique présent** | un `.jpg`/`.png` existe réellement sur disque, quel que soit son usage |
| **cover référencée** | un `Title.cover_image_url` non vide dans `titles.csv` pointe vers ce fichier |
| **cover validée** | `validate_cover_candidate()` a retourné `verdict="OK"` (dimensions/format/poids) |
| **cover sélectionnée** | le `cover_id` figure dans `edition.cover_id` d'au moins un candidat retenu (indépendant du téléchargement) |
| **cover téléchargée** | un appel réel à `fetch_cover_image()` a réussi pendant l'exécution auditée |
| **cover effectivement liée à un Title du bundle final** | le fichier existe **et** son URL apparaît dans `titles.csv` du bundle chargé en base |

Ces six notions sont **distinctes** et ne se recouvrent pas nécessairement — voir §4.

## 2. Les quatre dossiers de covers

| Dossier | Nombre de fichiers | Origine | Utilisé par | Statut |
|---|---|---|---|---|
| `data-seeding/data/covers/full/` | 7 108 | `pipeline/full_build.py` (cover pipeline DEV-16.5), naming `ol-cover-<cover_id>.jpg` | `titles.csv` du profil `full` (partiellement, voir §4) | ACTIF |
| `data-seeding/data/covers/final/` | 10 578 | **NON DÉTERMINÉ** — aucune trace dans `src/`, aucun `.claude/logs/DEV-13*`/`DEV-16*`, naming par sha256 (`cover-<hash>.jpg`) | aucun (0 référence dans un `titles.csv` quel qu'il soit) | EXPÉRIMENTAL / OBSOLÈTE |
| `primatis-web/public/covers/catalogue/` (tracké Git) | 31 (30 covers + `.gitkeep`) | matérialisation manuelle DEV-13.19.E (profil medium) | `titles.csv` du profil `medium` (30/30) | ACTIF, HISTORIQUE |
| `primatis-web/public/covers/catalogue/` (non tracké, en plus des 31) | 7 079 | copie du contenu de `data/covers/full/` (mécanisme de copie non retrouvé dans le code — voir §6) | potentiellement le futur déploiement du profil `full` | ACTIF mais NON COMMITÉ |

## 3. Le pipeline actif (`pipeline/full_build.py`)

Seul mécanisme de production de covers **câblé au code courant** :

```text
pour chaque Title final (après trim déterministe à 15000) :
    cover_id = edition.cover_id (Open Library Search API, champ cover_i)
    si cover_id est None -> ignoré (pas de candidat)
    cover_metrics.candidates += 1
    si déjà téléchargé cette exécution (cover_ids_seen) -> duplicates += 1,
        réutilise l'URL si le fichier existe déjà
    si le fichier ol-cover-<id>.jpg existe déjà sur disque -> réutilisé (0 appel réseau)
    sinon -> téléchargement réel (fetch_cover_image, rate-limité, retry x5)
    validate_cover_candidate(bytes) :
        OK      -> écrit sur disque, cover_url_by_title[title] = "/covers/catalogue/ol-cover-<id>.jpg"
        REJECT  -> jamais écrit sur disque, invalid += 1, motif comptabilisé
```

Fail-closed strict : `resolve_cover_image_url()`/l'assignation dans `full_build.py` ne produit **jamais** une URL dont le fichier n'existe pas réellement (revérifié en base par DEV-16.8 §P : *« 0 broken cover reference (1768/1768 fichiers réellement présents sur disque, vérifié programmatiquement) »*).

## 4. Mesures réelles, calculées pendant cet audit

Recalcul indépendant effectué pendant cet audit (script Python ad hoc, lecture seule, sur `data/bundles/full/titles.csv` et les répertoires de covers — reproductible) :

```text
Titles du bundle full                              : 15 000
Lignes Title avec cover_image_url non vide          :  1 768   (11,79 % — exactement cover_coverage=0.1179 du rapport)
cover_id distincts réellement référencés            :  1 728
Basenames référencés MANQUANTS sur disque           :      0   (0/1728 — confirmé, aucune référence cassée)
Fichiers présents dans data/covers/full/            :  7 108
Fichiers présents mais NON référencés (orphelins)   :  5 380   (7108 − 1728)
Fichiers data/covers/full/ ∩ primatis-web tracké Git :      0   (les 30 covers historiques sont un ensemble disjoint)
```

Interprétation des 5 380 fichiers orphelins : **NON DÉTERMINÉ avec certitude** (aucune preuve directe), mais **INFÉRÉ** avec un niveau de confiance élevé qu'il s'agit du résidu d'itérations successives du profil `full` (le pipeline a été rejoué plusieurs fois — DEV-16.5, DEV-16.5.1, DEV-16.8 — avec des tirages/lots/`trim` déterministe différents à chaque itération de développement ; un `cover_id` téléchargé et validé lors d'une itération antérieure, dont le Title correspondant a ensuite été écarté par le tri déterministe des 15 171→15 000 Titles retenus, reste sur disque sans jamais être supprimé — `full_build.py` n'a aucune étape de nettoyage des fichiers devenus orphelins).

Par profil :

| Profil | `cover_coverage` (rapport) | Covers référencées | Dossier de covers dédié |
|---|---|---|---|
| `small` | 0,0 (implicite — champ absent, 0 cover_id résolu) | 0/100 | aucun |
| `medium` | 0,0 (champ absent du rapport, mais mesuré ici : 30/1000 = 3 %) | 30/1000 | aucun (les 30 fichiers vivent directement dans `primatis-web/public/covers/catalogue/`, DEV-13.19.E) |
| `large` | 0,0 | 0/5000 | aucun — le pipeline de covers n'existe pas dans `pipeline/large_build.py` (aucun paramètre `covers_assets_dir`/`cover_fetcher`, confirmé par lecture du fichier) |
| `full` | 0,1179 | 1 768/15 000 (1 728 distincts) | `data/covers/full/` |

## 5. `data/covers/final/` — le dossier orphelin

- 10 578 fichiers, naming `cover-<sha256 64 hex>.jpg` — schéma **de contenu adressable** (déduplication par hash de contenu), différent du schéma `ol-cover-<id>.jpg` produit par le code actuel.
- Échantillon inspecté (`file <path>`) : JPEG valides, dimensions cohérentes avec le format catalogue attendu (ex. 334×500, 296×500, 307×475 — proches de la fourchette 244-335×475-500 documentée dans `normalization/cover_validation.py`).
- Correspond très probablement (**INFÉRÉ**, jamais confirmé) au produit final d'une campagne éditoriale de complétion de couvertures visible dans `data/audit/` sous des noms explicites : `final-cover-consolidated-manifest.csv` (4,6 Mo), `final-cover-existing-keep-manifest.csv`, `final-cover-acquisition-source-manifest.csv`, `final-master-cover-rescue*.csv` (candidates/recovered/unresolved), `full-covered-<LANGUE>-decisions.csv` (par langue : DE/ES/FR/EN/IT/NL/LA), `reconstruction-<LANGUE>-{candidate,recovery,replacement}-covers/` — une architecture de dossiers et fichiers cohérente, multi-étapes (candidats → révision → décision → manifeste consolidé), qui **ne correspond à aucun module de `src/primatis_data_seeding/`** et **n'est mentionnée dans aucun rapport `.claude/logs/`**.
- Conséquence : ce corpus de 330 Mo représente un travail d'ingénierie substantiel, mais **non intégré** — ni au pipeline exécutable, ni au bundle chargé en base, ni à la documentation du projet. Voir [18](./18-audits-et-rapports-existants.md) et [20](./20-incoherences-et-risques.md).

## 6. Historique de `primatis-web/public/covers/catalogue/`

```text
git ls-files primatis-web/public/covers/catalogue | wc -l   -> 31  (30 covers + .gitkeep)
ls primatis-web/public/covers/catalogue | wc -l              -> 7110
```

Les 30 fichiers **committés** sont ceux de la baseline `medium` (DEV-13.19.E) — c'est **cet échantillon exact de 30 fichiers** qui a servi à calibrer les seuils de `normalization/cover_validation.py` (le docstring du module le cite littéralement : *« observing the 30 real cover files already committed under `primatis-web/public/covers/catalogue/` »*). Les 7 079 fichiers supplémentaires présents mais **non commités** correspondent (recoupement exact, à 2 fichiers près : `.gitkeep` et `ol-cover-317517.jpg`, qui appartient au lot historique des 30) au contenu intégral de `data-seeding/data/covers/full/` — preuve qu'une copie a eu lieu du dossier `data-seeding/data/covers/full/` vers `primatis-web/public/covers/catalogue/`, mais **aucun script du dépôt n'effectue cette copie** (`grep` négatif sur `primatis-web/public/covers` dans tout `data-seeding/src/`) : le mécanisme exact de cette copie est **NON DÉTERMINÉ** (a pu être fait manuellement, ou par un outil/agent hors du dépôt de code inspecté ici).

## 7. Anomalie documentée dans le rapport lui-même

`data/bundles/full/full_build_report.json`, champ `covers` :

```json
"covers": {
  "bytes_total": 41607075, "candidates": 7661,
  "download_attempted": 0, "download_successful": 0,
  "duplicates": 394, "invalid": 0, "invalid_reasons": {}, "valid": 1728
}
```

Le champ `"regeneration_note"` du même fichier explique **lui-même** que ce rapport résulte d'un *rejeu sans réseau* (idempotence prouvée) dont le compteur brut de covers invalides était un artefact de mesure (`invalid=5539` factice, provenant d'un `cover_fetcher` volontairement bloqué levant une exception à chaque tentative) et que la section `covers` ci-dessus **a été corrigée manuellement après un re-scan indépendant** des 1728 fichiers réels via `validate_cover_candidate()`. C'est une preuve de transparence exemplaire (la correction est explicitement tracée), mais aussi la preuve que **le JSON généré automatiquement par le code n'est, à lui seul, pas fiable pour ce champ** sans cette annotation manuelle a posteriori — à traiter comme une dette de fiabilité, pas une erreur ponctuelle (voir [20-incoherences-et-risques.md](./20-incoherences-et-risques.md)).

## 8. Ce qui n'existe PAS

- Aucun pipeline de covers pour `small`/`medium`/`large` dans le code actuel (seul `full_build.py` en possède un).
- Aucune suppression/nettoyage automatique des fichiers de covers devenus orphelins.
- Aucune preuve que `data/covers/final/` ait jamais été chargé, référencé, ou même lu par un test.
- Aucun script de synchronisation documenté entre `data-seeding/data/covers/` et `primatis-web/public/covers/catalogue/`.

## Rapports liés

- [Sources de données](./05-sources-de-donnees.md)
- [Titres](./08-titres.md)
- [Audits et rapports existants](./18-audits-et-rapports-existants.md)
- [Incohérences et risques](./20-incoherences-et-risques.md)
