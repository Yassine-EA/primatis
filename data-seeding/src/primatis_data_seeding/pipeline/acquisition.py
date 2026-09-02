from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
import json
import os
from pathlib import Path

from primatis_data_seeding.acquisition.openlibrary import (
    PROFILE_LANGUAGE_QUOTAS,
    acquire_openlibrary,
    has_complete_snapshot,
    reuse_openlibrary_snapshot,
    write_selected_jsonl,
)
from primatis_data_seeding.acquisition.openlibrary_authors import (
    acquire_authors_snapshot,
)
from primatis_data_seeding.acquisition.openlibrary_details import (
    acquire_records,
    write_records_snapshot,
)
from primatis_data_seeding.acquisition.provenance import archive_source_file
from primatis_data_seeding.reference.bpost import load_bpost_localities


BPOST_SOURCE_PAGE = "https://www.bpost.be/fr/outil-de-validation-de-codes-postaux"


def _export_localities(rows, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=("postal_code", "locality"))
        writer.writeheader()
        for row in rows:
            writer.writerow({
                "postal_code": row.postal_code,
                "locality": row.locality,
            })


def build_acquisition_bundle(
    *,
    profile: str,
    project_root: Path,
    bpost_xlsx: Path,
    contact: str,
    refresh_openlibrary: bool = False,
    authors_dump: Path | None = None,
    refresh_authors: bool = False,
    fetch_work_summaries: bool = False,
    refresh_works: bool = False,
    fetch_edition_details: bool = False,
    refresh_editions: bool = False,
) -> dict:
    quotas = PROFILE_LANGUAGE_QUOTAS.get(profile)
    if quotas is None:
        available = ", ".join(sorted(PROFILE_LANGUAGE_QUOTAS))
        raise ValueError(
            f"Unsupported acquisition profile {profile!r}. Available: {available}."
        )

    data_dir = project_root / "data"
    raw_ol = data_dir / "raw" / "openlibrary" / profile
    raw_bpost = data_dir / "raw" / "bpost"
    validated = data_dir / "validated" / profile

    if has_complete_snapshot(raw_ol, quotas=quotas) and not refresh_openlibrary:
        selected, ol_manifest = reuse_openlibrary_snapshot(raw_ol, quotas=quotas)
        openlibrary_mode = "snapshot"
    else:
        selected, ol_manifest = acquire_openlibrary(
            raw_ol,
            quotas=quotas,
            contact=contact,
            profile=profile,
        )
        openlibrary_mode = "live"

    write_selected_jsonl(
        selected,
        validated / "openlibrary_selected.jsonl",
    )

    authors_manifest: dict | None = None
    if authors_dump is not None:
        # Exact author_key match only — never a name search — against the
        # keys actually referenced by the selected editions of this profile.
        required_author_keys = {
            key for candidate in selected for key in candidate.author_keys
        }
        authors_manifest = acquire_authors_snapshot(
            dump_path=authors_dump,
            required_keys=required_author_keys,
            snapshot_path=validated / "authors_selected.jsonl",
            refresh=refresh_authors,
        )

    works_manifest: dict | None = None
    if fetch_work_summaries:
        # Exact work_key match only, against the keys actually referenced
        # by the selected editions of this profile.
        required_work_keys = {
            candidate.work_key for candidate in selected if candidate.work_key
        }
        work_records, works_manifest = acquire_records(
            required_work_keys,
            cache_dir=data_dir / "raw" / "openlibrary" / profile / "works",
            contact=contact,
            refresh=refresh_works,
        )
        write_records_snapshot(work_records, validated / "works_selected.jsonl")

    editions_manifest: dict | None = None
    if fetch_edition_details:
        # Exact edition_key match only, against the keys actually selected
        # for this profile.
        required_edition_keys = {candidate.edition_key for candidate in selected}
        edition_records, editions_manifest = acquire_records(
            required_edition_keys,
            cache_dir=data_dir / "raw" / "openlibrary" / profile / "editions",
            contact=contact,
            refresh=refresh_editions,
        )
        write_records_snapshot(
            edition_records, validated / "editions_selected.jsonl"
        )

    bpost_provenance = archive_source_file(
        bpost_xlsx,
        raw_bpost,
        source_name="Bpost — Liste des localités et codes postaux",
        source_kind="official public Excel reference",
        source_url=BPOST_SOURCE_PAGE,
        notes="postal code + locality only; no commercial personal-address file",
    )
    archived_bpost = Path(bpost_provenance.local_file)
    localities = load_bpost_localities(archived_bpost)
    _export_localities(
        localities,
        validated / "bpost_localities.csv",
    )

    report = {
        "profile": profile,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "openlibrary_mode": openlibrary_mode,
        "openlibrary_selected": len(selected),
        "openlibrary_language_counts": ol_manifest["language_counts"],
        "bpost_localities": len(localities),
        "bpost_sha256": bpost_provenance.sha256,
        "authors_dump": (
            {
                "matched": len(authors_manifest.get("matched_keys", ())),
                "requested": len(authors_manifest.get("requested_keys", ())),
                "missing": len(authors_manifest.get("missing_keys", ())),
            }
            if authors_manifest is not None
            else None
        ),
        "work_records": (
            {
                "requested": len(works_manifest.get("requested_keys", ())),
                "reused": len(works_manifest.get("reused_keys", ())),
                "fetched": len(works_manifest.get("fetched_keys", ())),
            }
            if works_manifest is not None
            else None
        ),
        "edition_records": (
            {
                "requested": len(editions_manifest.get("requested_keys", ())),
                "reused": len(editions_manifest.get("reused_keys", ())),
                "fetched": len(editions_manifest.get("fetched_keys", ())),
            }
            if editions_manifest is not None
            else None
        ),
        "outputs": {
            "openlibrary_selected": str(
                validated / "openlibrary_selected.jsonl"
            ),
            "bpost_localities": str(
                validated / "bpost_localities.csv"
            ),
            **(
                {"authors_selected": str(validated / "authors_selected.jsonl")}
                if authors_manifest is not None
                else {}
            ),
            **(
                {"works_selected": str(validated / "works_selected.jsonl")}
                if works_manifest is not None
                else {}
            ),
            **(
                {"editions_selected": str(validated / "editions_selected.jsonl")}
                if editions_manifest is not None
                else {}
            ),
        },
    }
    (validated / "acquisition_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Acquire or reuse the real external sources for a PRIMATIS profile."
    )
    parser.add_argument(
        "--profile",
        required=True,
        choices=tuple(sorted(PROFILE_LANGUAGE_QUOTAS)),
    )
    parser.add_argument(
        "--bpost-xlsx",
        type=Path,
        required=True,
        help="Official Bpost localities/postal-codes Excel file (.xls/.xlsx).",
    )
    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path.cwd(),
        help="data-seeding project root (default: current directory).",
    )
    parser.add_argument(
        "--refresh-openlibrary",
        action="store_true",
        help=(
            "Force fresh Open Library network acquisition. "
            "Without this flag, a complete existing snapshot is reused."
        ),
    )
    parser.add_argument(
        "--authors-dump",
        type=Path,
        default=None,
        help=(
            "Optional local Open Library Authors bulk dump (.txt or .txt.gz). "
            "When provided, records matching the selected editions' "
            "author_key (exact match only) are extracted into "
            "data/validated/<profile>/authors_selected.jsonl. Omit to keep "
            "the current Search-API-only Author baseline unchanged."
        ),
    )
    parser.add_argument(
        "--refresh-authors",
        action="store_true",
        help="Force re-extraction from --authors-dump even if a matching snapshot exists.",
    )
    parser.add_argument(
        "--fetch-work-summaries",
        action="store_true",
        help=(
            "Fetch each selected edition's Open Library Work record (exact "
            "work_key match) for Title.summary, into "
            "data/validated/<profile>/works_selected.jsonl. A rerun without "
            "--refresh-works reuses the existing per-key cache and performs "
            "no network call. Omit entirely to keep summary=NULL."
        ),
    )
    parser.add_argument(
        "--refresh-works",
        action="store_true",
        help="Force re-fetching every required Work record even if already cached.",
    )
    parser.add_argument(
        "--fetch-edition-details",
        action="store_true",
        help=(
            "Fetch each selected edition's Open Library Edition record "
            "(exact edition_key match) for Title.page_count, into "
            "data/validated/<profile>/editions_selected.jsonl. A rerun "
            "without --refresh-editions reuses the existing per-key cache "
            "and performs no network call. Omit entirely to keep the "
            "Search-API-only baseline."
        ),
    )
    parser.add_argument(
        "--refresh-editions",
        action="store_true",
        help="Force re-fetching every required Edition record even if already cached.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()

    contact = os.environ.get("PRIMATIS_OPENLIBRARY_CONTACT", "").strip()
    if args.refresh_openlibrary and not contact:
        raise SystemExit(
            "PRIMATIS_OPENLIBRARY_CONTACT is required with --refresh-openlibrary."
        )
    if (args.fetch_work_summaries or args.fetch_edition_details) and not contact:
        raise SystemExit(
            "PRIMATIS_OPENLIBRARY_CONTACT is required with "
            "--fetch-work-summaries/--fetch-edition-details."
        )

    report = build_acquisition_bundle(
        profile=args.profile,
        project_root=args.project_root,
        bpost_xlsx=args.bpost_xlsx,
        contact=contact,
        refresh_openlibrary=args.refresh_openlibrary,
        authors_dump=args.authors_dump,
        refresh_authors=args.refresh_authors,
        fetch_work_summaries=args.fetch_work_summaries,
        refresh_works=args.refresh_works,
        fetch_edition_details=args.fetch_edition_details,
        refresh_editions=args.refresh_editions,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
