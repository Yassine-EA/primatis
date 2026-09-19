from datetime import date, datetime, timezone
from decimal import Decimal

from primatis_data_seeding.generation.copies import generate_copies
from primatis_data_seeding.generation.scenarios import (
    ScenarioSettings,
    generate_demo_scenarios,
)
from primatis_data_seeding.generation.users import SyntheticUserRow
from primatis_data_seeding.mapping.models import PrimatisTitleRow


def _title(index: int) -> PrimatisTitleRow:
    return PrimatisTitleRow(
        source_key=f"isbn:{index:013d}",
        isbn=f"{index:013d}",
        title=f"Titre {index}",
        subtitle=None,
        summary=None,
        publication_year=None,
        language="FR",
        page_count=None,
        publisher=None,
        cover_image_url=None,
    )


def _user(index: int) -> SyntheticUserRow:
    return SyntheticUserRow(
        source_key=f"seed-member-{index:06d}",
        email=f"member{index:06d}@seed.primatis.invalid",
        password_hash="{bcrypt}x",
        first_name="Alex",
        last_name="Leroy",
        phone_number=None,
        account_status="ACTIVE",
        member_number=f"M{800_000_000 + index}",
        member_status="ACTIVE",
        registration_date=date(2024, 1, 1),
        member_expiration_date=date(2027, 1, 1),
        blocked_reason=None,
        failed_login_count=0,
        role_code="ROLE_MEMBER",
    )


def test_scenarios_are_generated_on_full_consolidated_catalogue() -> None:
    copies = generate_copies(
        [_title(index) for index in range(1, 14_601)], profile="full_consolidated"
    ).copies
    settings = ScenarioSettings(
        loan_duration_days=21,
        reservation_ready_hold_hours=48,
        loan_due_soon_days=3,
        fine_weekly_rate=Decimal("0.80"),
        fine_max_amount=Decimal("25.00"),
    )

    result = generate_demo_scenarios(
        [_user(index) for index in range(1, 1_501)],
        copies,
        settings=settings,
        reference_datetime=datetime(2026, 9, 12, 12, tzinfo=timezone.utc),
    )

    assert len(result.loans) == 160
    assert len(result.reservations) == 60
    assert len(result.fines) == 60
    assert len(result.notifications) == 270
    assert len(result.copy_states) == 120
    assert all(not loan.inventory_code.startswith("/books/") for loan in result.loans)
