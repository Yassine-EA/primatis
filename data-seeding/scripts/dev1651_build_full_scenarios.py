"""DEV-16.5.1 §3 — real Full scenarios build.

Generates the synthetic Users (1500, `profiles.toml` `full.user_target`)
and demo business Scenarios (Loan/Reservation/Fine/Notification) for the
ALREADY-BUILT Full catalogue, reusing `data/bundles/full/copies.csv`
as-is (never reconstructed). Business settings (LOAN_DURATION_DAYS,
RESERVATION_READY_HOLD_HOURS, LOAN_DUE_SOON_DAYS, FINE_WEEKLY_RATE,
FINE_MAX_AMOUNT) are read live from `primatis_preview.application_setting`
(data-seeding.md: "Le seeding peut les lire si nécessaire pour construire
des scénarios cohérents") rather than hardcoded, so the scenarios always
match whatever is actually configured.

Deterministic: fixed seed, fixed reference_datetime (2026-09-12 12:00
UTC — the day this sub-task runs, not `datetime.now()`), fixed Bpost
localities reference (`data/validated/medium/bpost_localities.csv`,
identical content to `small`'s — a static Belgian postal reference, not
a profile-specific artifact, reused rather than duplicated).
"""

from __future__ import annotations

import os
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

import psycopg

from primatis_data_seeding.generation.scenarios import ScenarioSettings
from primatis_data_seeding.pipeline.full_scenarios import build_full_scenarios

SEED = 1651
REFERENCE_DATETIME = datetime(2026, 9, 12, 12, tzinfo=timezone.utc)
USER_COUNT = 1500

REQUIRED_SETTING_KEYS = (
    "LOAN_DURATION_DAYS",
    "RESERVATION_READY_HOLD_HOURS",
    "LOAN_DUE_SOON_DAYS",
    "FINE_WEEKLY_RATE",
    "FINE_MAX_AMOUNT",
)


def _load_settings_from_db() -> ScenarioSettings:
    with psycopg.connect("") as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT setting_key, setting_value FROM application_setting "
                "WHERE setting_key = ANY(%s)",
                (list(REQUIRED_SETTING_KEYS),),
            )
            values = dict(cur.fetchall())
    missing = [key for key in REQUIRED_SETTING_KEYS if key not in values]
    if missing:
        raise ValueError(f"Missing required application_setting key(s): {missing}")
    return ScenarioSettings(
        loan_duration_days=int(values["LOAN_DURATION_DAYS"]),
        reservation_ready_hold_hours=int(values["RESERVATION_READY_HOLD_HOURS"]),
        loan_due_soon_days=int(values["LOAN_DUE_SOON_DAYS"]),
        fine_weekly_rate=Decimal(values["FINE_WEEKLY_RATE"]),
        fine_max_amount=Decimal(values["FINE_MAX_AMOUNT"]),
    )


def main() -> int:
    import json
    import sys

    password = os.environ.get("PRIMATIS_SEED_USER_PASSWORD")
    if password is None or len(password) < 12:
        raise SystemExit(
            "PRIMATIS_SEED_USER_PASSWORD must be defined and at least 12 characters."
        )

    settings = _load_settings_from_db()
    print(f"settings (live from primatis_preview): {settings}", file=sys.stderr)

    report = build_full_scenarios(
        copies_csv=Path("data/bundles/full/copies.csv"),
        bpost_csv=Path("data/validated/medium/bpost_localities.csv"),
        output_dir=Path("data/bundles/full"),
        seed=SEED,
        user_count=USER_COUNT,
        reference_datetime=REFERENCE_DATETIME,
        raw_password=password,
        settings=settings,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
