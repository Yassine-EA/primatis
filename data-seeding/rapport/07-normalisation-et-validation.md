# Normalisation et validation — data-seeding

[Retour au rapport général](./general.md)

Chaque règle ci-dessous cite : fonction, fichier, condition, conséquence.

## 1. Texte générique

| Fonction | Fichier | Condition | Conséquence |
|---|---|---|---|
| `normalize_text()` | `normalization/text.py` | toute valeur non `None` | NFC Unicode + collapse des espaces + `strip()` ; chaîne vide → `None` |
| `truncate_or_none()` | `normalization/text.py` | idem + `max_length` | tronque après normalisation (jamais avant, pour ne pas couper au milieu d'espaces multiples) |

## 2. Qualité de texte / mojibake (`normalization/text_quality.py`)

Module de **détection seule** — ne corrige jamais, ne devine jamais ; le verdict est ensuite interprété par `quality/quarantine.py`.

| Code | Précision | Condition | Conséquence |
|---|---|---|---|
| `REPLACEMENT_CHARACTER` | haute | `U+FFFD` présent | `QUARANTINE` (ou `REJECT` si champ bloquant) |
| `MOJIBAKE_SIGNATURE` | haute | une des 23 séquences confirmées (`Ã©`, `Ã¤`, `â€™`, ...) — trouvées réellement dans `data/raw/openlibrary/medium/search_fr.json` et un editions detail | idem |
| `MOJIBAKE_SUSPECT` | faible (WARNING seul) | `[ÃÂ]` suivi d'un caractère de contrôle C1 (motif réel observé : `"L Â\x8cuvre"`) | signalé pour échantillonnage humain, jamais bloquant seul |
| `CONTROL_CHARACTER` | haute | caractère de contrôle C0/C1 hors espace normalisé | `QUARANTINE`/`REJECT` |
| `HTML_RESIDUE` | haute | balise ou entité HTML détectée par regex | idem |
| `PATHOLOGICAL_WHITESPACE` | faible | espace Unicode non standard non collapsé par `normalize_text` | `WARNING` seul |
| `DISGUISED_EMPTY` | haute | chaîne non vide sans aucun caractère alphanumérique | `QUARANTINE`/`REJECT` |
| `UNICODE_NOT_NFC` | faible | valeur non normalisée NFC après coup (théoriquement impossible si `normalize_text` a été appliqué en amont) | `WARNING` seul |

Règle de blocage : un champ **bloquant** (`Title.title`, `Author.full_name`) escalade tout problème « haute précision » de `QUARANTINE` vers `REJECT` (l'enregistrement entier est écarté) ; un champ non bloquant (`subtitle`, `summary`, `publisher`, `biography`) est simplement mis à `NULL` par l'appelant.

## 3. Langue

| Fonction | Fichier | Condition | Conséquence |
|---|---|---|---|
| `normalize_language_code()` | `normalization/language.py` | table exacte 15 entrées (`fre/fra→FR`, `dut/nld→NL`, ...) | code inconnu → `None` (jamais d'enum inventé) |
| `select_supported_language()` | idem | premier code de la liste qui matche la table | — |
| `is_confident_french()` | `normalization/language_detection.py` | ensembles de marqueurs FR distinctifs (≥2 hits, ratio ≥0,04, ≥4 tokens) **et** absence de bloc EN/DE substantiel par phrase (≥2 hits sur ≥6 tokens) | `Author.biography` gardée seulement si `True`, sinon `None` — **jamais** utilisée pour `Title.language` |

`is_confident_french()` est né de deux corrections réelles découvertes sur le corpus Full (11 518 auteurs, 14 biographies non nulles) : collision « sa » (possessif FR) / « BY-SA » (licence CC), collision « roman »/« romans » (FR) / « Roman » (adjectif anglais, Empire romain). Après correction : seules 2 des 14 biographies restent classées françaises (`biography_coverage = 0,0002` dans `full_build_report.json`).

## 4. ISBN

| Fonction | Fichier | Condition | Conséquence |
|---|---|---|---|
| `normalize_isbn()` | `normalization/isbn.py` | retire tout caractère hors `[0-9Xx]`, met en majuscule | vide → `None` |
| `is_valid_isbn10()` / `is_valid_isbn13()` | idem | checksum modulo 11 / modulo 10 | rejette silencieusement (renvoie `False`, ne lève pas) |
| `select_valid_isbn()` | idem | parcourt d'abord `isbn13`, puis `isbn10` | premier ISBN valide trouvé, sinon `None` — **jamais** d'ISBN fabriqué |

Conforme strictement à la règle `data-seeding.md` « NE JAMAIS INVENTER UN ISBN » : aucun code du dépôt ne génère de checksum, aucun fallback ne réutilise un ISBN d'une autre édition.

## 5. Publisher, année, pagination

| Fonction | Fichier | Condition | Conséquence |
|---|---|---|---|
| `normalize_publication_year()` | `normalization/openlibrary.py` | regex `(1[0-9]{3}\|20[0-9]{2}\|2100)`, exactement **une** année distincte trouvée, comprise entre 1000 et année courante+1 | sinon `None` (ambiguïté → jamais devinée) |
| `normalize_page_count()` | idem | entier strictement positif (bool exclu explicitement) | sinon `None` |
| `normalize_pagination()` | idem | regex stricte `^(\d+)\s*p\.?$` **uniquement** | rejette délibérément « 2 v. », « xii, 352 p. », « 1 v. (unpaged) », « p. cm. » — convertible seulement si forme exacte |
| publisher (dans `mapping/catalogue.py`/`pipeline/bundle.py`) | — | premier élément non vide de la liste `publishers`, tronqué à 255 | — |

## 6. Résumé, biographie

| Fonction | Fichier | Condition | Conséquence |
|---|---|---|---|
| `normalize_summary()` / `normalize_work_record()` | `normalization/openlibrary.py` | gère les deux représentations Open Library (chaîne brute ou objet `{"type": "/type/text", "value": ...}`) | `None` si absent — jamais fabriqué |
| `normalize_biography()` | idem | idem, puis filtrée par `is_confident_french()` en aval | — |

## 7. Auteurs

| Fonction | Fichier | Condition | Conséquence |
|---|---|---|---|
| `canonical_author_key()` | `normalization/openlibrary.py` | strip du préfixe `/authors/` **uniquement** si la forme restante matche `^OL\d+A$` | réconcilie la forme nue (Search API) et préfixée (dump bulk) — jamais de correspondance approximative |
| `normalize_exact_date()` | idem | 3 formats stricts (`%Y-%m-%d`, `%d %B %Y`, `%B %d, %Y`) | tout le reste → `None` (jamais d'année seule interprétée comme date) |

## 8. Nationalité (Wikidata)

| Fonction | Fichier | Condition | Conséquence |
|---|---|---|---|
| `resolve_author_nationality()` | `normalization/wikidata.py` | exactement 1 claim `P27`, entité pays résolue, `is_modern_sovereign_state()` vrai (`P31 ∈ {Q6256, Q3624078}`) | sinon `None` — jamais de choix arbitraire entre plusieurs citoyennetés, jamais une entité historique |

## 9. Bpost / codes postaux

| Fonction | Fichier | Condition | Conséquence |
|---|---|---|---|
| `normalize_postal_locality()` | `normalization/bpost.py` | code postal exactement 4 chiffres après retrait des espaces | sinon `None` (ligne ignorée) |
| `_coerce_postal_code()` | `reference/bpost.py` | conversion `.xls` legacy (`1040.0` float → `"1040"`) | seulement pour entiers exacts |

## 10. Covers (bytes)

| Fonction | Fichier | Condition | Conséquence |
|---|---|---|---|
| `validate_cover_candidate()` | `normalization/cover_validation.py` | signature JPEG/PNG reconnue, dimensions 120×160 à 3000×3000, poids ≤5 Mo | `REJECT` sinon, avec code précis (`NOT_DECODABLE_IMAGE`, `IMAGE_TOO_SMALL`, `UNSUPPORTED_FORMAT`, ...) |

## 11. Validation structurelle finale (`validation/catalogue.py`)

Appliquée à `NormalizedAuthor`/`NormalizedEdition` juste avant mapping (dans `pipeline/bundle.py::normalize_selected_catalogue`, mécanisme A uniquement — le mécanisme B ne rappelle pas explicitement `validate_author`/`validate_edition` en dehors de ce même chemin partagé) :

```text
AUTHOR_NAME_REQUIRED / AUTHOR_NAME_TOO_LONG (>255)
AUTHOR_DATE_ORDER_INVALID (death_date < birth_date)
TITLE_REQUIRED / TITLE_TOO_LONG (>500) / SUBTITLE_TOO_LONG (>500)
PUBLISHER_TOO_LONG (>255)
LANGUAGE_UNSUPPORTED (hors {FR,EN,NL,DE,ES,IT,LA})
PAGE_COUNT_INVALID (<=0)
ISBN_INVALID (checksum/format)
AUTHOR_REQUIRED (0 author_keys)
```

Une violation ici lève une `ValueError` explicite qui interrompt tout le bundle — il ne s'agit pas d'un rejet « doux » comme la quarantaine, mais d'un signal que l'amont (acquisition/normalisation) a produit une donnée structurellement invalide.

## Rapports liés

- [Titres](./08-titres.md)
- [Auteurs](./09-auteurs.md)
- [Audits et rapports existants](./18-audits-et-rapports-existants.md)
