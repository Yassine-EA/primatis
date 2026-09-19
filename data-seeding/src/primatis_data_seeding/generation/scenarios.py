from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP

from primatis_data_seeding.generation.copies import PrimatisCopyRow
from primatis_data_seeding.generation.users import SyntheticUserRow


UTC = timezone.utc


@dataclass(frozen=True)
class ScenarioSettings:
    loan_duration_days: int
    reservation_ready_hold_hours: int
    loan_due_soon_days: int
    fine_weekly_rate: Decimal
    fine_max_amount: Decimal


@dataclass(frozen=True)
class ScenarioCounts:
    active_loans: int
    overdue_loans: int
    returned_late_loans: int
    waiting_reservations: int
    ready_reservations: int
    # DEV-17.3 — diversité (0 par défaut : les appels historiques sont inchangés).
    returned_on_time_loans: int = 0
    fulfilled_reservations: int = 0  # emprunteurs pris parmi les retours à temps
    cancelled_reservations: int = 0  # moitié depuis WAITING, moitié depuis READY
    expired_reservations: int = 0
    damaged_available_copies: int = 0
    damaged_unavailable_copies: int = 0
    lost_copies: int = 0
    out_of_service_copies: int = 0


DEFAULT_FULL_SCENARIO_COUNTS = ScenarioCounts(
    active_loans=80,
    overdue_loans=20,
    returned_late_loans=60,
    waiting_reservations=40,
    ready_reservations=20,
)

# DEV-17.3 (HD-13) : profil `full_consolidated` = scénarios historiques + diversité.
DEMO_TARGETED_SCENARIO_COUNTS = ScenarioCounts(
    active_loans=80,
    overdue_loans=20,
    returned_late_loans=60,
    waiting_reservations=40,
    ready_reservations=20,
    returned_on_time_loans=15,
    fulfilled_reservations=6,
    cancelled_reservations=6,
    expired_reservations=6,
    damaged_available_copies=4,
    damaged_unavailable_copies=4,
    lost_copies=3,
    out_of_service_copies=3,
)


@dataclass(frozen=True)
class ScenarioLoanRow:
    source_key: str
    user_source_key: str
    inventory_code: str
    loan_date: datetime
    due_date: date
    return_date: date | None
    loan_status: str
    notes: str | None


@dataclass(frozen=True)
class ScenarioReservationRow:
    source_key: str
    user_source_key: str
    title_source_key: str
    title_inventory_code: str
    assigned_inventory_code: str | None
    fulfilled_by_loan_source_key: str | None
    reservation_date: datetime
    expiration_date: datetime | None
    reservation_status: str


@dataclass(frozen=True)
class ScenarioFineRow:
    source_key: str
    loan_source_key: str
    amount: Decimal
    reason: str
    issued_at: datetime
    fine_status: str
    paid_at: datetime | None
    cancelled_at: datetime | None


@dataclass(frozen=True)
class ScenarioNotificationRow:
    source_key: str
    recipient_user_source_key: str
    notification_type: str
    title: str
    message: str
    notification_status: str
    created_at: datetime
    read_at: datetime | None
    loan_source_key: str | None = None
    reservation_source_key: str | None = None
    fine_source_key: str | None = None
    article_source_key: str | None = None


@dataclass(frozen=True)
class ScenarioCopyStateRow:
    inventory_code: str
    availability_status: str
    copy_condition: str = "GOOD"


@dataclass
class ScenarioGenerationResult:
    loans: list[ScenarioLoanRow] = field(default_factory=list)
    reservations: list[ScenarioReservationRow] = field(default_factory=list)
    fines: list[ScenarioFineRow] = field(default_factory=list)
    notifications: list[ScenarioNotificationRow] = field(default_factory=list)
    copy_states: list[ScenarioCopyStateRow] = field(default_factory=list)


def _validate_settings(settings: ScenarioSettings) -> None:
    if settings.loan_duration_days <= 0:
        raise ValueError("loan_duration_days must be strictly positive.")
    if settings.reservation_ready_hold_hours <= 0:
        raise ValueError("reservation_ready_hold_hours must be strictly positive.")
    if settings.loan_due_soon_days < 0:
        raise ValueError("loan_due_soon_days cannot be negative.")
    if settings.fine_weekly_rate <= 0:
        raise ValueError("fine_weekly_rate must be strictly positive.")
    if settings.fine_max_amount <= 0:
        raise ValueError("fine_max_amount must be strictly positive.")


def _fine_amount(overdue_days: int, settings: ScenarioSettings) -> tuple[int, Decimal]:
    if overdue_days <= 0:
        raise ValueError("Fine can only be generated for a late returned Loan.")
    started_weeks = (overdue_days + 6) // 7
    amount = min(
        settings.fine_weekly_rate * Decimal(started_weeks),
        settings.fine_max_amount,
    )
    return started_weeks, amount.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def _notification_status(index: int, created_at: datetime) -> tuple[str, datetime | None]:
    if index % 3 == 0:
        return "READ", created_at + timedelta(hours=6)
    return "UNREAD", None


def _add_notification(
    result: ScenarioGenerationResult,
    *,
    recipient: str,
    notification_type: str,
    title: str,
    message: str,
    created_at: datetime,
    origin_type: str,
    origin_key: str,
) -> None:
    index = len(result.notifications) + 1
    status, read_at = _notification_status(index, created_at)
    origin = {
        "loan_source_key": None,
        "reservation_source_key": None,
        "fine_source_key": None,
        "article_source_key": None,
    }
    origin[f"{origin_type}_source_key"] = origin_key
    result.notifications.append(
        ScenarioNotificationRow(
            source_key=f"seed-notification-{index:06d}",
            recipient_user_source_key=recipient,
            notification_type=notification_type,
            title=title,
            message=message,
            notification_status=status,
            created_at=created_at,
            read_at=read_at,
            **origin,
        )
    )


def _group_copies(copies: list[PrimatisCopyRow]) -> dict[str, list[PrimatisCopyRow]]:
    by_title: dict[str, list[PrimatisCopyRow]] = defaultdict(list)
    for copy in copies:
        if copy.copy_condition != "GOOD" or copy.availability_status != "AVAILABLE":
            raise ValueError(
                "Scenario generation expects the neutral DEV-13.8 base catalogue "
                "(GOOD + AVAILABLE Copies)."
            )
        by_title[copy.title_source_key].append(copy)
    for group in by_title.values():
        group.sort(key=lambda item: item.inventory_code)
    return dict(by_title)


def generate_demo_scenarios(
    users: list[SyntheticUserRow],
    copies: list[PrimatisCopyRow],
    *,
    settings: ScenarioSettings,
    reference_datetime: datetime,
    counts: ScenarioCounts = DEFAULT_FULL_SCENARIO_COUNTS,
) -> ScenarioGenerationResult:
    _validate_settings(settings)
    if reference_datetime.tzinfo is None:
        raise ValueError("reference_datetime must be timezone-aware.")

    required_users = sum((
        counts.active_loans,
        counts.overdue_loans,
        counts.returned_late_loans,
        counts.waiting_reservations,
        counts.ready_reservations,
        counts.returned_on_time_loans,
        # FULFILLED : l'emprunteur est celui d'un retour à temps (même utilisateur).
        counts.cancelled_reservations,
        counts.expired_reservations,
    ))
    if counts.fulfilled_reservations > counts.returned_on_time_loans:
        raise ValueError("fulfilled_reservations cannot exceed returned_on_time_loans.")
    if len(users) < required_users:
        raise ValueError(
            f"Not enough synthetic members: received={len(users)} required={required_users}."
        )

    ordered_users = sorted(users, key=lambda item: item.source_key)
    by_title = _group_copies(copies)
    single_copy_titles = sorted(
        (title_key, group[0])
        for title_key, group in by_title.items()
        if len(group) == 1
    )
    all_copies = sorted(copies, key=lambda item: item.inventory_code)

    open_loan_count = counts.active_loans + counts.overdue_loans
    if len(single_copy_titles) < open_loan_count:
        raise ValueError("Not enough one-Copy Titles for isolated open Loan scenarios.")
    if counts.waiting_reservations and open_loan_count == 0:
        raise ValueError("WAITING Reservation scenarios require unavailable Titles.")

    result = ScenarioGenerationResult()
    user_cursor = 0
    reserved_inventory: set[str] = set()
    used_history_inventory: set[str] = set()
    open_loan_titles: list[tuple[str, str]] = []

    for index in range(counts.active_loans):
        user = ordered_users[user_cursor]; user_cursor += 1
        title_key, copy = single_copy_titles[index]
        open_loan_titles.append((title_key, copy.inventory_code))
        reserved_inventory.add(copy.inventory_code)

        if index < min(counts.active_loans, 10) and settings.loan_due_soon_days > 0:
            due_date = reference_datetime.date() + timedelta(days=max(1, settings.loan_due_soon_days))
            loan_date = datetime.combine(
                due_date - timedelta(days=settings.loan_duration_days),
                time(hour=10),
                tzinfo=reference_datetime.tzinfo,
            )
        else:
            loan_date = reference_datetime - timedelta(days=5 + index % 10)
            due_date = loan_date.date() + timedelta(days=settings.loan_duration_days)

        loan = ScenarioLoanRow(
            f"seed-loan-active-{index + 1:05d}",
            user.source_key,
            copy.inventory_code,
            loan_date,
            due_date,
            None,
            "ACTIVE",
            None,
        )
        result.loans.append(loan)
        result.copy_states.append(ScenarioCopyStateRow(copy.inventory_code, "ON_LOAN"))

        days_until_due = (due_date - reference_datetime.date()).days
        if 0 < days_until_due <= settings.loan_due_soon_days:
            _add_notification(
                result,
                recipient=user.source_key,
                notification_type="LOAN_DUE_SOON",
                title="Prêt bientôt à échéance",
                message="Un prêt arrive bientôt à échéance.",
                created_at=reference_datetime - timedelta(hours=2),
                origin_type="loan",
                origin_key=loan.source_key,
            )

    for index in range(counts.overdue_loans):
        user = ordered_users[user_cursor]; user_cursor += 1
        title_key, copy = single_copy_titles[counts.active_loans + index]
        open_loan_titles.append((title_key, copy.inventory_code))
        reserved_inventory.add(copy.inventory_code)
        due_date = reference_datetime.date() - timedelta(days=1 + index % 20)
        loan_date = datetime.combine(
            due_date - timedelta(days=settings.loan_duration_days),
            time(hour=11),
            tzinfo=reference_datetime.tzinfo,
        )
        loan = ScenarioLoanRow(
            f"seed-loan-overdue-{index + 1:05d}",
            user.source_key,
            copy.inventory_code,
            loan_date,
            due_date,
            None,
            "OVERDUE",
            None,
        )
        result.loans.append(loan)
        result.copy_states.append(ScenarioCopyStateRow(copy.inventory_code, "ON_LOAN"))
        _add_notification(
            result,
            recipient=user.source_key,
            notification_type="LOAN_OVERDUE",
            title="Prêt en retard",
            message="Un prêt a dépassé sa date d'échéance.",
            created_at=reference_datetime - timedelta(hours=4),
            origin_type="loan",
            origin_key=loan.source_key,
        )

    available_history_copies = [
        copy for copy in all_copies if copy.inventory_code not in reserved_inventory
    ]
    if len(available_history_copies) < counts.returned_late_loans:
        raise ValueError("Not enough Copies for returned Loan scenarios.")

    for index in range(counts.returned_late_loans):
        user = ordered_users[user_cursor]; user_cursor += 1
        copy = available_history_copies[index]
        used_history_inventory.add(copy.inventory_code)
        return_date = reference_datetime.date() - timedelta(days=7 + index % 20)
        overdue_days = 1 + index % 28
        due_date = return_date - timedelta(days=overdue_days)
        loan_date = datetime.combine(
            due_date - timedelta(days=settings.loan_duration_days),
            time(hour=9),
            tzinfo=reference_datetime.tzinfo,
        )
        loan = ScenarioLoanRow(
            f"seed-loan-returned-{index + 1:05d}",
            user.source_key,
            copy.inventory_code,
            loan_date,
            due_date,
            return_date,
            "RETURNED",
            None,
        )
        result.loans.append(loan)
        returned_at = datetime.combine(return_date, time(hour=16), tzinfo=reference_datetime.tzinfo)
        _add_notification(
            result,
            recipient=user.source_key,
            notification_type="LOAN_RETURNED",
            title="Retour enregistré",
            message="Le retour d'un exemplaire a été enregistré.",
            created_at=returned_at,
            origin_type="loan",
            origin_key=loan.source_key,
        )

        started_weeks, amount = _fine_amount(overdue_days, settings)
        fine_status = ("UNPAID", "PAID", "CANCELLED")[index % 3]
        paid_at = returned_at + timedelta(days=2) if fine_status == "PAID" else None
        cancelled_at = returned_at + timedelta(days=1) if fine_status == "CANCELLED" else None
        fine = ScenarioFineRow(
            f"seed-fine-{index + 1:05d}",
            loan.source_key,
            amount,
            f"Retour tardif — {overdue_days} jour(s) de retard, {started_weeks} semaine(s) entamée(s).",
            returned_at,
            fine_status,
            paid_at,
            cancelled_at,
        )
        result.fines.append(fine)
        _add_notification(
            result,
            recipient=user.source_key,
            notification_type="FINE_ISSUED",
            title="Amende émise",
            message="Une amende a été émise à la suite d'un retour tardif.",
            created_at=returned_at,
            origin_type="fine",
            origin_key=fine.source_key,
        )
        if fine_status == "PAID":
            _add_notification(
                result, recipient=user.source_key, notification_type="FINE_PAID",
                title="Paiement confirmé", message="Le paiement externe de l'amende a été confirmé.",
                created_at=paid_at, origin_type="fine", origin_key=fine.source_key,
            )
        elif fine_status == "CANCELLED":
            _add_notification(
                result, recipient=user.source_key, notification_type="FINE_CANCELLED",
                title="Amende annulée", message="L'amende a été annulée.",
                created_at=cancelled_at, origin_type="fine", origin_key=fine.source_key,
            )

    for index in range(counts.waiting_reservations):
        user = ordered_users[user_cursor]; user_cursor += 1
        title_key, anchor_inventory = open_loan_titles[index % len(open_loan_titles)]
        created_at = reference_datetime - timedelta(days=2, minutes=index)
        reservation = ScenarioReservationRow(
            f"seed-reservation-waiting-{index + 1:05d}",
            user.source_key,
            title_key,
            anchor_inventory,
            None,
            None,
            created_at,
            None,
            "WAITING",
        )
        result.reservations.append(reservation)
        _add_notification(
            result, recipient=user.source_key, notification_type="RESERVATION_CREATED",
            title="Réservation créée", message="Votre réservation a été enregistrée.",
            created_at=created_at, origin_type="reservation", origin_key=reservation.source_key,
        )

    available_for_ready = [
        copy for copy in all_copies
        if copy.inventory_code not in reserved_inventory
        and copy.inventory_code not in used_history_inventory
    ]
    if len(available_for_ready) < counts.ready_reservations:
        raise ValueError("Not enough AVAILABLE Copies for READY scenarios.")

    for index in range(counts.ready_reservations):
        user = ordered_users[user_cursor]; user_cursor += 1
        copy = available_for_ready[index]
        reserved_inventory.add(copy.inventory_code)
        reservation_date = reference_datetime - timedelta(days=4, minutes=index)
        ready_at = reference_datetime - timedelta(hours=6)
        expiration = ready_at + timedelta(hours=settings.reservation_ready_hold_hours)
        reservation = ScenarioReservationRow(
            f"seed-reservation-ready-{index + 1:05d}",
            user.source_key,
            copy.title_source_key,
            copy.inventory_code,
            copy.inventory_code,
            None,
            reservation_date,
            expiration,
            "READY",
        )
        result.reservations.append(reservation)
        result.copy_states.append(ScenarioCopyStateRow(copy.inventory_code, "RESERVED"))
        _add_notification(
            result, recipient=user.source_key, notification_type="RESERVATION_CREATED",
            title="Réservation créée", message="Votre réservation a été enregistrée.",
            created_at=reservation_date, origin_type="reservation", origin_key=reservation.source_key,
        )
        _add_notification(
            result, recipient=user.source_key, notification_type="RESERVATION_READY",
            title="Réservation disponible", message="Un exemplaire est disponible pour votre réservation.",
            created_at=ready_at, origin_type="reservation", origin_key=reservation.source_key,
        )

    _add_diversity_scenarios(
        result, counts=counts, settings=settings, reference_datetime=reference_datetime,
        ordered_users=ordered_users, user_cursor=user_cursor, all_copies=all_copies,
        by_title=by_title, reserved_inventory=reserved_inventory,
        used_history_inventory=used_history_inventory, open_loan_titles=open_loan_titles,
    )

    _validate_generated_scenarios(result, copies, settings, reference_datetime)
    return result


def _add_diversity_scenarios(
    result: ScenarioGenerationResult,
    *,
    counts: ScenarioCounts,
    settings: ScenarioSettings,
    reference_datetime: datetime,
    ordered_users: list[SyntheticUserRow],
    user_cursor: int,
    all_copies: list[PrimatisCopyRow],
    by_title: dict[str, list[PrimatisCopyRow]],
    reserved_inventory: set[str],
    used_history_inventory: set[str],
    open_loan_titles: list[tuple[str, str]],
) -> None:
    """DEV-17.3 : retours à temps, réservations terminales, copies dégradées.

    Toutes les dates sont relatives à `reference_datetime`. Les Copies utilisées
    proviennent de l'ensemble « libre » (ni prêt ouvert, ni READY, ni historique) ;
    aucune Fine n'est créée pour un retour à temps (règle §5 : Fine = retour tardif).
    """
    ref_date = reference_datetime.date()
    tz = reference_datetime.tzinfo
    hold = timedelta(hours=settings.reservation_ready_hold_hours)

    free = [
        copy for copy in all_copies
        if copy.inventory_code not in reserved_inventory
        and copy.inventory_code not in used_history_inventory
    ]
    free_cursor = 0
    needed = (
        counts.returned_on_time_loans + counts.expired_reservations
        + counts.cancelled_reservations - counts.cancelled_reservations // 2
    )
    # Pas régulier dans la liste triée : évite de concentrer tous les cas sur les
    # exemplaires consécutifs d'un même Title (déterministe).
    stride = max(1, len(free) // (needed + 1)) if needed else 1

    def take_free() -> PrimatisCopyRow:
        nonlocal free_cursor
        index = free_cursor * stride
        if index >= len(free):
            raise ValueError("Not enough free Copies for diversity scenarios.")
        copy = free[index]
        free_cursor += 1
        used_history_inventory.add(copy.inventory_code)
        return copy

    def next_user() -> SyntheticUserRow:
        nonlocal user_cursor
        user = ordered_users[user_cursor]
        user_cursor += 1
        return user

    # --- Retours à temps (+ Reservations FULFILLED sur les premiers) -----------
    for index in range(counts.returned_on_time_loans):
        user = next_user()
        copy = take_free()
        return_date = ref_date - timedelta(days=11 + index % 15)
        due_date = return_date + timedelta(days=1 + index % 10)  # due <= ref - 1 j
        loan_date = datetime.combine(
            due_date - timedelta(days=settings.loan_duration_days), time(hour=9), tzinfo=tz,
        )
        loan = ScenarioLoanRow(
            f"seed-loan-returned-ontime-{index + 1:05d}", user.source_key,
            copy.inventory_code, loan_date, due_date, return_date, "RETURNED", None,
        )
        result.loans.append(loan)
        returned_at = datetime.combine(return_date, time(hour=16), tzinfo=tz)
        _add_notification(
            result, recipient=user.source_key, notification_type="LOAN_RETURNED",
            title="Retour enregistré", message="Le retour d'un exemplaire a été enregistré.",
            created_at=returned_at, origin_type="loan", origin_key=loan.source_key,
        )
        if index < counts.fulfilled_reservations:
            ready_at = loan_date - timedelta(hours=20)
            reservation_date = ready_at - timedelta(days=3, minutes=index)
            reservation = ScenarioReservationRow(
                f"seed-reservation-fulfilled-{index + 1:05d}", user.source_key,
                copy.title_source_key, copy.inventory_code, copy.inventory_code,
                loan.source_key, reservation_date, ready_at + hold, "FULFILLED",
            )
            result.reservations.append(reservation)
            _add_notification(
                result, recipient=user.source_key, notification_type="RESERVATION_CREATED",
                title="Réservation créée", message="Votre réservation a été enregistrée.",
                created_at=reservation_date, origin_type="reservation",
                origin_key=reservation.source_key,
            )
            _add_notification(
                result, recipient=user.source_key, notification_type="RESERVATION_READY",
                title="Réservation disponible",
                message="Un exemplaire est disponible pour votre réservation.",
                created_at=ready_at, origin_type="reservation", origin_key=reservation.source_key,
            )

    # --- Reservations CANCELLED : moitié depuis WAITING, moitié depuis READY ----
    cancelled_from_waiting = counts.cancelled_reservations // 2
    for index in range(counts.cancelled_reservations):
        user = next_user()
        if index < cancelled_from_waiting:
            if not open_loan_titles:
                raise ValueError("CANCELLED-from-WAITING scenarios require unavailable Titles.")
            title_key, anchor = open_loan_titles[(index + 7) % len(open_loan_titles)]
            assigned, expiration = None, None
            reservation_date = reference_datetime - timedelta(days=6 + index, minutes=index)
            ready_at = None
            cancelled_at = reservation_date + timedelta(days=1)
        else:
            copy = take_free()
            title_key, anchor, assigned = copy.title_source_key, copy.inventory_code, copy.inventory_code
            reservation_date = reference_datetime - timedelta(days=9 + index, minutes=index)
            ready_at = reservation_date + timedelta(days=1)
            expiration = ready_at + hold  # conservée (READY -> CANCELLED, DEV-DEC-0038)
            cancelled_at = ready_at + timedelta(hours=5)
        reservation = ScenarioReservationRow(
            f"seed-reservation-cancelled-{index + 1:05d}", user.source_key,
            title_key, anchor, assigned, None, reservation_date, expiration, "CANCELLED",
        )
        result.reservations.append(reservation)
        _add_notification(
            result, recipient=user.source_key, notification_type="RESERVATION_CREATED",
            title="Réservation créée", message="Votre réservation a été enregistrée.",
            created_at=reservation_date, origin_type="reservation", origin_key=reservation.source_key,
        )
        if ready_at is not None:
            _add_notification(
                result, recipient=user.source_key, notification_type="RESERVATION_READY",
                title="Réservation disponible",
                message="Un exemplaire est disponible pour votre réservation.",
                created_at=ready_at, origin_type="reservation", origin_key=reservation.source_key,
            )
        _add_notification(
            result, recipient=user.source_key, notification_type="RESERVATION_CANCELLED",
            title="Réservation annulée", message="Votre réservation a été annulée.",
            created_at=cancelled_at, origin_type="reservation", origin_key=reservation.source_key,
        )

    # --- Reservations EXPIRED (anciennes READY dont la retenue est échue) -------
    for index in range(counts.expired_reservations):
        user = next_user()
        copy = take_free()
        reservation_date = reference_datetime - timedelta(days=12 + index, minutes=index)
        ready_at = reservation_date + timedelta(days=1)
        expiration = ready_at + hold
        reservation = ScenarioReservationRow(
            f"seed-reservation-expired-{index + 1:05d}", user.source_key,
            copy.title_source_key, copy.inventory_code, copy.inventory_code,
            None, reservation_date, expiration, "EXPIRED",
        )
        result.reservations.append(reservation)
        _add_notification(
            result, recipient=user.source_key, notification_type="RESERVATION_CREATED",
            title="Réservation créée", message="Votre réservation a été enregistrée.",
            created_at=reservation_date, origin_type="reservation", origin_key=reservation.source_key,
        )
        _add_notification(
            result, recipient=user.source_key, notification_type="RESERVATION_READY",
            title="Réservation disponible",
            message="Un exemplaire est disponible pour votre réservation.",
            created_at=ready_at, origin_type="reservation", origin_key=reservation.source_key,
        )
        _add_notification(
            result, recipient=user.source_key, notification_type="RESERVATION_EXPIRED",
            title="Réservation expirée",
            message="Le délai de retrait de votre réservation est écoulé.",
            created_at=expiration + timedelta(minutes=1), origin_type="reservation",
            origin_key=reservation.source_key,
        )

    # --- Copies dégradées : un seul exemplaire par Title à >= 2 exemplaires ----
    degraded_plan = (
        [("DAMAGED", "AVAILABLE")] * counts.damaged_available_copies
        + [("DAMAGED", "UNAVAILABLE")] * counts.damaged_unavailable_copies
        + [("LOST", "UNAVAILABLE")] * counts.lost_copies
        + [("OUT_OF_SERVICE", "UNAVAILABLE")] * counts.out_of_service_copies
    )
    if degraded_plan:
        used = reserved_inventory | used_history_inventory
        candidates = [
            (title_key, group) for title_key, group in sorted(by_title.items())
            if len(group) >= 2 and group[-1].inventory_code not in used
            and any(other.inventory_code not in used for other in group[:-1])
        ]
        if len(candidates) < len(degraded_plan):
            raise ValueError("Not enough multi-copy Titles for degraded Copy scenarios.")
        stride = max(1, len(candidates) // len(degraded_plan))
        for offset, (condition, availability) in enumerate(degraded_plan):
            _, group = candidates[offset * stride]
            result.copy_states.append(
                ScenarioCopyStateRow(group[-1].inventory_code, availability, condition)
            )


def _validate_generated_scenarios(
    result: ScenarioGenerationResult,
    base_copies: list[PrimatisCopyRow],
    settings: ScenarioSettings,
    reference_datetime: datetime | None = None,
) -> None:
    copy_title = {copy.inventory_code: copy.title_source_key for copy in base_copies}

    open_loans = Counter(
        loan.inventory_code for loan in result.loans
        if loan.loan_status in {"ACTIVE", "OVERDUE"}
    )
    if any(count > 1 for count in open_loans.values()):
        raise AssertionError("A Copy has more than one open Loan.")

    active_reservations = Counter(
        (r.user_source_key, r.title_source_key) for r in result.reservations
        if r.reservation_status in {"WAITING", "READY"}
    )
    if any(count > 1 for count in active_reservations.values()):
        raise AssertionError("Duplicate active Reservation for member + Title.")

    ready_copies = Counter(
        r.assigned_inventory_code for r in result.reservations if r.reservation_status == "READY"
    )
    if any(count > 1 for count in ready_copies.values()):
        raise AssertionError("A Copy has more than one READY Reservation.")

    for reservation in result.reservations:
        if reservation.title_inventory_code not in copy_title:
            raise AssertionError("Reservation title anchor Copy is unknown.")
        if copy_title[reservation.title_inventory_code] != reservation.title_source_key:
            raise AssertionError("Reservation title anchor must belong to reserved Title.")
        if reservation.reservation_status == "WAITING":
            if reservation.assigned_inventory_code is not None or reservation.expiration_date is not None:
                raise AssertionError("WAITING Reservation state mismatch.")
        if reservation.reservation_status == "READY":
            if reservation.assigned_inventory_code is None or reservation.expiration_date is None:
                raise AssertionError("READY Reservation requires assigned Copy and expiration.")
            if reservation.assigned_inventory_code != reservation.title_inventory_code:
                raise AssertionError("READY Reservation anchor must be the assigned Copy.")

    loan_by_key = {loan.source_key: loan for loan in result.loans}
    fines_by_loan = Counter(f.loan_source_key for f in result.fines)
    if any(count > 1 for count in fines_by_loan.values()):
        raise AssertionError("A Loan has more than one Fine.")
    for fine in result.fines:
        loan = loan_by_key[fine.loan_source_key]
        if loan.loan_status != "RETURNED" or loan.return_date is None or loan.return_date <= loan.due_date:
            raise AssertionError("Fine requires a late RETURNED Loan.")
        if fine.amount <= 0 or fine.amount > settings.fine_max_amount:
            raise AssertionError("Fine amount outside allowed range.")

    origin_type = {
        "LOAN_DUE_SOON": "loan", "LOAN_OVERDUE": "loan", "LOAN_RETURNED": "loan",
        "RESERVATION_CREATED": "reservation", "RESERVATION_READY": "reservation",
        "RESERVATION_EXPIRED": "reservation", "RESERVATION_CANCELLED": "reservation",
        "FINE_ISSUED": "fine", "FINE_PAID": "fine", "FINE_CANCELLED": "fine",
        "ARTICLE_PUBLISHED": "article",
    }
    due_soon = Counter()
    for n in result.notifications:
        origins = {
            "loan": n.loan_source_key, "reservation": n.reservation_source_key,
            "fine": n.fine_source_key, "article": n.article_source_key,
        }
        present = [key for key, value in origins.items() if value is not None]
        if len(present) != 1 or origin_type[n.notification_type] != present[0]:
            raise AssertionError("Notification origin/type mismatch.")
        if n.notification_status == "UNREAD" and n.read_at is not None:
            raise AssertionError("UNREAD Notification cannot have read_at.")
        if n.notification_status == "READ" and (
            n.read_at is None or n.read_at < n.created_at
        ):
            raise AssertionError("READ Notification timestamp mismatch.")
        if n.notification_type == "LOAN_DUE_SOON":
            due_soon[n.loan_source_key] += 1
    if any(count > 1 for count in due_soon.values()):
        raise AssertionError("LOAN_DUE_SOON must be unique per Loan.")

    final_states = {row.inventory_code: row.availability_status for row in result.copy_states}
    for loan in result.loans:
        if loan.loan_status in {"ACTIVE", "OVERDUE"} and final_states.get(loan.inventory_code) != "ON_LOAN":
            raise AssertionError("Open Loan requires Copy ON_LOAN.")
    for reservation in result.reservations:
        if reservation.reservation_status == "READY" and final_states.get(reservation.assigned_inventory_code) != "RESERVED":
            raise AssertionError("READY Reservation requires Copy RESERVED.")

    _validate_diversity_and_time(result, copy_title, reference_datetime)


def _validate_diversity_and_time(
    result: ScenarioGenerationResult,
    copy_title: dict[str, str],
    reference_datetime: datetime | None,
) -> None:
    """Invariants DEV-17.3 (états terminaux, copies dégradées, dates relatives)."""
    codes = [row.inventory_code for row in result.copy_states]
    if len(codes) != len(set(codes)):
        raise AssertionError("Duplicate Copy state row.")
    for row in result.copy_states:
        if row.copy_condition in {"LOST", "OUT_OF_SERVICE"} and row.availability_status != "UNAVAILABLE":
            raise AssertionError("LOST / OUT_OF_SERVICE Copy must be UNAVAILABLE.")
        if row.copy_condition not in {"GOOD", "DAMAGED", "LOST", "OUT_OF_SERVICE"}:
            raise AssertionError("Unknown copy_condition.")
    degraded = {r.inventory_code for r in result.copy_states if r.copy_condition != "GOOD"}
    busy = {l.inventory_code for l in result.loans if l.loan_status in {"ACTIVE", "OVERDUE"}}
    busy |= {
        r.assigned_inventory_code for r in result.reservations
        if r.reservation_status == "READY"
    }
    if degraded & busy:
        raise AssertionError("A degraded Copy cannot carry an open Loan or READY Reservation.")

    loan_by_key = {loan.source_key: loan for loan in result.loans}
    fine_loans = {fine.loan_source_key for fine in result.fines}
    for loan in result.loans:
        if loan.loan_status == "RETURNED":
            if loan.return_date is None or loan.return_date < loan.loan_date.date():
                raise AssertionError("RETURNED Loan chronology mismatch.")
            if loan.return_date <= loan.due_date and loan.source_key in fine_loans:
                raise AssertionError("On-time RETURNED Loan cannot carry a Fine.")
    for reservation in result.reservations:
        status = reservation.reservation_status
        if status == "FULFILLED":
            loan = loan_by_key.get(reservation.fulfilled_by_loan_source_key or "")
            if loan is None or loan.user_source_key != reservation.user_source_key:
                raise AssertionError("FULFILLED Reservation requires a Loan of the same member.")
            if copy_title[loan.inventory_code] != reservation.title_source_key:
                raise AssertionError("FULFILLED Reservation Loan must be on the reserved Title.")
            if reservation.assigned_inventory_code != loan.inventory_code:
                raise AssertionError("FULFILLED Reservation must keep the fulfilling Copy.")
            if not reservation.reservation_date < loan.loan_date:
                raise AssertionError("FULFILLED Reservation must precede its Loan.")
        elif status in {"CANCELLED", "EXPIRED"}:
            if reservation.fulfilled_by_loan_source_key is not None:
                raise AssertionError("CANCELLED/EXPIRED Reservation cannot reference a Loan.")
        if status == "EXPIRED" and (
            reservation.expiration_date is None or reservation.assigned_inventory_code is None
        ):
            raise AssertionError("EXPIRED Reservation keeps its former READY Copy and expiration.")

    if reference_datetime is None:
        return
    ref_date = reference_datetime.date()
    for loan in result.loans:
        if loan.loan_status == "ACTIVE" and not loan.due_date > ref_date:
            raise AssertionError("ACTIVE Loan must be due after the reference date.")
        if loan.loan_status == "OVERDUE" and not loan.due_date < ref_date:
            raise AssertionError("OVERDUE Loan must be due before the reference date.")
        if loan.loan_date > reference_datetime:
            raise AssertionError("Loan cannot start after the reference datetime.")
    for reservation in result.reservations:
        if reservation.reservation_status == "READY" and not reservation.expiration_date > reference_datetime:
            raise AssertionError("READY Reservation must expire after the reference datetime.")
        if reservation.reservation_status == "EXPIRED" and not reservation.expiration_date < reference_datetime:
            raise AssertionError("EXPIRED Reservation must have expired before the reference datetime.")
        if reservation.reservation_date > reference_datetime:
            raise AssertionError("Reservation cannot start after the reference datetime.")
    for notification in result.notifications:
        if notification.created_at > reference_datetime:
            raise AssertionError("Notification cannot be created after the reference datetime.")
