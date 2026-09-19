import pytest

from primatis_data_seeding.generation.text_cleanup import clean_text


@pytest.mark.parametrize("raw, expected", [
    ("Maus: a Survivor\\'s Tale", "Maus: a Survivor's Tale"),
    ('edition of \\"Technical Analysis\\"', 'edition of "Technical Analysis"'),
    ("Jeff  Kinney", "Jeff Kinney"),
    ("DAHL   ROALD", "DAHL ROALD"),
    ("  Thomas  Piketty ", "Thomas Piketty"),
    ("Bastien Piano Basics -  Performance (Level 1)", "Bastien Piano Basics - Performance (Level 1)"),
])
def test_clean_text_rules(raw, expected) -> None:
    assert clean_text(raw) == expected
    assert clean_text(clean_text(raw)) == expected  # idempotence


@pytest.mark.parametrize("untouched", [
    "Bryan Lee O'Malley", "L'Âme d'une pieuvre", "DAHL ROALD", "Francisca ... [et al.] Castro Viudez",
])
def test_clean_text_does_not_touch_clean_or_semantic_content(untouched) -> None:
    assert clean_text(untouched) == untouched


def test_clean_text_never_reorders_or_corrects_names() -> None:
    assert clean_text("Pasekoff Norma   Weinberg") == "Pasekoff Norma Weinberg"
