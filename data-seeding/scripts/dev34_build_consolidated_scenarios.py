"""DEV-16.G3.4 — scénarios métier du bundle Full consolidé (fichiers uniquement).

Reconstruit loans / reservations / fines / notifications / copy_states pour le
catalogue consolidé (14 600 Titles / 24 000 Copies) et réutilise octet-à-octet
users / addresses / residences / bpost_localities de l'ancien bundle Full
(données démographiques indépendantes du catalogue).

Aucun accès PostgreSQL : les paramètres métier sont ceux enregistrés dans
`scenarios_build_report.json` du Full historique (lus en base à l'époque).
Déterministe : graine, `reference_datetime` et paramètres repris du Full
historique, aucun horodatage courant.
"""

from __future__ import annotations

import csv
import json
import shutil
import sys
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

from primatis_data_seeding.acquisition.provenance import sha256_file
from primatis_data_seeding.export.scenarios_csv import export_scenarios_csv
from primatis_data_seeding.generation.scenarios import (
    DEFAULT_FULL_SCENARIO_COUNTS,
    ScenarioSettings,
    generate_demo_scenarios,
)
from primatis_data_seeding.generation.users import SyntheticUserRow
from primatis_data_seeding.pipeline.full_scenarios import load_copies_csv

REUSED_FILES = ("users.csv", "addresses.csv", "residences.csv", "bpost_localities.csv")
SCENARIO_FILES = (
    "loans.csv", "reservations.csv", "fines.csv", "notifications.csv", "copy_states.csv",
)


def load_users(path: Path) -> list[SyntheticUserRow]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return [
            SyntheticUserRow(
                source_key=row["source_key"],
                email=row["email"],
                password_hash=row["password_hash"],
                first_name=row["first_name"],
                last_name=row["last_name"],
                phone_number=row["phone_number"] or None,
                account_status=row["account_status"],
                member_number=row["member_number"],
                member_status=row["member_status"],
                registration_date=date.fromisoformat(row["registration_date"]),
                member_expiration_date=date.fromisoformat(row["member_expiration_date"]),
                blocked_reason=row["blocked_reason"] or None,
                failed_login_count=int(row["failed_login_count"]),
                role_code=row["role_code"],
            )
            for row in csv.DictReader(handle)
        ]


def build(old_full: Path, candidate: Path, output_dir: Path) -> dict:
    """Écrit les 9 fichiers users/scénarios dans `output_dir` et retourne le rapport."""
    historical = json.loads((old_full / "scenarios_build_report.json").read_text(encoding="utf-8"))
    hs = historical["settings"]
    settings = ScenarioSettings(
        loan_duration_days=int(hs["loan_duration_days"]),
        reservation_ready_hold_hours=int(hs["reservation_ready_hold_hours"]),
        loan_due_soon_days=int(hs["loan_due_soon_days"]),
        fine_weekly_rate=Decimal(hs["fine_weekly_rate"]),
        fine_max_amount=Decimal(hs["fine_max_amount"]),
    )
    reference_datetime = datetime.fromisoformat(historical["reference_datetime"])

    output_dir.mkdir(parents=True, exist_ok=True)
    for name in REUSED_FILES:
        shutil.copyfile(old_full / name, output_dir / name)

    users = load_users(output_dir / "users.csv")
    copies = load_copies_csv(candidate / "copies.csv")
    scenarios = generate_demo_scenarios(
        users, copies, settings=settings, reference_datetime=reference_datetime,
        counts=DEFAULT_FULL_SCENARIO_COUNTS,
    )
    export_scenarios_csv(scenarios, output_dir)

    return {
        "profile": "full_consolidated",
        "seed_users": historical["seed"],
        "reference_datetime": reference_datetime.isoformat(),
        "settings": {k: str(v) for k, v in hs.items()},
        "counts": historical["counts"],
        "inputs": {
            "candidate_copies_sha256": sha256_file(candidate / "copies.csv"),
            "candidate_titles_sha256": sha256_file(candidate / "titles.csv"),
            "historical_full_report_sha256": sha256_file(old_full / "scenarios_build_report.json"),
        },
        "scenarios": {
            "loans": len(scenarios.loans),
            "reservations": len(scenarios.reservations),
            "fines": len(scenarios.fines),
            "notifications": len(scenarios.notifications),
            "copy_states": len(scenarios.copy_states),
        },
    }


def main() -> int:
    old_full, candidate, output_dir = (Path(arg) for arg in sys.argv[1:4])
    report = build(old_full, candidate, output_dir)
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
