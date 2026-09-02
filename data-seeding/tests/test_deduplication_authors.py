from datetime import date

from primatis_data_seeding.deduplication.catalogue import deduplicate_authors
from primatis_data_seeding.models import NormalizedAuthor


def author(
    source_key: str,
    *,
    full_name: str = "Victor Exemple",
    birth_date: date | None = None,
    death_date: date | None = None,
) -> NormalizedAuthor:
    return NormalizedAuthor(
        source_key=source_key,
        full_name=full_name,
        birth_date=birth_date,
        death_date=death_date,
    )


def test_same_author_source_key_is_deduplicated() -> None:
    result = deduplicate_authors(
        [
            author("/authors/A1"),
            author("/authors/A1", birth_date=date(1900, 1, 1)),
        ]
    )

    assert len(result.kept) == 1
    assert result.kept[0].birth_date == date(1900, 1, 1)
    assert len(result.duplicates) == 1


def test_same_name_different_source_keys_is_not_automatically_merged() -> None:
    result = deduplicate_authors(
        [
            author("/authors/A1"),
            author("/authors/A2"),
        ]
    )

    assert len(result.kept) == 2
    assert result.duplicates == []
    assert len(result.candidates) == 1


def test_same_name_but_different_exact_birth_dates_is_not_candidate() -> None:
    result = deduplicate_authors(
        [
            author("/authors/A1", birth_date=date(1900, 1, 1)),
            author("/authors/A2", birth_date=date(1901, 1, 1)),
        ]
    )

    assert len(result.kept) == 2
    assert result.candidates == []


def test_accent_and_case_folding_only_surfaces_candidate() -> None:
    result = deduplicate_authors(
        [
            author("/authors/A1", full_name="Émile Zola"),
            author("/authors/A2", full_name="emile zola"),
        ]
    )

    assert len(result.kept) == 2
    assert len(result.candidates) == 1
