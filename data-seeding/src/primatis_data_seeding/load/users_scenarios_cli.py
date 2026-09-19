"""DEV-16.5.1 — CLI entrypoint for `load_users_and_scenarios`.

`load/users_scenarios.py` never went through any CLI (unlike the
catalogue loader, `load/cli.py`) and, on its own, never validates which
database it is actually connected to — it trusts the caller's `conn`
entirely. This wrapper closes that gap with the SAME guard machinery
already used and tested for the catalogue (`load/guard.py`), rather
than inventing a second one: CHECK is read-only, APPLY requires
`--confirm-database` to exactly match the profile's allowed database,
and `primatis_test` is refused unconditionally.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import psycopg

from primatis_data_seeding.load.guard import (
    expected_database,
    require_apply_confirmation,
    validate_live_database,
)
from primatis_data_seeding.load.postgres import _live_database
from primatis_data_seeding.load.users_scenarios import load_users_and_scenarios


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate or load a PRIMATIS Users + demo Scenarios export into PostgreSQL."
    )
    parser.add_argument("--profile", required=True, choices=("full", "full_consolidated"))
    parser.add_argument("--export-dir", type=Path, required=True)
    parser.add_argument(
        "--database",
        help="Expected database name. Defaults to the database fixed by the profile.",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Persist Users/Scenarios. Without this flag, CHECK mode is used.",
    )
    parser.add_argument(
        "--confirm-database",
        help="Required with --apply and must exactly match the target database name.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    database = args.database or expected_database(args.profile)

    require_apply_confirmation(
        apply=args.apply, confirmation=args.confirm_database, database=database,
    )

    with psycopg.connect("") as conn:
        live_database = _live_database(conn)
        validate_live_database(args.profile, database, live_database)

        summary = load_users_and_scenarios(
            conn=conn, export_dir=args.export_dir, apply=args.apply,
        )

    mode = "APPLY" if summary.applied else "CHECK"
    print(
        f"mode={mode} database={live_database} "
        f"users={summary.users} localities={summary.localities} "
        f"addresses={summary.addresses} residences={summary.residences} "
        f"loans={summary.loans} reservations={summary.reservations} "
        f"fines={summary.fines} notifications={summary.notifications}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
