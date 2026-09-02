from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

from primatis_data_seeding.generation.scenarios import ScenarioGenerationResult


@dataclass(frozen=True)
class ScenarioExportPaths:
    root: Path
    loans: Path
    reservations: Path
    fines: Path
    notifications: Path
    copy_states: Path


def _write(path: Path, fieldnames: tuple[str, ...], rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def _dt(value):
    return value.isoformat() if value is not None else ""


def export_scenarios_csv(result: ScenarioGenerationResult, output_dir: Path) -> ScenarioExportPaths:
    paths = ScenarioExportPaths(
        output_dir,
        output_dir / "loans.csv",
        output_dir / "reservations.csv",
        output_dir / "fines.csv",
        output_dir / "notifications.csv",
        output_dir / "copy_states.csv",
    )
    _write(
        paths.loans,
        ("source_key","user_source_key","inventory_code","loan_date","due_date","return_date","loan_status","notes"),
        [{
            "source_key": r.source_key, "user_source_key": r.user_source_key,
            "inventory_code": r.inventory_code, "loan_date": r.loan_date.isoformat(),
            "due_date": r.due_date.isoformat(),
            "return_date": r.return_date.isoformat() if r.return_date else "",
            "loan_status": r.loan_status, "notes": r.notes or "",
        } for r in result.loans],
    )
    _write(
        paths.reservations,
        (
            "source_key","user_source_key","title_source_key","title_inventory_code",
            "assigned_inventory_code","fulfilled_by_loan_source_key",
            "reservation_date","expiration_date","reservation_status",
        ),
        [{
            "source_key": r.source_key, "user_source_key": r.user_source_key,
            "title_source_key": r.title_source_key,
            "title_inventory_code": r.title_inventory_code,
            "assigned_inventory_code": r.assigned_inventory_code or "",
            "fulfilled_by_loan_source_key": r.fulfilled_by_loan_source_key or "",
            "reservation_date": r.reservation_date.isoformat(),
            "expiration_date": _dt(r.expiration_date),
            "reservation_status": r.reservation_status,
        } for r in result.reservations],
    )
    _write(
        paths.fines,
        ("source_key","loan_source_key","amount","reason","issued_at","fine_status","paid_at","cancelled_at"),
        [{
            "source_key": r.source_key, "loan_source_key": r.loan_source_key,
            "amount": f"{r.amount:.2f}", "reason": r.reason,
            "issued_at": r.issued_at.isoformat(), "fine_status": r.fine_status,
            "paid_at": _dt(r.paid_at), "cancelled_at": _dt(r.cancelled_at),
        } for r in result.fines],
    )
    _write(
        paths.notifications,
        (
            "source_key","recipient_user_source_key","loan_source_key",
            "reservation_source_key","fine_source_key","article_source_key",
            "notification_type","title","message","notification_status","created_at","read_at",
        ),
        [{
            "source_key": r.source_key, "recipient_user_source_key": r.recipient_user_source_key,
            "loan_source_key": r.loan_source_key or "",
            "reservation_source_key": r.reservation_source_key or "",
            "fine_source_key": r.fine_source_key or "",
            "article_source_key": r.article_source_key or "",
            "notification_type": r.notification_type, "title": r.title,
            "message": r.message, "notification_status": r.notification_status,
            "created_at": r.created_at.isoformat(), "read_at": _dt(r.read_at),
        } for r in result.notifications],
    )
    _write(
        paths.copy_states,
        ("inventory_code","availability_status"),
        [{"inventory_code": r.inventory_code, "availability_status": r.availability_status}
         for r in sorted(result.copy_states, key=lambda x: x.inventory_code)],
    )
    return paths
