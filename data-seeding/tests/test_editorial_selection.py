import pytest

from primatis_data_seeding.reference.editorial_selection import (
    load_all_editorial_selections,
    load_editorial_selection,
)


def test_rejects_unsupported_language():
    with pytest.raises(ValueError):
        load_editorial_selection("PT")


def test_missing_file_returns_empty_tuple(tmp_path):
    result = load_editorial_selection("FR", catalogue_dir=tmp_path)
    assert result == ()


def test_loads_a_real_pilot_entry_per_supported_language():
    # These are the real, verified pilot entries committed under
    # data-seeding/reference/catalogue/ (DEV-16.3 §D/§F) — this test
    # exercises the actual shipped files, not a fixture copy.
    for language in ("FR", "EN", "NL", "DE", "ES", "IT", "LA"):
        entries = load_editorial_selection(language)
        assert len(entries) >= 1, f"expected at least one pilot entry for {language}"
        entry = entries[0]
        assert entry.author_key.startswith("OL")
        assert entry.author_key.endswith("A")
        assert entry.full_name
        assert entry.selection_rationale
        assert entry.verified_via.startswith("openlibrary.org")


def test_socle_is_visibly_plural_per_language_dev16_5():
    # DEV-16.5 §5: each language must have a socle "visiblement pluriel"
    # — not a single pilot author as sole cultural signal (DEV-16.3/16.4
    # state). Real expansion committed under reference/catalogue/*.toml.
    all_selections = load_all_editorial_selections()
    for language, entries in all_selections.items():
        assert len(entries) >= 4, (
            f"{language}: expected a visibly plural socle (>=4 authors), got {len(entries)}"
        )
        # No duplicate author across a single language's socle.
        assert len({e.author_key for e in entries}) == len(entries)


def test_socle_shows_period_diversity_for_most_languages():
    # Not every language MUST hit exactly 3 distinct periods (LA has no
    # "contemporary" era, DEV-16.5 §5 note in la.toml) — but the socle
    # as a whole must not be monolithic.
    all_selections = load_all_editorial_selections()
    for language, entries in all_selections.items():
        periods = {e.period for e in entries if e.period}
        assert len(periods) >= 2, f"{language}: expected period diversity, got {periods}"


def test_load_all_editorial_selections_covers_all_seven_languages():
    all_selections = load_all_editorial_selections()
    assert set(all_selections) == {"FR", "EN", "NL", "DE", "ES", "IT", "LA"}
    for language, entries in all_selections.items():
        assert len(entries) >= 1


def test_parses_custom_toml_fixture(tmp_path):
    (tmp_path / "fr.toml").write_text(
        '[[authors]]\n'
        'author_key = "OL1A"\n'
        'full_name = "Auteur Test"\n'
        'verified_at = "2026-01-01"\n'
        'verified_via = "openlibrary.org/search/authors.json?q=Auteur+Test"\n'
        'work_count_at_verification = 10\n'
        'selection_rationale = "Pour test."\n',
        encoding="utf-8",
    )
    result = load_editorial_selection("FR", catalogue_dir=tmp_path)
    assert len(result) == 1
    assert result[0].author_key == "OL1A"
    assert result[0].work_count_at_verification == 10


def test_missing_required_field_raises(tmp_path):
    (tmp_path / "fr.toml").write_text(
        '[[authors]]\n'
        'author_key = "OL1A"\n'
        'full_name = "Auteur Test"\n',
        encoding="utf-8",
    )
    with pytest.raises(ValueError):
        load_editorial_selection("FR", catalogue_dir=tmp_path)


def test_duplicate_author_key_raises(tmp_path):
    entry = (
        '[[authors]]\n'
        'author_key = "OL1A"\n'
        'full_name = "Auteur Test"\n'
        'verified_at = "2026-01-01"\n'
        'verified_via = "openlibrary.org/search/authors.json?q=x"\n'
        'selection_rationale = "Pour test."\n'
    )
    (tmp_path / "fr.toml").write_text(entry + "\n" + entry, encoding="utf-8")
    with pytest.raises(ValueError):
        load_editorial_selection("FR", catalogue_dir=tmp_path)
