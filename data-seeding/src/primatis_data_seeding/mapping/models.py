from dataclasses import dataclass, field
from datetime import date


@dataclass(frozen=True)
class PrimatisAuthorRow:
    source_key: str
    full_name: str
    birth_date: date | None
    death_date: date | None
    nationality: str | None = None
    biography: str | None = None


@dataclass(frozen=True)
class PrimatisGenreRow:
    code: str
    label: str
    description: str | None = None


@dataclass(frozen=True)
class PrimatisTitleRow:
    source_key: str
    isbn: str | None
    title: str
    subtitle: str | None
    summary: str | None
    publication_year: int | None
    language: str
    page_count: int | None
    publisher: str | None
    cover_image_url: str | None
    title_status: str = "ACTIVE"


@dataclass(frozen=True)
class PrimatisTitleAuthorRow:
    title_source_key: str
    author_source_key: str


@dataclass(frozen=True)
class PrimatisTitleGenreRow:
    title_source_key: str
    genre_code: str


@dataclass(frozen=True)
class MappingRejection:
    source_key: str
    code: str
    message: str


@dataclass(frozen=True)
class MappingWarning:
    source_key: str
    code: str
    message: str


@dataclass
class CatalogueMappingResult:
    authors: list[PrimatisAuthorRow] = field(default_factory=list)
    genres: list[PrimatisGenreRow] = field(default_factory=list)
    titles: list[PrimatisTitleRow] = field(default_factory=list)
    title_authors: list[PrimatisTitleAuthorRow] = field(default_factory=list)
    title_genres: list[PrimatisTitleGenreRow] = field(default_factory=list)
    rejections: list[MappingRejection] = field(default_factory=list)
    warnings: list[MappingWarning] = field(default_factory=list)
