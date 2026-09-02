from datetime import date, datetime, timezone
from decimal import Decimal

from primatis_data_seeding.generation.copies import PrimatisCopyRow
from primatis_data_seeding.generation.scenarios import (
    ScenarioCounts, ScenarioSettings, generate_demo_scenarios
)
from primatis_data_seeding.generation.users import SyntheticUserRow


def _users(count: int):
    return [
        SyntheticUserRow(
            source_key=f"seed-member-{i:06d}",
            email=f"m{i}@seed.primatis.invalid",
            password_hash="{bcrypt}x",
            first_name="A", last_name="B", phone_number=None,
            account_status="ACTIVE", member_number=f"M8{i:08d}",
            member_status="ACTIVE", registration_date=date(2025,1,1),
            member_expiration_date=date(2027,1,1), blocked_reason=None,
            failed_login_count=0, role_code="ROLE_MEMBER",
        )
        for i in range(1,count+1)
    ]


def test_every_reservation_has_persistent_title_resolution_anchor() -> None:
    copies = [
        PrimatisCopyRow(f"/books/T{i}", f"PRI-C-{i:03d}-01", None, "GOOD", "AVAILABLE")
        for i in range(1,10)
    ]
    result = generate_demo_scenarios(
        _users(8), copies,
        settings=ScenarioSettings(21,48,3,Decimal("0.80"),Decimal("25.00")),
        reference_datetime=datetime(2026,8,25,12,tzinfo=timezone.utc),
        counts=ScenarioCounts(1,1,1,1,1),
    )
    title_by_inventory = {c.inventory_code:c.title_source_key for c in copies}
    for reservation in result.reservations:
        assert reservation.title_inventory_code in title_by_inventory
        assert title_by_inventory[reservation.title_inventory_code] == reservation.title_source_key
