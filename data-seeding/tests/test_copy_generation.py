import re

import pytest

from primatis_data_seeding.generation.copies import (
    LOCATION_AISLES_PER_LEVEL,
    LOCATION_COPIES_PER_SHELF,
    LOCATION_SHELVES_PER_AISLE,
    PROFILE_COPY_DISTRIBUTIONS,
    generate_copies,
    location_for_copy,
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


_LOCATION_RE = re.compile(r"^(RDC|ET\d+)-A\d{2}-E\d{2}$")


def test_location_for_copy_matches_expected_format() -> None:
    assert location_for_copy(0) == "RDC-A01-E01"
    assert _LOCATION_RE.fullmatch(location_for_copy(0))
    assert _LOCATION_RE.fullmatch(location_for_copy(23_999))


def test_location_for_copy_is_deterministic() -> None:
    assert location_for_copy(4321) == location_for_copy(4321)
    assert [location_for_copy(i) for i in range(50)] == [
        location_for_copy(i) for i in range(50)
    ]


def test_location_for_copy_rejects_negative_index() -> None:
    with pytest.raises(ValueError):
        location_for_copy(-1)


def test_location_for_copy_shelf_boundary() -> None:
    last_of_shelf = LOCATION_COPIES_PER_SHELF - 1
    first_of_next_shelf = LOCATION_COPIES_PER_SHELF

    assert location_for_copy(last_of_shelf) == "RDC-A01-E01"
    assert location_for_copy(first_of_next_shelf) == "RDC-A01-E02"


def test_location_for_copy_aisle_boundary() -> None:
    shelf_capacity = LOCATION_SHELVES_PER_AISLE * LOCATION_COPIES_PER_SHELF
    last_of_aisle = shelf_capacity - 1
    first_of_next_aisle = shelf_capacity

    assert location_for_copy(last_of_aisle) == "RDC-A01-E10"
    assert location_for_copy(first_of_next_aisle) == "RDC-A02-E01"


def test_location_for_copy_level_boundary() -> None:
    level_capacity = (
        LOCATION_AISLES_PER_LEVEL * LOCATION_SHELVES_PER_AISLE * LOCATION_COPIES_PER_SHELF
    )
    last_of_level = level_capacity - 1
    first_of_next_level = level_capacity

    assert location_for_copy(last_of_level) == "RDC-A20-E10"
    assert location_for_copy(first_of_next_level) == "ET1-A01-E01"


def test_location_for_copy_second_level_up_boundary() -> None:
    level_capacity = (
        LOCATION_AISLES_PER_LEVEL * LOCATION_SHELVES_PER_AISLE * LOCATION_COPIES_PER_SHELF
    )
    assert location_for_copy(2 * level_capacity) == "ET2-A01-E01"


def test_location_for_copy_supports_at_least_24000_without_error() -> None:
    # Capacity must not be artificially limited to a few hundred exemplars.
    for index in (0, 12_000, 23_999, 24_000, 100_000):
        location_for_copy(index)


def test_location_for_copy_shares_the_same_shelf_by_design() -> None:
    # Explicit, tested decision (DEV-13.19.D §9): several Copies legitimately
    # share one shelf — location is not required to be unique per Copy.
    shelf_indices = range(LOCATION_COPIES_PER_SHELF)
    locations = {location_for_copy(i) for i in shelf_indices}

    assert locations == {"RDC-A01-E01"}


def test_location_for_copy_fits_varchar_255() -> None:
    assert len(location_for_copy(24_000_000)) <= 255


def test_generated_copies_have_non_null_valid_location() -> None:
    result = generate_copies(titles(100), profile="small")

    assert len(result.copies) == 160
    for copy in result.copies:
        assert copy.location is not None
        assert copy.location != ""
        assert _LOCATION_RE.fullmatch(copy.location)
        assert len(copy.location) <= 255


def test_medium_profile_copies_all_have_valid_location() -> None:
    result = generate_copies(titles(1_000), profile="medium")

    assert len(result.copies) == 1_600
    for copy in result.copies:
        assert copy.location is not None
        assert _LOCATION_RE.fullmatch(copy.location)


def test_locations_are_stable_across_repeated_generation() -> None:
    first = generate_copies(titles(100), profile="small")
    second = generate_copies(titles(100), profile="small")

    assert [copy.location for copy in first.copies] == [
        copy.location for copy in second.copies
    ]


def test_first_copy_location_is_identical_across_profile_sizes() -> None:
    # location_for_copy() depends only on the global index, never on the
    # profile's total Copy count — the very first generated Copy of small
    # and of medium therefore share the same location.
    small = generate_copies(titles(100), profile="small")
    medium = generate_copies(titles(1_000), profile="medium")

    assert small.copies[0].location == medium.copies[0].location == "RDC-A01-E01"
