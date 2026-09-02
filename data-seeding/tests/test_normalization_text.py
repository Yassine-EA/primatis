from primatis_data_seeding.normalization.text import normalize_text


def test_normalize_text_preserves_accents_and_collapses_whitespace() -> None:
    assert normalize_text("  Bruxelles   Édition\n") == "Bruxelles Édition"


def test_normalize_text_returns_none_for_blank_value() -> None:
    assert normalize_text(" \t ") is None
