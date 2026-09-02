from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil


@dataclass(frozen=True)
class SourceProvenance:
    source_name: str
    source_kind: str
    source_url: str
    acquired_at: str
    sha256: str
    local_file: str
    notes: str | None = None


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def archive_source_file(
    source: Path,
    destination_dir: Path,
    *,
    source_name: str,
    source_kind: str,
    source_url: str,
    notes: str | None = None,
) -> SourceProvenance:
    if not source.is_file():
        raise ValueError(f"Source file not found: {source}")

    destination_dir.mkdir(parents=True, exist_ok=True)
    digest = sha256_file(source)
    suffix = "".join(source.suffixes) or ".bin"
    archived = destination_dir / f"{digest}{suffix}"
    if not archived.exists():
        shutil.copy2(source, archived)

    provenance = SourceProvenance(
        source_name=source_name,
        source_kind=source_kind,
        source_url=source_url,
        acquired_at=datetime.now(timezone.utc).isoformat(),
        sha256=digest,
        local_file=str(archived),
        notes=notes,
    )
    manifest = destination_dir / "provenance.json"
    manifest.write_text(
        json.dumps(asdict(provenance), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return provenance
