from pathlib import Path

from primatis_data_seeding.audit.completeness import (
    AUDIT_FIELDNAMES,
    AuditRow,
    audit_author_date,
    audit_author_row,
    audit_biography,
    audit_cover_image_url,
    audit_isbn,
    audit_nationality,
    audit_page_count,
    audit_publication_year,
    audit_publisher,
    audit_summary,
    audit_title_row,
    build_completeness_audit,
    write_audit_csv,
)
from primatis_data_seeding.pipeline.bundle import SelectedEdition


def _candidate(**overrides) -> SelectedEdition:
    base = dict(
        language="FR",
        language_code="fre",
        work_key="/works/OL1W",
        edition_key="/books/OL1M",
        title="Book",
        subtitle=None,
        author_keys=("OL1A",),
        author_names=("Author",),
        subjects=(),
        isbn_10=(),
        isbn_13=(),
        isbn=(),
        publishers=(),
        publish_date=None,
        publish_year=None,
        number_of_pages=None,
        cover_id=None,
    )
    base.update(overrides)
    return SelectedEdition(**base)


# --------------------------------------------------------------------------
# isbn
# --------------------------------------------------------------------------


def test_audit_isbn_present_confirmed_absent() -> None:
    row = audit_isbn(_candidate(), {"key": "/books/OL1M"})
    assert row.status == "SOURCE_CONFIRMED_ABSENT"
    assert row.before_value_present is False
    assert row.after_value_present is False


def test_audit_isbn_enriched_from_edition_detail() -> None:
    row = audit_isbn(_candidate(), {"key": "/books/OL1M", "isbn_13": ["9780306406157"]})
    assert row.status == "ENRICHED_FROM_SOURCE"
    assert row.after_value_present is True
    assert row.source == "openlibrary_edition_detail"


def test_audit_isbn_present_already_at_search_api_level() -> None:
    row = audit_isbn(
        _candidate(isbn_13=("9780306406157",)), {"key": "/books/OL1M"}
    )
    assert row.status == "PRESENT"
    assert row.before_value_present is True


def test_audit_isbn_invalid_source_value() -> None:
    row = audit_isbn(_candidate(), {"key": "/books/OL1M", "isbn_10": ["250350325"]})
    assert row.status == "SOURCE_VALUE_INVALID"
    assert row.after_value_present is False


def test_audit_isbn_record_missing() -> None:
    row = audit_isbn(_candidate(), None)
    assert row.status == "SOURCE_RECORD_MISSING"


# --------------------------------------------------------------------------
# publisher / publication_year / page_count / summary
# --------------------------------------------------------------------------


def test_audit_publisher_enriched() -> None:
    row = audit_publisher(_candidate(), {"key": "/books/OL1M", "publishers": ["Real Publisher"]})
    assert row.status == "ENRICHED_FROM_SOURCE"


def test_audit_publisher_confirmed_absent() -> None:
    row = audit_publisher(_candidate(), {"key": "/books/OL1M"})
    assert row.status == "SOURCE_CONFIRMED_ABSENT"


def test_audit_publication_year_enriched() -> None:
    row = audit_publication_year(_candidate(), {"key": "/books/OL1M", "publish_date": "1998"})
    assert row.status == "ENRICHED_FROM_SOURCE"


def test_audit_publication_year_ambiguous() -> None:
    row = audit_publication_year(
        _candidate(), {"key": "/books/OL1M", "publish_date": "199?"}
    )
    assert row.status == "AMBIGUOUS_NOT_USED"


def test_audit_page_count_enriched_via_pagination() -> None:
    row = audit_page_count(
        _candidate(), {"key": "/books/OL1M", "pagination": "132 p."}
    )
    assert row.status == "ENRICHED_FROM_SOURCE"


def test_audit_page_count_ambiguous_multivolume() -> None:
    row = audit_page_count(
        _candidate(), {"key": "/books/OL1M", "pagination": "2 v. ;"}
    )
    assert row.status == "AMBIGUOUS_NOT_USED"


def test_audit_page_count_confirmed_absent() -> None:
    row = audit_page_count(_candidate(), {"key": "/books/OL1M"})
    assert row.status == "SOURCE_CONFIRMED_ABSENT"


def test_audit_summary_present() -> None:
    # summary enrichment already shipped in DEV-13.19.C/E: a value found
    # here is PRESENT, not new to DEV-13.20.
    row = audit_summary(
        _candidate(), {"key": "/works/OL1W", "description": "Un résumé réel."}
    )
    assert row.status == "PRESENT"
    assert row.before_value_present is True


def test_audit_summary_confirmed_absent() -> None:
    row = audit_summary(_candidate(), {"key": "/works/OL1W"})
    assert row.status == "SOURCE_CONFIRMED_ABSENT"


def test_audit_cover_image_url_present_when_asset_materialized(tmp_path: Path) -> None:
    asset_dir = tmp_path / "covers"
    asset_dir.mkdir()
    (asset_dir / "ol-cover-123.jpg").write_bytes(b"fake-jpeg-bytes")

    row = audit_cover_image_url(_candidate(cover_id=123), covers_assets_dir=asset_dir)
    assert row.status == "PRESENT"
    assert row.before_value_present is True
    assert row.after_value_present is True


def test_audit_cover_image_url_out_of_scope_when_available_but_not_materialized(
    tmp_path: Path,
) -> None:
    row = audit_cover_image_url(_candidate(cover_id=123), covers_assets_dir=tmp_path)
    assert row.status == "OUT_OF_SCOPE_BY_POLICY"
    assert row.after_value_present is False


def test_audit_cover_image_url_confirmed_absent_without_cover_id() -> None:
    row = audit_cover_image_url(_candidate(cover_id=None), covers_assets_dir=None)
    assert row.status == "SOURCE_CONFIRMED_ABSENT"


def test_audit_summary_record_missing() -> None:
    row = audit_summary(_candidate(), None)
    assert row.status == "SOURCE_RECORD_MISSING"


# --------------------------------------------------------------------------
# nationality
# --------------------------------------------------------------------------


def test_audit_nationality_enriched() -> None:
    author_record = {"key": "/authors/OL1A", "remote_ids": {"wikidata": "Q1"}}
    wikidata_author_records = {
        "Q1": {
            "id": "Q1",
            "claims": {
                "P27": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q142"}}}}]
            },
        }
    }
    wikidata_country_records = {
        "Q142": {
            "id": "Q142",
            "labels": {"en": {"value": "France"}},
            "claims": {
                "P31": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q6256"}}}}]
            },
        }
    }
    row = audit_nationality(
        "OL1A", author_record, wikidata_author_records, wikidata_country_records
    )
    assert row.status == "ENRICHED_FROM_SOURCE"
    assert row.after_value_present is True


def test_audit_nationality_no_remote_id() -> None:
    row = audit_nationality("OL1A", {"key": "/authors/OL1A"}, {}, {})
    assert row.status == "SOURCE_CONFIRMED_ABSENT"


def test_audit_nationality_ambiguous_multiple_citizenships() -> None:
    author_record = {"key": "/authors/OL1A", "remote_ids": {"wikidata": "Q1"}}
    wikidata_author_records = {
        "Q1": {
            "id": "Q1",
            "claims": {
                "P27": [
                    {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q142"}}}},
                    {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q30"}}}},
                ]
            },
        }
    }
    row = audit_nationality("OL1A", author_record, wikidata_author_records, {})
    assert row.status == "AMBIGUOUS_NOT_USED"


def test_audit_nationality_out_of_scope_historical_polity() -> None:
    author_record = {"key": "/authors/OL1A", "remote_ids": {"wikidata": "Q1"}}
    wikidata_author_records = {
        "Q1": {
            "id": "Q1",
            "claims": {
                "P27": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q209857"}}}}]
            },
        }
    }
    wikidata_country_records = {
        "Q209857": {
            "id": "Q209857",
            "labels": {"en": {"value": "Kingdom of Lombardy-Venetia"}},
            "claims": {
                "P31": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q417175"}}}}]
            },
        }
    }
    row = audit_nationality(
        "OL1A", author_record, wikidata_author_records, wikidata_country_records
    )
    assert row.status == "OUT_OF_SCOPE_BY_POLICY"


def test_audit_nationality_record_missing() -> None:
    row = audit_nationality("OL1A", None, {}, {})
    assert row.status == "SOURCE_RECORD_MISSING"


def test_audit_author_date_present_ambiguous_absent_missing() -> None:
    # Already shipped in DEV-13.19.F: a value found here is PRESENT.
    present = audit_author_date(
        "OL1A", "birth_date", "1900-01-01", {"key": "/authors/OL1A", "birth_date": "1900-01-01"}
    )
    assert present.status == "PRESENT"
    assert present.before_value_present is True

    ambiguous = audit_author_date(
        "OL1A", "birth_date", "1900", {"key": "/authors/OL1A", "birth_date": "1900"}
    )
    assert ambiguous.status == "AMBIGUOUS_NOT_USED"

    absent = audit_author_date("OL1A", "birth_date", None, {"key": "/authors/OL1A"})
    assert absent.status == "SOURCE_CONFIRMED_ABSENT"

    missing = audit_author_date("OL1A", "birth_date", None, None)
    assert missing.status == "SOURCE_RECORD_MISSING"


def test_audit_biography_present_and_absent() -> None:
    # Biography enrichment already shipped in DEV-13.19.F: a value found
    # here is PRESENT (pre-existing baseline), not new to DEV-13.20.
    present = audit_biography("OL1A", {"key": "/authors/OL1A", "bio": "Une bio réelle."})
    assert present.status == "PRESENT"
    assert present.before_value_present is True

    absent = audit_biography("OL1A", {"key": "/authors/OL1A"})
    assert absent.status == "SOURCE_CONFIRMED_ABSENT"

    missing = audit_biography("OL1A", None)
    assert missing.status == "SOURCE_RECORD_MISSING"


# --------------------------------------------------------------------------
# coverage / determinism
# --------------------------------------------------------------------------


def test_audit_title_row_covers_all_six_fields() -> None:
    rows = audit_title_row(
        _candidate(),
        edition_records={},
        work_records={},
        covers_assets_dir=None,
    )
    fields = {row.field for row in rows}
    assert fields == {
        "isbn", "publisher", "publication_year", "page_count", "summary", "cover_image_url",
    }
    assert all(row.entity_type == "title" for row in rows)


def test_audit_author_row_covers_all_four_fields() -> None:
    rows = audit_author_row(
        "OL1A", {"key": "/authors/OL1A"}, wikidata_author_records={}, wikidata_country_records={}
    )
    fields = {row.field for row in rows}
    assert fields == {"birth_date", "death_date", "biography", "nationality"}
    assert all(row.entity_type == "author" for row in rows)


def test_build_completeness_audit_covers_100_percent_of_lines(tmp_path: Path) -> None:
    selected = [
        _candidate(edition_key="/books/OL1M", work_key="/works/OL1W", author_keys=("OL1A",)),
        _candidate(edition_key="/books/OL2M", work_key="/works/OL2W", author_keys=("OL1A", "OL2A")),
    ]

    rows = build_completeness_audit(
        selected,
        author_records={},
        edition_records={},
        work_records={},
        wikidata_author_records={},
        wikidata_country_records={},
        covers_assets_dir=None,
    )

    # 2 Titles x 6 fields + 2 unique authors x 4 fields.
    assert len(rows) == 2 * 6 + 2 * 4
    audited_titles = {row.source_key for row in rows if row.entity_type == "title"}
    audited_authors = {row.source_key for row in rows if row.entity_type == "author"}
    assert audited_titles == {"/books/OL1M", "/books/OL2M"}
    assert audited_authors == {"OL1A", "OL2A"}


def test_build_completeness_audit_is_deterministic() -> None:
    selected = [_candidate(author_keys=("OL2A", "OL1A"))]

    first = build_completeness_audit(
        selected,
        author_records={},
        edition_records={},
        work_records={},
        wikidata_author_records={},
        wikidata_country_records={},
        covers_assets_dir=None,
    )
    second = build_completeness_audit(
        selected,
        author_records={},
        edition_records={},
        work_records={},
        wikidata_author_records={},
        wikidata_country_records={},
        covers_assets_dir=None,
    )
    assert first == second


def test_write_audit_csv_round_trip(tmp_path: Path) -> None:
    rows = [
        AuditRow("title", "/books/OL1M", "isbn", False, True, "openlibrary_edition_detail", "ENRICHED_FROM_SOURCE", "test"),
    ]
    path = tmp_path / "completeness_audit.csv"
    write_audit_csv(rows, path)

    import csv

    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        assert reader.fieldnames == list(AUDIT_FIELDNAMES)
        loaded = list(reader)

    assert len(loaded) == 1
    assert loaded[0]["source_key"] == "/books/OL1M"
    assert loaded[0]["after_value_present"] == "true"
