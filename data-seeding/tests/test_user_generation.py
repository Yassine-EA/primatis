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
