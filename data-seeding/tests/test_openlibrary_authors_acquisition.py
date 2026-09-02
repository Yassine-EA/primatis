import gzip
import json
from pathlib import Path

import pytest

from primatis_data_seeding.acquisition.openlibrary_authors import (
    acquire_authors_snapshot,
    extract_authors_by_key,
    has_reusable_authors_snapshot,
    load_authors_manifest,
    load_authors_snapshot,
    write_authors_snapshot,
)


def _dump_line(*, key: str, record: dict, record_type: str = "/type/author") -> str:
    return "\t".join(
        (record_type, key, "1", "2024-01-01T00:00:00.000000", json.dumps(record))
    )


def _write_dump(path: Path, lines: list[str]) -> None:
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def test_extracts_only_exact_author_key_matches(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [
            _dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "Victor Exemple"}),
            _dump_line(key="/authors/OL2A", record={"key": "/authors/OL2A", "name": "Autre Auteur"}),
        ],
    )

    matched = extract_authors_by_key(dump, {"/authors/OL1A"})

    assert set(matched) == {"OL1A"}
    assert matched["OL1A"]["name"] == "Victor Exemple"


def test_never_matches_by_name_only_by_exact_key(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [
            _dump_line(
                key="/authors/OL9A",
                record={"key": "/authors/OL9A", "name": "Victor Exemple"},
            ),
        ],
    )

    # Same name, but the required key does not match this record's key.
    matched = extract_authors_by_key(dump, {"/authors/OL1A"})

    assert matched == {}


def test_missing_required_keys_are_not_an_error(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [_dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "A"})],
    )

    matched = extract_authors_by_key(dump, {"/authors/OL1A", "/authors/OL404A"})

    assert set(matched) == {"OL1A"}


def test_non_author_dump_lines_are_ignored(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [
            _dump_line(
                key="/authors/OL1A",
                record={"key": "/authors/OL1A", "name": "Wrong type"},
                record_type="/type/work",
            ),
        ],
    )

    matched = extract_authors_by_key(dump, {"/authors/OL1A"})

    assert matched == {}


def test_malformed_lines_are_skipped_without_raising(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    lines = [
        "not-a-valid-tsv-line",
        _dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "A"}),
        "\t".join(("/type/author", "/authors/OL2A", "1", "2024-01-01", "{not json")),
    ]
    _write_dump(dump, lines)

    matched = extract_authors_by_key(dump, {"/authors/OL1A", "/authors/OL2A"})

    assert set(matched) == {"OL1A"}


def test_gzip_dump_is_supported(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt.gz"
    line = _dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "A"})
    with gzip.open(dump, "wt", encoding="utf-8") as handle:
        handle.write(line + "\n")

    matched = extract_authors_by_key(dump, {"/authors/OL1A"})

    assert set(matched) == {"OL1A"}


def test_missing_dump_file_raises(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        extract_authors_by_key(tmp_path / "missing.txt", {"/authors/OL1A"})


def test_snapshot_is_written_in_deterministic_sorted_order(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [
            _dump_line(key="/authors/OL2A", record={"key": "/authors/OL2A", "name": "B"}),
            _dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "A"}),
        ],
    )
    snapshot_path = tmp_path / "authors_selected.jsonl"
    required = {"/authors/OL1A", "/authors/OL2A"}

    matched = extract_authors_by_key(dump, required)
    manifest = write_authors_snapshot(
        matched, snapshot_path=snapshot_path, dump_path=dump, required_keys=required
    )

    lines = snapshot_path.read_text(encoding="utf-8").splitlines()
    # The snapshot preserves each record's own raw "key" field (the
    # dump's prefixed form) — only the pipeline-internal *index* into
    # matched/manifest is canonicalized (bare).
    keys_in_order = [json.loads(line)["key"] for line in lines]
    assert keys_in_order == ["/authors/OL1A", "/authors/OL2A"]
    assert manifest["matched_keys"] == ["OL1A", "OL2A"]
    assert manifest["missing_keys"] == []


def test_snapshot_reports_missing_keys_in_manifest(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [_dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "A"})],
    )
    snapshot_path = tmp_path / "authors_selected.jsonl"
    required = {"/authors/OL1A", "/authors/OL404A"}

    manifest = acquire_authors_snapshot(
        dump_path=dump,
        required_keys=required,
        snapshot_path=snapshot_path,
    )

    assert manifest["missing_keys"] == ["OL404A"]
    assert manifest["matched_keys"] == ["OL1A"]


def test_rebuilding_snapshot_twice_is_byte_identical(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [
            _dump_line(key="/authors/OL2A", record={"key": "/authors/OL2A", "name": "B"}),
            _dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "A"}),
        ],
    )
    snapshot_path = tmp_path / "authors_selected.jsonl"
    required = {"/authors/OL1A", "/authors/OL2A"}

    acquire_authors_snapshot(dump_path=dump, required_keys=required, snapshot_path=snapshot_path)
    first_bytes = snapshot_path.read_bytes()

    acquire_authors_snapshot(
        dump_path=dump, required_keys=required, snapshot_path=snapshot_path, refresh=True
    )
    second_bytes = snapshot_path.read_bytes()

    assert first_bytes == second_bytes


def test_reuse_is_idempotent_and_does_not_rescan_dump(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [_dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "A"})],
    )
    snapshot_path = tmp_path / "authors_selected.jsonl"
    required = {"/authors/OL1A"}

    acquire_authors_snapshot(dump_path=dump, required_keys=required, snapshot_path=snapshot_path)
    assert has_reusable_authors_snapshot(snapshot_path, required_keys=required) is True

    # The dump changes, but with the same required_keys the existing
    # snapshot must be reused as-is (idempotence), never rescanned.
    _write_dump(
        dump,
        [_dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "CHANGED"})],
    )
    manifest = acquire_authors_snapshot(
        dump_path=dump, required_keys=required, snapshot_path=snapshot_path
    )
    snapshot = load_authors_snapshot(snapshot_path)

    assert snapshot["OL1A"]["name"] == "A"
    assert manifest == load_authors_manifest(snapshot_path)


def test_changed_required_keys_forces_regeneration(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [
            _dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "A"}),
            _dump_line(key="/authors/OL2A", record={"key": "/authors/OL2A", "name": "B"}),
        ],
    )
    snapshot_path = tmp_path / "authors_selected.jsonl"

    acquire_authors_snapshot(
        dump_path=dump, required_keys={"/authors/OL1A"}, snapshot_path=snapshot_path
    )
    assert set(load_authors_snapshot(snapshot_path)) == {"OL1A"}

    acquire_authors_snapshot(
        dump_path=dump,
        required_keys={"/authors/OL1A", "/authors/OL2A"},
        snapshot_path=snapshot_path,
    )
    assert set(load_authors_snapshot(snapshot_path)) == {"OL1A", "OL2A"}


def test_load_snapshot_rejects_duplicate_keys(tmp_path: Path) -> None:
    snapshot_path = tmp_path / "authors_selected.jsonl"
    snapshot_path.write_text(
        json.dumps({"key": "/authors/OL1A", "name": "A"})
        + "\n"
        + json.dumps({"key": "/authors/OL1A", "name": "A duplicate"})
        + "\n",
        encoding="utf-8",
    )

    with pytest.raises(ValueError):
        load_authors_snapshot(snapshot_path)


def test_load_snapshot_detects_duplicates_across_key_representations(tmp_path: Path) -> None:
    # Bare and prefixed forms of the SAME Author identity must be treated
    # as one duplicate key, not two distinct entries.
    snapshot_path = tmp_path / "authors_selected.jsonl"
    snapshot_path.write_text(
        json.dumps({"key": "OL1A", "name": "A"})
        + "\n"
        + json.dumps({"key": "/authors/OL1A", "name": "A duplicate"})
        + "\n",
        encoding="utf-8",
    )

    with pytest.raises(ValueError):
        load_authors_snapshot(snapshot_path)


def test_load_snapshot_rejects_invalid_json_line(tmp_path: Path) -> None:
    snapshot_path = tmp_path / "authors_selected.jsonl"
    snapshot_path.write_text("not-json\n", encoding="utf-8")

    with pytest.raises(ValueError):
        load_authors_snapshot(snapshot_path)


def test_load_snapshot_rejects_invalid_author_key(tmp_path: Path) -> None:
    snapshot_path = tmp_path / "authors_selected.jsonl"
    snapshot_path.write_text(
        json.dumps({"key": "/works/OL1W", "name": "Not an author"}) + "\n",
        encoding="utf-8",
    )

    with pytest.raises(ValueError):
        load_authors_snapshot(snapshot_path)


def test_load_missing_snapshot_raises(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        load_authors_snapshot(tmp_path / "missing.jsonl")


def test_empty_required_keys_yields_empty_snapshot(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [_dump_line(key="/authors/OL1A", record={"key": "/authors/OL1A", "name": "A"})],
    )

    matched = extract_authors_by_key(dump, set())

    assert matched == {}


# --- DEV-13.19.F: real Search-API-vs-dump key format reconciliation ---


def test_bare_search_api_key_matches_prefixed_dump_key(tmp_path: Path) -> None:
    """Reproduces the exact real bug found in DEV-13.19.E: the Open
    Library Search API returns author_key bare ("OL1098039A"), while the
    Authors bulk dump uses the canonical prefixed key
    ("/authors/OL1098039A"). Both must resolve to the SAME Author via
    canonicalization — never via name matching, never via a "/" split.
    """
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [
            _dump_line(
                key="/authors/OL1098039A",
                record={
                    "key": "/authors/OL1098039A",
                    "name": "Carl Maria von Weber",
                    "bio": {
                        "type": "/type/text",
                        "value": "Deutscher Komponist, Dirigent und Pianist",
                    },
                },
            ),
        ],
    )

    # required_keys as they REALLY come out of the Search API: bare.
    matched = extract_authors_by_key(dump, {"OL1098039A"})

    assert len(matched) == 1
    assert set(matched) == {"OL1098039A"}
    assert matched["OL1098039A"]["name"] == "Carl Maria von Weber"
    assert (
        matched["OL1098039A"]["bio"]["value"]
        == "Deutscher Komponist, Dirigent und Pianist"
    )


def test_bare_search_api_key_end_to_end_snapshot_has_zero_missing(tmp_path: Path) -> None:
    dump = tmp_path / "authors_dump.txt"
    _write_dump(
        dump,
        [
            _dump_line(
                key="/authors/OL1098039A",
                record={"key": "/authors/OL1098039A", "name": "Carl Maria von Weber"},
            ),
        ],
    )
    snapshot_path = tmp_path / "authors_selected.jsonl"

    manifest = acquire_authors_snapshot(
        dump_path=dump,
        required_keys={"OL1098039A"},
        snapshot_path=snapshot_path,
    )

    assert manifest["requested_keys"] == ["OL1098039A"]
    assert manifest["matched_keys"] == ["OL1098039A"]
    assert manifest["missing_keys"] == []

    snapshot = load_authors_snapshot(snapshot_path)
    assert snapshot["OL1098039A"]["name"] == "Carl Maria von Weber"
