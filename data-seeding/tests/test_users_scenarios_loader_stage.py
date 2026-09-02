from pathlib import Path

import pytest

from primatis_data_seeding.load.users_scenarios import _required_paths


FILENAMES = (
    "bpost_localities.csv",
    "users.csv",
    "addresses.csv",
    "residences.csv",
    "loans.csv",
    "reservations.csv",
    "fines.csv",
    "notifications.csv",
    "copy_states.csv",
)


def test_requires_all_export_files(tmp_path: Path) -> None:
    for name in FILENAMES:
        (tmp_path / name).write_text("x\n", encoding="utf-8")

    paths = _required_paths(tmp_path)

    assert paths.users.name == "users.csv"
    assert paths.notifications.name == "notifications.csv"


def test_missing_export_file_is_rejected(tmp_path: Path) -> None:
    for name in FILENAMES[:-1]:
        (tmp_path / name).write_text("x\n", encoding="utf-8")

    with pytest.raises(ValueError, match="Missing users/scenario export"):
        _required_paths(tmp_path)
