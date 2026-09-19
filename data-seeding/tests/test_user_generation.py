from datetime import date

import bcrypt
import pytest

from primatis_data_seeding.generation.users import (
    SYNTHETIC_EMAIL_DOMAIN,
    generate_synthetic_members,
)
from primatis_data_seeding.reference.bpost import BpostLocality


LOCALITIES = [
    BpostLocality("1000", "Bruxelles"),
    BpostLocality("6000", "Charleroi"),
    BpostLocality("9000", "Gent"),
]


def test_generates_requested_number_of_members_addresses_and_residences() -> None:
    result = generate_synthetic_members(
        LOCALITIES,
        count=25,
        seed=7,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    assert len(result.users) == 25
    assert len(result.addresses) == 25
    assert len(result.residences) == 25


def test_synthetic_users_are_members_only() -> None:
    result = generate_synthetic_members(
        LOCALITIES,
        count=2,
        seed=7,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    assert {row.role_code for row in result.users} == {"ROLE_MEMBER"}
    assert {row.account_status for row in result.users} == {"ACTIVE"}
    assert {row.member_status for row in result.users} == {"ACTIVE"}


def test_emails_use_reserved_invalid_domain() -> None:
    result = generate_synthetic_members(
        LOCALITIES,
        count=2,
        seed=7,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    assert all(row.email.endswith(f"@{SYNTHETIC_EMAIL_DOMAIN}") for row in result.users)


def test_member_numbers_use_reserved_m8_namespace_and_are_unique() -> None:
    result = generate_synthetic_members(
        LOCALITIES,
        count=100,
        seed=7,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    numbers = [row.member_number for row in result.users]
    assert len(numbers) == len(set(numbers))
    assert all(number.startswith("M8") and len(number) == 10 for number in numbers)


def test_password_hash_is_spring_delegating_bcrypt_compatible() -> None:
    result = generate_synthetic_members(
        LOCALITIES,
        count=1,
        seed=7,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    stored = result.users[0].password_hash
    assert stored.startswith("{bcrypt}$2a$")
    raw_hash = stored.removeprefix("{bcrypt}").encode("ascii")
    assert bcrypt.checkpw(b"DemoPassword!2026", raw_hash)


def test_seed_controls_identity_and_address_selection() -> None:
    first = generate_synthetic_members(
        LOCALITIES,
        count=5,
        seed=42,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )
    second = generate_synthetic_members(
        LOCALITIES,
        count=5,
        seed=42,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    assert [
        (u.email, u.first_name, u.last_name, u.member_number)
        for u in first.users
    ] == [
        (u.email, u.first_name, u.last_name, u.member_number)
        for u in second.users
    ]
    assert first.addresses == second.addresses
    assert first.residences == second.residences


def test_each_user_has_exactly_one_current_residence() -> None:
    result = generate_synthetic_members(
        LOCALITIES,
        count=10,
        seed=7,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    assert all(row.end_date is None for row in result.residences)
    assert len({row.user_source_key for row in result.residences}) == 10


def test_address_uses_real_postal_locality_but_synthetic_street() -> None:
    result = generate_synthetic_members(
        LOCALITIES,
        count=5,
        seed=7,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    allowed = {(row.postal_code, row.locality) for row in LOCALITIES}

    assert all((row.postal_code, row.locality) in allowed for row in result.addresses)
    assert all(
        row.street.startswith(("Rue Démo", "Avenue Exemple", "Chemin Test", "Allée Primatis", "Place Fictive"))
        for row in result.addresses
    )


def test_rejects_empty_locality_reference() -> None:
    with pytest.raises(ValueError, match="Bpost locality"):
        generate_synthetic_members(
            [],
            count=1,
            seed=7,
            reference_date=date(2026, 8, 25),
            raw_password="DemoPassword!2026",
        )


def test_assign_staff_roles_is_deterministic_and_keeps_total() -> None:
    from primatis_data_seeding.generation.users import assign_staff_roles

    localities = LOCALITIES
    users = generate_synthetic_members(
        localities, count=1500, seed=1651,
        reference_date=date(2026, 9, 12), raw_password="not-a-real-password-1",
    ).users
    excluded = frozenset(user.source_key for user in users[:220])

    first = assign_staff_roles(users, excluded_source_keys=excluded, librarians=5, admins=2)
    second = assign_staff_roles(users, excluded_source_keys=excluded, librarians=5, admins=2)

    assert first == second
    assert len(first) == 1500
    roles = [user.role_code for user in first]
    assert roles.count("ROLE_MEMBER") == 1493
    assert roles.count("ROLE_LIBRARIAN") == 5
    assert roles.count("ROLE_ADMIN") == 2

    staff = [user for user in first if user.role_code != "ROLE_MEMBER"]
    assert not {user.source_key for user in staff} & excluded
    assert all(
        user.member_number is None and user.member_status is None
        and user.registration_date is None and user.member_expiration_date is None
        for user in staff
    )
    by_key = {user.source_key: user for user in users}
    assert all(user.password_hash == by_key[user.source_key].password_hash for user in staff)


def test_assign_staff_roles_rejects_insufficient_eligible_accounts() -> None:
    from primatis_data_seeding.generation.users import assign_staff_roles

    localities = LOCALITIES
    users = generate_synthetic_members(
        localities, count=10, seed=1, reference_date=date(2026, 9, 12),
        raw_password="not-a-real-password-1",
    ).users
    with pytest.raises(ValueError, match="Not enough eligible"):
        assign_staff_roles(
            users, excluded_source_keys=frozenset(u.source_key for u in users[:5]),
            librarians=5, admins=2,
        )


def test_historical_full_bundle_users_remain_all_members() -> None:
    import csv
    from pathlib import Path

    path = Path(__file__).resolve().parents[1] / "data" / "bundles" / "full" / "users.csv"
    if not path.is_file():
        pytest.skip("historical Full bundle not present")

    with path.open("r", encoding="utf-8", newline="") as handle:
        roles = [row["role_code"] for row in csv.DictReader(handle)]

    assert len(roles) == 1500
    assert set(roles) == {"ROLE_MEMBER"}
