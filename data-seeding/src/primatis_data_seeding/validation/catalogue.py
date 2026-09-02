from primatis_data_seeding.models import NormalizedAuthor, NormalizedEdition
from primatis_data_seeding.normalization.isbn import is_valid_isbn
from primatis_data_seeding.validation.result import ValidationResult


_ALLOWED_LANGUAGES = {"FR", "EN", "NL", "DE", "ES", "IT", "LA"}


def validate_author(author: NormalizedAuthor) -> ValidationResult:
    result = ValidationResult()

    if not author.full_name.strip():
        result.add("AUTHOR_NAME_REQUIRED", "full_name", "Author full_name is required.")

    if len(author.full_name) > 255:
        result.add("AUTHOR_NAME_TOO_LONG", "full_name", "Author full_name exceeds 255 characters.")

    if (
        author.birth_date is not None
        and author.death_date is not None
        and author.death_date < author.birth_date
    ):
        result.add(
            "AUTHOR_DATE_ORDER_INVALID",
            "death_date",
            "Author death_date cannot be before birth_date.",
        )

    return result


def validate_edition(edition: NormalizedEdition) -> ValidationResult:
    result = ValidationResult()

    if not edition.title.strip():
        result.add("TITLE_REQUIRED", "title", "Title is required.")

    if len(edition.title) > 500:
        result.add("TITLE_TOO_LONG", "title", "Title exceeds 500 characters.")

    if edition.subtitle is not None and len(edition.subtitle) > 500:
        result.add("SUBTITLE_TOO_LONG", "subtitle", "Subtitle exceeds 500 characters.")

    if edition.publisher is not None and len(edition.publisher) > 255:
        result.add("PUBLISHER_TOO_LONG", "publisher", "Publisher exceeds 255 characters.")

    if edition.language not in _ALLOWED_LANGUAGES:
        result.add("LANGUAGE_UNSUPPORTED", "language", "Language is not supported by PRIMATIS.")

    if edition.page_count is not None and edition.page_count <= 0:
        result.add("PAGE_COUNT_INVALID", "page_count", "page_count must be strictly positive.")

    if edition.isbn is not None and not is_valid_isbn(edition.isbn):
        result.add("ISBN_INVALID", "isbn", "ISBN checksum or format is invalid.")

    if not edition.author_keys:
        result.add(
            "AUTHOR_REQUIRED",
            "author_keys",
            "A Title must resolve to at least one Author before loading.",
        )

    return result
