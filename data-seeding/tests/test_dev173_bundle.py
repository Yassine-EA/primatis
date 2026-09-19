"""DEV-17.3 — nettoyage, enrichissement exact, provenance et couvertures placeholder."""

import csv
import importlib.util
import json
import os
import shutil
import struct
import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(SCRIPTS))
import dev173_build_targeted_bundle as bundle  # noqa: E402
import dev173_cover_placeholders as placeholders  # noqa: E402

TOOLS = shutil.which("rsvg-convert") and shutil.which("magick")
needs_tools = pytest.mark.skipif(not TOOLS, reason="rsvg-convert / magick indisponibles")
BUNDLE_DIR = Path(os.environ.get(
    "DEV173_BUNDLE_DIR", "/home/yassine/workspace/sandbox/dataset/work/final-bundle-dev173"))


def test_cleanup_reports_changes_and_preserves_identifiers() -> None:
    authors = [
        {"source_key": "dataset-author-1", "full_name": "Jeff  Kinney"},
        {"source_key": "dataset-author-2", "full_name": "Jeff Kinney"},
        {"source_key": "dataset-author-3", "full_name": "Bryan Lee O\\'Malley"},
    ]
    changes = bundle.apply_cleanup(authors, entity="author", id_column="source_key", field="full_name")
    assert [c["identifier"] for c in changes] == ["dataset-author-1", "dataset-author-3"]
    assert [a["source_key"] for a in authors] == ["dataset-author-1", "dataset-author-2", "dataset-author-3"]
    # aucune fusion : deux lignes portent désormais le même nom, sous deux source_key
    assert authors[0]["full_name"] == authors[1]["full_name"] == "Jeff Kinney"
    assert bundle.apply_cleanup(authors, entity="author", id_column="source_key", field="full_name") == []


def test_enrichment_only_applies_enriched_decisions_on_empty_cells() -> None:
    titles = [
        {"isbn": "1", "publisher": "", "publication_year": ""},
        {"isbn": "2", "publisher": "Déjà là", "publication_year": ""},
        {"isbn": "3", "publisher": "", "publication_year": ""},
    ]
    decisions = [
        {"isbn": "1", "field": "publisher", "value": "Penguin", "decision": "ENRICHED"},
        {"isbn": "1", "field": "publication_year", "value": "2001", "decision": "ENRICHED"},
        {"isbn": "2", "field": "publisher", "value": "Autre", "decision": "ENRICHED"},
        {"isbn": "3", "field": "publisher", "value": "", "decision": "REJECTED_CONFLICT"},
        {"isbn": "999", "field": "publisher", "value": "Inconnu", "decision": "ENRICHED"},
    ]
    applied = bundle.apply_enrichment(titles, decisions)
    assert applied == {"publisher": 1, "publication_year": 1}
    assert titles[1]["publisher"] == "Déjà là" and titles[2]["publisher"] == ""


def test_provenance_verification_detects_tampering(tmp_path: Path) -> None:
    (tmp_path / "a.csv").write_text("x\n1\n2\n", encoding="utf-8")
    entry = {"sha256": bundle.sha256_file(tmp_path / "a.csv"), "rows": 2}
    (tmp_path / bundle.PROVENANCE).write_text(json.dumps({"files": {"a.csv": entry}}), encoding="utf-8")
    assert bundle.verify_provenance(tmp_path) == []
    (tmp_path / "a.csv").write_text("x\n1\n2\n3\n", encoding="utf-8")
    assert any("sha mismatch" in p for p in bundle.verify_provenance(tmp_path))
    (tmp_path / "b.csv").write_text("x\n", encoding="utf-8")
    assert any("files mismatch" in p for p in bundle.verify_provenance(tmp_path))


def test_candidate_bundle_is_immutable(tmp_path: Path) -> None:
    from primatis_data_seeding.generation.reference_datetime import parse_reference_datetime
    with pytest.raises(ValueError, match="immutable"):
        bundle.build_bundle(tmp_path, tmp_path, tmp_path, tmp_path, parse_reference_datetime("2026-09-19T12:00:00Z"))


@pytest.mark.skipif(not BUNDLE_DIR.is_dir(), reason="bundle DEV-17.3 local absent")
def test_real_bundle_provenance_is_consistent_and_complete() -> None:
    assert bundle.verify_provenance(BUNDLE_DIR) == []
    prov = json.loads((BUNDLE_DIR / bundle.PROVENANCE).read_text(encoding="utf-8"))
    assert prov["reference_datetime"] == prov["scenario_generation"]["reference_datetime"]
    for key in ("title_metadata_enrichment", "text_cleanup", "cover_placeholders", "articles", "scenario_generation"):
        assert key in prov
    assert prov["text_cleanup"]["titles"] == 8 and prov["text_cleanup"]["authors"] == 27
    assert prov["text_cleanup"]["author_merges"] == 0
    assert prov["files"]["titles.csv"]["rows"] == 14600 and prov["files"]["authors.csv"]["rows"] == 8594
    assert prov["files"]["copies.csv"]["rows"] == 24000 and prov["files"]["users.csv"]["rows"] == 1500
    assert prov["covers"]["upscaled"] + prov["covers"]["upscaled_replaced_by_placeholder"] == 628


@pytest.mark.skipif(not BUNDLE_DIR.is_dir(), reason="bundle DEV-17.3 local absent")
def test_real_bundle_enrichment_is_exact_and_traceable() -> None:
    rows = list(csv.DictReader((BUNDLE_DIR / "title_metadata_enrichment.csv").open(encoding="utf-8", newline="")))
    enriched = {(r["isbn"], r["field"]): r["value"] for r in rows if r["decision"] == "ENRICHED"}
    titles = {r["isbn"]: r for r in csv.DictReader((BUNDLE_DIR / "titles.csv").open(encoding="utf-8", newline=""))}
    populated_publisher = {i for i, t in titles.items() if t["publisher"]}
    populated_year = {i for i, t in titles.items() if t["publication_year"]}
    assert populated_publisher == {i for i, f in enriched if f == "publisher"}
    assert populated_year == {i for i, f in enriched if f == "publication_year"}
    assert len(populated_publisher) <= 101 and len(populated_year) <= 121
    assert all(r["source"] and r["decision"] for r in rows)
    assert all(not t["page_count"] and not t["subtitle"] and not t["summary"] for t in titles.values())


def _jpeg_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    i = 2
    while i < len(data):
        marker = data[i + 1]
        if marker in (0xC0, 0xC1, 0xC2):
            h, w = struct.unpack(">HH", data[i + 5:i + 9])
            return w, h
        i += 2 + struct.unpack(">H", data[i + 2:i + 4])[0]
    raise AssertionError("no SOF")


@needs_tools
def test_committed_black_covers_are_now_the_institutional_placeholder() -> None:
    expected = placeholders.render_placeholder_jpeg(REPO / placeholders.SOURCE_ASSET)
    for isbn in placeholders.BLACK_COVER_ISBNS:
        path = REPO / placeholders.COVER_DIR / f"{isbn}.jpg"
        assert path.read_bytes() == expected
        assert _jpeg_size(path) == (360, 520)
        assert not placeholders.is_uniform(path.read_bytes())


@needs_tools
def test_placeholder_never_overwrites_a_real_cover(tmp_path: Path) -> None:
    (tmp_path / "primatis-web/public/assets/fallbacks").mkdir(parents=True)
    (tmp_path / "primatis-web/public/covers/catalogue").mkdir(parents=True)
    shutil.copy(REPO / placeholders.SOURCE_ASSET, tmp_path / placeholders.SOURCE_ASSET)
    real = REPO / "primatis-web/public/covers/catalogue/9783764374952.jpg"  # vraie couverture (non noire)
    for isbn in placeholders.BLACK_COVER_ISBNS:
        shutil.copy(real, tmp_path / placeholders.COVER_DIR / f"{isbn}.jpg")
    with pytest.raises(ValueError, match="non-uniform"):
        placeholders.apply_placeholders(tmp_path)


def test_source_svg_is_untouched_reference() -> None:
    svg = (REPO / placeholders.SOURCE_ASSET).read_text(encoding="utf-8")
    assert svg.startswith("<svg") and "Couverture indisponible" in svg
