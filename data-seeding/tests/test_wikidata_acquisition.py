import hashlib
import json
from pathlib import Path

import pytest

from primatis_data_seeding.acquisition.wikidata import (
    acquire_entities,
    cache_path,
    fetch_entity,
    has_cached_entity,
    load_cached_entity,
    load_entities_snapshot,
    save_cached_entity,
    write_entities_snapshot,
)


def test_cache_path_uses_qid_as_filename(tmp_path: Path) -> None:
    assert cache_path(tmp_path, "Q142") == tmp_path / "Q142.json"


def test_fetch_entity_rejects_invalid_qid() -> None:
    with pytest.raises(ValueError):
        fetch_entity("142", contact="dev@example.com")
    with pytest.raises(ValueError):
        fetch_entity("Qabc", contact="dev@example.com")
    with pytest.raises(ValueError):
        fetch_entity("", contact="dev@example.com")


def test_fetch_entity_rejects_missing_contact() -> None:
    with pytest.raises(ValueError):
        fetch_entity("Q142", contact="")
    with pytest.raises(ValueError):
        fetch_entity("Q142", contact="not-an-email")


def test_acquire_entities_fetches_exact_qids_only(tmp_path: Path) -> None:
    calls: list[str] = []

    def fetcher(qid: str, *, contact: str) -> dict:
        calls.append(qid)
        return {"id": qid, "labels": {"en": {"language": "en", "value": qid}}}

    entities, manifest = acquire_entities(
        {"Q142", "Q30"},
        cache_dir=tmp_path,
        contact="dev@example.com",
        fetcher=fetcher,
    )

    assert sorted(calls) == ["Q142", "Q30"]
    assert entities["Q142"]["id"] == "Q142"
    assert manifest["fetched_qids"] == ["Q142", "Q30"]
    assert manifest["reused_qids"] == []
    assert set(manifest["sha256"]) == {"Q142", "Q30"}


def test_acquire_entities_reuses_cache_without_network_when_unchanged(
    tmp_path: Path,
) -> None:
    calls: list[str] = []

    def fetcher(qid: str, *, contact: str) -> dict:
        calls.append(qid)
        return {"id": qid, "labels": {"en": {"language": "en", "value": "first"}}}

    acquire_entities(
        {"Q142"}, cache_dir=tmp_path, contact="dev@example.com", fetcher=fetcher
    )
    assert calls == ["Q142"]

    def fetcher_should_not_be_called(qid: str, *, contact: str) -> dict:
        raise AssertionError("network fetch must not happen on reuse")

    entities, manifest = acquire_entities(
        {"Q142"},
        cache_dir=tmp_path,
        contact="dev@example.com",
        fetcher=fetcher_should_not_be_called,
    )

    assert entities["Q142"]["labels"]["en"]["value"] == "first"
    assert manifest["reused_qids"] == ["Q142"]
    assert manifest["fetched_qids"] == []


def test_acquire_entities_refresh_forces_refetch(tmp_path: Path) -> None:
    call_count = {"n": 0}

    def fetcher(qid: str, *, contact: str) -> dict:
        call_count["n"] += 1
        return {"id": qid, "revision": call_count["n"]}

    acquire_entities(
        {"Q142"}, cache_dir=tmp_path, contact="dev@example.com", fetcher=fetcher
    )
    entities, manifest = acquire_entities(
        {"Q142"},
        cache_dir=tmp_path,
        contact="dev@example.com",
        fetcher=fetcher,
        refresh=True,
    )

    assert entities["Q142"]["revision"] == 2
    assert manifest["fetched_qids"] == ["Q142"]
    assert manifest["reused_qids"] == []


def test_acquire_entities_manifest_sha256_matches_cached_file(tmp_path: Path) -> None:
    def fetcher(qid: str, *, contact: str) -> dict:
        return {"id": qid}

    entities, manifest = acquire_entities(
        {"Q142"}, cache_dir=tmp_path, contact="dev@example.com", fetcher=fetcher
    )

    expected = hashlib.sha256(cache_path(tmp_path, "Q142").read_bytes()).hexdigest()
    assert manifest["sha256"]["Q142"] == expected


def test_load_cached_entity_missing_raises(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        load_cached_entity(tmp_path, "Q142")


def test_has_cached_entity_reflects_disk_state(tmp_path: Path) -> None:
    assert has_cached_entity(tmp_path, "Q142") is False
    save_cached_entity(tmp_path, "Q142", {"id": "Q142"})
    assert has_cached_entity(tmp_path, "Q142") is True


def test_entities_snapshot_round_trip_is_deterministic(tmp_path: Path) -> None:
    entities = {
        "Q30": {"id": "Q30", "labels": {"en": {"value": "United States"}}},
        "Q142": {"id": "Q142", "labels": {"en": {"value": "France"}}},
    }
    path = tmp_path / "wikidata_countries_selected.jsonl"

    write_entities_snapshot(entities, path)
    lines = path.read_text(encoding="utf-8").splitlines()
    ids_in_order = [json.loads(line)["id"] for line in lines]
    assert ids_in_order == ["Q142", "Q30"]

    loaded = load_entities_snapshot(path)
    assert loaded == entities


def test_entities_snapshot_rewrite_is_byte_identical(tmp_path: Path) -> None:
    entities = {"Q142": {"id": "Q142", "labels": {"en": {"value": "France"}}}}
    path = tmp_path / "wikidata_countries_selected.jsonl"

    write_entities_snapshot(entities, path)
    first = path.read_bytes()
    write_entities_snapshot(entities, path)
    second = path.read_bytes()

    assert first == second


def test_load_entities_snapshot_rejects_duplicate_qids(tmp_path: Path) -> None:
    path = tmp_path / "wikidata_countries_selected.jsonl"
    path.write_text(
        json.dumps({"id": "Q142"}) + "\n" + json.dumps({"id": "Q142"}) + "\n",
        encoding="utf-8",
    )

    with pytest.raises(ValueError):
        load_entities_snapshot(path)


def test_load_entities_snapshot_missing_file_raises(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        load_entities_snapshot(tmp_path / "missing.jsonl")
