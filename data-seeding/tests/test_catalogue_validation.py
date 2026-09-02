from primatis_data_seeding.models import NormalizedEdition
from primatis_data_seeding.validation.catalogue import validate_edition


def test_title_requires_author_before_loading() -> None:
    edition = NormalizedEdition(
        source_key="/books/OL1M",
        work_key="/works/OL1W",
        title="Titre",
        subtitle=None,
        isbn=None,
        language="FR",
        publication_year=2020,
        page_count=200,
        publisher=None,
        author_keys=(),
    )

    result = validate_edition(edition)

    assert not result.valid
    assert [issue.code for issue in result.issues] == ["AUTHOR_REQUIRED"]


def test_valid_normalized_edition_passes() -> None:
    edition = NormalizedEdition(
        source_key="/books/OL1M",
        work_key="/works/OL1W",
        title="Titre",
        subtitle=None,
        isbn="9780306406157",
        language="FR",
        publication_year=2020,
        page_count=200,
        publisher="Éditeur",
        author_keys=("/authors/OL1A",),
    )

    assert validate_edition(edition).valid
