from datetime import date

from primatis_data_seeding.normalization.openlibrary import (
    canonical_author_key,
    normalize_author_record,
    normalize_biography,
    normalize_edition_record,
    normalize_publication_year,
    normalize_summary,
    normalize_work_record,
)


def test_normalizes_supported_edition() -> None:
    record = {
        "key": "/books/OL123M",
        "title": "  Le livre ",
        "subtitle": " Sous-titre ",
        "languages": [{"key": "/languages/fre"}],
        "isbn_13": ["9780306406157"],
        "publish_date": "2007",
        "number_of_pages": 383,
        "publishers": [" Éditeur Exemple "],
        "authors": [{"key": "/authors/OL1A"}],
        "works": [{"key": "/works/OL1W"}],
    }

    edition = normalize_edition_record(record)

    assert edition is not None
    assert edition.title == "Le livre"
    assert edition.language == "FR"
    assert edition.isbn == "9780306406157"
    assert edition.publication_year == 2007
    assert edition.page_count == 383
    assert edition.publisher == "Éditeur Exemple"
    assert edition.author_keys == ("/authors/OL1A",)
    assert edition.work_key == "/works/OL1W"


def test_unsupported_language_rejects_normalization() -> None:
    record = {
        "key": "/books/OL123M",
        "title": "Book",
        "languages": [{"key": "/languages/jpn"}],
    }

    assert normalize_edition_record(record) is None


def test_ambiguous_publication_year_is_not_guessed() -> None:
    assert normalize_publication_year("1998-2001") is None


def test_author_year_only_does_not_fabricate_exact_date() -> None:
    author = normalize_author_record(
        {
            "key": "/authors/OL1A",
            "name": "Victor Exemple",
            "birth_date": "1835",
        }
    )

    assert author is not None
    assert author.birth_date is None


def test_author_record_uses_canonical_name_and_exact_dates() -> None:
    author = normalize_author_record(
        {
            "key": "/authors/OL1A",
            "name": "Victor Exemple",
            "birth_date": "26 February 1802",
            "death_date": "22 May 1885",
            "bio": "Écrivain et poète français.",
        }
    )

    assert author is not None
    assert author.full_name == "Victor Exemple"
    assert author.birth_date == date(1802, 2, 26)
    assert author.death_date == date(1885, 5, 22)
    assert author.biography == "Écrivain et poète français."


def test_author_record_without_bio_has_no_biography() -> None:
    author = normalize_author_record({"key": "/authors/OL1A", "name": "Victor Exemple"})

    assert author is not None
    assert author.biography is None


def test_author_record_name_containing_slash_is_never_split() -> None:
    author = normalize_author_record(
        {"key": "/authors/OL1A", "name": "Charlotte Brontë / Currer Bell"}
    )

    assert author is not None
    assert author.full_name == "Charlotte Brontë / Currer Bell"


def test_normalize_biography_accepts_plain_string() -> None:
    assert normalize_biography("  Notice biographique.  ") == "Notice biographique."


def test_normalize_biography_accepts_open_library_text_object() -> None:
    assert (
        normalize_biography({"type": "/type/text", "value": "Notice biographique."})
        == "Notice biographique."
    )


def test_normalize_biography_returns_none_for_missing_value() -> None:
    assert normalize_biography(None) is None
    assert normalize_biography({"type": "/type/text", "value": None}) is None


def test_normalize_summary_accepts_plain_string() -> None:
    assert normalize_summary("  Un résumé réel.  ") == "Un résumé réel."


def test_normalize_summary_accepts_open_library_text_object() -> None:
    assert (
        normalize_summary({"type": "/type/text", "value": "Un résumé réel."})
        == "Un résumé réel."
    )


def test_normalize_summary_returns_none_for_missing_value() -> None:
    assert normalize_summary(None) is None
    assert normalize_summary({"type": "/type/text", "value": None}) is None


def test_normalize_summary_does_not_rewrite_content() -> None:
    raw = "Texte   avec   espaces   multiples."
    # normalize_text collapses whitespace but never rewrites wording.
    assert normalize_summary(raw) == "Texte avec espaces multiples."


def test_normalize_work_record_extracts_description() -> None:
    assert (
        normalize_work_record({"key": "/works/OL1W", "description": "Un résumé réel."})
        == "Un résumé réel."
    )


def test_normalize_work_record_extracts_text_object_description() -> None:
    record = {
        "key": "/works/OL1W",
        "description": {"type": "/type/text", "value": "Un résumé réel."},
    }
    assert normalize_work_record(record) == "Un résumé réel."


def test_normalize_work_record_without_description_is_none() -> None:
    assert normalize_work_record({"key": "/works/OL1W"}) is None


def test_normalize_work_record_invalid_description_is_none() -> None:
    # A /type/text object missing its "value" key is unusable -> None.
    record = {"key": "/works/OL1W", "description": {"type": "/type/text"}}
    assert normalize_work_record(record) is None


def test_normalize_work_record_none_record_is_none() -> None:
    assert normalize_work_record(None) is None


def test_canonical_author_key_accepts_bare_search_api_form() -> None:
    assert canonical_author_key("OL1098039A") == "OL1098039A"


def test_canonical_author_key_strips_dump_prefix() -> None:
    assert canonical_author_key("/authors/OL1098039A") == "OL1098039A"


def test_canonical_author_key_both_forms_are_equal() -> None:
    assert canonical_author_key("OL1098039A") == canonical_author_key(
        "/authors/OL1098039A"
    )


def test_canonical_author_key_rejects_none() -> None:
    assert canonical_author_key(None) is None


def test_canonical_author_key_rejects_empty_string() -> None:
    assert canonical_author_key("") is None


def test_canonical_author_key_rejects_work_key() -> None:
    assert canonical_author_key("/works/OL1W") is None


def test_canonical_author_key_rejects_edition_key() -> None:
    assert canonical_author_key("/books/OL1M") is None


def test_canonical_author_key_rejects_arbitrary_string() -> None:
    assert canonical_author_key("foo") is None


def test_canonical_author_key_rejects_malformed_prefixed_value() -> None:
    assert canonical_author_key("/authors/foo") is None


def test_canonical_author_key_rejects_key_missing_trailing_letter() -> None:
    assert canonical_author_key("OL1098039") is None


def test_canonical_author_key_never_splits_on_slash_generically() -> None:
    # A value with a "/" that is NOT the literal "/authors/" prefix must
    # never be guessed at via a generic split.
    assert canonical_author_key("Charlotte Bronte/Currer Bell") is None


def test_canonical_author_key_rejects_non_string_type_gracefully() -> None:
    assert canonical_author_key(123) is None
