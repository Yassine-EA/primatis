import json

import pytest

from primatis_data_seeding.acquisition.openlibrary_works import (
    acquire_works_snapshot,
    extract_works_by_key,
    has_reusable_works_snapshot,
    load_works_manifest,
    load_works_snapshot,
    write_works_snapshot,
)


def _write_dump(path, rows):
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write("\t".join(row) + "\n")


def test_extract_works_by_key_matches_exact_keys_only(tmp_path):
    dump = tmp_path / "works.txt"
    _write_dump(dump, [
        ("/type/work", "/works/OL1W", "1", "2020-01-01T00:00:00Z",
         json.dumps({"key": "/works/OL1W", "description": "Un résumé réel."})),
        ("/type/work", "/works/OL2W", "1", "2020-01-01T00:00:00Z",
         json.dumps({"key": "/works/OL2W", "description": "Another summary."})),
        ("/type/author", "/authors/OL1A", "1", "2020-01-01T00:00:00Z",
         json.dumps({"key": "/authors/OL1A", "name": "Not a work"})),
    ])
    result = extract_works_by_key(dump, {"/works/OL1W"})
    assert set(result) == {"/works/OL1W"}
    assert result["/works/OL1W"]["description"] == "Un résumé réel."


def test_missing_key_is_not_an_error(tmp_path):
    dump = tmp_path / "works.txt"
    _write_dump(dump, [
        ("/type/work", "/works/OL1W", "1", "t", json.dumps({"key": "/works/OL1W"})),
    ])
    result = extract_works_by_key(dump, {"/works/OL1W", "/works/OL999W"})
    assert set(result) == {"/works/OL1W"}


def test_malformed_lines_are_skipped_without_raising(tmp_path):
    dump = tmp_path / "works.txt"
    dump.write_text(
        "not-enough-columns\n"
        "/type/work\t/works/OL1W\t1\tt\t{not valid json\n"
        "/type/work\t/works/OL2W\t1\tt\t" + json.dumps({"key": "/works/OL2W"}) + "\n",
        encoding="utf-8",
    )
    result = extract_works_by_key(dump, {"/works/OL1W", "/works/OL2W"})
    assert set(result) == {"/works/OL2W"}


def test_gzip_dump_supported(tmp_path):
    import gzip

    dump = tmp_path / "works.txt.gz"
    line = "\t".join((
        "/type/work", "/works/OL1W", "1", "t", json.dumps({"key": "/works/OL1W"}),
    )) + "\n"
    with gzip.open(dump, "wt", encoding="utf-8") as handle:
        handle.write(line)
    result = extract_works_by_key(dump, {"/works/OL1W"})
    assert set(result) == {"/works/OL1W"}


def test_write_and_load_snapshot_round_trip(tmp_path):
    dump = tmp_path / "works.txt"
    _write_dump(dump, [
        ("/type/work", "/works/OL1W", "1", "t", json.dumps({"key": "/works/OL1W", "description": "X"})),
    ])
    records = extract_works_by_key(dump, {"/works/OL1W"})
    snapshot_path = tmp_path / "snapshot.jsonl"
    manifest = write_works_snapshot(
        records, snapshot_path=snapshot_path, dump_path=dump, required_keys={"/works/OL1W"},
    )
    assert manifest["matched_keys"] == ["/works/OL1W"]
    assert manifest["missing_keys"] == []

    loaded = load_works_snapshot(snapshot_path)
    assert loaded == records


def test_duplicate_key_in_snapshot_raises(tmp_path):
    snapshot_path = tmp_path / "snapshot.jsonl"
    snapshot_path.write_text(
        json.dumps({"key": "/works/OL1W"}) + "\n" + json.dumps({"key": "/works/OL1W"}) + "\n",
        encoding="utf-8",
    )
    with pytest.raises(ValueError):
        load_works_snapshot(snapshot_path)


def test_acquire_works_snapshot_is_idempotent_without_refresh(tmp_path):
    dump = tmp_path / "works.txt"
    _write_dump(dump, [
        ("/type/work", "/works/OL1W", "1", "t", json.dumps({"key": "/works/OL1W", "description": "X"})),
    ])
    snapshot_path = tmp_path / "snapshot.jsonl"

    manifest1 = acquire_works_snapshot(
        dump_path=dump, required_keys={"/works/OL1W"}, snapshot_path=snapshot_path,
    )
    mtime1 = snapshot_path.stat().st_mtime_ns

    manifest2 = acquire_works_snapshot(
        dump_path=dump, required_keys={"/works/OL1W"}, snapshot_path=snapshot_path,
    )
    mtime2 = snapshot_path.stat().st_mtime_ns

    assert manifest1 == manifest2
    assert mtime1 == mtime2  # not rewritten -> proves the dump was not re-read


def test_acquire_works_snapshot_refresh_forces_rebuild(tmp_path):
    dump = tmp_path / "works.txt"
    _write_dump(dump, [
        ("/type/work", "/works/OL1W", "1", "t", json.dumps({"key": "/works/OL1W", "description": "X"})),
    ])
    snapshot_path = tmp_path / "snapshot.jsonl"
    acquire_works_snapshot(dump_path=dump, required_keys={"/works/OL1W"}, snapshot_path=snapshot_path)

    manifest = acquire_works_snapshot(
        dump_path=dump, required_keys={"/works/OL1W"}, snapshot_path=snapshot_path, refresh=True,
    )
    assert manifest["matched_keys"] == ["/works/OL1W"]


def test_has_reusable_works_snapshot_detects_changed_required_keys(tmp_path):
    dump = tmp_path / "works.txt"
    _write_dump(dump, [
        ("/type/work", "/works/OL1W", "1", "t", json.dumps({"key": "/works/OL1W"})),
    ])
    snapshot_path = tmp_path / "snapshot.jsonl"
    acquire_works_snapshot(dump_path=dump, required_keys={"/works/OL1W"}, snapshot_path=snapshot_path)

    assert has_reusable_works_snapshot(snapshot_path, required_keys={"/works/OL1W"}) is True
    assert has_reusable_works_snapshot(snapshot_path, required_keys={"/works/OL2W"}) is False


def test_empty_required_keys_returns_empty(tmp_path):
    dump = tmp_path / "works.txt"
    _write_dump(dump, [
        ("/type/work", "/works/OL1W", "1", "t", json.dumps({"key": "/works/OL1W"})),
    ])
    result = extract_works_by_key(dump, set())
    assert result == {}


def test_missing_dump_file_raises():
    from pathlib import Path

    with pytest.raises(ValueError):
        extract_works_by_key(Path("/nonexistent/works.txt"), {"/works/OL1W"})
