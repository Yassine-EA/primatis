from dataclasses import dataclass
from datetime import date


@dataclass(frozen=True)
class NormalizedAuthor:
    source_key: str
    full_name: str
    birth_date: date | None
    death_date: date | None
    biography: str | None = None


@dataclass(frozen=True)
class NormalizedEdition:
    source_key: str
    work_key: str | None
    title: str
    subtitle: str | None
    isbn: str | None
    language: str
    publication_year: int | None
    page_count: int | None
    publisher: str | None
    author_keys: tuple[str, ...]
    cover_id: int | None = None
    summary: str | None = None


@dataclass(frozen=True)
class NormalizedPostalLocality:
    postal_code: str
    locality: str
