import csv
from datetime import date
import json
from pathlib import Path

import pytest

from primatis_data_seeding.config import SeedProfile, load_profiles
from primatis_data_seeding.generation.copies import PROFILE_COPY_DISTRIBUTIONS
from primatis_data_seeding.pipeline.bundle import (
    SelectedEdition,
    _classify_generic_isbn,
    _select_isbn,
    build_bundle,
    load_selected_editions,
    load_validated_localities,
    normalize_selected_catalogue,
)


CONFIG_PATH = Path(__file__).resolve().parents[1] / "config" / "profiles.toml"
PROFILES = load_profiles(CONFIG_PATH)

SMALL_PROFILE = PROFILES["small"]
MEDIUM_PROFILE = PROFILES["medium"]

SMALL_LANGUAGES = (
    ("FR", "fre", 75),
    ("EN", "eng", 10),
    ("NL", "dut", 8),
    ("DE", "ger", 3),
    ("ES", "spa", 2),
    ("IT", "ita", 1),
    ("LA", "lat", 1),
)

MEDIUM_LANGUAGES = (
    ("FR", "fre", 750),
    ("EN", "eng", 100),
    ("NL", "dut", 80),
    ("DE", "ger", 30),
    ("ES", "spa", 20),
    ("IT", "ita", 15),
    ("LA", "lat", 5),
)

BUNDLE_PROFILES = (
    (SMALL_PROFILE, SMALL_LANGUAGES, 100, 160, 25),
    (MEDIUM_PROFILE, MEDIUM_LANGUAGES, 1000, 1600, 100),
)


def _valid_isbn13(ordinal: int) -> str:
    if not 1 <= ordinal <= 999999:
        raise ValueError("ordinal out of range")

    first_twelve = f"978000{ordinal:06d}"
    total = sum(
        int(digit) * (1 if index % 2 == 0 else 3)
        for index, digit in enumerate(first_twelve)
    )
    check_digit = (10 - (total % 10)) % 10
    return f"{first_twelve}{check_digit}"


def _write_selected(
    path: Path,
    languages=SMALL_LANGUAGES,
    *,
    duplicate_edition: bool = False,
    cover_id_for_first: int | None = None,
) -> None:
    rows = []
    ordinal = 1
    total = sum(count for _, _, count in languages)
    for language, language_code, count in languages:
        for _ in range(count):
            edition_ordinal = (
                1 if duplicate_edition and ordinal == total else ordinal
            )
            rows.append({
                "language": language,
                "language_code": language_code,
                "work_key": f"/works/OL{ordinal}W",
                "edition_key": f"/books/OL{edition_ordinal}M",
                "title": f"Book {ordinal}",
                "subtitle": None,
                "author_keys": [f"/authors/OL{ordinal}A"],
                "author_names": [f"Author {ordinal}"],
                "subjects": ["Fiction"],
                "isbn_10": [],
                "isbn_13": [],
                "isbn": [_valid_isbn13(ordinal)],
                "publishers": ["Publisher"],
                "publish_date": "2020",
                "publish_year": 2020,
                "number_of_pages": 100 + ordinal,
                "cover_id": cover_id_for_first if ordinal == 1 else None,
            })
            ordinal += 1

    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def _write_bpost(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=("postal_code", "locality"))
        writer.writeheader()
        writer.writerow({"postal_code": "1000", "locality": "Bruxelles"})
        writer.writerow({"postal_code": "6000", "locality": "Charleroi"})


def _candidate(
    *,
    isbn=(),
    isbn_10=(),
    isbn_13=(),
    number_of_pages=None,
    cover_id=None,
    author_keys=("/authors/OL1A",),
) -> SelectedEdition:
    return SelectedEdition(
        language="FR",
        language_code="fre",
        work_key="/works/OL1W",
        edition_key="/books/OL1M",
        title="Book",
        subtitle=None,
        author_keys=tuple(author_keys),
        author_names=("Author",),
        subjects=(),
        isbn_10=tuple(isbn_10),
        isbn_13=tuple(isbn_13),
        isbn=tuple(isbn),
        publishers=(),
        publish_date=None,
        publish_year=None,
        number_of_pages=number_of_pages,
        cover_id=cover_id,
    )


def test_normalize_selected_catalogue_without_author_records_is_unchanged() -> None:
    authors, _editions, _subjects = normalize_selected_catalogue([_candidate()])

    assert len(authors) == 1
    assert authors[0].full_name == "Author"
    assert authors[0].birth_date is None
    assert authors[0].death_date is None
    assert authors[0].biography is None


def test_normalize_selected_catalogue_enriches_by_exact_author_key() -> None:
    # author_records mirrors load_authors_snapshot()'s real contract: it
    # is indexed by the CANONICAL (bare) author_key, even though the raw
    # record's own "key" field keeps the dump's prefixed form.
    author_records = {
        "OL1A": {
            "key": "/authors/OL1A",
            "name": "Canonical Author Name",
            "birth_date": "1900-01-01",
            "death_date": "1980-01-01",
            "bio": "Notice biographique.",
        }
    }

    authors, _editions, _subjects = normalize_selected_catalogue(
        [_candidate()], author_records=author_records
    )

    assert len(authors) == 1
    enriched = authors[0]
    assert enriched.full_name == "Canonical Author Name"
    assert enriched.birth_date == date(1900, 1, 1)
    assert enriched.death_date == date(1980, 1, 1)
    assert enriched.biography == "Notice biographique."


def test_normalize_selected_catalogue_never_enriches_by_name(tmp_path: Path) -> None:
    # A record keyed by a DIFFERENT author_key must never be applied, even
    # if its name happens to match the Search-API author_name.
    author_records = {
        "OL999A": {
            "key": "/authors/OL999A",
            "name": "Author",
            "birth_date": "1900-01-01",
        }
    }

    authors, _editions, _subjects = normalize_selected_catalogue(
        [_candidate()], author_records=author_records
    )

    assert authors[0].full_name == "Author"
    assert authors[0].birth_date is None


def test_normalize_selected_catalogue_name_with_slash_is_not_split() -> None:
    author_records = {
        "OL1A": {
            "key": "/authors/OL1A",
            "name": "Charlotte Brontë / Currer Bell",
        }
    }

    authors, _editions, _subjects = normalize_selected_catalogue(
        [_candidate()], author_records=author_records
    )

    assert authors[0].full_name == "Charlotte Brontë / Currer Bell"


def test_normalize_selected_catalogue_reconciles_bare_search_api_key_with_prefixed_snapshot() -> None:
    # DEV-13.19.F regression: reproduces the exact real bug found in
    # DEV-13.19.E. The Search API author_key is bare ("OL1098039A"); the
    # Authors dump snapshot (via load_authors_snapshot) indexes by the
    # SAME canonical bare form even though the dump's own record carries
    # the prefixed "key" field. Enrichment must apply via canonicalization
    # — never a name match, never a "/" split.
    author_records = {
        "OL1098039A": {
            "key": "/authors/OL1098039A",
            "name": "Carl Maria von Weber",
            "bio": {
                "type": "/type/text",
                "value": "Deutscher Komponist, Dirigent und Pianist",
            },
        }
    }

    authors, _editions, _subjects = normalize_selected_catalogue(
        [_candidate(author_keys=("OL1098039A",))],
        author_records=author_records,
    )

    assert len(authors) == 1
    enriched = authors[0]
    assert enriched.full_name == "Carl Maria von Weber"
    assert enriched.biography == "Deutscher Komponist, Dirigent und Pianist"


def test_normalize_selected_catalogue_reconciles_prefixed_candidate_with_bare_snapshot() -> None:
    # The reverse direction: candidate carries the prefixed form (as some
    # fixtures/legacy inputs might), snapshot is indexed bare (the real
    # load_authors_snapshot contract). Must still match.
    author_records = {"OL1098039A": {"key": "/authors/OL1098039A", "name": "Carl Maria von Weber"}}

    authors, _editions, _subjects = normalize_selected_catalogue(
        [_candidate(author_keys=("/authors/OL1098039A",))],
        author_records=author_records,
    )

    assert authors[0].full_name == "Carl Maria von Weber"


def test_normalize_selected_catalogue_cover_id_is_propagated_from_candidate() -> None:
    _authors, editions, _subjects = normalize_selected_catalogue(
        [_candidate(cover_id=258027)]
    )

    assert editions[0].cover_id == 258027


def test_normalize_selected_catalogue_cover_id_absent_is_none() -> None:
    _authors, editions, _subjects = normalize_selected_catalogue([_candidate()])

    assert editions[0].cover_id is None


def test_normalize_selected_catalogue_never_derives_cover_id_from_isbn() -> None:
    # A real ISBN must never be used as a substitute cover identity: only
    # a real cover_i from the Search API can ever produce a cover_id.
    _authors, editions, _subjects = normalize_selected_catalogue(
        [_candidate(isbn=("9780306406157",))]
    )

    assert editions[0].cover_id is None


def test_normalize_selected_catalogue_summary_from_exact_work_key() -> None:
    work_records = {
        "/works/OL1W": {"key": "/works/OL1W", "description": "Un résumé réel."}
    }

    _authors, editions, _subjects = normalize_selected_catalogue(
        [_candidate()], work_records=work_records
    )

    assert editions[0].summary == "Un résumé réel."


def test_normalize_selected_catalogue_summary_never_matched_by_other_work_key() -> None:
    # A Work record filed under a DIFFERENT work_key must never leak in.
    work_records = {
        "/works/OL999W": {"key": "/works/OL999W", "description": "Autre œuvre."}
    }

    _authors, editions, _subjects = normalize_selected_catalogue(
        [_candidate()], work_records=work_records
    )

    assert editions[0].summary is None


def test_normalize_selected_catalogue_summary_without_work_records_is_none() -> None:
    _authors, editions, _subjects = normalize_selected_catalogue([_candidate()])

    assert editions[0].summary is None


def test_normalize_selected_catalogue_page_count_from_exact_edition_key() -> None:
    edition_records = {
        "/books/OL1M": {"key": "/books/OL1M", "number_of_pages": 342}
    }

    _authors, editions, _subjects = normalize_selected_catalogue(
        [_candidate(number_of_pages=None)], edition_records=edition_records
    )

    assert editions[0].page_count == 342


def test_normalize_selected_catalogue_page_count_never_uses_another_edition() -> None:
    # A detailed Edition record filed under a DIFFERENT edition_key must
    # never be used as a fallback/estimate for this edition.
    edition_records = {
        "/books/OL999M": {"key": "/books/OL999M", "number_of_pages": 999}
    }

    _authors, editions, _subjects = normalize_selected_catalogue(
        [_candidate(number_of_pages=None)], edition_records=edition_records
    )

    assert editions[0].page_count is None


def test_normalize_selected_catalogue_page_count_falls_back_to_search_api() -> None:
    # Without a matching Edition detail record, historical behavior
    # (Search API number_of_pages) is preserved.
    _authors, editions, _subjects = normalize_selected_catalogue(
        [_candidate(number_of_pages=250)]
    )

    assert editions[0].page_count == 250


def test_normalize_selected_catalogue_page_count_invalid_detail_is_none() -> None:
    edition_records = {
        "/books/OL1M": {"key": "/books/OL1M", "number_of_pages": "not-a-number"}
    }

    _authors, editions, _subjects = normalize_selected_catalogue(
        [_candidate(number_of_pages=None)], edition_records=edition_records
    )

    assert editions[0].page_count is None


def test_normalize_selected_catalogue_page_count_absent_detail_is_none() -> None:
    edition_records = {"/books/OL1M": {"key": "/books/OL1M"}}

    _authors, editions, _subjects = normalize_selected_catalogue(
        [_candidate(number_of_pages=None)], edition_records=edition_records
    )

    assert editions[0].page_count is None


def test_small_profile_targets_are_stable() -> None:
    assert SMALL_PROFILE.title_target == 100
    assert SMALL_PROFILE.user_target == 25
    assert PROFILE_COPY_DISTRIBUTIONS["small"].copy_count == 160


def test_medium_profile_targets_are_stable() -> None:
    assert MEDIUM_PROFILE.title_target == 1000
    assert MEDIUM_PROFILE.user_target == 100
    assert PROFILE_COPY_DISTRIBUTIONS["medium"].copy_count == 1600


@pytest.mark.parametrize("profile_name", ("small", "medium"))
def test_copy_distribution_title_count_matches_profile_target(
    profile_name: str,
) -> None:
    assert (
        PROFILE_COPY_DISTRIBUTIONS[profile_name].title_count
        == PROFILES[profile_name].title_target
    )


def test_generic_isbn_is_classified_by_length() -> None:
    isbn13, isbn10 = _classify_generic_isbn(
        ("978-0-306-40615-7", "0-306-40615-2", "garbage")
    )
    assert isbn13 == ("978-0-306-40615-7",)
    assert isbn10 == ("0-306-40615-2",)


def test_generic_valid_isbn13_is_used_as_fallback() -> None:
    assert _select_isbn(_candidate(isbn=("9780306406157",))) == "9780306406157"


def test_invalid_generic_isbn_is_not_invented() -> None:
    assert _select_isbn(_candidate(isbn=("9780306406158",))) is None


def test_load_selected_requires_exact_row_count(tmp_path: Path) -> None:
    path = tmp_path / "selected.jsonl"
    path.write_text("", encoding="utf-8")

    with pytest.raises(ValueError, match="exactly 100"):
        load_selected_editions(path, expected_count=100)


def test_load_selected_rejects_duplicate_edition_key(tmp_path: Path) -> None:
    path = tmp_path / "selected.jsonl"
    _write_selected(path, duplicate_edition=True)

    with pytest.raises(ValueError, match="Duplicate edition_key"):
        load_selected_editions(path, expected_count=100)


def test_loads_validated_bpost_csv(tmp_path: Path) -> None:
    path = tmp_path / "bpost.csv"
    _write_bpost(path)

    rows = load_validated_localities(path)

    assert [(row.postal_code, row.locality) for row in rows] == [
        ("1000", "Bruxelles"),
        ("6000", "Charleroi"),
    ]


def test_build_bundle_rejects_empty_password(tmp_path: Path) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    _write_selected(selected)
    _write_bpost(bpost)

    with pytest.raises(ValueError, match="non-empty"):
        build_bundle(
            profile=SMALL_PROFILE,
            selected_jsonl=selected,
            bpost_csv=bpost,
            output_dir=tmp_path / "bundle",
            seed=13014,
            reference_date=date(2026, 8, 25),
            raw_password="",
        )


def test_build_bundle_rejects_short_password(tmp_path: Path) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    _write_selected(selected)
    _write_bpost(bpost)

    with pytest.raises(ValueError, match="at least 12"):
        build_bundle(
            profile=SMALL_PROFILE,
            selected_jsonl=selected,
            bpost_csv=bpost,
            output_dir=tmp_path / "bundle",
            seed=13014,
            reference_date=date(2026, 8, 25),
            raw_password="too-short",
        )


def test_build_bundle_rejects_profile_with_scenarios_enabled(tmp_path: Path) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    _write_selected(selected)
    _write_bpost(bpost)

    scenario_profile = SeedProfile(
        name="small",
        database=SMALL_PROFILE.database,
        title_target=SMALL_PROFILE.title_target,
        copy_target=SMALL_PROFILE.copy_target,
        include_demo_scenarios=True,
        user_target=SMALL_PROFILE.user_target,
    )

    with pytest.raises(NotImplementedError):
        build_bundle(
            profile=scenario_profile,
            selected_jsonl=selected,
            bpost_csv=bpost,
            output_dir=tmp_path / "bundle",
            seed=13014,
            reference_date=date(2026, 8, 25),
            raw_password="DemoPassword!2026",
        )


@pytest.mark.parametrize(
    ("profile", "languages", "title_target", "copy_target", "user_target"),
    BUNDLE_PROFILES,
)
def test_build_bundle_exports_exact_targets(
    tmp_path: Path,
    profile: SeedProfile,
    languages,
    title_target: int,
    copy_target: int,
    user_target: int,
) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    output = tmp_path / "bundle"
    _write_selected(selected, languages)
    _write_bpost(bpost)

    report = build_bundle(
        profile=profile,
        selected_jsonl=selected,
        bpost_csv=bpost,
        output_dir=output,
        seed=13014,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    assert report["catalogue"]["mapped_titles"] == title_target
    assert report["copies"]["count"] == copy_target
    assert report["users"]["count"] == user_target
    assert report["scenarios"]["enabled"] is False
    assert report["catalogue"]["isbn_present"] == title_target
    assert report["catalogue"]["authors_enriched"] == 0
    assert report["catalogue"]["summary_present"] == 0
    assert report["catalogue"]["cover_image_present"] == 0


def _write_authors_snapshot(path: Path, records: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for record in sorted(records, key=lambda item: item["key"]):
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")


def test_build_bundle_enriches_authors_from_snapshot_and_preserves_others(
    tmp_path: Path,
) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    authors_snapshot = tmp_path / "authors_selected.jsonl"
    output = tmp_path / "bundle"
    _write_selected(selected, SMALL_LANGUAGES)
    _write_bpost(bpost)
    _write_authors_snapshot(
        authors_snapshot,
        [
            {
                "key": "/authors/OL1A",
                "name": "Canonical Author One",
                "birth_date": "1900-01-01",
                "death_date": "1980-01-01",
                "bio": "Notice biographique enrichie.",
            }
        ],
    )

    report = build_bundle(
        profile=SMALL_PROFILE,
        selected_jsonl=selected,
        bpost_csv=bpost,
        output_dir=output,
        seed=13014,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
        authors_snapshot_jsonl=authors_snapshot,
    )

    assert report["catalogue"]["authors_enriched"] == 1

    with (output / "authors.csv").open(encoding="utf-8", newline="") as handle:
        rows = {row["source_key"]: row for row in csv.DictReader(handle)}

    enriched = rows["/authors/OL1A"]
    assert enriched["full_name"] == "Canonical Author One"
    assert enriched["birth_date"] == "1900-01-01"
    assert enriched["death_date"] == "1980-01-01"
    assert enriched["biography"] == "Notice biographique enrichie."
    assert enriched["nationality"] == ""

    unenriched = rows["/authors/OL2A"]
    assert unenriched["full_name"] == "Author 2"
    assert unenriched["birth_date"] == ""
    assert unenriched["death_date"] == ""
    assert unenriched["biography"] == ""
    assert unenriched["nationality"] == ""

    # Nationality is never populated, even on an enriched Author (rule: NULL always).
    for row in rows.values():
        assert row["nationality"] == ""


def _write_records_snapshot(path: Path, records: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for record in sorted(records, key=lambda item: item["key"]):
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")


def test_build_bundle_enriches_titles_from_snapshots_and_preserves_others(
    tmp_path: Path,
) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    work_snapshot = tmp_path / "works_selected.jsonl"
    edition_snapshot = tmp_path / "editions_selected.jsonl"
    covers_assets_dir = tmp_path / "covers"
    output = tmp_path / "bundle"

    _write_selected(selected, SMALL_LANGUAGES, cover_id_for_first=258027)
    _write_bpost(bpost)
    _write_records_snapshot(
        work_snapshot,
        [{"key": "/works/OL1W", "description": "Un résumé réel."}],
    )
    _write_records_snapshot(
        edition_snapshot,
        [{"key": "/books/OL1M", "number_of_pages": 342}],
    )
    covers_assets_dir.mkdir(parents=True)
    (covers_assets_dir / "ol-cover-258027.jpg").write_bytes(b"fake-jpeg-bytes")

    report = build_bundle(
        profile=SMALL_PROFILE,
        selected_jsonl=selected,
        bpost_csv=bpost,
        output_dir=output,
        seed=13014,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
        work_snapshot_jsonl=work_snapshot,
        edition_snapshot_jsonl=edition_snapshot,
        covers_assets_dir=covers_assets_dir,
    )

    assert report["catalogue"]["summary_present"] == 1
    assert report["catalogue"]["cover_image_present"] == 1
    # page_count remains present for every Title: either the Edition-detail
    # override for /books/OL1M, or the historical Search-API fallback.
    assert report["catalogue"]["page_count_present"] == 100

    with (output / "titles.csv").open(encoding="utf-8", newline="") as handle:
        rows = {row["source_key"]: row for row in csv.DictReader(handle)}

    enriched = rows["/books/OL1M"]
    assert enriched["summary"] == "Un résumé réel."
    assert enriched["page_count"] == "342"
    assert enriched["cover_image_url"] == "/covers/catalogue/ol-cover-258027.jpg"

    unenriched = rows["/books/OL2M"]
    assert unenriched["summary"] == ""
    assert unenriched["page_count"] == "102"
    assert unenriched["cover_image_url"] == ""


def test_build_bundle_cover_id_present_but_asset_missing_stays_null(
    tmp_path: Path,
) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    covers_assets_dir = tmp_path / "covers"
    output = tmp_path / "bundle"

    _write_selected(selected, SMALL_LANGUAGES, cover_id_for_first=258027)
    _write_bpost(bpost)
    # covers_assets_dir is provided but the asset was never materialized.

    report = build_bundle(
        profile=SMALL_PROFILE,
        selected_jsonl=selected,
        bpost_csv=bpost,
        output_dir=output,
        seed=13014,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
        covers_assets_dir=covers_assets_dir,
    )

    assert report["catalogue"]["cover_image_present"] == 0

    with (output / "titles.csv").open(encoding="utf-8", newline="") as handle:
        rows = {row["source_key"]: row for row in csv.DictReader(handle)}

    # Fail-closed: cover_id existed but no local asset -> NULL, never a
    # guessed/external URL.
    assert rows["/books/OL1M"]["cover_image_url"] == ""


def test_build_bundle_copies_csv_is_byte_identical_on_rerun(tmp_path: Path) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    output_first = tmp_path / "bundle_first"
    output_second = tmp_path / "bundle_second"
    _write_selected(selected, SMALL_LANGUAGES)
    _write_bpost(bpost)

    for output in (output_first, output_second):
        build_bundle(
            profile=SMALL_PROFILE,
            selected_jsonl=selected,
            bpost_csv=bpost,
            output_dir=output,
            seed=13014,
            reference_date=date(2026, 8, 25),
            raw_password="DemoPassword!2026",
        )

    first_bytes = (output_first / "copies.csv").read_bytes()
    second_bytes = (output_second / "copies.csv").read_bytes()
    assert first_bytes == second_bytes

    with (output_first / "copies.csv").open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    assert len(rows) == 160
    for row in rows:
        assert row["location"] != ""


@pytest.mark.parametrize(
    ("profile", "languages", "title_target", "copy_target", "user_target"),
    BUNDLE_PROFILES,
)
def test_bundle_exports_all_loader_contract_files(
    tmp_path: Path,
    profile: SeedProfile,
    languages,
    title_target: int,
    copy_target: int,
    user_target: int,
) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    output = tmp_path / "bundle"
    _write_selected(selected, languages)
    _write_bpost(bpost)

    build_bundle(
        profile=profile,
        selected_jsonl=selected,
        bpost_csv=bpost,
        output_dir=output,
        seed=13014,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    expected = {
        "authors.csv",
        "genres.csv",
        "titles.csv",
        "title_authors.csv",
        "title_genres.csv",
        "copies.csv",
        "bpost_localities.csv",
        "users.csv",
        "addresses.csv",
        "residences.csv",
        "loans.csv",
        "reservations.csv",
        "fines.csv",
        "notifications.csv",
        "copy_states.csv",
        "bundle_report.json",
        "deduplication_report.json",
    }
    assert expected <= {path.name for path in output.iterdir()}


@pytest.mark.parametrize(
    ("profile", "languages", "title_target", "copy_target", "user_target"),
    BUNDLE_PROFILES,
)
def test_bundle_copies_all_have_a_non_null_location(
    tmp_path: Path,
    profile: SeedProfile,
    languages,
    title_target: int,
    copy_target: int,
    user_target: int,
) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    output = tmp_path / "bundle"
    _write_selected(selected, languages)
    _write_bpost(bpost)

    build_bundle(
        profile=profile,
        selected_jsonl=selected,
        bpost_csv=bpost,
        output_dir=output,
        seed=13014,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    with (output / "copies.csv").open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    assert len(rows) == copy_target
    for row in rows:
        assert row["location"] != ""


@pytest.mark.parametrize(
    ("profile", "languages", "title_target", "copy_target", "user_target"),
    BUNDLE_PROFILES,
)
def test_bundle_has_empty_business_scenario_csvs(
    tmp_path: Path,
    profile: SeedProfile,
    languages,
    title_target: int,
    copy_target: int,
    user_target: int,
) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    output = tmp_path / "bundle"
    _write_selected(selected, languages)
    _write_bpost(bpost)

    build_bundle(
        profile=profile,
        selected_jsonl=selected,
        bpost_csv=bpost,
        output_dir=output,
        seed=13014,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    for name in ("loans.csv", "reservations.csv", "fines.csv", "notifications.csv", "copy_states.csv"):
        with (output / name).open(encoding="utf-8", newline="") as handle:
            rows = list(csv.DictReader(handle))
        assert rows == []


def test_small_bundle_language_distribution_matches_acquisition(tmp_path: Path) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    output = tmp_path / "bundle"
    _write_selected(selected, SMALL_LANGUAGES)
    _write_bpost(bpost)

    report = build_bundle(
        profile=SMALL_PROFILE,
        selected_jsonl=selected,
        bpost_csv=bpost,
        output_dir=output,
        seed=13014,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    assert report["catalogue"]["language_counts"] == {
        "DE": 3,
        "EN": 10,
        "ES": 2,
        "FR": 75,
        "IT": 1,
        "LA": 1,
        "NL": 8,
    }


def test_medium_bundle_language_distribution_matches_acquisition(tmp_path: Path) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    output = tmp_path / "bundle"
    _write_selected(selected, MEDIUM_LANGUAGES)
    _write_bpost(bpost)

    report = build_bundle(
        profile=MEDIUM_PROFILE,
        selected_jsonl=selected,
        bpost_csv=bpost,
        output_dir=output,
        seed=13014,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    assert report["catalogue"]["language_counts"] == {
        "DE": 30,
        "EN": 100,
        "ES": 20,
        "FR": 750,
        "IT": 15,
        "LA": 5,
        "NL": 80,
    }


def test_bundle_report_contains_input_hashes(tmp_path: Path) -> None:
    selected = tmp_path / "selected.jsonl"
    bpost = tmp_path / "bpost.csv"
    output = tmp_path / "bundle"
    _write_selected(selected)
    _write_bpost(bpost)

    report = build_bundle(
        profile=SMALL_PROFILE,
        selected_jsonl=selected,
        bpost_csv=bpost,
        output_dir=output,
        seed=13014,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    assert len(report["inputs"]["selected_sha256"]) == 64
    assert len(report["inputs"]["bpost_sha256"]) == 64
