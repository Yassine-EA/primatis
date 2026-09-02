from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

from primatis_data_seeding.generation.copies import PrimatisCopyRow
from primatis_data_seeding.mapping.models import CatalogueMappingResult


@dataclass(frozen=True)
class CatalogueExportPaths:
    root: Path
    authors: Path
    genres: Path
    titles: Path
    title_authors: Path
    title_genres: Path
    copies: Path


def _write_csv(path: Path, fieldnames: tuple[str, ...], rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def export_catalogue_csv(
    mapping: CatalogueMappingResult,
    copies: list[PrimatisCopyRow],
    output_dir: Path,
) -> CatalogueExportPaths:
    output_dir.mkdir(parents=True, exist_ok=True)

    paths = CatalogueExportPaths(
        root=output_dir,
        authors=output_dir / "authors.csv",
        genres=output_dir / "genres.csv",
        titles=output_dir / "titles.csv",
        title_authors=output_dir / "title_authors.csv",
        title_genres=output_dir / "title_genres.csv",
        copies=output_dir / "copies.csv",
    )

    _write_csv(
        paths.authors,
        ("source_key", "full_name", "birth_date", "death_date", "nationality", "biography"),
        [
            {
                "source_key": row.source_key,
                "full_name": row.full_name,
                "birth_date": row.birth_date.isoformat() if row.birth_date else "",
                "death_date": row.death_date.isoformat() if row.death_date else "",
                "nationality": row.nationality or "",
                "biography": row.biography or "",
            }
            for row in mapping.authors
        ],
    )

    _write_csv(
        paths.genres,
        ("code", "label", "description"),
        [
            {
                "code": row.code,
                "label": row.label,
                "description": row.description or "",
            }
            for row in mapping.genres
        ],
    )

    _write_csv(
        paths.titles,
        (
            "source_key",
            "isbn",
            "title",
            "subtitle",
            "summary",
            "publication_year",
            "language",
            "page_count",
            "publisher",
            "cover_image_url",
            "title_status",
        ),
        [
            {
                "source_key": row.source_key,
                "isbn": row.isbn or "",
                "title": row.title,
                "subtitle": row.subtitle or "",
                "summary": row.summary or "",
                "publication_year": row.publication_year or "",
                "language": row.language,
                "page_count": row.page_count or "",
                "publisher": row.publisher or "",
                "cover_image_url": row.cover_image_url or "",
                "title_status": row.title_status,
            }
            for row in mapping.titles
        ],
    )

    _write_csv(
        paths.title_authors,
        ("title_source_key", "author_source_key"),
        [
            {
                "title_source_key": row.title_source_key,
                "author_source_key": row.author_source_key,
            }
            for row in mapping.title_authors
        ],
    )

    _write_csv(
        paths.title_genres,
        ("title_source_key", "genre_code"),
        [
            {
                "title_source_key": row.title_source_key,
                "genre_code": row.genre_code,
            }
            for row in mapping.title_genres
        ],
    )

    _write_csv(
        paths.copies,
        (
            "title_source_key",
            "inventory_code",
            "location",
            "copy_condition",
            "availability_status",
        ),
        [
            {
                "title_source_key": row.title_source_key,
                "inventory_code": row.inventory_code,
                "location": row.location or "",
                "copy_condition": row.copy_condition,
                "availability_status": row.availability_status,
            }
            for row in copies
        ],
    )

    return paths
