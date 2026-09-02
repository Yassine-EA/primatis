from primatis_data_seeding.normalization.bpost import normalize_postal_locality
from primatis_data_seeding.validation.postal import validate_postal_locality


def test_normalizes_belgian_postal_locality() -> None:
    value = normalize_postal_locality(" 6000 ", "  Charleroi ")

    assert value is not None
    assert value.postal_code == "6000"
    assert value.locality == "Charleroi"
    assert validate_postal_locality(value).valid


def test_rejects_invalid_postal_code() -> None:
    assert normalize_postal_locality("600", "Charleroi") is None
