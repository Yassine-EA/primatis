import pytest

from primatis_data_seeding.config import SeedProfile
from primatis_data_seeding.guard import validate_target


@pytest.mark.parametrize(
    ("profile_name", "database"),
    [
        ("small", "primatis_dev"),
        ("medium", "primatis_dev"),
        ("large", "primatis_dev"),
        ("full", "primatis_preview"),
    ],
)
def test_valid_profile_database_pairs_are_allowed(
    profile_name: str,
    database: str,
) -> None:
    profile = SeedProfile(profile_name, database, 1, None, False)
    validate_target(profile, database)


def test_primatis_test_is_always_rejected() -> None:
    profile = SeedProfile("small", "primatis_dev", 100, None, False)

    with pytest.raises(ValueError, match="forbidden"):
        validate_target(profile, "primatis_test")


def test_profile_cannot_target_another_database() -> None:
    profile = SeedProfile("full", "primatis_preview", 15000, 24000, True)

    with pytest.raises(ValueError, match="must target"):
        validate_target(profile, "primatis_dev")
