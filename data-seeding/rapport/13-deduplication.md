# Déduplication — data-seeding

[Retour au rapport général](./general.md)

Module unique : `deduplication/catalogue.py` (296 lignes), deux fonctions principales : `deduplicate_editions()` et `deduplicate_authors()`. Utilisé identiquement par les quatre pipelines (`bundle.py`, `batch.py`, `large_build.py`, `full_build.py`) — aucune divergence de règles entre profils.

## 1. Éditions (Titles) — trois étapes strictement ordonnées

### Étape 1 — doublon exact de `source_key`

```text
Regroupement par source_key (edition_key Open Library).
Gagnant = plus haut score de "complétude" :
    +4 si work_key présent
    +3 si author_keys non vide
    +2 si publication_year présent
    +2 si page_count présent
    +2 si publisher présent
    +1 si subtitle présent
    (égalité -> source_key lexicographique, déterministe)
Perdants -> DuplicateRecord(reason="SAME_SOURCE_KEY")
```

### Étape 2 — ISBN valide identique

```text
Groupement des survivants de l'étape 1 par ISBN valide (ceux sans ISBN
passent directement à l'étape 3, jamais groupés par erreur).
Si le groupe partage la MÊME langue et le MÊME titre (folded) -> le
  gagnant (même règle de score) est retenu, les autres deviennent
  DuplicateRecord(reason="SAME_VALID_ISBN").
Si le groupe présente une langue OU un titre DIFFÉRENT malgré un ISBN
  identique -> DeduplicationConflict("ISBN_METADATA_CONFLICT") : le
  GROUPE ENTIER est exclu du catalogue (aucun survivant), jamais un choix
  arbitraire entre deux éditions incohérentes portant le même ISBN.
```

`pipeline/bundle.py::build_bundle()` fait échouer tout le bundle si `edition_dedup.conflicts` n'est pas vide (`ValueError`) — un conflit ISBN n'est jamais silencieusement résolu.

### Étape 3 — candidats sans ISBN (jamais fusionnés automatiquement)

```text
Empreinte = fold(title) || fold(subtitle) || language || fold(publisher) || auteurs triés
            (publication_year est VOLONTAIREMENT EXCLU de l'empreinte — DEC-16.3-07,
            deux éditions identiques par ailleurs mais d'années différentes sont
            très probablement des rééditions légitimes, pas des doublons)
Si >1 édition partage cette empreinte :
    années distinctes présentes  -> DuplicateCandidate(reason="EDITION_VARIANT_CANDIDATE")
    sinon                        -> DuplicateCandidate(reason="EXACT_METADATA_CANDIDATE_NO_ISBN")
    -> les DEUX cas restent TOUS les deux dans result.kept (aucune fusion,
       seulement un signalement pour revue humaine)
```

Ceci respecte à la lettre la règle `data-seeding.md` : *« Ne jamais fusionner automatiquement deux éditions différentes uniquement parce que : titre similaire + auteur similaire »* — la similarité floue (titre+auteur) ne déclenche **jamais** de fusion, seulement l'ISBN identique le fait, et un conflit de métadonnées sur ISBN identique **exclut** plutôt que de choisir.

## 2. Auteurs — deux étapes

```text
Étape 1 — SAME_SOURCE_KEY : gagnant = le plus de dates renseignées
           (birth_date + death_date), puis full_name lexicographique.
Étape 2 — AUTHOR_IDENTITY_CANDIDATE : empreinte fold(full_name) || birth_date
           || death_date -> signalé si partagé par >1 author_key DISTINCT,
           jamais fusionné (deux author_key Open Library différents restent
           deux Author PRIMATIS distincts, même en cas de nom+dates identiques).
```

## 3. Résultats mesurés

| Profil | `edition_duplicates` | `edition_candidates` | `edition_conflicts` | `author_duplicates` | `author_candidates` |
|---|---|---|---|---|---|
| small | 0 | 0 | 0 | 18 | 1 |
| medium | 0 | 0 | 0 | 247 | 7 |
| large | 1 923 (total dup+cand, voir note) | inclus ci-contre : 52 candidats | 0 | inclus | inclus |
| full | 0 (`merged_losers`) | — | 0 | — | — |

Note large : `large_build_report.json` agrège `total_duplicate` (= éditions + auteurs dupliqués au sens strict, 1 923) et `total_candidate` (= candidats jamais fusionnés, 52) séparément des rejets/quarantaines — la distinction candidate/duplicate est préservée jusqu'au rapport final, jamais mélangée.

Le rapport Full (`full_build_report.json`, bloc `funnel`) montre `merged_losers: 0` — à l'échelle du profil complet (15 187 éditions uniques après filtrage inter-lots), **aucune** fusion `SAME_SOURCE_KEY`/`SAME_VALID_ISBN` n'a eu lieu à l'étape finale de déduplication catalogue (la déduplication inter-lots par `edition_key`/ISBN valide, faite en amont dans `pipeline/batch.py::acquire_batch_candidates`, a déjà éliminé les doublons évidents avant que `deduplicate_editions()` ne soit appelé).

## 4. Ce que ce module ne fait jamais

- Aucun matching flou/« fuzzy » (Levenshtein, similarité cosinus, etc.) — uniquement des égalités exactes après un repliement Unicode/casefold déterministe (`_fold_text()`).
- Aucune fusion automatique fondée sur une similarité de titre+auteur seule.
- Aucun ISBN choisi arbitrairement en cas de conflit — le groupe entier est écarté.

## Rapports liés

- [Titres](./08-titres.md)
- [Auteurs](./09-auteurs.md)
- [Acquisition](./06-acquisition.md)
