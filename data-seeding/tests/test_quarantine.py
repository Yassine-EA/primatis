from datetime import date

from primatis_data_seeding.mapping.models import (
    CatalogueMappingResult,
    PrimatisAuthorRow,
    PrimatisTitleAuthorRow,
    PrimatisTitleRow,
)
from primatis_data_seeding.quality.quarantine import apply_text_quality_policy


def author_row(**overrides) -> PrimatisAuthorRow:
    defaults = dict(
        source_key="/authors/OL1A",
        full_name="Jean Dupont",
        birth_date=date(1900, 1, 1),
        death_date=None,
        nationality=None,
        biography=None,
    )
    defaults.update(overrides)
    return PrimatisAuthorRow(**defaults)


def title_row(**overrides) -> PrimatisTitleRow:
    defaults = dict(
        source_key="/books/OL1M",
        isbn=None,
        title="Un titre propre",
        subtitle=None,
        summary=None,
        publication_year=2020,
        language="FR",
        page_count=None,
        publisher=None,
        cover_image_url=None,
    )
    defaults.update(overrides)
    return PrimatisTitleRow(**defaults)


def link(title_key="/books/OL1M", author_key="/authors/OL1A") -> PrimatisTitleAuthorRow:
    return PrimatisTitleAuthorRow(title_source_key=title_key, author_source_key=author_key)


def test_clean_record_is_accepted_unchanged():
    mapping = CatalogueMappingResult(
        authors=[author_row()], titles=[title_row()], title_authors=[link()],
    )
    result = apply_text_quality_policy(mapping)

    assert len(result.titles) == 1
    assert len(result.authors) == 1
    assert result.title_authors == [link()]
    assert result.quarantined == []
    assert result.is_quarantined("/books/OL1M") is False


def test_real_corrupted_title_is_quarantined_not_accepted():
    # Real value confirmed present in primatis_dev (title.id=7293, Zola),
    # already present verbatim in the RAW Open Library payload.
    mapping = CatalogueMappingResult(
        authors=[author_row()],
        titles=[title_row(source_key="/books/OL7293M", title="L Âuvre, Emile Zola")],
        title_authors=[link(title_key="/books/OL7293M")],
    )
    result = apply_text_quality_policy(mapping)

    assert result.titles == []  # DEC-16.3-03: never in the accepted collection
    assert result.is_quarantined("/books/OL7293M") is True
    entry = next(e for e in result.quarantined if e.source_key == "/books/OL7293M")
    assert entry.entity == "title"
    assert entry.reason_code == "TEXT_ENCODING_SUSPECT"


def test_real_corrupted_publisher_nulls_field_but_keeps_title():
    # Real value confirmed present in primatis_dev (title.id=7379,
    # edition OL12623748M): "Anne CarriÃ¨re" — optional field, so the
    # Title itself stays ACCEPTED with the field nulled.
    mapping = CatalogueMappingResult(
        authors=[author_row()],
        titles=[title_row(publisher="Anne CarriÃ¨re")],
        title_authors=[link()],
    )
    result = apply_text_quality_policy(mapping)

    assert len(result.titles) == 1
    assert result.titles[0].publisher is None
    assert result.is_quarantined("/books/OL1M") is False
    decisions = result.field_decisions["/books/OL1M"]
    publisher_decision = next(d for d in decisions if d.field == "publisher")
    assert publisher_decision.outcome == "NULLED"


def test_corrupted_author_name_quarantines_author_only():
    mapping = CatalogueMappingResult(
        authors=[author_row(full_name="SmaÃ¯n Laacher")],
        titles=[],
        title_authors=[],
    )
    result = apply_text_quality_policy(mapping)

    assert result.authors == []
    assert result.is_quarantined("/authors/OL1A") is True


def test_corrupted_biography_nulls_field_but_keeps_author():
    mapping = CatalogueMappingResult(
        authors=[author_row(biography="Notice avec un artefact â€™ visible.")],
        titles=[],
        title_authors=[],
    )
    result = apply_text_quality_policy(mapping)

    assert len(result.authors) == 1
    assert result.authors[0].biography is None
    assert result.is_quarantined("/authors/OL1A") is False


def test_clean_optional_fields_are_recorded_as_kept():
    mapping = CatalogueMappingResult(
        authors=[author_row()],
        titles=[title_row(publisher="Gallimard", subtitle="Un vrai sous-titre")],
        title_authors=[link()],
    )
    result = apply_text_quality_policy(mapping)

    decisions = {d.field: d.outcome for d in result.field_decisions["/books/OL1M"]}
    assert decisions["publisher"] == "KEPT"
    assert decisions["subtitle"] == "KEPT"


def test_mapping_result_is_not_mutated():
    mapping = CatalogueMappingResult(
        authors=[author_row()],
        titles=[title_row(publisher="Anne CarriÃ¨re")],
        title_authors=[link()],
    )
    apply_text_quality_policy(mapping)

    # Original mapping result untouched — apply_text_quality_policy()
    # returns a new result rather than mutating its input.
    assert mapping.titles[0].publisher == "Anne CarriÃ¨re"


# --- DEV-16.4 §4.2: Author -> Title quarantine cascade -----------------


def test_single_quarantined_author_quarantines_the_only_title():
    mapping = CatalogueMappingResult(
        authors=[author_row(full_name="SmaÃ¯n Laacher")],
        titles=[title_row()],
        title_authors=[link()],
    )
    result = apply_text_quality_policy(mapping)

    assert result.titles == []
    assert result.title_authors == []
    assert result.is_quarantined("/books/OL1M") is True
    entry = next(e for e in result.quarantined if e.source_key == "/books/OL1M")
    assert entry.reason_code == "NO_VALID_AUTHOR_AFTER_QUARANTINE"


def test_two_authors_one_quarantined_title_stays_accepted_with_valid_link_only():
    mapping = CatalogueMappingResult(
        authors=[
            author_row(source_key="/authors/OL1A", full_name="SmaÃ¯n Laacher"),
            author_row(source_key="/authors/OL2A", full_name="Marie Curie"),
        ],
        titles=[title_row()],
        title_authors=[
            link(author_key="/authors/OL1A"),
            link(author_key="/authors/OL2A"),
        ],
    )
    result = apply_text_quality_policy(mapping)

    assert len(result.titles) == 1
    assert result.is_quarantined("/books/OL1M") is False
    assert result.title_authors == [link(author_key="/authors/OL2A")]
    # the quarantined author itself is also reported, separately
    assert result.is_quarantined("/authors/OL1A") is True
    assert result.is_quarantined("/authors/OL2A") is False


def test_all_authors_quarantined_quarantines_the_title():
    mapping = CatalogueMappingResult(
        authors=[
            author_row(source_key="/authors/OL1A", full_name="SmaÃ¯n Laacher"),
            author_row(source_key="/authors/OL2A", full_name="AprÃ¨s sangatte"),
        ],
        titles=[title_row()],
        title_authors=[
            link(author_key="/authors/OL1A"),
            link(author_key="/authors/OL2A"),
        ],
    )
    result = apply_text_quality_policy(mapping)

    assert result.titles == []
    assert result.title_authors == []
    assert result.is_quarantined("/books/OL1M") is True
    title_entry = next(e for e in result.quarantined if e.source_key == "/books/OL1M")
    assert title_entry.reason_code == "NO_VALID_AUTHOR_AFTER_QUARANTINE"
    assert result.is_quarantined("/authors/OL1A") is True
    assert result.is_quarantined("/authors/OL2A") is True


def test_clean_author_does_not_trigger_cascade():
    mapping = CatalogueMappingResult(
        authors=[author_row()],
        titles=[title_row()],
        title_authors=[link()],
    )
    result = apply_text_quality_policy(mapping)

    assert len(result.titles) == 1
    assert result.title_authors == [link()]
    assert result.quarantined == []


def test_cascade_provenance_reason_is_traced_in_field_decisions():
    mapping = CatalogueMappingResult(
        authors=[author_row(full_name="SmaÃ¯n Laacher")],
        titles=[title_row()],
        title_authors=[link()],
    )
    result = apply_text_quality_policy(mapping)

    decisions = result.field_decisions["/books/OL1M"]
    authors_decision = next(d for d in decisions if d.field == "authors")
    assert authors_decision.outcome == "REJECTED"
    assert authors_decision.reason == "NO_VALID_AUTHOR_AFTER_QUARANTINE"
    assert "DEV-16.4" in authors_decision.rule_reference


def test_title_already_text_quarantined_is_not_double_counted_by_cascade():
    # A Title quarantined for its OWN text corruption, whose only Author
    # is ALSO quarantined, must appear exactly once in `quarantined`
    # (its own text reason), never a second time via the cascade.
    mapping = CatalogueMappingResult(
        authors=[author_row(full_name="SmaÃ¯n Laacher")],
        titles=[title_row(title="AprÃ¨s sangatte")],
        title_authors=[link()],
    )
    result = apply_text_quality_policy(mapping)

    title_entries = [e for e in result.quarantined if e.source_key == "/books/OL1M"]
    assert len(title_entries) == 1
    assert title_entries[0].reason_code == "TEXT_ENCODING_SUSPECT"
