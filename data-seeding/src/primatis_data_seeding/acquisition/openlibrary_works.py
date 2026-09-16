"""Extraction ciblée du dump bulk Open Library Works (DEV-16.5 §10).

Même principe exact que `acquisition/openlibrary_authors.py` pour le
dump Authors (déjà validé DEV-13/DEV-16.4) : un seul passage streaming
du dump `/type/work`, correspondance stricte par `key` exact — jamais
une recherche par titre. Contrairement aux clés Author, les clés Work
n'ont pas de forme "nue vs préfixée" divergente entre la Search API et
le dump : `candidate.work_key` ("/works/OL...W") et le champ `key` du
dump sont déjà dans le même format — aucune canonicalisation requise.

Résultat consommé par `pipeline/bundle.py::normalize_selected_catalogue(
work_records=...)` (paramètre déjà existant et déjà testé, DEV-13/16 —
seule l'ACQUISITION du dump était manquante avant DEV-16.5).
"""

from __future__ import annotations

from datetime import datetime, timezone
import gzip
import json
from pathlib import Path
from typing import IO

from primatis_data_seeding.acquisition.provenance import sha256_file

WORK_DUMP_TYPE = "/type/work"


def _open_dump(path: Path) -> IO[str]:
    if path.suffix == ".gz":
        return gzip.open(path, mode="rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


def _manifest_path(snapshot_path: Path) -> Path:
    return snapshot_path.with_name(f"{snapshot_path.stem}_manifest.json")


def extract_works_by_key(
    dump_path: Path,
    required_keys: set[str],
) -> dict[str, dict]:
    """Un seul passage séquentiel du dump Works Open Library.

    Correspondance EXCLUSIVEMENT sur la colonne `key`, égalité stricte à
    `required_keys` (déjà dans la forme "/works/OL...W", identique des
    deux côtés). Une clé absente du dump reste simplement absente du
    résultat (ce n'est pas une erreur).
    """
    if not dump_path.is_file():
        raise ValueError(f"Open Library Works dump not found: {dump_path}")

    matched: dict[str, dict] = {}
    if not required_keys:
        return matched

    remaining = set(required_keys)
    with _open_dump(dump_path) as handle:
        for line in handle:
            if not remaining:
                break

            parts = line.rstrip("\n").split("\t", 4)
            if len(parts) != 5:
                continue

            record_type, raw_key, _revision, _last_modified, raw_json = parts
            if record_type != WORK_DUMP_TYPE or raw_key not in remaining:
                continue

            try:
                record = json.loads(raw_json)
            except json.JSONDecodeError:
                continue
            if not isinstance(record, dict):
                continue

            matched[raw_key] = record
            remaining.discard(raw_key)

    return matched


def write_works_snapshot(
    records: dict[str, dict],
    *,
    snapshot_path: Path,
    dump_path: Path,
    required_keys: set[str],
) -> dict:
    snapshot_path.parent.mkdir(parents=True, exist_ok=True)

    with snapshot_path.open("w", encoding="utf-8") as handle:
        for key in sorted(records):
            handle.write(
                json.dumps(records[key], ensure_ascii=False, sort_keys=True) + "\n"
            )

    manifest = {
        "source": "Open Library Works dump",
        "source_file": str(dump_path),
        "source_sha256": sha256_file(dump_path),
        "extracted_at": datetime.now(timezone.utc).isoformat(),
        "requested_keys": sorted(required_keys),
        "matched_keys": sorted(records),
        "missing_keys": sorted(required_keys - records.keys()),
    }
    _manifest_path(snapshot_path).write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return manifest


def load_works_snapshot(snapshot_path: Path) -> dict[str, dict]:
    if not snapshot_path.is_file():
        raise ValueError(f"Works snapshot not found: {snapshot_path}")

    records: dict[str, dict] = {}
    with snapshot_path.open("r", encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(
                    f"Invalid Works snapshot at {snapshot_path}:{line_number}."
                ) from exc
            if not isinstance(record, dict) or not isinstance(record.get("key"), str):
                raise ValueError(
                    f"Invalid Work record at {snapshot_path}:{line_number}."
                )
            key = record["key"]
            if key in records:
                raise ValueError(f"Duplicate Work key in snapshot: {key}.")
            records[key] = record

    return records


def load_works_manifest(snapshot_path: Path) -> dict:
    manifest_path = _manifest_path(snapshot_path)
    if not manifest_path.is_file():
        return {}
    loaded = json.loads(manifest_path.read_text(encoding="utf-8"))
    return loaded if isinstance(loaded, dict) else {}


def has_reusable_works_snapshot(
    snapshot_path: Path,
    *,
    required_keys: set[str],
) -> bool:
    if not snapshot_path.is_file():
        return False
    manifest = load_works_manifest(snapshot_path)
    return set(manifest.get("requested_keys", ())) == set(required_keys)


def acquire_works_snapshot(
    *,
    dump_path: Path,
    required_keys: set[str],
    snapshot_path: Path,
    refresh: bool = False,
) -> dict:
    """Acquiert (ou réutilise) le snapshot Works pour `required_keys`.

    Idempotent : tant que le dump source et le required_keys demandé
    sont inchangés, une réexécution ne relit pas le dump (742 Mo à 11+
    Go selon le dump) et ne modifie pas le snapshot déjà produit.
    """
    if not refresh and has_reusable_works_snapshot(snapshot_path, required_keys=required_keys):
        return load_works_manifest(snapshot_path)

    matched = extract_works_by_key(dump_path, required_keys)
    return write_works_snapshot(
        matched, snapshot_path=snapshot_path, dump_path=dump_path, required_keys=required_keys,
    )
