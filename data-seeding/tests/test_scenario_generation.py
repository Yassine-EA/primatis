from datetime import date, datetime, timezone
from decimal import Decimal

from primatis_data_seeding.generation.copies import PrimatisCopyRow
from primatis_data_seeding.generation.scenarios import (
    ScenarioCounts,
    ScenarioSettings,
    generate_demo_scenarios,
)
from primatis_data_seeding.generation.users import SyntheticUserRow


SETTINGS = ScenarioSettings(
    loan_duration_days=21,
    reservation_ready_hold_hours=48,
    loan_due_soon_days=3,
    fine_weekly_rate=Decimal("0.80"),
    fine_max_amount=Decimal("25.00"),
)
REFERENCE = datetime(2026, 8, 25, 12, tzinfo=timezone.utc)


def users(count: int) -> list[SyntheticUserRow]:
    return [
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
        for i in range(1, count + 1)
    ]


def copies(single_titles: int = 10, extra_multi: int = 5) -> list[PrimatisCopyRow]:
    rows = [
        PrimatisCopyRow(
            title_source_key=f"/books/T{i:03d}",
            inventory_code=f"PRI-C-SINGLE-{i:03d}-01",
            location=None,
            copy_condition="GOOD",
            availability_status="AVAILABLE",
        )
        for i in range(1, single_titles + 1)
    ]
    for i in range(1, extra_multi + 1):
        for ordinal in (1, 2):
            rows.append(
                PrimatisCopyRow(
                    title_source_key=f"/books/M{i:03d}",
                    inventory_code=f"PRI-C-MULTI-{i:03d}-{ordinal:02d}",
                    location=None,
                    copy_condition="GOOD",
                    availability_status="AVAILABLE",
                )
            )
    return rows


COUNTS = ScenarioCounts(
    active_loans=3,
    overdue_loans=2,
    returned_late_loans=3,
    waiting_reservations=2,
    ready_reservations=2,
)


def generated():
    return generate_demo_scenarios(
        users(20),
        copies(),
        settings=SETTINGS,
        reference_datetime=REFERENCE,
        counts=COUNTS,
    )


def test_generates_requested_scenario_volumes() -> None:
    result = generated()

    assert len(result.loans) == 8
    assert len(result.fines) == 3
    assert len(result.reservations) == 4


def test_open_loans_keep_copies_on_loan() -> None:
    result = generated()
    states = {row.inventory_code: row.availability_status for row in result.copy_states}

    for loan in result.loans:
        if loan.loan_status in {"ACTIVE", "OVERDUE"}:
            assert states[loan.inventory_code] == "ON_LOAN"


def test_overdue_open_loans_do_not_have_fines() -> None:
    result = generated()
    fine_loan_keys = {fine.loan_source_key for fine in result.fines}

    overdue = [loan for loan in result.loans if loan.loan_status == "OVERDUE"]
    assert all(loan.source_key not in fine_loan_keys for loan in overdue)


def test_fines_exist_only_for_late_returned_loans() -> None:
    result = generated()
    loans = {loan.source_key: loan for loan in result.loans}

    for fine in result.fines:
        loan = loans[fine.loan_source_key]
        assert loan.loan_status == "RETURNED"
        assert loan.return_date is not None
        assert loan.return_date > loan.due_date


def test_fine_formula_and_cap_are_respected() -> None:
    result = generated()

    assert all(Decimal("0.80") <= fine.amount <= Decimal("25.00") for fine in result.fines)
    assert all("semaine(s) entamée(s)" in fine.reason for fine in result.fines)


def test_ready_reservation_has_matching_reserved_copy_and_expiration() -> None:
    result = generated()
    base = {copy.inventory_code: copy.title_source_key for copy in copies()}
    states = {row.inventory_code: row.availability_status for row in result.copy_states}

    for reservation in result.reservations:
        if reservation.reservation_status == "READY":
            assert reservation.assigned_inventory_code is not None
            assert reservation.expiration_date is not None
            assert base[reservation.assigned_inventory_code] == reservation.title_source_key
            assert states[reservation.assigned_inventory_code] == "RESERVED"


def test_waiting_reservation_has_no_copy_or_expiration() -> None:
    result = generated()

    for reservation in result.reservations:
        if reservation.reservation_status == "WAITING":
            assert reservation.assigned_inventory_code is None
            assert reservation.expiration_date is None


def test_notification_has_exactly_one_origin() -> None:
    result = generated()

    for notification in result.notifications:
        origins = [
            notification.loan_source_key,
            notification.reservation_source_key,
            notification.fine_source_key,
            notification.article_source_key,
        ]
        assert sum(value is not None for value in origins) == 1


def test_due_soon_notification_is_unique_per_loan() -> None:
    result = generated()
    keys = [
        notification.loan_source_key
        for notification in result.notifications
        if notification.notification_type == "LOAN_DUE_SOON"
    ]

    assert len(keys) == len(set(keys))


def test_notification_read_state_is_temporally_coherent() -> None:
    result = generated()

    for notification in result.notifications:
        if notification.notification_status == "READ":
            assert notification.read_at is not None
            assert notification.read_at >= notification.created_at
        else:
            assert notification.read_at is None


def test_scenario_generation_is_structurally_deterministic() -> None:
    first = generated()
    second = generated()

    assert first.loans == second.loans
    assert first.reservations == second.reservations
    assert first.fines == second.fines
    assert first.copy_states == second.copy_states


def test_different_business_settings_change_due_dates_and_fines() -> None:
    alternative = ScenarioSettings(
        loan_duration_days=28,
        reservation_ready_hold_hours=72,
        loan_due_soon_days=5,
        fine_weekly_rate=Decimal("1.00"),
        fine_max_amount=Decimal("30.00"),
    )

    first = generated()
    second = generate_demo_scenarios(
        users(20),
        copies(),
        settings=alternative,
        reference_datetime=REFERENCE,
        counts=COUNTS,
    )

    assert first.loans[0].due_date != second.loans[0].due_date
    assert first.fines[0].amount != second.fines[0].amount
