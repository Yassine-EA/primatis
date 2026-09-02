from __future__ import annotations

from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Callable
from urllib.request import Request, urlopen

from primatis_data_seeding.acquisition.provenance import sha256_file


OPENLIBRARY_BASE_URL = "https://openlibrary.org"


def fetch_record(
    key: str,
    *,
    contact: str,
    timeout_seconds: int = 30,
    base_url: str = OPENLIBRARY_BASE_URL,
) -> dict:
    """Fetches exactly ONE Open Library record by its EXACT key
    (e.g. "/works/OL1W" or "/books/OL1M"). Never a search/name lookup."""
    if not contact or "@" not in contact:
        raise ValueError(
            "Open Library contact must be an email address for an identified User-Agent."
        )

    request = Request(
        f"{base_url}{key}.json",
        headers={
            "Accept": "application/json",
            "User-Agent": f"PRIMATIS-Data-Seeding/0.1 ({contact})",
        },
    )
    with urlopen(request, timeout=timeout_seconds) as response:
        payload = json.load(response)

    if not isinstance(payload, dict):
        raise ValueError(f"Unexpected Open Library record payload for {key}.")
    return payload


def _cache_filename(key: str) -> str:
    sanitized = key.strip("/").replace("/", "_")
    if not sanitized:
        raise ValueError(f"Invalid Open Library key: {key!r}.")
    return f"{sanitized}.json"


def cache_path(cache_dir: Path, key: str) -> Path:
    return cache_dir / _cache_filename(key)


def has_cached_record(cache_dir: Path, key: str) -> bool:
    return cache_path(cache_dir, key).is_file()


def load_cached_record(cache_dir: Path, key: str) -> dict:
    path = cache_path(cache_dir, key)
    if not path.is_file():
        raise ValueError(f"Cached Open Library record not found: {path}")
    record = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(record, dict):
        raise ValueError(f"Invalid cached Open Library record: {path}")
    return record


def save_cached_record(cache_dir: Path, key: str, record: dict) -> Path:
    path = cache_path(cache_dir, key)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(record, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
        encoding="utf-8",
    )
    return path


def acquire_records(
    keys: set[str],
    *,
    cache_dir: Path,
    contact: str,
    refresh: bool = False,
    fetcher: Callable[..., dict] = fetch_record,
) -> tuple[dict[str, dict], dict]:
    """Acquires exactly the given EXACT keys, one Open Library record each.

    A rerun without `refresh` reuses every already-cached key and performs
    NO network call at all when every key is already cached (idempotent).
    `refresh=True` forces a live re-fetch of every requested key.
    """
    records: dict[str, dict] = {}
    reused_keys: list[str] = []
    fetched_keys: list[str] = []

    for key in sorted(keys):
        if not refresh and has_cached_record(cache_dir, key):
            records[key] = load_cached_record(cache_dir, key)
            reused_keys.append(key)
            continue

        record = fetcher(key, contact=contact)
        save_cached_record(cache_dir, key, record)
        records[key] = record
        fetched_keys.append(key)

    manifest = {
        "source": "Open Library",
        "requested_keys": sorted(keys),
        "reused_keys": sorted(reused_keys),
        "fetched_keys": sorted(fetched_keys),
        "sha256": {
            key: sha256_file(cache_path(cache_dir, key)) for key in sorted(records)
        },
        "acquired_at": datetime.now(timezone.utc).isoformat(),
    }
    manifest_path = cache_dir / "manifest.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return records, manifest


def write_records_snapshot(records: dict[str, dict], path: Path) -> None:
    """Deterministic, key-sorted JSONL pin of acquired records — the input
    consumed by the bundle step, decoupled from the live cache_dir above."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for key in sorted(records):
            handle.write(
                json.dumps(records[key], ensure_ascii=False, sort_keys=True) + "\n"
            )


def load_records_snapshot(path: Path) -> dict[str, dict]:
    if not path.is_file():
        raise ValueError(f"Records snapshot not found: {path}")

    records: dict[str, dict] = {}
    with path.open("r", encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(
                    f"Invalid records snapshot at {path}:{line_number}."
                ) from exc
            if not isinstance(record, dict) or not isinstance(record.get("key"), str):
                raise ValueError(f"Invalid record at {path}:{line_number}.")

            key = record["key"]
            if key in records:
                raise ValueError(f"Duplicate key in records snapshot: {key}.")
            records[key] = record

    return records
