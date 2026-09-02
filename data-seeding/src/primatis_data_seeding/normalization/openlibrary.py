import re
from datetime import date, datetime

from primatis_data_seeding.models import NormalizedAuthor, NormalizedEdition
from primatis_data_seeding.normalization.isbn import select_valid_isbn
from primatis_data_seeding.normalization.language import select_supported_language
from primatis_data_seeding.normalization.text import normalize_text, truncate_or_none


_YEAR_RE = re.compile(r"(?<!\d)(1[0-9]{3}|20[0-9]{2}|2100)(?!\d)")


def _extract_key(value: object) -> str | None:
    if isinstance(value, dict):
        key = value.get("key")
        return str(key) if key else None
    if isinstance(value, str):
        return value
    return None


def normalize_publication_year(value: object) -> int | None:
    text = normalize_text(value)
    if text is None:
        return None

    years = {int(match) for match in _YEAR_RE.findall(text)}
    if len(years) != 1:
        return None

    year = years.pop()
    current_year = datetime.now().year
    if year < 1000 or year > current_year + 1:
        return None
    return year


def normalize_page_count(value: object) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value if value > 0 else None
    if isinstance(value, str) and value.strip().isdigit():
        parsed = int(value.strip())
        return parsed if parsed > 0 else None
    return None


def normalize_exact_date(value: object) -> date | None:
    text = normalize_text(value)
    if text is None:
        return None

    # Intentionally conservative: only exact dates are accepted.
    for fmt in ("%Y-%m-%d", "%d %B %Y", "%B %d, %Y"):
        try:
            return datetime.strptime(text, fmt).date()
        except ValueError:
            continue
    return None


def normalize_biography(value: object) -> str | None:
    # Open Library represents `bio` either as a plain string or as a
    # `/type/text` object ({"type": "/type/text", "value": "..."}).
    if isinstance(value, dict):
        value = value.get("value")
    return normalize_text(value)


def normalize_author_record(record: dict[str, object]) -> NormalizedAuthor | None:
    source_key = normalize_text(record.get("key"))
    # The canonical `name` is used as-is: it is never split (e.g. on "/"),
    # since Open Library sometimes concatenates alternate name forms there.
    full_name = truncate_or_none(record.get("name"), 255)

    if source_key is None or full_name is None:
        return None

    return NormalizedAuthor(
        source_key=source_key,
        full_name=full_name,
        birth_date=normalize_exact_date(record.get("birth_date")),
        death_date=normalize_exact_date(record.get("death_date")),
        biography=normalize_biography(record.get("bio")),
    )


def normalize_edition_record(record: dict[str, object]) -> NormalizedEdition | None:
    source_key = normalize_text(record.get("key"))
    title = truncate_or_none(record.get("title"), 500)
    language = select_supported_language(record.get("languages"))

    if source_key is None or title is None or language is None:
        return None

    works = record.get("works")
    work_key = None
    if isinstance(works, list) and works:
        work_key = _extract_key(works[0])

    authors = record.get("authors")
    author_keys: list[str] = []
    if isinstance(authors, list):
        for raw_author in authors:
            key = _extract_key(raw_author)
            if key and key not in author_keys:
                author_keys.append(key)

    publishers = record.get("publishers")
    publisher = None
    if isinstance(publishers, list):
        for raw_publisher in publishers:
            publisher = truncate_or_none(raw_publisher, 255)
            if publisher is not None:
                break

    return NormalizedEdition(
        source_key=source_key,
        work_key=work_key,
        title=title,
        subtitle=truncate_or_none(record.get("subtitle"), 500),
        isbn=select_valid_isbn(record.get("isbn_13"), record.get("isbn_10")),
        language=language,
        publication_year=normalize_publication_year(record.get("publish_date")),
        page_count=normalize_page_count(record.get("number_of_pages")),
        publisher=publisher,
        author_keys=tuple(author_keys),
    )
