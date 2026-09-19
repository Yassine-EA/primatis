"""DEV-16.G3.4 / DEV-17.3 — scénarios métier du bundle Full consolidé (fichiers uniquement).

Reconstruit loans / reservations / fines / notifications / copy_states pour le
catalogue consolidé (14 600 Titles / 24 000 Copies), applique quelques états de
comptes (BLOCKED / EXPIRED / DISABLED) et réutilise octet-à-octet
addresses / residences / bpost_localities.

DEV-17.3 :
- `--reference-datetime` est OBLIGATOIRE (ISO-8601 avec fuseau) : plus aucune
  lecture du rapport historique DEV-16, plus de date codée en dur ; toutes les
  dates de scénarios sont relatives à cet instant (voir
  `generation/reference_datetime.py`) ;
- paramètres métier (21 j / 48 h / 3 j / 0,80 / 25,00) = valeurs de référence de
  `application_setting` (constantes ci-dessous, non lues en base) ;
- la base `users.csv` (comptes staff déjà promus) reste immuable : la sortie est
  reconstruite à chaque exécution depuis `--base-dir` (idempotent) ;
- le pool des scénarios exclut les comptes staff ; les états de comptes sont pris
  sur des membres sans scénario.

Aucun accès PostgreSQL. Déterministe (aucun `now()`).
"""

from __future__ import annotations

import argparse
import csv
import json
import shutil
import sys
from dataclasses import asdict
from datetime import datetime
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from dev36_assign_staff_roles import read_users, render  # noqa: E402

from primatis_data_seeding.acquisition.provenance import sha256_file
from primatis_data_seeding.export.scenarios_csv import export_scenarios_csv
from primatis_data_seeding.generation.reference_datetime import parse_reference_datetime
from primatis_data_seeding.generation.scenarios import (
    DEMO_TARGETED_SCENARIO_COUNTS,
    ScenarioCounts,
    ScenarioSettings,
    generate_demo_scenarios,
)
from primatis_data_seeding.generation.users import apply_member_states
from primatis_data_seeding.pipeline.full_scenarios import load_copies_csv

REUSED_FILES = ("addresses.csv", "residences.csv", "bpost_localities.csv")
SCENARIO_FILES = (
    "loans.csv", "reservations.csv", "fines.csv", "notifications.csv", "copy_states.csv",
)
SETTINGS = ScenarioSettings(
    loan_duration_days=21,
    reservation_ready_hold_hours=48,
    loan_due_soon_days=3,
    fine_weekly_rate=Decimal("0.80"),
    fine_max_amount=Decimal("25.00"),
)
MEMBER_STATE_COUNTS = {"blocked": 3, "expired": 3, "disabled": 2}
MEMBER_ROLE = "ROLE_MEMBER"


def scenario_user_keys(scenarios) -> frozenset[str]:
    keys = {loan.user_source_key for loan in scenarios.loans}
    keys |= {r.user_source_key for r in scenarios.reservations}
    keys |= {n.recipient_user_source_key for n in scenarios.notifications}
    return frozenset(keys)


def build(
    base_dir: Path,
    output_dir: Path,
    reference_datetime: datetime,
    *,
    counts: ScenarioCounts = DEMO_TARGETED_SCENARIO_COUNTS,
) -> dict:
    """Écrit users + adresses + scénarios dans `output_dir` et retourne le rapport."""
    output_dir.mkdir(parents=True, exist_ok=True)
    for name in REUSED_FILES:
        if (base_dir / name).resolve() != (output_dir / name).resolve():
            shutil.copyfile(base_dir / name, output_dir / name)

    base_users = read_users(base_dir / "users.csv")
    members = [user for user in base_users if user.role_code == MEMBER_ROLE]
    copies = load_copies_csv(base_dir / "copies.csv")

    scenarios = generate_demo_scenarios(
        members, copies, settings=SETTINGS, reference_datetime=reference_datetime, counts=counts,
    )
    export_scenarios_csv(scenarios, output_dir)

    users = apply_member_states(
        base_users,
        excluded_source_keys=scenario_user_keys(scenarios),
        reference_date=reference_datetime.date(),
        **MEMBER_STATE_COUNTS,
    )
    (output_dir / "users.csv").write_bytes(render(users))

    return {
        "profile": "full_consolidated",
        "reference_datetime": reference_datetime.isoformat(),
        "settings": {k: str(v) for k, v in asdict(SETTINGS).items()},
        "counts": asdict(counts),
        "member_states": dict(MEMBER_STATE_COUNTS),
        "inputs": {
            "base_users_sha256": sha256_file(base_dir / "users.csv"),
            "base_copies_sha256": sha256_file(base_dir / "copies.csv"),
        },
        "scenarios": {
            "loans": len(scenarios.loans),
            "reservations": len(scenarios.reservations),
            "fines": len(scenarios.fines),
            "notifications": len(scenarios.notifications),
            "copy_states": len(scenarios.copy_states),
        },
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base-dir", type=Path, required=True,
                        help="Bundle DEV-16 immuable (users.csv, copies.csv, adresses…).")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--reference-datetime", required=True,
                        help="ISO-8601 avec fuseau, ex. 2026-09-19T08:00:00Z (obligatoire).")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    reference = parse_reference_datetime(args.reference_datetime)
    report = build(args.base_dir, args.output_dir, reference)
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
