from __future__ import annotations

from datetime import datetime, timezone
import gzip
import json
from pathlib import Path
from typing import IO

from primatis_data_seeding.acquisition.provenance import sha256_file
from primatis_data_seeding.normalization.openlibrary import canonical_author_key


# Format des dumps bulk Open Library : une ligne TSV par enregistrement
# `type\tkey\trevision\tlast_modified\tJSON`.
AUTHOR_DUMP_TYPE = "/type/author"


def _open_dump(path: Path) -> IO[str]:
    if path.suffix == ".gz":
        return gzip.open(path, mode="rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


def _manifest_path(snapshot_path: Path) -> Path:
    return snapshot_path.with_name(f"{snapshot_path.stem}_manifest.json")


def _canonicalize_keys(keys: set[str]) -> set[str]:
    # Silently drops values that are not a valid Open Library author_key
    # in either representation (bare or "/authors/"-prefixed); this is
    # exact-identity normalization, never a fuzzy filter.
    return {
        canonical
        for raw in keys
        if (canonical := canonical_author_key(raw)) is not None
    }


def extract_authors_by_key(
    dump_path: Path,
    required_keys: set[str],
) -> dict[str, dict]:
    """Un seul passage séquentiel du dump Authors Open Library.

    La correspondance se fait EXCLUSIVEMENT sur la colonne `key` du dump,
    comparée pour égalité stricte à `required_keys`, APRÈS normalisation
    canonique des deux côtés (`canonical_author_key`) : le Search API
    Open Library renvoie `author_key` sous forme nue ("OL1098039A"), le
    dump bulk sous forme préfixée ("/authors/OL1098039A") — les deux
    désignent le même Author et sont réconciliées ici, sans jamais
    rechercher par nom. Le résultat est indexé par la forme canonique
    (nue), qui devient la représentation interne unique du pipeline.

    Un author_key absent du dump reste simplement absent du résultat
    (ce n'est pas une erreur).
    """
    if not dump_path.is_file():
        raise ValueError(f"Open Library Authors dump not found: {dump_path}")

    matched: dict[str, dict] = {}
    canonical_required = _canonicalize_keys(required_keys)
    if not canonical_required:
        return matched

    remaining = set(canonical_required)
    with _open_dump(dump_path) as handle:
        for line in handle:
            if not remaining:
                break

            parts = line.rstrip("\n").split("\t", 4)
            if len(parts) != 5:
                continue

            record_type, raw_key, _revision, _last_modified, raw_json = parts
            if record_type != AUTHOR_DUMP_TYPE:
                continue

            canonical_key = canonical_author_key(raw_key)
            if canonical_key is None or canonical_key not in remaining:
                continue

            try:
                record = json.loads(raw_json)
            except json.JSONDecodeError:
                continue
            if not isinstance(record, dict):
                continue

            matched[canonical_key] = record
            remaining.discard(canonical_key)

    return matched


def write_authors_snapshot(
    records: dict[str, dict],
    *,
    snapshot_path: Path,
    dump_path: Path,
    required_keys: set[str],
) -> dict:
    snapshot_path.parent.mkdir(parents=True, exist_ok=True)

    # Ordre de clé déterministe : la reconstruction du snapshot à partir
    # du même dump et du même required_keys produit un fichier identique.
    with snapshot_path.open("w", encoding="utf-8") as handle:
        for key in sorted(records):
            handle.write(
                json.dumps(records[key], ensure_ascii=False, sort_keys=True) + "\n"
            )

    canonical_required = _canonicalize_keys(required_keys)
    manifest = {
        "source": "Open Library Authors dump",
        "source_file": str(dump_path),
        "source_sha256": sha256_file(dump_path),
        "extracted_at": datetime.now(timezone.utc).isoformat(),
        "requested_keys": sorted(canonical_required),
        "matched_keys": sorted(records),
        "missing_keys": sorted(canonical_required - records.keys()),
    }
    _manifest_path(snapshot_path).write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return manifest


def load_authors_snapshot(snapshot_path: Path) -> dict[str, dict]:
    """Charge le snapshot Authors, indexé par la clé CANONIQUE (nue) de
    chaque record — jamais par le champ `key` brut du record (qui reste
    sous sa forme préfixée d'origine "/authors/..." dans le JSON). C'est
    cette forme canonique que `pipeline/bundle.py` consulte pour
    l'enrichissement, avec le même `author_key` (nu) que le Search API.
    """
    if not snapshot_path.is_file():
        raise ValueError(f"Authors snapshot not found: {snapshot_path}")

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
                    f"Invalid Authors snapshot at {snapshot_path}:{line_number}."
                ) from exc
            if not isinstance(record, dict) or not isinstance(record.get("key"), str):
                raise ValueError(
                    f"Invalid Author record at {snapshot_path}:{line_number}."
                )

            key = canonical_author_key(record["key"])
            if key is None:
                raise ValueError(
                    f"Invalid Author key in snapshot at {snapshot_path}:{line_number}: "
                    f"{record['key']!r}."
                )
            if key in records:
                raise ValueError(f"Duplicate Author key in snapshot: {key}.")
            records[key] = record

    return records


def load_authors_manifest(snapshot_path: Path) -> dict:
    manifest_path = _manifest_path(snapshot_path)
    if not manifest_path.is_file():
        return {}
    loaded = json.loads(manifest_path.read_text(encoding="utf-8"))
    return loaded if isinstance(loaded, dict) else {}


def has_reusable_authors_snapshot(
    snapshot_path: Path,
    *,
    required_keys: set[str],
) -> bool:
    if not snapshot_path.is_file():
        return False
    manifest = load_authors_manifest(snapshot_path)
    return set(manifest.get("requested_keys", ())) == _canonicalize_keys(required_keys)


def acquire_authors_snapshot(
    *,
    dump_path: Path,
    required_keys: set[str],
    snapshot_path: Path,
    refresh: bool = False,
) -> dict:
    """Acquiert (ou réutilise) le snapshot Authors pour `required_keys`.

    Idempotent : tant que le dump source et le required_keys demandé sont
    inchangés, une réexécution ne relit pas le dump et ne modifie pas le
    snapshot déjà produit.
    """
    if not refresh and has_reusable_authors_snapshot(
        snapshot_path, required_keys=required_keys
    ):
        return load_authors_manifest(snapshot_path)

    matched = extract_authors_by_key(dump_path, required_keys)
    return write_authors_snapshot(
        matched,
        snapshot_path=snapshot_path,
        dump_path=dump_path,
        required_keys=required_keys,
    )
