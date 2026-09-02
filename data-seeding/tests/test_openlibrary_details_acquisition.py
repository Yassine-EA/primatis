import json
from pathlib import Path

import pytest

from primatis_data_seeding.acquisition.openlibrary_details import (
    acquire_records,
    cache_path,
    has_cached_record,
    load_cached_record,
    load_records_snapshot,
    save_cached_record,
    write_records_snapshot,
)


def test_cache_path_sanitizes_key(tmp_path: Path) -> None:
    assert cache_path(tmp_path, "/works/OL1W") == tmp_path / "works_OL1W.json"


def test_acquire_records_fetches_exact_keys_only(tmp_path: Path) -> None:
    calls: list[str] = []

    def fetcher(key: str, *, contact: str) -> dict:
        calls.append(key)
        return {"key": key, "description": f"desc for {key}"}

    records, manifest = acquire_records(
        {"/works/OL1W", "/works/OL2W"},
        cache_dir=tmp_path,
        contact="dev@example.com",
        fetcher=fetcher,
    )

    assert sorted(calls) == ["/works/OL1W", "/works/OL2W"]
    assert records["/works/OL1W"]["description"] == "desc for /works/OL1W"
    assert manifest["fetched_keys"] == ["/works/OL1W", "/works/OL2W"]
    assert manifest["reused_keys"] == []
    assert set(manifest["sha256"]) == {"/works/OL1W", "/works/OL2W"}


def test_acquire_records_reuses_cache_without_network_when_unchanged(
    tmp_path: Path,
) -> None:
    calls: list[str] = []

    def fetcher(key: str, *, contact: str) -> dict:
        calls.append(key)
        return {"key": key, "description": "first"}

    acquire_records(
        {"/works/OL1W"}, cache_dir=tmp_path, contact="dev@example.com", fetcher=fetcher
    )
    assert calls == ["/works/OL1W"]

    def fetcher_should_not_be_called(key: str, *, contact: str) -> dict:
        raise AssertionError("network fetch must not happen on reuse")

    records, manifest = acquire_records(
        {"/works/OL1W"},
        cache_dir=tmp_path,
        contact="dev@example.com",
        fetcher=fetcher_should_not_be_called,
    )

    assert records["/works/OL1W"]["description"] == "first"
    assert manifest["reused_keys"] == ["/works/OL1W"]
    assert manifest["fetched_keys"] == []


def test_acquire_records_refresh_forces_refetch(tmp_path: Path) -> None:
    call_count = {"n": 0}

    def fetcher(key: str, *, contact: str) -> dict:
        call_count["n"] += 1
        return {"key": key, "description": f"version-{call_count['n']}"}

    acquire_records(
        {"/works/OL1W"}, cache_dir=tmp_path, contact="dev@example.com", fetcher=fetcher
    )
    records, manifest = acquire_records(
        {"/works/OL1W"},
        cache_dir=tmp_path,
        contact="dev@example.com",
        fetcher=fetcher,
        refresh=True,
    )

    assert records["/works/OL1W"]["description"] == "version-2"
    assert manifest["fetched_keys"] == ["/works/OL1W"]
    assert manifest["reused_keys"] == []


def test_acquire_records_manifest_sha256_matches_cached_file(tmp_path: Path) -> None:
    def fetcher(key: str, *, contact: str) -> dict:
        return {"key": key}

    records, manifest = acquire_records(
        {"/works/OL1W"}, cache_dir=tmp_path, contact="dev@example.com", fetcher=fetcher
    )

    import hashlib

    expected = hashlib.sha256(
        cache_path(tmp_path, "/works/OL1W").read_bytes()
    ).hexdigest()
    assert manifest["sha256"]["/works/OL1W"] == expected


def test_load_cached_record_missing_raises(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        load_cached_record(tmp_path, "/works/OL1W")


def test_has_cached_record_reflects_disk_state(tmp_path: Path) -> None:
    assert has_cached_record(tmp_path, "/works/OL1W") is False
    save_cached_record(tmp_path, "/works/OL1W", {"key": "/works/OL1W"})
    assert has_cached_record(tmp_path, "/works/OL1W") is True


def test_records_snapshot_round_trip_is_deterministic(tmp_path: Path) -> None:
    records = {
        "/works/OL2W": {"key": "/works/OL2W", "description": "B"},
        "/works/OL1W": {"key": "/works/OL1W", "description": "A"},
    }
    path = tmp_path / "works_selected.jsonl"

    write_records_snapshot(records, path)
    lines = path.read_text(encoding="utf-8").splitlines()
    keys_in_order = [json.loads(line)["key"] for line in lines]
    assert keys_in_order == ["/works/OL1W", "/works/OL2W"]

    loaded = load_records_snapshot(path)
    assert loaded == records


def test_records_snapshot_rewrite_is_byte_identical(tmp_path: Path) -> None:
    records = {"/works/OL1W": {"key": "/works/OL1W", "description": "A"}}
    path = tmp_path / "works_selected.jsonl"

    write_records_snapshot(records, path)
    first = path.read_bytes()
    write_records_snapshot(records, path)
    second = path.read_bytes()

    assert first == second


def test_load_records_snapshot_rejects_duplicate_keys(tmp_path: Path) -> None:
    path = tmp_path / "works_selected.jsonl"
    path.write_text(
        json.dumps({"key": "/works/OL1W"}) + "\n" + json.dumps({"key": "/works/OL1W"}) + "\n",
        encoding="utf-8",
    )

    with pytest.raises(ValueError):
        load_records_snapshot(path)


def test_load_records_snapshot_missing_file_raises(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        load_records_snapshot(tmp_path / "missing.jsonl")
