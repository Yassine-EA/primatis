from primatis_data_seeding.normalization.wikidata import (
    extract_country_of_citizenship_qids,
    extract_english_label,
    is_modern_sovereign_state,
    resolve_author_nationality,
)


def _author_entity(country_qids: list[str]) -> dict:
    return {
        "id": "Q154812",
        "claims": {
            "P27": [
                {
                    "mainsnak": {
                        "snaktype": "value",
                        "datavalue": {"value": {"id": qid}},
                    }
                }
                for qid in country_qids
            ]
        },
    }


def _country_entity(qid: str, *, label: str | None, modern: bool) -> dict:
    instance_of = ["Q6256"] if modern else ["Q417175"]
    claims = {}
    if instance_of:
        claims["P31"] = [
            {
                "mainsnak": {
                    "snaktype": "value",
                    "datavalue": {"value": {"id": qid2}},
                }
            }
            for qid2 in instance_of
        ]
    entity: dict = {"id": qid, "claims": claims}
    if label is not None:
        entity["labels"] = {"en": {"language": "en", "value": label}}
    return entity


def test_extract_country_of_citizenship_qids_single_value() -> None:
    entity = _author_entity(["Q142"])
    assert extract_country_of_citizenship_qids(entity) == ("Q142",)


def test_extract_country_of_citizenship_qids_multiple_values() -> None:
    entity = _author_entity(["Q142", "Q30"])
    assert extract_country_of_citizenship_qids(entity) == ("Q142", "Q30")


def test_extract_country_of_citizenship_qids_absent() -> None:
    assert extract_country_of_citizenship_qids({"id": "Q1", "claims": {}}) == ()
    assert extract_country_of_citizenship_qids({}) == ()


def test_extract_country_of_citizenship_qids_ignores_novalue_snak() -> None:
    entity = {
        "id": "Q1",
        "claims": {"P27": [{"mainsnak": {"snaktype": "novalue"}}]},
    }
    assert extract_country_of_citizenship_qids(entity) == ()


def test_is_modern_sovereign_state_true_for_country_class() -> None:
    assert is_modern_sovereign_state(_country_entity("Q142", label="France", modern=True))


def test_is_modern_sovereign_state_false_for_historical_polity() -> None:
    entity = _country_entity("Q209857", label="Kingdom of Lombardy-Venetia", modern=False)
    assert is_modern_sovereign_state(entity) is False


def test_extract_english_label() -> None:
    entity = _country_entity("Q142", label="France", modern=True)
    assert extract_english_label(entity) == "France"


def test_extract_english_label_missing() -> None:
    assert extract_english_label({"id": "Q1", "labels": {}}) is None
    assert extract_english_label({"id": "Q1"}) is None


def test_resolve_author_nationality_single_modern_country() -> None:
    author = _author_entity(["Q142"])
    countries = {"Q142": _country_entity("Q142", label="France", modern=True)}
    assert resolve_author_nationality(author, countries) == "France"


def test_resolve_author_nationality_none_when_no_p27() -> None:
    author = _author_entity([])
    assert resolve_author_nationality(author, {}) is None


def test_resolve_author_nationality_none_when_multiple_citizenships() -> None:
    author = _author_entity(["Q142", "Q30"])
    countries = {
        "Q142": _country_entity("Q142", label="France", modern=True),
        "Q30": _country_entity("Q30", label="United States", modern=True),
    }
    # Deliberately never guesses between multiple real citizenships.
    assert resolve_author_nationality(author, countries) is None


def test_resolve_author_nationality_none_when_historical_polity() -> None:
    author = _author_entity(["Q209857"])
    countries = {
        "Q209857": _country_entity(
            "Q209857", label="Kingdom of Lombardy-Venetia", modern=False
        )
    }
    assert resolve_author_nationality(author, countries) is None


def test_resolve_author_nationality_none_when_country_entity_not_cached() -> None:
    author = _author_entity(["Q142"])
    assert resolve_author_nationality(author, {}) is None


def test_resolve_author_nationality_none_when_author_entity_missing() -> None:
    assert resolve_author_nationality(None, {}) is None
