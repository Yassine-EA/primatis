from primatis_data_seeding.normalization.text_quality import (
    validate_text_quality,
)


def test_none_is_ok():
    result = validate_text_quality(None)
    assert result.verdict == "OK"
    assert result.issues == ()


def test_plain_ascii_is_ok():
    result = validate_text_quality("The Catcher in the Rye")
    assert result.verdict == "OK"


def test_valid_french_accents_are_ok():
    result = validate_text_quality("Café, été, à côté, Œuvre, garçon, hôtel")
    assert result.verdict == "OK"


def test_valid_unicode_apostrophe_is_ok():
    # A real U+2019 apostrophe (single codepoint) must never be confused
    # with the mojibake artifact "â€™" (three separate characters).
    result = validate_text_quality("L’Œuvre")
    assert result.verdict == "OK"


def test_valid_non_latin_script_is_ok():
    result = validate_text_quality("Владимир Набоков")
    assert result.verdict == "OK"

    result_cjk = validate_text_quality("紅樓夢")
    assert result_cjk.verdict == "OK"


def test_confirmed_mojibake_signature_quarantines_optional_field():
    # Real signature confirmed present in primatis_dev / RAW Open Library
    # payload (DEV-16.3 Étape A, edition OL12623748M, "Anne CarriÃ¨re").
    result = validate_text_quality("Anne CarriÃ¨re", blocking=False)
    assert result.verdict == "QUARANTINE"
    assert any(issue.code == "MOJIBAKE_SIGNATURE" for issue in result.issues)


def test_confirmed_mojibake_signature_rejects_blocking_field():
    result = validate_text_quality("AprÃ¨s sangatte", blocking=True)
    assert result.verdict == "REJECT"


def test_mojibake_precursor_pattern_always_carries_a_control_character():
    # By construction, "\u00c3"/"\u00c2" immediately followed by a C1 control
    # character (U+0080-U+009F) always ALSO trips CONTROL_CHARACTER
    # (a C1 control is itself a control character) - so this
    # combination always escalates through the high-precision path
    # (QUARANTINE), never relying on MOJIBAKE_SUSPECT alone.
    result = validate_text_quality("Prix \u00c2\x90special")
    assert result.verdict == "QUARANTINE"
    codes = {issue.code for issue in result.issues}
    assert "MOJIBAKE_SUSPECT" in codes
    assert "CONTROL_CHARACTER" in codes


def test_real_confirmed_case_zola_oeuvre():
    # Real value observed in primatis_dev (title.id=7293), confirmed
    # already present verbatim in the RAW Open Library Search API
    # payload before any PRIMATIS transformation.
    result = validate_text_quality("L Âuvre, Emile Zola", blocking=True)
    assert result.verdict == "REJECT"
    codes = {issue.code for issue in result.issues}
    assert "MOJIBAKE_SUSPECT" in codes
    assert "CONTROL_CHARACTER" in codes


def test_replacement_character_quarantines():
    result = validate_text_quality("corrupted � text")
    assert result.verdict == "QUARANTINE"
    assert any(issue.code == "REPLACEMENT_CHARACTER" for issue in result.issues)


def test_replacement_character_rejects_blocking():
    result = validate_text_quality("�", blocking=True)
    assert result.verdict == "REJECT"


def test_control_character_quarantines():
    result = validate_text_quality("Title with \x01 control char")
    assert result.verdict == "QUARANTINE"
    assert any(issue.code == "CONTROL_CHARACTER" for issue in result.issues)


def test_tab_and_newline_are_not_flagged_as_control_characters():
    # normalize_text() already collapses these; the validator must not
    # double-flag legitimate whitespace that reaches it unnormalized.
    result = validate_text_quality("Line one\nLine two\tEnd")
    assert result.verdict == "OK"


def test_html_residue_quarantines():
    result = validate_text_quality("A summary with <b>bold</b> markup")
    assert result.verdict == "QUARANTINE"
    assert any(issue.code == "HTML_RESIDUE" for issue in result.issues)


def test_html_entity_quarantines():
    result = validate_text_quality("Tom &amp; Jerry")
    assert result.verdict == "QUARANTINE"


def test_disguised_empty_quarantines():
    result = validate_text_quality("   .   ")
    assert result.verdict == "QUARANTINE"
    assert any(issue.code == "DISGUISED_EMPTY" for issue in result.issues)


def test_genuine_punctuation_title_is_not_disguised_empty():
    result = validate_text_quality("1984")
    assert result.verdict == "OK"


def test_pathological_unicode_space_is_warning_only():
    result = validate_text_quality("Word Word")
    assert result.verdict == "WARNING"
    assert any(issue.code == "PATHOLOGICAL_WHITESPACE" for issue in result.issues)


def test_non_nfc_unicode_is_warning_only():
    combining = "é"  # "é" as e + combining acute accent, not NFC
    result = validate_text_quality(combining)
    assert result.verdict == "WARNING"
    assert any(issue.code == "UNICODE_NOT_NFC" for issue in result.issues)


def test_real_confirmed_case_german_umlaut_mojibake():
    # Real value confirmed present in primatis_dev Large (title.id=9348):
    # "Lenz-ErzÃ¤hlungen..." — "Ã¤" (mis-decoded ä) was missing from the
    # DEV-16.3 signature list (French-only observations); added
    # DEV-16.4 §19 after this real post-APPLY audit finding.
    result = validate_text_quality("Lenz-ErzÃ¤hlungen in der deutschen Literatur")
    assert result.verdict == "QUARANTINE"
    assert any(issue.code == "MOJIBAKE_SIGNATURE" for issue in result.issues)


def test_legitimate_french_a_circumflex_is_not_mojibake():
    # "Â" alone (a real, valid French capital letter, e.g. in "Âge",
    # "Âme") must never be flagged — only exact corrupted byte sequences
    # or "Â" + an actual C1 control character are.
    result = validate_text_quality("Le nouveau Moyen Âge")
    assert result.verdict == "OK"
    result2 = validate_text_quality("L'Âge d'homme")
    assert result2.verdict == "OK"


def test_spanish_and_italian_mojibake_signatures():
    assert validate_text_quality("EspaÃ±a y AmÃ©rica").verdict == "QUARANTINE"
    assert validate_text_quality("CanciÃ³n de cuna").verdict == "QUARANTINE"
    assert validate_text_quality("PoesÃ­a completa").verdict == "QUARANTINE"
    assert validate_text_quality("CosÃ¬ fan tutte").verdict == "QUARANTINE"
