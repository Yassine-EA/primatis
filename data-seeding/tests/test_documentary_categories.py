import pytest

from primatis_data_seeding.mapping.documentary_categories import (
    DOCUMENTARY_CATEGORY_SUBJECT_ALIASES,
    matches_documentary_category,
)


def test_every_category_has_at_least_two_aliases():
    for category, aliases in DOCUMENTARY_CATEGORY_SUBJECT_ALIASES.items():
        assert len(aliases) >= 2, category


def test_exact_match_after_fold():
    assert matches_documentary_category(["History"], "history") is True
    assert matches_documentary_category(["HISTORIOGRAPHY"], "history") is True
    assert matches_documentary_category(["Histoire"], "history") is False  # not an alias for history (see genres.py)


def test_no_match_returns_false():
    assert matches_documentary_category(["Cooking"], "history") is False
    assert matches_documentary_category([], "history") is False


def test_never_fuzzy_or_substring():
    # "History of France" must NOT match "history" — no substring logic.
    assert matches_documentary_category(["History of France"], "history") is False


def test_unknown_category_raises():
    with pytest.raises(KeyError):
        matches_documentary_category(["Fiction"], "not_a_real_category")


def test_multiple_subjects_any_match_suffices():
    assert matches_documentary_category(["Unrelated topic", "Modern Art"], "arts") is True
