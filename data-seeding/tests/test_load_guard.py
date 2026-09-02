import pytest

from primatis_data_seeding.load.guard import (
    require_apply_confirmation,
    validate_live_database,
    validate_requested_target,
)


def test_full_targets_preview() -> None:
    validate_requested_target("full", "primatis_preview")


def test_small_targets_dev() -> None:
    validate_requested_target("small", "primatis_dev")


def test_test_database_is_forbidden() -> None:
    with pytest.raises(ValueError, match="forbidden"):
        validate_requested_target("small", "primatis_test")


def test_profile_database_mismatch_is_rejected() -> None:
    with pytest.raises(ValueError, match="must target"):
        validate_requested_target("full", "primatis_dev")


def test_live_database_must_equal_explicit_target() -> None:
    with pytest.raises(ValueError, match="does not match"):
        validate_live_database("full", "primatis_preview", "other_db")


def test_apply_requires_exact_database_confirmation() -> None:
    with pytest.raises(ValueError, match="confirm-database"):
        require_apply_confirmation(
            apply=True,
            confirmation=None,
            database="primatis_preview",
        )


def test_apply_accepts_exact_database_confirmation() -> None:
    require_apply_confirmation(
        apply=True,
        confirmation="primatis_preview",
        database="primatis_preview",
    )


def test_check_mode_needs_no_confirmation() -> None:
    require_apply_confirmation(
        apply=False,
        confirmation=None,
        database="primatis_preview",
    )
