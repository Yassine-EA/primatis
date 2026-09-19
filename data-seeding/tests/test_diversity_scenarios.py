"""DEV-17.3 — scénarios de diversité : retours à temps, réservations terminales, copies dégradées."""

from collections import Counter
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal

import pytest

from primatis_data_seeding.generation.copies import generate_copies
from primatis_data_seeding.generation.scenarios import (
    DEFAULT_FULL_SCENARIO_COUNTS,
    DEMO_TARGETED_SCENARIO_COUNTS,
    ScenarioSettings,
    generate_demo_scenarios,
)
from primatis_data_seeding.generation.users import SyntheticUserRow
from primatis_data_seeding.mapping.models import PrimatisTitleRow

REF = datetime(2026, 9, 19, 12, tzinfo=timezone.utc)
SETTINGS = ScenarioSettings(21, 48, 3, Decimal("0.80"), Decimal("25.00"))


def _title(i: int) -> PrimatisTitleRow:
    return PrimatisTitleRow(f"isbn:{i:013d}", f"{i:013d}", f"Titre {i}", None, None, None, "FR", None, None, None)


def _user(i: int) -> SyntheticUserRow:
    return SyntheticUserRow(
        f"seed-member-{i:06d}", f"member{i:06d}@seed.primatis.invalid", "{bcrypt}x", "Alex", "Leroy",
        None, "ACTIVE", f"M{800_000_000 + i}", "ACTIVE", date(2024, 1, 1), date(2027, 1, 1), None, 0,
        "ROLE_MEMBER",
    )


@pytest.fixture(scope="module")
def copies():
    return generate_copies([_title(i) for i in range(1, 14_601)], profile="full_consolidated").copies


@pytest.fixture(scope="module")
def result(copies):
    return generate_demo_scenarios(
        [_user(i) for i in range(1, 1_501)], copies, settings=SETTINGS,
        reference_datetime=REF, counts=DEMO_TARGETED_SCENARIO_COUNTS,
    )


def test_historical_counts_are_unchanged_by_default(copies) -> None:
    result = generate_demo_scenarios(
        [_user(i) for i in range(1, 1_501)], copies, settings=SETTINGS,
        reference_datetime=REF, counts=DEFAULT_FULL_SCENARIO_COUNTS,
    )
    assert (len(result.loans), len(result.reservations), len(result.fines),
            len(result.notifications), len(result.copy_states)) == (160, 60, 60, 270, 120)


def test_targeted_volumes(result) -> None:
    loans = Counter(l.loan_status for l in result.loans)
    assert loans == {"ACTIVE": 80, "OVERDUE": 20, "RETURNED": 75}
    assert Counter(r.reservation_status for r in result.reservations) == {
        "WAITING": 40, "READY": 20, "FULFILLED": 6, "CANCELLED": 6, "EXPIRED": 6,
    }
    assert len(result.fines) == 60
    assert Counter((c.copy_condition, c.availability_status) for c in result.copy_states) == {
        ("GOOD", "ON_LOAN"): 100, ("GOOD", "RESERVED"): 20, ("DAMAGED", "AVAILABLE"): 4,
        ("DAMAGED", "UNAVAILABLE"): 4, ("LOST", "UNAVAILABLE"): 3, ("OUT_OF_SERVICE", "UNAVAILABLE"): 3,
    }


def test_returned_on_time_have_no_fine_and_a_returned_notification(result) -> None:
    on_time = [l for l in result.loans if l.source_key.startswith("seed-loan-returned-ontime-")]
    assert len(on_time) == 15
    fined = {f.loan_source_key for f in result.fines}
    returned_notifs = {n.loan_source_key for n in result.notifications if n.notification_type == "LOAN_RETURNED"}
    for loan in on_time:
        assert loan.return_date <= loan.due_date
        assert loan.return_date >= loan.loan_date.date()
        assert loan.source_key not in fined
        assert loan.source_key in returned_notifs
        assert loan.inventory_code not in {c.inventory_code for c in result.copy_states}  # copie AVAILABLE


def test_late_returns_still_carry_their_fine(result) -> None:
    late = [l for l in result.loans if l.source_key.startswith("seed-loan-returned-0")]
    assert len(late) == 60 and {f.loan_source_key for f in result.fines} == {l.source_key for l in late}


def test_fulfilled_reservations_are_consistent(result) -> None:
    loans = {l.source_key: l for l in result.loans}
    fulfilled = [r for r in result.reservations if r.reservation_status == "FULFILLED"]
    assert len(fulfilled) == 6
    for r in fulfilled:
        loan = loans[r.fulfilled_by_loan_source_key]
        assert loan.user_source_key == r.user_source_key
        assert loan.inventory_code == r.assigned_inventory_code == r.title_inventory_code
        assert r.reservation_date < loan.loan_date <= datetime.combine(loan.return_date, datetime.min.time(), tzinfo=timezone.utc)


def test_cancelled_and_expired_reservations(result) -> None:
    cancelled = [r for r in result.reservations if r.reservation_status == "CANCELLED"]
    expired = [r for r in result.reservations if r.reservation_status == "EXPIRED"]
    assert len(cancelled) == 6 and len(expired) == 6
    assert sum(1 for r in cancelled if r.assigned_inventory_code is None) == 3  # depuis WAITING
    # READY -> CANCELLED : copie et échéance conservées (DEV-DEC-0038)
    assert all(r.expiration_date is not None for r in cancelled if r.assigned_inventory_code)
    assert all(r.fulfilled_by_loan_source_key is None for r in cancelled + expired)
    for r in expired:
        assert r.expiration_date < REF and r.assigned_inventory_code is not None
    types = Counter(n.notification_type for n in result.notifications)
    assert types["RESERVATION_CANCELLED"] == 6 and types["RESERVATION_EXPIRED"] == 6


def test_expired_and_cancelled_copies_are_not_reserved_or_on_loan(result) -> None:
    states = {c.inventory_code for c in result.copy_states}
    for r in result.reservations:
        if r.reservation_status in {"CANCELLED", "EXPIRED", "FULFILLED"} and r.assigned_inventory_code:
            assert r.assigned_inventory_code not in states


def test_degraded_copies_respect_availability_rules(result, copies) -> None:
    by_code = {c.inventory_code: c for c in copies}
    by_title = Counter(c.title_source_key for c in copies)
    open_copies = {l.inventory_code for l in result.loans if l.loan_status in {"ACTIVE", "OVERDUE"}}
    degraded = [c for c in result.copy_states if c.copy_condition != "GOOD"]
    assert len(degraded) == 14
    titles = [by_code[c.inventory_code].title_source_key for c in degraded]
    assert len(set(titles)) == 14  # un exemplaire dégradé par Title
    assert all(by_title[t] >= 2 for t in titles)
    for c in degraded:
        assert c.inventory_code not in open_copies
        if c.copy_condition in {"LOST", "OUT_OF_SERVICE"}:
            assert c.availability_status == "UNAVAILABLE"


def test_temporal_relations_to_reference(result) -> None:
    ref_date = REF.date()
    for loan in result.loans:
        if loan.loan_status == "ACTIVE":
            assert loan.due_date > ref_date
        if loan.loan_status == "OVERDUE":
            assert loan.due_date < ref_date
    due_soon = [l for l in result.loans if l.loan_status == "ACTIVE" and 0 < (l.due_date - ref_date).days <= 3]
    assert len(due_soon) == 10
    for r in result.reservations:
        if r.reservation_status == "READY":
            assert r.expiration_date > REF
    assert all(n.created_at <= REF for n in result.notifications)


def test_generation_is_deterministic_and_relative(copies) -> None:
    users = [_user(i) for i in range(1, 1_501)]
    a = generate_demo_scenarios(users, copies, settings=SETTINGS, reference_datetime=REF, counts=DEMO_TARGETED_SCENARIO_COUNTS)
    b = generate_demo_scenarios(users, copies, settings=SETTINGS, reference_datetime=REF, counts=DEMO_TARGETED_SCENARIO_COUNTS)
    shifted = generate_demo_scenarios(
        users, copies, settings=SETTINGS, reference_datetime=REF + timedelta(days=30),
        counts=DEMO_TARGETED_SCENARIO_COUNTS,
    )
    assert a.loans == b.loans and a.reservations == b.reservations
    for x, y in zip(a.loans, shifted.loans):  # mêmes acteurs, dates décalées de 30 jours
        assert (x.user_source_key, x.inventory_code) == (y.user_source_key, y.inventory_code)
        assert y.due_date - x.due_date == timedelta(days=30)


def test_fulfilled_cannot_exceed_on_time_returns(copies) -> None:
    from dataclasses import replace
    counts = replace(DEMO_TARGETED_SCENARIO_COUNTS, fulfilled_reservations=20)
    with pytest.raises(ValueError, match="fulfilled_reservations"):
        generate_demo_scenarios([_user(i) for i in range(1, 1_501)], copies, settings=SETTINGS,
                                reference_datetime=REF, counts=counts)
