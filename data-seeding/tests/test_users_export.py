import csv
from datetime import date
from pathlib import Path

from primatis_data_seeding.export.users_csv import export_users_csv
from primatis_data_seeding.generation.users import generate_synthetic_members
from primatis_data_seeding.reference.bpost import BpostLocality


def test_exports_user_dataset_without_raw_password(tmp_path: Path) -> None:
    localities = [BpostLocality("6000", "Charleroi")]
    generated = generate_synthetic_members(
        localities,
        count=1,
        seed=7,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    paths = export_users_csv(localities, generated, tmp_path)

    with paths.users.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        rows = list(reader)

    assert "password_hash" in reader.fieldnames
    assert "password" not in reader.fieldnames
    assert "raw_password" not in reader.fieldnames
    assert rows[0]["password_hash"].startswith("{bcrypt}")


def test_exports_bpost_locality_reference(tmp_path: Path) -> None:
    localities = [BpostLocality("6000", "Charleroi")]
    generated = generate_synthetic_members(
        localities,
        count=1,
        seed=7,
        reference_date=date(2026, 8, 25),
        raw_password="DemoPassword!2026",
    )

    paths = export_users_csv(localities, generated, tmp_path)

    with paths.localities.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    assert rows == [{"postal_code": "6000", "locality": "Charleroi"}]
