"""DEV-16.5.1 §3 — wiring of the already-tested scenario machinery
(`generation/scenarios.py`, `generation/users.py`) for the `full`
profile.

This module does NOT reconstruct the catalogue. It consumes the
already-built, already-loaded Full catalogue's `copies.csv` (read back
as `PrimatisCopyRow`, never regenerated) and produces the synthetic
Users + demo business Scenarios (Loan/Reservation/Fine/Notification)
for it, exported as CSV alongside the existing catalogue bundle.

`pipeline/bundle.py::build_bundle()` (small/medium path, its own
argparse only ever accepted `--profile {small,medium}`) is deliberately
left untouched: `full` never went through it (it has its own dedicated
`full_build.py`/`dev165_build_full.py`), so its
`include_demo_scenarios` -> `NotImplementedError` guard is dead code
with respect to `full` and out of scope here (DEV-16.5.1 §2: "ne pas
retoucher... hors nécessité directe démontrée").
"""

from __future__ import annotations

import csv
import json
from dataclasses import asdict
from datetime import datetime
from pathlib import Path

from primatis_data_seeding.acquisition.provenance import sha256_file
from primatis_data_seeding.export.scenarios_csv import export_scenarios_csv
from primatis_data_seeding.export.users_csv import export_users_csv
from primatis_data_seeding.generation.copies import PrimatisCopyRow
from primatis_data_seeding.generation.scenarios import (
    DEFAULT_FULL_SCENARIO_COUNTS,
    ScenarioCounts,
    ScenarioGenerationResult,
    ScenarioSettings,
    generate_demo_scenarios,
)
from primatis_data_seeding.generation.users import generate_synthetic_members
from primatis_data_seeding.reference.bpost import BpostLocality


def load_copies_csv(path: Path) -> list[PrimatisCopyRow]:
    """Reads back the catalogue's own `copies.csv` export — the exact
    24000 rows already generated (and already loaded) for `full`, never
    a re-derivation. Kept intentionally trivial (a straight CSV read),
    mirroring `load_selected_editions`'s "read the bundle's own export"
    convention elsewhere in the pipeline."""
    if not path.is_file():
        raise ValueError(f"Catalogue copies export not found: {path}")

    rows: list[PrimatisCopyRow] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            rows.append(
                PrimatisCopyRow(
                    title_source_key=row["title_source_key"],
                    inventory_code=row["inventory_code"],
                    location=row["location"] or None,
                    copy_condition=row["copy_condition"],
                    availability_status=row["availability_status"],
                )
            )
    if not rows:
        raise ValueError(f"Catalogue copies export is empty: {path}")
    return rows


def build_full_scenarios(
    *,
    copies_csv: Path,
    bpost_csv: Path,
    output_dir: Path,
    seed: int,
    user_count: int,
    reference_datetime: datetime,
    raw_password: str,
    settings: ScenarioSettings,
    counts: ScenarioCounts = DEFAULT_FULL_SCENARIO_COUNTS,
) -> dict:
    """Generates synthetic Users + demo business Scenarios for the
    already-built `full` catalogue and exports them as CSV into
    `output_dir` (the same directory as the catalogue bundle).

    Deterministic: same `copies_csv`/`bpost_csv` content + same `seed` +
    same `reference_datetime` => identical output (no `datetime.now()`,
    no unseeded randomness — cf. DEV-16.5.1 §5).
    """
    if reference_datetime.tzinfo is None:
        raise ValueError("reference_datetime must be timezone-aware.")

    from primatis_data_seeding.pipeline.bundle import load_validated_localities

    copies = load_copies_csv(copies_csv)
    localities: list[BpostLocality] = load_validated_localities(bpost_csv)

    users = generate_synthetic_members(
        localities,
        count=user_count,
        seed=seed,
        reference_date=reference_datetime.date(),
        raw_password=raw_password,
    )

    scenarios: ScenarioGenerationResult = generate_demo_scenarios(
        users.users,
        copies,
        settings=settings,
        reference_datetime=reference_datetime,
        counts=counts,
    )

    output_dir.mkdir(parents=True, exist_ok=True)
    export_users_csv(localities, users, output_dir)
    export_scenarios_csv(scenarios, output_dir)

    report = {
        "profile": "full",
        "seed": seed,
        "reference_datetime": reference_datetime.isoformat(),
        "inputs": {
            "copies_csv": str(copies_csv),
            "copies_sha256": sha256_file(copies_csv),
            "bpost_csv": str(bpost_csv),
            "bpost_sha256": sha256_file(bpost_csv),
        },
        "settings": asdict(settings),
        "counts": asdict(counts),
        "users": {
            "count": len(users.users),
            "addresses": len(users.addresses),
            "residences": len(users.residences),
        },
        "scenarios": {
            "enabled": True,
            "loans": len(scenarios.loans),
            "reservations": len(scenarios.reservations),
            "fines": len(scenarios.fines),
            "notifications": len(scenarios.notifications),
            "copy_states": len(scenarios.copy_states),
        },
    }
    (output_dir / "scenarios_build_report.json").write_text(
        json.dumps(
            report, ensure_ascii=False, indent=2, sort_keys=True, default=str,
        )
        + "\n",
        encoding="utf-8",
    )
    return report
