"""DEV-16.G3.6 — attribution déterministe des rôles Staff/Admin (fichiers uniquement).

Reconstruit `users.csv` du bundle consolidé à partir du `users.csv` du Full
historique (jamais modifié) : 5 ROLE_LIBRARIAN + 2 ROLE_ADMIN parmi les
comptes seedés SANS scénario de membre (loans / reservations /
notifications), triés par `source_key`. Le total reste 1 500.
"""

from __future__ import annotations

import csv
import io
import sys
from datetime import date
from pathlib import Path

from primatis_data_seeding.generation.users import SyntheticUserRow, assign_staff_roles

HEADER = (
    "source_key", "email", "password_hash", "first_name", "last_name", "phone_number",
    "account_status", "member_number", "member_status", "registration_date",
    "member_expiration_date", "blocked_reason", "failed_login_count", "role_code",
)
LIBRARIANS = 5
ADMINS = 2


def _date(value: str) -> date | None:
    return date.fromisoformat(value) if value else None


def read_users(path: Path) -> list[SyntheticUserRow]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return [
            SyntheticUserRow(
                source_key=r["source_key"], email=r["email"], password_hash=r["password_hash"],
                first_name=r["first_name"], last_name=r["last_name"],
                phone_number=r["phone_number"] or None, account_status=r["account_status"],
                member_number=r["member_number"] or None, member_status=r["member_status"] or None,
                registration_date=_date(r["registration_date"]),
                member_expiration_date=_date(r["member_expiration_date"]),
                blocked_reason=r["blocked_reason"] or None,
                failed_login_count=int(r["failed_login_count"]), role_code=r["role_code"],
            )
            for r in csv.DictReader(handle)
        ]


def scenario_user_keys(candidate: Path) -> frozenset[str]:
    keys: set[str] = set()
    for name, column in (
        ("loans.csv", "user_source_key"),
        ("reservations.csv", "user_source_key"),
        ("notifications.csv", "recipient_user_source_key"),
    ):
        with (candidate / name).open("r", encoding="utf-8", newline="") as handle:
            keys.update(row[column] for row in csv.DictReader(handle))
    return frozenset(keys)


def render(users: list[SyntheticUserRow]) -> bytes:
    buffer = io.StringIO()
    writer = csv.DictWriter(buffer, fieldnames=HEADER)  # même dialecte que export/users_csv.py
    writer.writeheader()
    for u in users:
        writer.writerow({
            "source_key": u.source_key, "email": u.email, "password_hash": u.password_hash,
            "first_name": u.first_name, "last_name": u.last_name,
            "phone_number": u.phone_number or "", "account_status": u.account_status,
            "member_number": u.member_number or "", "member_status": u.member_status or "",
            "registration_date": u.registration_date.isoformat() if u.registration_date else "",
            "member_expiration_date": u.member_expiration_date.isoformat() if u.member_expiration_date else "",
            "blocked_reason": u.blocked_reason or "", "failed_login_count": u.failed_login_count,
            "role_code": u.role_code,
        })
    return buffer.getvalue().encode("utf-8")


def build(historical_users: Path, candidate: Path) -> bytes:
    users = assign_staff_roles(
        read_users(historical_users),
        excluded_source_keys=scenario_user_keys(candidate),
        librarians=LIBRARIANS, admins=ADMINS,
    )
    return render(users)


def main() -> int:
    historical_users, candidate, output = (Path(a) for a in sys.argv[1:4])
    output.write_bytes(build(historical_users, candidate))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
