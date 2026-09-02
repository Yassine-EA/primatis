import json
from pathlib import Path

from primatis_data_seeding.acquisition.openlibrary import (
    PROFILE_LANGUAGE_QUOTAS,
    has_complete_snapshot,
    load_snapshot_payloads,
    reuse_openlibrary_snapshot,
    save_raw_payload,
)


SMALL_QUOTAS = PROFILE_LANGUAGE_QUOTAS["small"]


def _work(index: int, language_code: str) -> dict:
    return {
        "key": f"/works/OL{index}W",
        "author_key": [f"OL{index}A"],
        "author_name": [f"Author {index}"],
        "subject": ["Fiction"],
        "editions": {
            "docs": [{
                "key": f"/books/OL{index}M",
                "title": f"Book {index}",
                "language": [language_code],
            }]
        },
    }


def _write_complete_snapshot(raw_dir: Path) -> None:
    base = 1
    for language, (code, quota) in SMALL_QUOTAS.items():
        payload = {"docs": [_work(base + i, code) for i in range(quota)]}
        save_raw_payload(payload, raw_dir / f"search_{language.lower()}.json")
        base += 1000


def test_complete_snapshot_is_detected(tmp_path: Path) -> None:
    raw_dir = tmp_path / "raw"
    _write_complete_snapshot(raw_dir)

    assert has_complete_snapshot(raw_dir, quotas=SMALL_QUOTAS) is True


def test_incomplete_snapshot_is_not_detected(tmp_path: Path) -> None:
    raw_dir = tmp_path / "raw"
    raw_dir.mkdir()
    save_raw_payload({"docs": []}, raw_dir / "search_fr.json")

    assert has_complete_snapshot(raw_dir, quotas=SMALL_QUOTAS) is False


def test_snapshot_payloads_load_without_network(tmp_path: Path) -> None:
    raw_dir = tmp_path / "raw"
    _write_complete_snapshot(raw_dir)

    payloads = load_snapshot_payloads(raw_dir, quotas=SMALL_QUOTAS)

    assert set(payloads) == set(SMALL_QUOTAS)


def test_reuse_revalidates_exactly_100_candidates(tmp_path: Path) -> None:
    raw_dir = tmp_path / "raw"
    _write_complete_snapshot(raw_dir)
    (raw_dir / "manifest.json").write_text(
        json.dumps({"source": "Open Library Search API"}),
        encoding="utf-8",
    )

    selected, manifest = reuse_openlibrary_snapshot(raw_dir, quotas=SMALL_QUOTAS)

    assert len(selected) == 100
    assert manifest["reuse_mode"] is True
    assert manifest["selected_count"] == 100
