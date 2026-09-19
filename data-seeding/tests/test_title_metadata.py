import csv
import importlib.util
import json
import sys
from pathlib import Path

import pytest

from primatis_data_seeding.generation.title_metadata import (
    ENRICHED, decide_publication_year, decide_publisher,
)

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "dev173_enrich_title_metadata.py"


def _script():
    spec = importlib.util.spec_from_file_location("dev173_enrich", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_single_clean_publisher_is_enriched() -> None:
    d = decide_publisher("9780140435719", [{"publishers": ["Penguin Classics"]}, {"publisher": ["Penguin Classics"]}])
    assert (d.decision, d.value) == (ENRICHED, "Penguin Classics")


@pytest.mark.parametrize("records, decision", [
    ([{"publisher": ["Alfred A. Knopf", "Knopf"]}], "REJECTED_CONFLICT"),
    ([{"publisher": ["Da Capo Press"]}, {"publisher": ["Da Capo"]}], "REJECTED_CONFLICT"),
    ([{"publisher": ["Oxford World's Classics Series"]}], "REJECTED_NOISE"),
    ([{"publisher": ["VINTAGE (RAND)"]}], "REJECTED_NOISE"),
    ([{"publisher": ["Distributed in North America by X"]}], "REJECTED_NOISE"),
    ([{"publisher": []}, {"publishers": None}], "REJECTED_ABSENT"),
])
def test_publisher_rejections_are_traced_never_guessed(records, decision) -> None:
    d = decide_publisher("x", records)
    assert d.decision == decision and d.value == ""


@pytest.mark.parametrize("dates, expected", [
    (["2007"], "2007"),
    (["November 5, 2007", "2007"], "2007"),
    ("['2002']", "2002"),
])
def test_publication_year_single_consensus(dates, expected) -> None:
    d = decide_publication_year("x", [{"publish_date": dates}], max_year=2026)
    assert (d.decision, d.value) == (ENRICHED, expected)


@pytest.mark.parametrize("records, decision", [
    ([{"publish_date": ["2007"]}, {"publish_date": ["2008"]}], "REJECTED_CONFLICT"),
    ([{"publish_date": ["1999 2001"]}], "REJECTED_AMBIGUOUS_DATE"),
    ([{"publish_date": ["2099"]}], "REJECTED_OUT_OF_RANGE"),
    ([{"publish_date": ["sans date"]}], "REJECTED_ABSENT"),
])
def test_publication_year_rejections(records, decision) -> None:
    assert decide_publication_year("x", records, max_year=2026).decision == decision


def test_future_year_is_rejected_relative_to_reference_year() -> None:
    assert decide_publication_year("x", [{"publish_date": ["2027"]}], max_year=2026).decision == "REJECTED_OUT_OF_RANGE"


def test_isbn10_conversion_is_exact() -> None:
    script = _script()
    assert script.isbn10_to_isbn13("0306406152") == "9780306406157"
    assert script.isbn10_to_isbn13("030640615X") is None  # checksum X non converti


def test_matching_is_exact_isbn_only_no_title_similarity(tmp_path: Path) -> None:
    script = _script()
    data = tmp_path / "data" / "cache"
    data.mkdir(parents=True)
    # même titre, ISBN différent : ne doit JAMAIS être apparié
    (data / "a.json").write_text(json.dumps({"docs": [
        {"key": "/books/OL1M", "title": "The Same Title", "isbn": ["9780000000002"],
         "publisher": ["Wrong House"], "publish_date": ["1999"]},
        {"key": "/books/OL2M", "title": "Right", "isbn_10": ["0306406152"],
         "publisher": ["Da Capo"], "publish_date": ["November 5, 2007"]},
    ]}), encoding="utf-8")
    records = script.collect_records(tmp_path / "data", {"9780306406157", "9781111111113"})
    assert set(records) == {"9780306406157"}
    rows = script.build_rows(records, 2026)
    assert {(r["field"], r["value"], r["decision"]) for r in rows} == {
        ("publisher", "Da Capo", ENRICHED), ("publication_year", "2007", ENRICHED),
    }
    assert all(r["source_record"] == "/books/OL2M" and r["source"].endswith("a.json") for r in rows)
    assert list(rows[0]) == ["isbn", "field", "value", "source", "source_record", "decision"]
