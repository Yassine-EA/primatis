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
    return parser


def main() -> int:
    args = build_parser().parse_args()

    contact = os.environ.get("PRIMATIS_OPENLIBRARY_CONTACT", "").strip()
    if args.refresh_openlibrary and not contact:
        raise SystemExit(
            "PRIMATIS_OPENLIBRARY_CONTACT is required with --refresh-openlibrary."
        )

    report = build_acquisition_bundle(
        profile=args.profile,
        project_root=args.project_root,
        bpost_xlsx=args.bpost_xlsx,
        contact=contact,
        refresh_openlibrary=args.refresh_openlibrary,
        authors_dump=args.authors_dump,
        refresh_authors=args.refresh_authors,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
