from datetime import date

from primatis_data_seeding.mapping.catalogue import map_catalogue
from primatis_data_seeding.models import NormalizedAuthor, NormalizedEdition


def author(
    source_key: str = "/authors/A1",
    *,
    full_name: str = "Auteur Exemple",
    biography: str | None = None,
) -> NormalizedAuthor:
    return NormalizedAuthor(
        source_key=source_key,
        full_name=full_name,
        birth_date=date(1900, 1, 2),
        death_date=None,
        biography=biography,
    )


def edition(
    source_key: str = "/books/B1",
    *,
    work_key: str | None = "/works/W1",
    author_keys: tuple[str, ...] = ("/authors/A1",),
) -> NormalizedEdition:
    return NormalizedEdition(
        source_key=source_key,
        work_key=work_key,
        title="Le livre",
        subtitle="Sous-titre",
        isbn="9780306406157",
        language="FR",
        publication_year=2020,
        page_count=250,
        publisher="Éditeur",
        author_keys=author_keys,
    )


def test_maps_author_without_inventing_optional_metadata() -> None:
    result = map_catalogue([author()], [])

    mapped = result.authors[0]
    assert mapped.full_name == "Auteur Exemple"
    assert mapped.birth_date == date(1900, 1, 2)
    assert mapped.nationality is None
    assert mapped.biography is None


def test_maps_biography_from_normalized_author() -> None:
    result = map_catalogue(
        [author(biography="Notice biographique enrichie.")], []
    )

    assert result.authors[0].biography == "Notice biographique enrichie."


def test_nationality_is_always_null_even_when_author_is_enriched() -> None:
    result = map_catalogue(
        [author(biography="Notice biographique enrichie.")], []
    )

    assert result.authors[0].nationality is None


def test_maps_title_to_primatis_fields_and_active_status() -> None:
    result = map_catalogue([author()], [edition()])

    mapped = result.titles[0]
    assert mapped.isbn == "9780306406157"
    assert mapped.title == "Le livre"
    assert mapped.subtitle == "Sous-titre"
    assert mapped.summary is None
    assert mapped.publication_year == 2020
    assert mapped.language == "FR"
    assert mapped.page_count == 250
    assert mapped.publisher == "Éditeur"
    assert mapped.cover_image_url is None
    assert mapped.title_status == "ACTIVE"


def test_creates_title_author_association() -> None:
    result = map_catalogue([author()], [edition()])

    assert len(result.title_authors) == 1
    assert result.title_authors[0].title_source_key == "/books/B1"
    assert result.title_authors[0].author_source_key == "/authors/A1"


def test_rejects_title_with_unresolved_author_reference() -> None:
    result = map_catalogue(
        [author()],
        [edition(author_keys=("/authors/MISSING",))],
    )

    assert result.titles == []
    assert result.title_authors == []
    assert [item.code for item in result.rejections] == [
        "UNRESOLVED_AUTHOR_REFERENCE"
    ]


def test_rejects_title_without_author() -> None:
    result = map_catalogue(
        [author()],
        [edition(author_keys=())],
    )

    assert result.titles == []
    assert [item.code for item in result.rejections] == [
        "TITLE_WITHOUT_AUTHOR"
    ]


def test_genre_is_optional_when_no_subject_matches() -> None:
    result = map_catalogue(
        [author()],
        [edition()],
        subjects_by_work_key={"/works/W1": ["History of Belgium"]},
    )

    assert len(result.titles) == 1
    assert result.title_genres == []
    assert [item.code for item in result.warnings] == ["NO_GENRE_MATCH"]


def test_maps_work_subjects_to_title_genre_links() -> None:
    result = map_catalogue(
        [author()],
        [edition()],
        subjects_by_work_key={
            "/works/W1": ["History", "Political Science", "unknown subject"]
        },
    )

    assert [(link.title_source_key, link.genre_code) for link in result.title_genres] == [
        ("/books/B1", "HISTORY"),
        ("/books/B1", "POLITICS"),
    ]
    assert result.warnings == []


def test_mapping_is_deterministic() -> None:
    authors = [
        author("/authors/A2", full_name="B"),
        author("/authors/A1", full_name="A"),
    ]
    editions = [
        edition("/books/B2", author_keys=("/authors/A2",)),
        edition("/books/B1", author_keys=("/authors/A1",)),
    ]

    result = map_catalogue(authors, editions)

    assert [item.source_key for item in result.authors] == [
        "/authors/A1",
        "/authors/A2",
    ]
    assert [item.source_key for item in result.titles] == [
        "/books/B1",
        "/books/B2",
    ]


def test_all_titles_default_to_active_not_withdrawn() -> None:
    result = map_catalogue([author()], [edition()])

    assert {item.title_status for item in result.titles} == {"ACTIVE"}
