from datetime import date

import pytest

from primatis_data_seeding.generation.users import BLOCKED_REASON_DEMO, SyntheticUserRow, apply_member_states

REF = date(2026, 9, 19)


def _user(i: int, role="ROLE_MEMBER", registered=date(2023, 1, 1)) -> SyntheticUserRow:
    member = role == "ROLE_MEMBER"
    return SyntheticUserRow(
        f"seed-member-{i:06d}", f"member{i:06d}@seed.primatis.invalid", "{bcrypt}x", "Alex", "Leroy", None,
        "ACTIVE", f"M{800_000_000 + i}" if member else None, "ACTIVE" if member else None,
        registered if member else None, date(2027, 9, 12) if member else None, None, 0, role,
    )


def _users():
    users = [_user(i) for i in range(1, 60)]
    users[10] = _user(11, role="ROLE_LIBRARIAN")
    return users


def test_states_are_applied_on_unused_members_only() -> None:
    users = _users()
    excluded = frozenset(f"seed-member-{i:06d}" for i in range(1, 40))
    out = apply_member_states(users, excluded_source_keys=excluded, reference_date=REF, blocked=3, expired=3, disabled=2)
    assert len(out) == len(users)
    changed = [u for u, o in zip(users, out) if u != o]
    assert len(changed) == 8
    assert all(o.source_key not in excluded and o.role_code == "ROLE_MEMBER" for o in out if o in changed)


def test_blocked_expired_disabled_semantics() -> None:
    out = apply_member_states(_users(), excluded_source_keys=frozenset(), reference_date=REF, blocked=3, expired=3, disabled=2)
    blocked = [u for u in out if u.member_status == "BLOCKED"]
    expired = [u for u in out if u.member_status == "EXPIRED"]
    disabled = [u for u in out if u.account_status == "DISABLED"]
    assert len(blocked) == 3 and all(u.blocked_reason == BLOCKED_REASON_DEMO for u in blocked)
    assert len(expired) == 3 and all(u.member_expiration_date < REF for u in expired)
    assert all(u.member_expiration_date > u.registration_date for u in expired)
    assert len(disabled) == 2 and all(u.member_status == "ACTIVE" for u in disabled)  # dimensions distinctes
    assert all(u.blocked_reason is None for u in out if u.member_status != "BLOCKED")


def test_staff_accounts_are_never_touched() -> None:
    users = _users()
    out = apply_member_states(users, excluded_source_keys=frozenset(), reference_date=REF, blocked=3, expired=3, disabled=2)
    staff = [u for u in out if u.role_code != "ROLE_MEMBER"]
    assert staff == [u for u in users if u.role_code != "ROLE_MEMBER"]


def test_selection_is_deterministic_and_stable() -> None:
    args = dict(excluded_source_keys=frozenset(), reference_date=REF, blocked=3, expired=3, disabled=2)
    assert apply_member_states(_users(), **args) == apply_member_states(_users(), **args)


def test_expired_requires_old_enough_memberships() -> None:
    users = [_user(i, registered=date(2026, 9, 1)) for i in range(1, 30)]
    with pytest.raises(ValueError, match="old memberships"):
        apply_member_states(users, excluded_source_keys=frozenset(), reference_date=REF, blocked=1, expired=1, disabled=1)


def test_not_enough_members_is_rejected() -> None:
    with pytest.raises(ValueError, match="eligible"):
        apply_member_states([_user(1), _user(2)], excluded_source_keys=frozenset(), reference_date=REF,
                            blocked=3, expired=3, disabled=2)
