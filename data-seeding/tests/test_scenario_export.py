import csv
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path

from primatis_data_seeding.export.scenarios_csv import export_scenarios_csv
from primatis_data_seeding.generation.copies import PrimatisCopyRow
from primatis_data_seeding.generation.scenarios import (
    ScenarioCounts,
    ScenarioSettings,
    generate_demo_scenarios,
)
from primatis_data_seeding.generation.users import SyntheticUserRow


def test_exports_all_scenario_files(tmp_path: Path) -> None:
    users = [
        SyntheticUserRow(
            source_key=f"seed-member-{i:06d}",
            email=f"m{i}@seed.primatis.invalid",
            password_hash="{bcrypt}x",
            first_name="A",
            last_name="B",
            phone_number=None,
            account_status="ACTIVE",
            member_number=f"M8{i:08d}",
            member_status="ACTIVE",
            registration_date=date(2025, 1, 1),
            member_expiration_date=date(2027, 1, 1),
            blocked_reason=None,
            failed_login_count=0,
            role_code="ROLE_MEMBER",
        )
        for i in range(1, 8)
    ]
    copies = [
        PrimatisCopyRow(
            f"/books/T{i}",
            f"PRI-C-{i:03d}-01",
            None,
            "GOOD",
            "AVAILABLE",
        )
        for i in range(1, 10)
    ]
    settings = ScenarioSettings(
        21, 48, 3, Decimal("0.80"), Decimal("25.00")
    )
    result = generate_demo_scenarios(
        users,
        copies,
        settings=settings,
        reference_datetime=datetime(2026, 8, 25, 12, tzinfo=timezone.utc),
        counts=ScenarioCounts(1, 1, 1, 1, 1),
    )

    paths = export_scenarios_csv(result, tmp_path)

    assert all(
        path.is_file()
        for path in (
            paths.loans,
            paths.reservations,
            paths.fines,
            paths.notifications,
            paths.copy_states,
        )
    )


def test_notification_export_never_contains_multiple_origins(tmp_path: Path) -> None:
    users = [
        SyntheticUserRow(
            source_key=f"seed-member-{i:06d}",
            email=f"m{i}@seed.primatis.invalid",
            password_hash="{bcrypt}x",
            first_name="A",
            last_name="B",
            phone_number=None,
            account_status="ACTIVE",
            member_number=f"M8{i:08d}",
            member_status="ACTIVE",
            registration_date=date(2025, 1, 1),
            member_expiration_date=date(2027, 1, 1),
            blocked_reason=None,
            failed_login_count=0,
            role_code="ROLE_MEMBER",
        )
        for i in range(1, 8)
    ]
    copies = [
        PrimatisCopyRow(f"/books/T{i}", f"PRI-C-{i:03d}-01", None, "GOOD", "AVAILABLE")
        for i in range(1, 10)
    ]
    result = generate_demo_scenarios(
        users,
        copies,
        settings=ScenarioSettings(21, 48, 3, Decimal("0.80"), Decimal("25.00")),
        reference_datetime=datetime(2026, 8, 25, 12, tzinfo=timezone.utc),
        counts=ScenarioCounts(1, 1, 1, 1, 1),
    )
    paths = export_scenarios_csv(result, tmp_path)

    with paths.notifications.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    for row in rows:
        origins = [
            row["loan_source_key"],
            row["reservation_source_key"],
            row["fine_source_key"],
            row["article_source_key"],
        ]
        assert sum(bool(value) for value in origins) == 1
