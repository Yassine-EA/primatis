from primatis_data_seeding.deduplication.catalogue import deduplicate_editions
from primatis_data_seeding.models import NormalizedEdition


def edition(
    source_key: str,
    *,
    isbn: str | None = None,
    title: str = "Le livre",
    language: str = "FR",
    publication_year: int | None = 2020,
    page_count: int | None = 200,
    publisher: str | None = "Éditeur",
    subtitle: str | None = None,
    author_keys: tuple[str, ...] = ("/authors/A1",),
    work_key: str | None = "/works/W1",
) -> NormalizedEdition:
    return NormalizedEdition(
        source_key=source_key,
        work_key=work_key,
        title=title,
        subtitle=subtitle,
        isbn=isbn,
        language=language,
        publication_year=publication_year,
        page_count=page_count,
        publisher=publisher,
        author_keys=author_keys,
    )


def test_same_source_key_is_deduplicated() -> None:
    result = deduplicate_editions(
        [
            edition("/books/B1"),
            edition("/books/B1", publisher=None),
        ]
    )

    assert [item.source_key for item in result.kept] == ["/books/B1"]
    assert len(result.duplicates) == 1
    assert result.duplicates[0].reason == "SAME_SOURCE_KEY"


def test_same_valid_isbn_is_automatically_deduplicated() -> None:
    result = deduplicate_editions(
        [
            edition("/books/B1", isbn="9780306406157", publisher=None),
            edition("/books/B2", isbn="9780306406157"),
        ]
    )

    assert [item.source_key for item in result.kept] == ["/books/B2"]
    assert len(result.duplicates) == 1
    assert result.duplicates[0].kept_source_key == "/books/B2"
    assert result.duplicates[0].reason == "SAME_VALID_ISBN"


def test_same_isbn_with_conflicting_language_is_not_silently_merged() -> None:
    result = deduplicate_editions(
        [
            edition("/books/B1", isbn="9780306406157", language="FR"),
            edition("/books/B2", isbn="9780306406157", language="EN"),
        ]
    )

    assert result.kept == []
    assert result.duplicates == []
    assert len(result.conflicts) == 1
    assert result.conflicts[0].reason == "ISBN_METADATA_CONFLICT"


def test_same_isbn_with_materially_different_title_is_conflict() -> None:
    result = deduplicate_editions(
        [
            edition("/books/B1", isbn="9780306406157", title="Titre A"),
            edition("/books/B2", isbn="9780306406157", title="Titre B"),
        ]
    )

    assert result.kept == []
    assert len(result.conflicts) == 1


def test_same_metadata_without_isbn_is_candidate_but_not_merged() -> None:
    result = deduplicate_editions(
        [
            edition("/books/B1", isbn=None),
            edition("/books/B2", isbn=None),
        ]
    )

    assert [item.source_key for item in result.kept] == ["/books/B1", "/books/B2"]
    assert result.duplicates == []
    assert len(result.candidates) == 1
    assert result.candidates[0].reason == "EXACT_METADATA_CANDIDATE_NO_ISBN"


def test_title_similarity_alone_does_not_create_candidate_when_metadata_differs() -> None:
    result = deduplicate_editions(
        [
            edition("/books/B1", isbn=None, publication_year=1999),
            edition("/books/B2", isbn=None, publication_year=2020),
        ]
    )

    assert len(result.kept) == 2
    assert result.candidates == []


def test_folding_accents_and_case_only_affects_candidate_detection() -> None:
    result = deduplicate_editions(
        [
            edition("/books/B1", isbn=None, title="Été à Paris"),
            edition("/books/B2", isbn=None, title="ete a paris"),
        ]
    )

    assert len(result.kept) == 2
    assert len(result.candidates) == 1


def test_result_order_is_deterministic() -> None:
    result = deduplicate_editions(
        [
            edition("/books/B3", isbn=None),
            edition("/books/B1", isbn=None, title="Autre"),
            edition("/books/B2", isbn=None),
        ]
    )

    assert [item.source_key for item in result.kept] == [
        "/books/B1",
        "/books/B2",
        "/books/B3",
    ]
