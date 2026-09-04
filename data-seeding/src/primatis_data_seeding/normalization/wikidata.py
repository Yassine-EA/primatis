"""Normalisation conservative des entités Wikidata (identité exacte uniquement).

Utilisée uniquement pour ``Author.nationality``, via la chaîne :
    author_key OL exact -> remote_ids.wikidata (champ explicite du record
    Author Open Library) -> entité Wikidata exacte -> claim P27 (country of
    citizenship) -> entité pays exacte -> label anglais.

Aucun matching approximatif, aucune dérivation depuis la langue, le nom ou
la biographie de l'auteur.
"""

# Wikidata: "country" (Q6256) and "sovereign state" (Q3624078). A country
# candidate is only ever used as a nationality when it is an instance of one
# of these — historical polities (e.g. a defunct kingdom or prince-bishopric)
# are real, sourced P27 values but are deliberately out of scope for a
# library-catalogue "nationality" field (policy decision, not a fabrication).
_MODERN_STATE_CLASSES = frozenset({"Q6256", "Q3624078"})

_COUNTRY_OF_CITIZENSHIP_PROPERTY = "P27"
_INSTANCE_OF_PROPERTY = "P31"


def _claim_value_ids(entity: dict, property_id: str) -> tuple[str, ...]:
    if not isinstance(entity, dict):
        return ()
    claims = entity.get("claims")
    if not isinstance(claims, dict):
        return ()
    statements = claims.get(property_id)
    if not isinstance(statements, list):
        return ()

    ids: list[str] = []
    for statement in statements:
        if not isinstance(statement, dict):
            continue
        mainsnak = statement.get("mainsnak")
        if not isinstance(mainsnak, dict) or mainsnak.get("snaktype") != "value":
            continue
        datavalue = mainsnak.get("datavalue")
        if not isinstance(datavalue, dict):
            continue
        value = datavalue.get("value")
        if isinstance(value, dict) and value.get("id"):
            ids.append(str(value["id"]))
    return tuple(dict.fromkeys(ids))


def extract_country_of_citizenship_qids(entity: dict) -> tuple[str, ...]:
    """QIDs of the P27 (country of citizenship) claim(s) of a Wikidata entity."""
    return _claim_value_ids(entity, _COUNTRY_OF_CITIZENSHIP_PROPERTY)


def is_modern_sovereign_state(entity: dict) -> bool:
    """True only if the entity is explicitly an instance of a modern
    country/sovereign state (P31). Historical polities return False."""
    instance_of = _claim_value_ids(entity, _INSTANCE_OF_PROPERTY)
    return bool(set(instance_of) & _MODERN_STATE_CLASSES)


def extract_english_label(entity: dict) -> str | None:
    if not isinstance(entity, dict):
        return None
    labels = entity.get("labels")
    if not isinstance(labels, dict):
        return None
    label = labels.get("en")
    if not isinstance(label, dict):
        return None
    value = label.get("value")
    return value if isinstance(value, str) and value.strip() else None


def resolve_author_nationality(
    author_entity: dict | None,
    country_entities: dict,
) -> str | None:
    """Resolves a single, unambiguous nationality label for one Author's
    Wikidata entity, or None if no structurally reliable answer exists.

    Deliberately conservative:
      - no P27 claim at all -> None (never guessed);
      - more than one P27 value (multiple citizenships) -> None (never
        arbitrarily picks one — genuinely ambiguous for a single field);
      - the single country entity is not cached/resolvable -> None;
      - the single country is a historical polity, not a modern
        country/sovereign state -> None (policy scope, see module docstring);
      - otherwise -> the country's English label.
    """
    if not isinstance(author_entity, dict):
        return None

    country_qids = extract_country_of_citizenship_qids(author_entity)
    if len(country_qids) != 1:
        return None

    country_entity = country_entities.get(country_qids[0]) if isinstance(
        country_entities, dict
    ) else None
    if not isinstance(country_entity, dict):
        return None

    if not is_modern_sovereign_state(country_entity):
        return None

    return extract_english_label(country_entity)
