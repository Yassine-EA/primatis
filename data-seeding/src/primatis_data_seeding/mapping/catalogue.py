from __future__ import annotations

from collections.abc import Mapping

from primatis_data_seeding.mapping.genres import GENRES, map_subjects_to_genre_codes
from primatis_data_seeding.mapping.models import (
    CatalogueMappingResult,
    MappingRejection,
    MappingWarning,
    PrimatisAuthorRow,
    PrimatisTitleAuthorRow,
    PrimatisTitleGenreRow,
    PrimatisTitleRow,
)
from primatis_data_seeding.models import NormalizedAuthor, NormalizedEdition


def map_catalogue(
    authors: list[NormalizedAuthor],
    editions: list[NormalizedEdition],
    *,
    subjects_by_work_key: Mapping[str, list[str] | tuple[str, ...]] | None = None,
) -> CatalogueMappingResult:
    result = CatalogueMappingResult()
    subjects_by_work_key = subjects_by_work_key or {}

    author_by_key = {author.source_key: author for author in authors}

    result.authors = [
        PrimatisAuthorRow(
            source_key=author.source_key,
            full_name=author.full_name,
            birth_date=author.birth_date,
            death_date=author.death_date,
            nationality=None,
            biography=None,
        )
        for author in sorted(authors, key=lambda item: item.source_key)
    ]

    # The complete controlled taxonomy is deterministic and bounded.
    result.genres = list(GENRES)

    for edition in sorted(editions, key=lambda item: item.source_key):
        missing_author_keys = tuple(
            sorted(
                key
                for key in edition.author_keys
                if key not in author_by_key
            )
        )

        if not edition.author_keys:
            result.rejections.append(
                MappingRejection(
                    source_key=edition.source_key,
                    code="TITLE_WITHOUT_AUTHOR",
                    message="A PRIMATIS Title requires at least one Author.",
                )
            )
            continue

        if missing_author_keys:
            result.rejections.append(
                MappingRejection(
                    source_key=edition.source_key,
                    code="UNRESOLVED_AUTHOR_REFERENCE",
                    message=(
                        "Open Library author reference(s) are missing from the "
                        f"validated author set: {', '.join(missing_author_keys)}"
                    ),
                )
            )
            continue

        result.titles.append(
            PrimatisTitleRow(
                source_key=edition.source_key,
                isbn=edition.isbn,
                title=edition.title,
                subtitle=edition.subtitle,
                summary=None,
                publication_year=edition.publication_year,
                language=edition.language,
                page_count=edition.page_count,
                publisher=edition.publisher,
                cover_image_url=None,
                title_status="ACTIVE",
            )
        )

        for author_key in sorted(set(edition.author_keys)):
            result.title_authors.append(
                PrimatisTitleAuthorRow(
                    title_source_key=edition.source_key,
                    author_source_key=author_key,
                )
            )

        subjects: list[str] | tuple[str, ...] = ()
        if edition.work_key is not None:
            subjects = subjects_by_work_key.get(edition.work_key, ())

        genre_codes = map_subjects_to_genre_codes(subjects)
        if not genre_codes:
            result.warnings.append(
                MappingWarning(
                    source_key=edition.source_key,
                    code="NO_GENRE_MATCH",
                    message=(
                        "No controlled PRIMATIS Genre matched the available "
                        "Open Library Work subjects."
                    ),
                )
            )

        for genre_code in genre_codes:
            result.title_genres.append(
                PrimatisTitleGenreRow(
                    title_source_key=edition.source_key,
                    genre_code=genre_code,
                )
            )

    result.titles.sort(key=lambda item: item.source_key)
    result.title_authors.sort(
        key=lambda item: (item.title_source_key, item.author_source_key)
    )
    result.title_genres.sort(
        key=lambda item: (item.title_source_key, item.genre_code)
    )
    result.rejections.sort(key=lambda item: (item.source_key, item.code))
    result.warnings.sort(key=lambda item: (item.source_key, item.code))

    return result
