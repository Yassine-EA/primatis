"""DEV-17.3 — CLI de chargement des Articles / Tags (mêmes garde-fous que les autres chargeurs)."""

from __future__ import annotations

import argparse
from pathlib import Path

import psycopg

from primatis_data_seeding.load.articles import load_articles
from primatis_data_seeding.load.guard import (
    expected_database,
    require_apply_confirmation,
    validate_live_database,
)
from primatis_data_seeding.load.postgres import _live_database


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate or load the PRIMATIS demo Articles/Tags export into PostgreSQL."
    )
    parser.add_argument("--profile", required=True, choices=("full_consolidated",))
    parser.add_argument("--export-dir", type=Path, required=True)
    parser.add_argument("--database", help="Expected database name. Defaults to the profile database.")
    parser.add_argument("--apply", action="store_true", help="Persist Articles/Tags. Without it: CHECK mode.")
    parser.add_argument(
        "--confirm-database",
        help="Required with --apply and must exactly match the target database name.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    database = args.database or expected_database(args.profile)
    require_apply_confirmation(apply=args.apply, confirmation=args.confirm_database, database=database)

    with psycopg.connect("") as conn:
        live_database = _live_database(conn)
        validate_live_database(args.profile, database, live_database)
        summary = load_articles(conn=conn, export_dir=args.export_dir, apply=args.apply)

    mode = "APPLY" if summary.applied else "CHECK"
    print(
        f"mode={mode} database={live_database} tags={summary.tags} articles={summary.articles} "
        f"article_tags={summary.article_tags} published={summary.published} "
        f"drafts={summary.drafts} archived={summary.archived}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
