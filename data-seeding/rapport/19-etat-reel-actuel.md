# État réel actuel — data-seeding

[Retour au rapport général](./general.md)

Ce rapport répond directement aux questions du mandat d'audit. Toute affirmation est sourcée ; les déductions sont marquées **INFÉRÉ**, les inconnues **NON DÉTERMINÉ**.

## 1. Qu'est-ce qui fonctionne réellement aujourd'hui ?

```text
FAIT (reproduit pendant cet audit, 2026-09-15) :
  - `pytest -q` sous data-seeding/ -> 506 passed in 6.81s, 0 échec.
  - Les 4 profils (small/medium/large/full) ont chacun un bundle CSV complet
    et cohérent sous data/bundles/<profil>/, avec un rapport JSON associé.
  - Le profil full est reconstructible SANS accès réseau depuis les caches
    déjà présents (data/raw/, data/validated/) — prouvé par DEV-16.8 §D/§E
    (SHA-256 identiques sur tous les fichiers déterministes) et par la
    logique même du cache (voir 12-caches-et-replay.md).
```

## 2. Qu'est-ce qui est réellement finalisé ?

```text
DEV-13 (small/medium)  : FINALISÉ — chargé, testé, clos (development-status.md).
DEV-16.4 (large)       : FINALISÉ selon le tracking (« DONE, PASS »), chargé
                          dans primatis_dev (5000/8000).
DEV-16.5→16.8 (full)   : FINALISÉ selon les logs eux-mêmes (DEV-16.8 conclut
                          « Tous les hard gates DEV-16.8 sont satisfaits » et
                          « READY FOR REVIEW »), MAIS le tableau roadmap
                          central (development-status.md) n'a pas encore été
                          mis à jour pour refléter DEV-16.7/16.8 et qualifie
                          encore DEV-16 d'« IN PROGRESS ». Le rapport DEV-16.8
                          précise lui-même explicitement : « Le FINAL GATE
                          DEV-16 sera validé séparément après revue humaine
                          de ce rapport [...] ce rapport ne déclare pas
                          "DEV-16 FINAL PASS" ». -> DEV-16 est donc
                          fonctionnellement complet mais PAS FORMELLEMENT CLOS.
```

## 3. Quels pipelines sont réellement utilisables ?

Les cinq points d'entrée listés dans [03-points-entree-et-cli.md](./03-points-entree-et-cli.md) sont tous, à ce jour, exécutables tels quels (code présent, dépendances installées, testés). Le pipeline `batch.py`/`large_build.py`/`full_build.py` est utilisable **en réutilisant le cache existant** sans réseau ; une reconstruction *ex nihilo* nécessiterait à nouveau les dumps bulk Open Library (742 Mo + 3,8 Go) et un accès réseau réel — non testé pendant cet audit (règle read-only).

## 4. Quelles données sont réellement produites ? Quelle est la sortie actuelle réelle ?

```text
data/bundles/small/   : 100 Titles / 160 Copies / 25 Users              (COMPLET)
data/bundles/medium/  : 1000 Titles / 1600 Copies / 100 Users            (COMPLET)
data/bundles/large/   : 5000 Titles / 8000 Copies / 0 Users              (CATALOGUE
                          SEUL — voir §6)
data/bundles/full/    : 15000 Titles / 24000 Copies / 1500 Users /
                          160 Loans / 60 Reservations / 60 Fines /
                          270 Notifications                              (COMPLET)
```

Chargement réel en base confirmé par requêtes SQL (DEV-16.8 §B, §P, §S — pas une simple relecture de rapport JSON) :

```text
primatis_dev     : title=5000                    (profil large)
primatis_preview : title=15000 copy=24000 author=11518(bio=2) genre=26
                   title_author=17111 title_genre=13681 app_user=1500
                   loan=160 reservation=60 fine=60 notification=270
```

## 5. Quels scripts sont actifs ? Quels dossiers sont actifs ?

Voir la synthèse complète en [02-arborescence-et-fichiers.md](./02-arborescence-et-fichiers.md) §6. Résumé : **tout** le code sous `src/primatis_data_seeding/` est actif (aucun module mort détecté par `grep` d'imports croisés), à l'exception de `guard.py` (racine du package) qui **duplique** `load/guard.py` sans être appelé par aucun point d'entrée de la génération 2 (seul `main.py`, le bootstrap DEV-13.3, l'utilise encore) — voir [20](./20-incoherences-et-risques.md).

## 6. Quelles parties sont inachevées ?

```text
- large : `user_target=500` déclaré dans profiles.toml, mais AUCUN users.csv/
  addresses.csv/residences.csv n'existe sous data/bundles/large/ — le profil
  n'a jamais généré ses Users (pipeline/large_build.py ne les produit pas :
  lecture du fichier confirme qu'il ne fait qu'export_catalogue_csv(), jamais
  export_users_csv()).
- full : `data/validated/full/completeness_audit.csv` n'existe pas — l'audit
  de complétude exhaustif (DEV-13.20) n'a jamais été rejoué pour full.
- full : nationality_coverage = 0.0 malgré author_enrichment_applied=true —
  aucun fichier data/validated/full/wikidata_*_selected.jsonl (contrairement
  à medium, qui les possède) : le pipeline Wikidata n'a, semble-t-il, jamais
  été exécuté pour full (NON DÉTERMINÉ avec certitude absolue, mais fortement
  suggéré par l'absence totale des artefacts intermédiaires attendus).
- page_count_coverage = 0.0 sur TOUS les profils sauf medium (0,804) — un
  mécanisme de normalisation existe (normalize_pagination) mais ne produit
  jamais de résultat non nul en dehors de medium (voir 20-incoherences.md).
```

## 7. Quelles parties sont expérimentales ?

```text
data/audit/          (~230 fichiers, 87 Mo)  — aucune trace dans le code actuel
data/cache/*          (321 Mo, 7 sous-dossiers) — idem
data/covers/final/    (10578 fichiers, 330 Mo) — idem
```

Ces trois zones représentent ~740 Mo de données produites par un travail réel et structuré (conventions de nommage cohérentes, pipeline apparent multi-étapes par langue), mais **totalement déconnecté** du code source versionné ou non versionné actuellement présent sous `src/`. Voir [24-historique-technique-visible.md](./24-historique-technique-visible.md).

## 8. Quelles données semblent incohérentes ?

Développé intégralement dans [20-incoherences-et-risques.md](./20-incoherences-et-risques.md). Point le plus significatif : **aucun commit Git** ne couvre la génération 2 (DEV-16.x) alors que cette génération représente l'essentiel de la volumétrie et de la complexité actuelles du projet (batch/large/full, ~5300 lignes de code sur 10281 au total, estimation par soustraction des lignes DEV-13 committées).

## 9. Quelles statistiques actuelles peut-on réellement reproduire ?

Toutes celles citées dans ce rapport ont été **recalculées pendant cet audit**, pas seulement recopiées d'un JSON :

```text
pytest -q                                              -> 506 passed in 6.81s
comptage Python des cover_image_url dans titles.csv    -> reproduit exactement
  cover_coverage=0.1179 (full), 30/1000 (medium), 0 (small/large)
comptage des fichiers de data/covers/full/ vs référencés -> 7108 présents,
  1728 référencés, 0 référence cassée, 5380 orphelins
git status / git log                                    -> 6 commits, dates
  et statut ?? confirmés indépendamment
```

## Rapports liés

- [Incohérences et risques](./20-incoherences-et-risques.md)
- [Audits et rapports existants](./18-audits-et-rapports-existants.md)
- [Profils et volumétrie](./14-profils-et-volumetrie.md)
- [Historique technique visible](./24-historique-technique-visible.md)
