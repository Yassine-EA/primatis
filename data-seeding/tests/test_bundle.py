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


def _candidate(*, isbn=(), isbn_10=(), isbn_13=()) -> SelectedEdition:
    return SelectedEdition(
        language="FR",
        language_code="fre",
        work_key="/works/OL1W",
        edition_key="/books/OL1M",
        title="Book",
        subtitle=None,
        author_keys=("/authors/OL1A",),
        author_names=("Author",),
        subjects=(),
        isbn_10=tuple(isbn_10),
        isbn_13=tuple(isbn_13),
        isbn=tuple(isbn),
        publishers=(),
        publish_date=None,
        publish_year=None,
        number_of_pages=None,
    )


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
