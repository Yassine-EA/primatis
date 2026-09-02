from __future__ import annotations

import argparse
from collections import Counter
from collections.abc import Mapping
from dataclasses import asdict, dataclass
from datetime import date
import csv
import json
import os
from pathlib import Path
import re

from primatis_data_seeding.acquisition.openlibrary_authors import (
    load_authors_snapshot,
)
from primatis_data_seeding.acquisition.provenance import sha256_file
from primatis_data_seeding.config import SeedProfile, load_profiles
from primatis_data_seeding.deduplication.catalogue import (
    deduplicate_authors,
    deduplicate_editions,
)
from primatis_data_seeding.export.catalogue_csv import export_catalogue_csv
from primatis_data_seeding.export.scenarios_csv import export_scenarios_csv
from primatis_data_seeding.export.users_csv import export_users_csv
from primatis_data_seeding.generation.copies import (
    PROFILE_COPY_DISTRIBUTIONS,
    generate_copies,
)
from primatis_data_seeding.generation.scenarios import ScenarioGenerationResult
from primatis_data_seeding.generation.users import generate_synthetic_members
from primatis_data_seeding.mapping.catalogue import map_catalogue
from primatis_data_seeding.models import NormalizedAuthor, NormalizedEdition
from primatis_data_seeding.normalization.isbn import select_valid_isbn
from primatis_data_seeding.normalization.openlibrary import (
    normalize_author_record,
    normalize_page_count,
    normalize_publication_year,
)
from primatis_data_seeding.normalization.text import truncate_or_none
from primatis_data_seeding.reference.bpost import BpostLocality
from primatis_data_seeding.validation.catalogue import (
    validate_author,
    validate_edition,
)


@dataclass(frozen=True)
class SelectedEdition:
    language: str
    language_code: str
    work_key: str
    edition_key: str
    title: str
    subtitle: str | None
    author_keys: tuple[str, ...]
    author_names: tuple[str, ...]
    subjects: tuple[str, ...]
    isbn_10: tuple[str, ...]
    isbn_13: tuple[str, ...]
    isbn: tuple[str, ...]
    publishers: tuple[str, ...]
    publish_date: str | None
    publish_year: int | None
    number_of_pages: int | None


def _tuple_of_strings(value: object) -> tuple[str, ...]:
    if not isinstance(value, list):
        return ()
    return tuple(str(item) for item in value if item not in (None, ""))


def _classify_generic_isbn(
    values: tuple[str, ...],
) -> tuple[tuple[str, ...], tuple[str, ...]]:
    isbn13: list[str] = []
    isbn10: list[str] = []

    for raw in values:
        compact = re.sub(r"[^0-9Xx]", "", raw).upper()
        if len(compact) == 13 and compact.isdigit():
            isbn13.append(raw)
        elif len(compact) == 10 and compact[:9].isdigit() and (
            compact[9].isdigit() or compact[9] == "X"
        ):
            isbn10.append(raw)

    return tuple(isbn13), tuple(isbn10)


def _select_isbn(candidate: SelectedEdition) -> str | None:
    generic13, generic10 = _classify_generic_isbn(candidate.isbn)

    return select_valid_isbn(
        list(dict.fromkeys((*candidate.isbn_13, *generic13))),
        list(dict.fromkeys((*candidate.isbn_10, *generic10))),
    )


def load_selected_editions(
    path: Path,
    *,
    expected_count: int,
) -> list[SelectedEdition]:
    if not path.is_file():
        raise ValueError(f"Validated Open Library selection not found: {path}")

    result: list[SelectedEdition] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line:
                continue
            try:
                raw = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(
                    f"Invalid JSONL at {path}:{line_number}."
                ) from exc

            try:
                result.append(
                    SelectedEdition(
                        language=str(raw["language"]),
                        language_code=str(raw["language_code"]),
                        work_key=str(raw["work_key"]),
                        edition_key=str(raw["edition_key"]),
                        title=str(raw["title"]),
                        subtitle=(
                            str(raw["subtitle"])
                            if raw.get("subtitle") not in (None, "")
                            else None
                        ),
                        author_keys=_tuple_of_strings(raw.get("author_keys")),
                        author_names=_tuple_of_strings(raw.get("author_names")),
                        subjects=_tuple_of_strings(raw.get("subjects")),
                        isbn_10=_tuple_of_strings(raw.get("isbn_10")),
                        isbn_13=_tuple_of_strings(raw.get("isbn_13")),
                        isbn=_tuple_of_strings(raw.get("isbn")),
                        publishers=_tuple_of_strings(raw.get("publishers")),
                        publish_date=(
                            str(raw["publish_date"])
                            if raw.get("publish_date") not in (None, "")
                            else None
                        ),
                        publish_year=(
                            int(raw["publish_year"])
                            if raw.get("publish_year") not in (None, "")
                            else None
                        ),
                        number_of_pages=(
                            int(raw["number_of_pages"])
                            if raw.get("number_of_pages") not in (None, "")
                            else None
                        ),
                    )
                )
            except (KeyError, TypeError, ValueError) as exc:
                raise ValueError(
                    f"Invalid selected-edition contract at {path}:{line_number}."
                ) from exc

    if len(result) != expected_count:
        raise ValueError(
            "Validated Open Library selection must contain exactly "
            f"{expected_count} rows; received={len(result)}."
        )

    if len({row.edition_key for row in result}) != len(result):
        raise ValueError("Duplicate edition_key in validated selection.")

    return result


def load_validated_localities(path: Path) -> list[BpostLocality]:
    if not path.is_file():
        raise ValueError(f"Validated Bpost locality CSV not found: {path}")

    result: dict[tuple[str, str], BpostLocality] = {}
    with path.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            postal_code = str(row.get("postal_code") or "").strip()
            locality = str(row.get("locality") or "").strip()
            if len(postal_code) != 4 or not postal_code.isdigit() or not locality:
                raise ValueError(
                    "Validated Bpost locality CSV contains an invalid row."
                )
            item = BpostLocality(postal_code=postal_code, locality=locality)
            result[(postal_code, locality.casefold())] = item

    if not result:
        raise ValueError("Validated Bpost locality CSV is empty.")

    return sorted(
        result.values(),
        key=lambda item: (item.postal_code, item.locality.casefold()),
    )


def normalize_selected_catalogue(
    selected: list[SelectedEdition],
    *,
    author_records: Mapping[str, dict] | None = None,
) -> tuple[
    list[NormalizedAuthor],
    list[NormalizedEdition],
    dict[str, tuple[str, ...]],
]:
    author_records = author_records or {}
    authors: list[NormalizedAuthor] = []
    editions: list[NormalizedEdition] = []
    subjects_by_work_key: dict[str, tuple[str, ...]] = {}

    for candidate in selected:
        title = truncate_or_none(candidate.title, 500)
        if title is None:
            raise ValueError(f"Edition {candidate.edition_key} has no usable title.")

        paired_count = min(
            len(candidate.author_keys),
            len(candidate.author_names),
        )
        if paired_count == 0:
            raise ValueError(
                f"Edition {candidate.edition_key} has no resolvable Author."
            )

        author_keys: list[str] = []
        for index in range(paired_count):
            author_key = candidate.author_keys[index].strip()
            author_name = truncate_or_none(candidate.author_names[index], 255)
            if not author_key or author_name is None:
                continue
            author_keys.append(author_key)

            # Enrichment is looked up strictly by exact author_key. When no
            # Authors dump record is available, behavior is unchanged from
            # the Search-API-only baseline (name only, no dates, no bio).
            enriched_record = author_records.get(author_key)
            enriched = (
                normalize_author_record(enriched_record)
                if enriched_record is not None
                else None
            )

            author = NormalizedAuthor(
                source_key=author_key,
                # The Author dump's canonical name is preferred when the
                # record exists; it is never split (e.g. on "/").
                full_name=enriched.full_name if enriched else author_name,
                birth_date=enriched.birth_date if enriched else None,
                death_date=enriched.death_date if enriched else None,
                biography=enriched.biography if enriched else None,
            )
            validation = validate_author(author)
            if not validation.valid:
                codes = ",".join(issue.code for issue in validation.issues)
                raise ValueError(
                    f"Invalid normalized Author {author_key}: {codes}."
                )
            authors.append(author)

        if not author_keys:
            raise ValueError(
                f"Edition {candidate.edition_key} has no valid normalized Author."
            )

        publication_source: object = (
            candidate.publish_date
            if candidate.publish_date is not None
            else candidate.publish_year
        )
        publisher = None
        for raw_publisher in candidate.publishers:
            publisher = truncate_or_none(raw_publisher, 255)
            if publisher is not None:
                break

        edition = NormalizedEdition(
            source_key=candidate.edition_key,
            work_key=candidate.work_key or None,
            title=title,
            subtitle=truncate_or_none(candidate.subtitle, 500),
            isbn=_select_isbn(candidate),
            language=candidate.language,
            publication_year=normalize_publication_year(publication_source),
            page_count=normalize_page_count(candidate.number_of_pages),
            publisher=publisher,
            author_keys=tuple(dict.fromkeys(author_keys)),
        )
        validation = validate_edition(edition)
        if not validation.valid:
            codes = ",".join(issue.code for issue in validation.issues)
            raise ValueError(
                f"Invalid normalized Edition {edition.source_key}: {codes}."
            )
        editions.append(edition)

        if candidate.work_key:
            subjects_by_work_key[candidate.work_key] = candidate.subjects

    return authors, editions, subjects_by_work_key


def _write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def build_bundle(
    *,
    profile: SeedProfile,
    selected_jsonl: Path,
    bpost_csv: Path,
    output_dir: Path,
    seed: int,
    reference_date: date,
    raw_password: str | None = None,
    authors_snapshot_jsonl: Path | None = None,
) -> dict:
    if raw_password is None or not raw_password.strip():
        raise ValueError(
            "Synthetic User password must be non-empty."
        )
    if len(raw_password) < 12:
        raise ValueError(
            "Synthetic User password must contain at least 12 characters."
        )

    try:
        distribution = PROFILE_COPY_DISTRIBUTIONS[profile.name]
    except KeyError as exc:
        raise ValueError(
            f"No Copy distribution configured for profile {profile.name!r}."
        ) from exc

    if distribution.title_count != profile.title_target:
        raise ValueError(
            "Copy distribution title_count does not match profile title_target: "
            f"distribution={distribution.title_count} "
            f"profile={profile.title_target}."
        )

    if profile.include_demo_scenarios:
        raise NotImplementedError(
            "Business scenario generation is not implemented yet; "
            f"profile {profile.name!r} requires include_demo_scenarios=False "
            "to use build_bundle."
        )

    if profile.user_target <= 0:
        raise ValueError(
            f"Profile {profile.name!r} has no user_target configured."
        )

    selected = load_selected_editions(
        selected_jsonl, expected_count=profile.title_target
    )
    localities = load_validated_localities(bpost_csv)

    author_records = (
        load_authors_snapshot(authors_snapshot_jsonl)
        if authors_snapshot_jsonl is not None
        else {}
    )
    normalized_authors, normalized_editions, subjects = (
        normalize_selected_catalogue(selected, author_records=author_records)
    )

    author_dedup = deduplicate_authors(normalized_authors)
    edition_dedup = deduplicate_editions(normalized_editions)

    if edition_dedup.conflicts:
        raise ValueError(
            "Catalogue contains bibliographic conflicts after "
            f"deduplication: {len(edition_dedup.conflicts)} conflict(s)."
        )

    if len(edition_dedup.kept) != profile.title_target:
        raise ValueError(
            f"Catalogue no longer contains exactly {profile.title_target} "
            "Titles after deduplication. The acquisition snapshot must be "
            f"replenished: kept={len(edition_dedup.kept)} "
            f"duplicates={len(edition_dedup.duplicates)}."
        )

    mapping = map_catalogue(
        author_dedup.kept,
        edition_dedup.kept,
        subjects_by_work_key=subjects,
    )

    if mapping.rejections:
        raise ValueError(
            "Catalogue contains mapping rejection(s): "
            + ", ".join(
                f"{item.source_key}:{item.code}"
                for item in mapping.rejections[:10]
            )
        )

    if len(mapping.titles) != profile.title_target:
        raise ValueError(
            f"Mapped catalogue must contain exactly {profile.title_target} "
            f"Titles; received={len(mapping.titles)}."
        )

    copy_result = generate_copies(mapping.titles, profile=profile.name)
    if len(copy_result.copies) != distribution.copy_count:
        raise AssertionError(
            f"Copy target mismatch for profile {profile.name!r}: "
            f"{len(copy_result.copies)}."
        )

    users = generate_synthetic_members(
        localities,
        count=profile.user_target,
        seed=seed,
        reference_date=reference_date,
        raw_password=raw_password,
    )

    output_dir.mkdir(parents=True, exist_ok=True)

    export_catalogue_csv(mapping, copy_result.copies, output_dir)
    export_users_csv(localities, users, output_dir)
    export_scenarios_csv(ScenarioGenerationResult(), output_dir)

    isbn_present = sum(1 for title in mapping.titles if title.isbn is not None)
    language_counts = dict(
        sorted(Counter(title.language for title in mapping.titles).items())
    )
    authors_enriched = sum(
        1
        for author in author_dedup.kept
        if author.birth_date or author.death_date or author.biography
    )

    report = {
        "profile": profile.name,
        "seed": seed,
        "reference_date": reference_date.isoformat(),
        "inputs": {
            "selected_jsonl": str(selected_jsonl),
            "selected_sha256": sha256_file(selected_jsonl),
            "bpost_csv": str(bpost_csv),
            "bpost_sha256": sha256_file(bpost_csv),
        },
        "catalogue": {
            "selected": len(selected),
            "normalized_authors": len(normalized_authors),
            "normalized_editions": len(normalized_editions),
            "authors_kept": len(author_dedup.kept),
            "authors_enriched": authors_enriched,
            "author_duplicates": len(author_dedup.duplicates),
            "author_candidates": len(author_dedup.candidates),
            "titles_kept": len(edition_dedup.kept),
            "edition_duplicates": len(edition_dedup.duplicates),
            "edition_candidates": len(edition_dedup.candidates),
            "edition_conflicts": len(edition_dedup.conflicts),
            "mapped_titles": len(mapping.titles),
            "mapping_warnings": len(mapping.warnings),
            "mapping_rejections": len(mapping.rejections),
            "isbn_present": isbn_present,
            "isbn_absent": len(mapping.titles) - isbn_present,
            "language_counts": language_counts,
        },
        "copies": {
            "count": len(copy_result.copies),
            "titles_by_copy_count": {
                str(key): value
                for key, value in copy_result.titles_by_copy_count.items()
            },
        },
        "users": {
            "count": len(users.users),
            "addresses": len(users.addresses),
            "residences": len(users.residences),
            "bpost_localities": len(localities),
        },
        "scenarios": {
            "enabled": False,
            "loans": 0,
            "reservations": 0,
            "fines": 0,
            "notifications": 0,
        },
    }

    _write_json(output_dir / "bundle_report.json", report)
    _write_json(
        output_dir / "deduplication_report.json",
        {
            "edition_duplicates": [asdict(row) for row in edition_dedup.duplicates],
            "edition_candidates": [asdict(row) for row in edition_dedup.candidates],
            "edition_conflicts": [asdict(row) for row in edition_dedup.conflicts],
            "author_duplicates": [asdict(row) for row in author_dedup.duplicates],
            "author_candidates": [asdict(row) for row in author_dedup.candidates],
            "mapping_warnings": [asdict(row) for row in mapping.warnings],
            "mapping_rejections": [asdict(row) for row in mapping.rejections],
        },
    )

    return report


def _parse_date(value: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(
            "Expected ISO date YYYY-MM-DD."
        ) from exc


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Build the complete PRIMATIS bundle from validated snapshots for a given profile."
    )
    parser.add_argument(
        "--profile",
        required=True,
        choices=("small", "medium"),
    )
    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path.cwd(),
        help="data-seeding project root (default: current directory).",
    )
    parser.add_argument(
        "--selected-jsonl",
        type=Path,
        default=None,
        help="Defaults to data/validated/<profile>/openlibrary_selected.jsonl",
    )
    parser.add_argument(
        "--bpost-csv",
        type=Path,
        default=None,
        help="Defaults to data/validated/<profile>/bpost_localities.csv",
    )
    parser.add_argument(
        "--authors-snapshot",
        type=Path,
        default=None,
        help=(
            "Optional Open Library Authors dump snapshot (JSONL, exact "
            "author_key match). When omitted, Authors keep the "
            "Search-API-only baseline (no dates, no biography)."
        ),
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Defaults to data/bundles/<profile>",
    )
    parser.add_argument("--seed", type=int, default=13014)
    parser.add_argument("--reference-date", type=_parse_date, required=True)
    return parser


def main() -> int:
    args = build_parser().parse_args()

    config_path = Path(__file__).resolve().parents[3] / "config" / "profiles.toml"
    profiles = load_profiles(config_path)
    if args.profile not in profiles:
        available = ", ".join(sorted(profiles))
        raise SystemExit(f"Unknown profile '{args.profile}'. Available: {available}")
    profile = profiles[args.profile]

    selected_jsonl = args.selected_jsonl or (
        args.project_root / "data" / "validated" / profile.name / "openlibrary_selected.jsonl"
    )
    bpost_csv = args.bpost_csv or (
        args.project_root / "data" / "validated" / profile.name / "bpost_localities.csv"
    )
    output_dir = args.output_dir or (
        args.project_root / "data" / "bundles" / profile.name
    )

    password = os.environ.get("PRIMATIS_SEED_USER_PASSWORD")
    if password is None or not password.strip():
        raise SystemExit(
            "PRIMATIS_SEED_USER_PASSWORD must be defined and non-empty."
        )
    if len(password) < 12:
        raise SystemExit(
            "PRIMATIS_SEED_USER_PASSWORD must contain at least 12 characters."
        )

    report = build_bundle(
        profile=profile,
        selected_jsonl=selected_jsonl,
        bpost_csv=bpost_csv,
        output_dir=output_dir,
        seed=args.seed,
        reference_date=args.reference_date,
        raw_password=password,
        authors_snapshot_jsonl=args.authors_snapshot,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
