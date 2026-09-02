import re

import pytest

from primatis_data_seeding.generation.copies import (
    PROFILE_COPY_DISTRIBUTIONS,
    generate_copies,
)
from primatis_data_seeding.mapping.models import PrimatisTitleRow


def title(index: int) -> PrimatisTitleRow:
    return PrimatisTitleRow(
        source_key=f"/books/OL{index}M",
        isbn=None,
        title=f"Titre {index}",
        subtitle=None,
        summary=None,
        publication_year=2020,
        language="FR",
        page_count=200,
        publisher=None,
        cover_image_url=None,
        title_status="ACTIVE",
    )


def titles(count: int) -> list[PrimatisTitleRow]:
    return [title(index) for index in range(1, count + 1)]


def test_profile_distribution_targets_are_consistent() -> None:
    assert PROFILE_COPY_DISTRIBUTIONS["small"].title_count == 100
    assert PROFILE_COPY_DISTRIBUTIONS["small"].copy_count == 160
    assert PROFILE_COPY_DISTRIBUTIONS["medium"].title_count == 1_000
    assert PROFILE_COPY_DISTRIBUTIONS["medium"].copy_count == 1_600
    assert PROFILE_COPY_DISTRIBUTIONS["large"].title_count == 5_000
    assert PROFILE_COPY_DISTRIBUTIONS["large"].copy_count == 8_000
    assert PROFILE_COPY_DISTRIBUTIONS["full"].title_count == 15_000
    assert PROFILE_COPY_DISTRIBUTIONS["full"].copy_count == 24_000


def test_full_profile_matches_frozen_distribution_exactly() -> None:
    result = generate_copies(titles(15_000), profile="full")

    assert len(result.copies) == 24_000
    assert result.titles_by_copy_count == {
        1: 9_000,
        2: 4_000,
        3: 1_500,
        5: 500,
    }


def test_small_profile_generates_expected_volume() -> None:
    result = generate_copies(titles(100), profile="small")

    assert len(result.copies) == 160
    assert result.titles_by_copy_count == {
        1: 59,
        2: 28,
        3: 10,
        5: 3,
    }


def test_all_base_catalogue_copies_are_good_and_available() -> None:
    result = generate_copies(titles(100), profile="small")

    assert {copy.copy_condition for copy in result.copies} == {"GOOD"}
    assert {copy.availability_status for copy in result.copies} == {"AVAILABLE"}


def test_no_business_state_is_faked_during_base_copy_generation() -> None:
    result = generate_copies(titles(100), profile="small")

    statuses = {copy.availability_status for copy in result.copies}

    assert "ON_LOAN" not in statuses
    assert "RESERVED" not in statuses
    assert "UNAVAILABLE" not in statuses


def test_inventory_codes_are_unique_and_fit_schema() -> None:
    result = generate_copies(titles(100), profile="small")
    codes = [copy.inventory_code for copy in result.copies]

    assert len(codes) == len(set(codes))
    assert all(len(code) <= 50 for code in codes)
    assert all(
        re.fullmatch(r"PRI-C-[0-9A-F]{16}-\d{2}", code)
        for code in codes
    )


def test_generation_is_independent_from_input_order() -> None:
    original = titles(100)
    reversed_input = list(reversed(original))

    first = generate_copies(original, profile="small")
    second = generate_copies(reversed_input, profile="small")

    assert first.copies == second.copies
    assert first.titles_by_copy_count == second.titles_by_copy_count


def test_inventory_code_is_stable_for_same_title_and_ordinal() -> None:
    first = generate_copies(titles(100), profile="small")
    second = generate_copies(titles(100), profile="small")

    assert [copy.inventory_code for copy in first.copies] == [
        copy.inventory_code for copy in second.copies
    ]


def test_rejects_wrong_title_count_for_profile() -> None:
    with pytest.raises(ValueError, match="Title count does not match Copy distribution"):
        generate_copies(titles(99), profile="small")


def test_rejects_duplicate_title_source_keys() -> None:
    duplicated = titles(99) + [title(1)]

    with pytest.raises(ValueError, match="Duplicate title source_key"):
        generate_copies(duplicated, profile="small")


def test_rejects_unknown_profile() -> None:
    with pytest.raises(ValueError, match="Unknown Copy generation profile"):
        generate_copies(titles(100), profile="unknown")
