from primatis_data_seeding.normalization.openlibrary import (
    normalize_author_record,
    normalize_edition_record,
    normalize_publication_year,
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
