from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta
import os
import random

import bcrypt

from primatis_data_seeding.reference.bpost import BpostLocality


SYNTHETIC_EMAIL_DOMAIN = "seed.primatis.invalid"
SYNTHETIC_MEMBER_NUMBER_START = 800_000_001
SYNTHETIC_ROLE_CODE = "ROLE_MEMBER"

_FIRST_NAMES = (
    "Alex",
    "Camille",
    "Charlie",
    "Dominique",
    "Émilie",
    "Gabriel",
    "Inès",
    "Jules",
    "Lina",
    "Noah",
    "Océane",
    "Robin",
    "Sami",
    "Zoé",
)

_LAST_NAMES = (
    "Bernard",
    "Dubois",
    "Fontaine",
    "Lambert",
    "Leroy",
    "Martin",
    "Moreau",
    "Petit",
    "Robert",
    "Simon",
)

_STREET_LABELS = (
    "Rue Démo",
    "Avenue Exemple",
    "Chemin Test",
    "Allée Primatis",
    "Place Fictive",
)


@dataclass(frozen=True)
class SyntheticUserRow:
    source_key: str
    email: str
    password_hash: str
    first_name: str
    last_name: str
    phone_number: str | None
    account_status: str
    member_number: str
    member_status: str
    registration_date: date
    member_expiration_date: date
    blocked_reason: str | None
    failed_login_count: int
    role_code: str


@dataclass(frozen=True)
class SyntheticAddressRow:
    source_key: str
    postal_code: str
    locality: str
    street: str
    street_number: str
    box_number: str | None
    additional_info: str | None


@dataclass(frozen=True)
class SyntheticResidenceRow:
    user_source_key: str
    address_source_key: str
    start_date: date
    end_date: date | None


@dataclass
class SyntheticUsersResult:
    users: list[SyntheticUserRow] = field(default_factory=list)
    addresses: list[SyntheticAddressRow] = field(default_factory=list)
    residences: list[SyntheticResidenceRow] = field(default_factory=list)


def _hash_password(raw_password: str) -> str:
    encoded = raw_password.encode("utf-8")
    if len(encoded) > 72:
        raise ValueError("Seed user password must be at most 72 UTF-8 bytes for BCrypt.")
    hashed = bcrypt.hashpw(encoded, bcrypt.gensalt(rounds=12, prefix=b"2a"))
    return "{bcrypt}" + hashed.decode("ascii")


def _required_password() -> str:
    value = os.environ.get("PRIMATIS_SEED_USER_PASSWORD")
    if value is None or len(value) < 12:
        raise ValueError(
            "PRIMATIS_SEED_USER_PASSWORD must be set and contain at least 12 characters."
        )
    return value


def _member_number(index: int) -> str:
    numeric = SYNTHETIC_MEMBER_NUMBER_START + index
    if numeric > 899_999_999:
        raise ValueError("Synthetic member-number namespace M8xxxxxxxx exhausted.")
    return f"M{numeric:09d}"


def generate_synthetic_members(
    localities: list[BpostLocality],
    *,
    count: int,
    seed: int,
    reference_date: date,
    raw_password: str | None = None,
) -> SyntheticUsersResult:
    if count <= 0:
        raise ValueError("Synthetic member count must be strictly positive.")
    if not localities:
        raise ValueError("At least one Bpost locality is required.")

    password = raw_password if raw_password is not None else _required_password()
    password_hash = _hash_password(password)

    rng = random.Random(seed)
    result = SyntheticUsersResult()

    for index in range(count):
        ordinal = index + 1
        user_source_key = f"seed-member-{ordinal:06d}"
        address_source_key = f"seed-address-{ordinal:06d}"

        first_name = rng.choice(_FIRST_NAMES)
        last_name = rng.choice(_LAST_NAMES)
        locality = rng.choice(localities)

        years_back_days = rng.randint(30, 5 * 365)
        registration_date = reference_date - timedelta(days=years_back_days)
        expiration_date = reference_date + timedelta(days=365)

        street_label = rng.choice(_STREET_LABELS)
        street = f"{street_label} {ordinal:04d}"
        street_number = str(rng.randint(1, 250))
        box_number = (
            str(rng.randint(1, 20))
            if rng.random() < 0.15
            else None
        )

        result.users.append(
            SyntheticUserRow(
                source_key=user_source_key,
                email=f"member{ordinal:06d}@{SYNTHETIC_EMAIL_DOMAIN}",
                password_hash=password_hash,
                first_name=first_name,
                last_name=last_name,
                phone_number=None,
                account_status="ACTIVE",
                member_number=_member_number(index),
                member_status="ACTIVE",
                registration_date=registration_date,
                member_expiration_date=expiration_date,
                blocked_reason=None,
                failed_login_count=0,
                role_code=SYNTHETIC_ROLE_CODE,
            )
        )

        result.addresses.append(
            SyntheticAddressRow(
                source_key=address_source_key,
                postal_code=locality.postal_code,
                locality=locality.locality,
                street=street,
                street_number=street_number,
                box_number=box_number,
                additional_info=None,
            )
        )

        result.residences.append(
            SyntheticResidenceRow(
                user_source_key=user_source_key,
                address_source_key=address_source_key,
                start_date=registration_date,
                end_date=None,
            )
        )

    return result
