from __future__ import annotations

import argparse
from pathlib import Path

from primatis_data_seeding.load.guard import expected_database
from primatis_data_seeding.load.postgres import load_catalogue_export


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate or load a PRIMATIS catalogue export into PostgreSQL."
    )
    parser.add_argument("--profile", required=True, choices=("small", "medium", "large", "full"))
    parser.add_argument("--export-dir", type=Path, required=True)
    parser.add_argument(
        "--database",
        help="Expected database name. Defaults to the database fixed by the profile.",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Persist catalogue replacement. Without this flag, CHECK mode is used.",
    )
    parser.add_argument(
        "--confirm-database",
        help="Required with --apply and must exactly match the target database name.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    database = args.database or expected_database(args.profile)

    summary = load_catalogue_export(
        export_dir=args.export_dir,
        profile=args.profile,
        requested_database=database,
        apply=args.apply,
        confirmation=args.confirm_database,
    )

    mode = "APPLY" if summary.applied else "CHECK"
    print(
        f"mode={mode} database={summary.database} "
        f"authors={summary.authors} genres={summary.genres} "
        f"titles={summary.titles} title_authors={summary.title_authors} "
        f"title_genres={summary.title_genres} copies={summary.copies} "
        f"previous_seed_titles={summary.previous_seed_titles}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
