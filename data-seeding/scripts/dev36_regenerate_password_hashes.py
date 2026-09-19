"""DEV-16.G3.6R — régénère le hash de mot de passe des comptes seed (fichiers uniquement).

Réutilise le contrat existant du générateur (`generation/users.py`) :
`{bcrypt}` + bcrypt 2a, 12 rounds, sel aléatoire, un seul hash calculé puis
partagé par tous les comptes (comportement historique). Le clair vient de
`PRIMATIS_SEED_USER_PASSWORD` (>= 12 caractères), n'est jamais affiché ni
écrit. Toutes les autres colonnes de `users.csv` restent identiques.
"""

from __future__ import annotations

import csv
import io
import sys
from pathlib import Path

import bcrypt

from primatis_data_seeding.generation.users import _hash_password, _required_password


def main() -> int:
    users_csv = Path(sys.argv[1])
    password = _required_password()

    with users_csv.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        fieldnames = reader.fieldnames
        rows = list(reader)

    new_hash = _hash_password(password)
    for row in rows:
        row["password_hash"] = new_hash

    if not all(
        bcrypt.checkpw(password.encode("utf-8"), row["password_hash"].removeprefix("{bcrypt}").encode("ascii"))
        for row in rows
    ):
        raise SystemExit("bcrypt verification failed.")

    buffer = io.StringIO()
    writer = csv.DictWriter(buffer, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(rows)
    temporary = users_csv.with_suffix(".csv.tmp")
    temporary.write_bytes(buffer.getvalue().encode("utf-8"))
    temporary.replace(users_csv)
    print(f"rows={len(rows)} bcrypt_verified={len(rows)}/{len(rows)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
