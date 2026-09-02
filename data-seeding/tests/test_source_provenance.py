import json
from pathlib import Path

from primatis_data_seeding.acquisition.provenance import (
    archive_source_file,
    sha256_file,
)


def test_archive_source_file_uses_content_hash(tmp_path: Path) -> None:
    source = tmp_path / "source.xlsx"
    source.write_bytes(b"official-reference")

    provenance = archive_source_file(
        source,
        tmp_path / "raw",
        source_name="Bpost",
        source_kind="Excel",
        source_url="https://example.invalid/source",
    )

    archived = Path(provenance.local_file)
    assert archived.is_file()
    assert archived.name.startswith(sha256_file(source))
    assert provenance.sha256 == sha256_file(source)


def test_archive_writes_provenance_manifest(tmp_path: Path) -> None:
    source = tmp_path / "source.xlsx"
    source.write_bytes(b"x")

    archive_source_file(
        source,
        tmp_path / "raw",
        source_name="Bpost",
        source_kind="Excel",
        source_url="https://example.invalid/source",
    )

    manifest = json.loads((tmp_path / "raw" / "provenance.json").read_text(encoding="utf-8"))
    assert manifest["source_name"] == "Bpost"
    assert len(manifest["sha256"]) == 64
