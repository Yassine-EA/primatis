from __future__ import annotations

from dataclasses import dataclass, field, replace
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
    # Attributs d'adhésion : None pour un compte sans ROLE_MEMBER (staff/admin),
    # comme le fait UserService à la création (memberNumber/memberStatus null).
    member_number: str | None
    member_status: str | None
    registration_date: date | None
    member_expiration_date: date | None
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


LIBRARIAN_ROLE_CODE = "ROLE_LIBRARIAN"
ADMIN_ROLE_CODE = "ROLE_ADMIN"


def assign_staff_roles(
    users: list[SyntheticUserRow],
    *,
    excluded_source_keys: frozenset[str],
    librarians: int,
    admins: int,
) -> list[SyntheticUserRow]:
    """Promeut des comptes seedés existants en ROLE_LIBRARIAN / ROLE_ADMIN.

    Sélection déterministe : parmi les comptes hors `excluded_source_keys`
    (ceux qui portent des scénarios de membre), triés par `source_key`, les
    `librarians` premiers deviennent ROLE_LIBRARIAN puis les `admins`
    suivants ROLE_ADMIN. Identité, e-mail, hash de mot de passe, adresse et
    résidence sont conservés ; seuls le rôle et les attributs d'adhésion
    (sans objet hors ROLE_MEMBER) changent. Le total d'utilisateurs est
    inchangé.
    """
    candidates = sorted(
        user.source_key for user in users if user.source_key not in excluded_source_keys
    )
    if len(candidates) < librarians + admins:
        raise ValueError(
            f"Not enough eligible accounts: eligible={len(candidates)} "
            f"required={librarians + admins}."
        )
    role_by_key = {key: LIBRARIAN_ROLE_CODE for key in candidates[:librarians]}
    role_by_key.update(
        {key: ADMIN_ROLE_CODE for key in candidates[librarians : librarians + admins]}
    )

    return [
        replace(
            user,
            role_code=role_by_key[user.source_key],
            member_number=None,
            member_status=None,
            registration_date=None,
            member_expiration_date=None,
            blocked_reason=None,
        )
        if user.source_key in role_by_key
        else user
        for user in users
    ]


BLOCKED_REASON_DEMO = "Blocage administratif de démonstration : dossier d'adhésion à régulariser."


def apply_member_states(
    users: list[SyntheticUserRow],
    *,
    excluded_source_keys: frozenset[str],
    reference_date: date,
    blocked: int,
    expired: int,
    disabled: int,
) -> list[SyntheticUserRow]:
    """DEV-17.3 : quelques comptes BLOCKED / EXPIRED / DISABLED (hors staff et scénarios).

    Sélection déterministe : parmi les `ROLE_MEMBER` hors `excluded_source_keys`,
    triés par `source_key`, on prend la **fin** de liste (BLOCKED, puis EXPIRED,
    puis DISABLED), ce qui laisse intacts les comptes de scénarios existants.

    - BLOCKED : `member_status=BLOCKED`, `blocked_reason` renseigné ;
    - EXPIRED : `member_status=EXPIRED`, `member_expiration_date` échue
      (`reference_date - (10 + 20 i)` jours), choisi parmi des adhésions
      antérieures d'au moins 120 jours (échéance postérieure à l'inscription) ;
    - DISABLED : `account_status=DISABLED`, `member_status` inchangé (ACTIVE) —
      AccountStatus et MemberStatus restent deux dimensions distinctes.
    """
    members = sorted(
        (u for u in users if u.role_code == SYNTHETIC_ROLE_CODE
         and u.source_key not in excluded_source_keys
         and u.account_status == "ACTIVE" and u.member_status == "ACTIVE"),
        key=lambda u: u.source_key,
    )
    if len(members) < blocked + expired + disabled:
        raise ValueError("Not enough eligible members for BLOCKED/EXPIRED/DISABLED states.")

    chosen: dict[str, SyntheticUserRow] = {}
    pool = list(members)
    for user in [pool.pop() for _ in range(blocked)]:
        chosen[user.source_key] = replace(
            user, member_status="BLOCKED", blocked_reason=BLOCKED_REASON_DEMO,
        )
    old_enough = date.fromordinal(reference_date.toordinal() - 120)
    expired_pool = [u for u in pool if u.registration_date and u.registration_date <= old_enough]
    if len(expired_pool) < expired:
        raise ValueError("Not enough old memberships for EXPIRED members.")
    for index in range(expired):
        user = expired_pool.pop()
        pool.remove(user)
        chosen[user.source_key] = replace(
            user, member_status="EXPIRED",
            member_expiration_date=reference_date - timedelta(days=10 + 20 * index),
        )
    for user in [pool.pop() for _ in range(disabled)]:
        chosen[user.source_key] = replace(user, account_status="DISABLED")

    return [chosen.get(user.source_key, user) for user in users]
