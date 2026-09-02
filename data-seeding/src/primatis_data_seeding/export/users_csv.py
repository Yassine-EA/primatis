from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

from primatis_data_seeding.generation.users import SyntheticUsersResult
from primatis_data_seeding.reference.bpost import BpostLocality


@dataclass(frozen=True)
class UserExportPaths:
    root: Path
    localities: Path
    users: Path
    addresses: Path
    residences: Path


def _write(path: Path, fieldnames: tuple[str, ...], rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def export_users_csv(
    localities: list[BpostLocality],
    generated: SyntheticUsersResult,
    output_dir: Path,
) -> UserExportPaths:
    paths = UserExportPaths(
        root=output_dir,
        localities=output_dir / "bpost_localities.csv",
        users=output_dir / "users.csv",
        addresses=output_dir / "addresses.csv",
        residences=output_dir / "residences.csv",
    )

    _write(
        paths.localities,
        ("postal_code", "locality"),
        [
            {"postal_code": row.postal_code, "locality": row.locality}
            for row in localities
        ],
    )
    _write(
        paths.users,
        (
            "source_key",
            "email",
            "password_hash",
            "first_name",
            "last_name",
            "phone_number",
            "account_status",
            "member_number",
            "member_status",
            "registration_date",
            "member_expiration_date",
            "blocked_reason",
            "failed_login_count",
            "role_code",
        ),
        [
            {
                "source_key": row.source_key,
                "email": row.email,
                "password_hash": row.password_hash,
                "first_name": row.first_name,
                "last_name": row.last_name,
                "phone_number": row.phone_number or "",
                "account_status": row.account_status,
                "member_number": row.member_number,
                "member_status": row.member_status,
                "registration_date": row.registration_date.isoformat(),
                "member_expiration_date": row.member_expiration_date.isoformat(),
                "blocked_reason": row.blocked_reason or "",
                "failed_login_count": row.failed_login_count,
                "role_code": row.role_code,
            }
            for row in generated.users
        ],
    )
    _write(
        paths.addresses,
        (
            "source_key",
            "postal_code",
            "locality",
            "street",
            "street_number",
            "box_number",
            "additional_info",
        ),
        [
            {
                "source_key": row.source_key,
                "postal_code": row.postal_code,
                "locality": row.locality,
                "street": row.street,
                "street_number": row.street_number,
                "box_number": row.box_number or "",
                "additional_info": row.additional_info or "",
            }
            for row in generated.addresses
        ],
    )
    _write(
        paths.residences,
        ("user_source_key", "address_source_key", "start_date", "end_date"),
        [
            {
                "user_source_key": row.user_source_key,
                "address_source_key": row.address_source_key,
                "start_date": row.start_date.isoformat(),
                "end_date": row.end_date.isoformat() if row.end_date else "",
            }
            for row in generated.residences
        ],
    )

    return paths
