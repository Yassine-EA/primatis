# Historique technique visible — data-seeding

[Retour au rapport général](./general.md)

## 1. Ce qui est un historique confirmé (traces documentées)

```text
guard.py (racine du package)
  -> superseded en pratique par load/guard.py (mêmes constantes, logique
     étendue avec validate_live_database/require_apply_confirmation).
     Encore utilisé par main.py (bootstrap DEV-13.3) uniquement.

data/backups/*.dump  (5 fichiers, 2,7 Mo)
  primatis_dev-before-DEV-13.16.dump          (avant 1er APPLY small)
  primatis_dev-before-DEV-13.19.E.dump        (avant régénération enrichie medium)
  primatis_dev-before-DEV-13.20.dump          (avant audit complétude)
  primatis_dev-before-DEV-16.4-large.dump     (avant chargement Large)
  primatis_preview-before-DEV-16.8-rebuild.dump (avant reconstruction complète Full)
  -> chronologie de sécurité cohérente et continue à travers les deux
     générations du projet, seule trace incontestable de continuité entre
     DEV-13 et DEV-16 (les fichiers de code, eux, ne montrent aucun lien
     Git direct).

DEV-16.4 §8 (scripts/dev164_build_large.py, commentaire du code) :
  1er run réel -> 3307/5000 Titles (shortfall=1693, FR sursaturé) ->
  8 lots de complément ajoutés -> 2e run -> 5000/5000. Le code garde
  TRACE de cet échec intermédiaire dans son propre commentaire, plutôt que
  de le dissimuler en réécrivant silencieusement BATCHES.

DEV-16.5 (scripts/dev165_build_full.py, commentaires) :
  "round 1" (proportions canoniques) -> plafond mesuré de rendement FR
  (~6800-7000 net quel que soit l'effort) -> "round 2" (comptes FR
  augmentés par catégorie) -> "round 3" (répartition du reliquat vers les
  6 autres langues, qui avaient toutes dépassé leur quota canonique dès
  le premier essai) -> 15000/15000 final. Trois itérations documentées
  dans le code lui-même, pas seulement dans un log externe.

DEV-16.5.1 (normalization/language_detection.py, docstring) :
  correction réelle de is_confident_french() après audit du corpus Full
  complet (11518 auteurs) : biography_coverage recalculé de 0,0012 (14/11518)
  à 0,0002 (2/11518) après élimination de deux collisions lexicales
  précisément identifiées (« sa »/« BY-SA », « roman »/« Roman Empire »).
```

## 2. Les deux générations, au sens Git (rappel synthétique)

```text
Génération 1 — DEV-13 — 6 commits Git (20c4b41 -> 47f8481, 2026-09-02 -> 09-04)
Génération 2 — DEV-16 — 0 commit Git, présente uniquement dans l'arbre de travail
```

Voir le détail complet en [01-architecture.md](./01-architecture.md) §2 et [02-arborescence-et-fichiers.md](./02-arborescence-et-fichiers.md) §6 — non répété ici.

## 3. Zone dont l'historique est INCONNU (ni Git, ni log, ni code)

```text
data/audit/                (~230 fichiers, 87 Mo)
data/cache/*-mass-editorial-engine/, final-master-cover-rescue*/,
  page-count-enrichment/, google-books-final-rescue-benchmark/  (321 Mo)
data/covers/final/         (10578 fichiers, 330 Mo)
```

Ces trois zones présentent des caractéristiques d'un **pipeline structuré et itératif**, très probablement postérieur ou parallèle à DEV-16 (noms de fichiers cohérents avec la terminologie du projet — langues ISO, notion de « lot »/« review »/« decisions », structure candidate→review→decision→manifeste consolidé identique en esprit à `pipeline/batch.py`), mais :

- **aucun fichier `.py` ne les produit ni ne les consomme** dans l'état actuel de `src/`, `tests/`, ou `scripts/` ;
- **aucun `.claude/logs/DEV-13*` ou `DEV-16*` ne les mentionne** (recherche exhaustive par motif de nom) ;
- ils ne sont ni gitignorés ni commités — statut Git identique à celui de la génération 2 documentée (`??`), ce qui interdit de les distinguer de cette dernière par ce seul critère.

**Hypothèses non confirmées (INFÉRÉ, à vérifier humainement)** :

1. Scripts ad hoc exécutés en dehors du dépôt suivi (REPL, notebook, ou un outil non enregistré dans `scripts/`) puis supprimés après exécution — cohérent avec le fait que `scripts/` ne contient que 3 fichiers alors que ces zones donnent l'impression d'au moins une dizaine d'étapes de traitement distinctes par langue.
2. Tentative d'amélioration du taux de couverture (`cover_coverage`, `page_count_coverage`, `summary_coverage` — les trois plus faibles métriques connues du profil `full`, voir [19](./19-etat-reel-actuel.md)) via des sources supplémentaires (Google Books, Europeana, variante Wikidata Action API) et une consolidation multi-passes des couvertures, restée à l'état expérimental et jamais fusionnée avec `pipeline/full_build.py`.
3. Travail réalisé par une session/outil distinct de celui qui a produit les commits Git et les `.claude/logs/` structurés — **NON DÉTERMINÉ**.

Aucune de ces hypothèses ne peut être confirmée avec les preuves disponibles dans le dépôt à la date de l'audit (2026-09-15).

## 4. Ce qui n'est PAS un historique — mise en garde

Il serait erroné de considérer `data/audit/`, `data/cache/*` ou `data/covers/final/` comme des « anciennes versions » d'un mécanisme actuel : rien ne prouve qu'ils aient jamais été actifs dans une version antérieure du code actuellement présent — ils pourraient tout aussi bien être **plus récents** que le dernier commit Git (2026-09-04) ou même que les derniers fichiers de `pipeline/full_build.py` (dont le dernier changement significatif documenté, DEV-16.8, date du 2026-09-15). Sans horodatage fiable indépendant des métadonnées du système de fichiers (non exploitées ici car non probantes après un `checkout`/une copie), la chronologie exacte de ces trois zones reste **NON DÉTERMINÉE**.

## Rapports liés

- [Architecture générale](./01-architecture.md)
- [Incohérences et risques](./20-incoherences-et-risques.md)
- [État réel actuel](./19-etat-reel-actuel.md)
