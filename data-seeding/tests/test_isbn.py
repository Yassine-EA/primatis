from primatis_data_seeding.normalization.isbn import (
    is_valid_isbn10,
    is_valid_isbn13,
    normalize_isbn,
    select_valid_isbn,
)


def test_normalizes_hyphens_and_spaces() -> None:
    assert normalize_isbn("978-0-306-40615-7") == "9780306406157"


def test_validates_known_isbn13() -> None:
    assert is_valid_isbn13("9780306406157")


def test_rejects_bad_isbn13_checksum() -> None:
    assert not is_valid_isbn13("9780306406158")


def test_validates_isbn10_with_x_check_digit() -> None:
    assert is_valid_isbn10("080442957X")


def test_selects_valid_isbn13_before_isbn10() -> None:
    assert select_valid_isbn(
        ["9780306406157"],
        ["080442957X"],
    ) == "9780306406157"


def test_invalid_isbn_is_treated_as_absent() -> None:
    assert select_valid_isbn(["9780306406158"], ["1234567890"]) is None
