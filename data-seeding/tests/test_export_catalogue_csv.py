import csv
from pathlib import Path

from primatis_data_seeding.export.catalogue_csv import export_catalogue_csv
from primatis_data_seeding.generation.copies import PrimatisCopyRow
from primatis_data_seeding.mapping.models import (
    CatalogueMappingResult,
    PrimatisAuthorRow,
    PrimatisGenreRow,
    PrimatisTitleAuthorRow,
    PrimatisTitleGenreRow,
    PrimatisTitleRow,
)


def test_exports_all_catalogue_files(tmp_path: Path) -> None:
    mapping = CatalogueMappingResult(
        authors=[PrimatisAuthorRow("/authors/A1", "Auteur", None, None)],
        genres=[PrimatisGenreRow("FICTION", "Fiction", "Description")],
        titles=[
            PrimatisTitleRow(
                "/books/B1",
                "9780306406157",
                "Titre",
                None,
                None,
                2020,
                "FR",
                200,
                "Éditeur",
                None,
            )
        ],
        title_authors=[PrimatisTitleAuthorRow("/books/B1", "/authors/A1")],
        title_genres=[PrimatisTitleGenreRow("/books/B1", "FICTION")],
    )
    copies = [
        PrimatisCopyRow(
            "/books/B1",
            "PRI-C-ABCDEF0123456789-01",
            None,
            "GOOD",
            "AVAILABLE",
        )
    ]

    paths = export_catalogue_csv(mapping, copies, tmp_path)

    assert all(
        path.is_file()
        for path in (
            paths.authors,
            paths.genres,
            paths.titles,
            paths.title_authors,
            paths.title_genres,
            paths.copies,
        )
    )


def test_export_is_utf8_and_preserves_accents(tmp_path: Path) -> None:
    mapping = CatalogueMappingResult(
        authors=[PrimatisAuthorRow("/authors/A1", "Émile Zola", None, None)]
    )

    paths = export_catalogue_csv(mapping, [], tmp_path)

    with paths.authors.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    assert rows[0]["full_name"] == "Émile Zola"
