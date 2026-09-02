import pytest

from primatis_data_seeding.normalization.language import normalize_language_code


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ({"key": "/languages/fre"}, "FR"),
        ({"key": "/languages/eng"}, "EN"),
        ({"key": "/languages/dut"}, "NL"),
        ({"key": "/languages/nld"}, "NL"),
        ({"key": "/languages/ger"}, "DE"),
        ({"key": "/languages/deu"}, "DE"),
        ({"key": "/languages/spa"}, "ES"),
        ({"key": "/languages/ita"}, "IT"),
        ({"key": "/languages/lat"}, "LA"),
    ],
)
def test_maps_open_library_language_codes(raw: object, expected: str) -> None:
    assert normalize_language_code(raw) == expected


def test_unknown_language_is_not_invented() -> None:
    assert normalize_language_code({"key": "/languages/jpn"}) is None
