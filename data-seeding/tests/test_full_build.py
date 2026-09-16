from datetime import date

import pytest

from primatis_data_seeding.pipeline.batch import BatchCriteria
from primatis_data_seeding.pipeline.full_build import (
    FullBuildPlan,
    build_full_bundle,
)


def test_plan_rejects_unknown_profile():
    with pytest.raises(ValueError):
        FullBuildPlan(
            profile="unknown", target_titles=10,
            batches=(BatchCriteria(profile="unknown", language="FR", documentary_category="history", count=10),),
        )


def test_plan_rejects_empty_batches():
    with pytest.raises(ValueError):
        FullBuildPlan(profile="full", target_titles=10, batches=())


def _fake_work(*, edition_key, isbn13, title, author_key, author_name, language="fre",
                work_key=None, cover_id=None):
    return {
        "key": work_key or ("/works/W" + edition_key),
        "title": title,
        "author_key": [author_key],
        "author_name": [author_name],
        "cover_i": cover_id,
        "subject": [],
        "editions": {
            "docs": [{
                "key": f"/books/{edition_key}",
                "title": title,
                "language": [language],
                "isbn": [isbn13],
                "isbn_13": [isbn13],
                "isbn_10": [],
                "publisher": ["A Publisher"],
                "publish_date": "2020",
                "publish_year": [2020],
                "number_of_pages": 200,
                "subtitle": None,
            }],
        },
    }


def _patched(monkeypatch, fake_fetcher):
    import primatis_data_seeding.pipeline.full_build as full_build_module
    from primatis_data_seeding.pipeline.full_build import acquire_batch_candidates

    def patched_acquire(criteria, **kwargs):
        kwargs["payload_fetcher"] = fake_fetcher
        return acquire_batch_candidates(criteria, **kwargs)

    monkeypatch.setattr(full_build_module, "acquire_batch_candidates", patched_acquire)


def test_build_full_bundle_tracks_exact_origin_batch_id(tmp_path, monkeypatch):
    criteria_a = BatchCriteria(profile="full", language="FR", documentary_category="history", count=2)
    criteria_b = BatchCriteria(profile="full", language="EN", documentary_category="science", count=2)
    plan = FullBuildPlan(profile="full", target_titles=4, batches=(criteria_a, criteria_b))

    def fake_fetcher(url, *, contact):
        if "language%3Afre" in url or "language:fre" in url:
            return {"docs": [
                _fake_work(edition_key=f"FR{i}M", isbn13=f"978100000000{i}", title=f"Titre {i}",
                           author_key="OLFRA", author_name="Auteur Francais", language="fre")
                for i in range(2)
            ]}
        return {"docs": [
            _fake_work(edition_key=f"EN{i}M", isbn13=f"978200000000{i}", title=f"Title {i}",
                       author_key="OLENA", author_name="English Author", language="eng")
            for i in range(2)
        ]}

    _patched(monkeypatch, fake_fetcher)

    report = build_full_bundle(
        plan, contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches", bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
    )

    assert report.funnel.accepted_final == 4
    assert report.shortfall == 0

    import json
    lines = (tmp_path / "bundle" / "provenance.jsonl").read_text(encoding="utf-8").splitlines()
    assert len(lines) == 4
    records = [json.loads(line) for line in lines]
    fr_batch_id = next(b.batch_id for b in report.batches if b.criteria["language"] == "FR")
    en_batch_id = next(b.batch_id for b in report.batches if b.criteria["language"] == "EN")
    for record in records:
        if record["source_key"].startswith("/books/FR"):
            assert record["batch_id"] == fr_batch_id
        else:
            assert record["batch_id"] == en_batch_id
        assert record["batch_id"] != ""  # DEV-16.5 §6: never the DEV-16.4 composite string


def test_funnel_is_arithmetically_reconcilable(tmp_path, monkeypatch):
    criteria = BatchCriteria(profile="full", language="FR", documentary_category="history", count=5)
    plan = FullBuildPlan(profile="full", target_titles=5, batches=(criteria,))

    def fake_fetcher(url, *, contact):
        return {"docs": [
            _fake_work(edition_key=f"FR{i}M", isbn13=f"978100000000{i}", title=f"Titre {i}",
                       author_key="OLFRA", author_name="Auteur Francais")
            for i in range(5)
        ]}

    _patched(monkeypatch, fake_fetcher)

    report = build_full_bundle(
        plan, contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches", bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
    )

    problems = report.funnel.reconcile()
    assert problems == [], problems
    assert report.funnel.source_records_received == 5
    assert report.funnel.unique_candidates_inter_batch == 5
    assert report.funnel.accepted_final == 5


def test_funnel_reconciles_when_an_author_is_quarantined_without_title_cascade(tmp_path, monkeypatch):
    """DEV-16.5 régression : sur le vrai bundle Full, `reconcile()` a
    détecté un écart de 1 (rejected+quarantined+accepted_pre_trim ==
    15188 != validated == 15187). Cause réelle : `funnel.quarantined`
    comptait TOUTES les entrées de `quality_result.quarantined`
    (entity="title" ET entity="author"), alors que l'équation du funnel
    compare cette valeur contre une population de Titles. Un Author
    quarantiné qui NE fait PAS chuter son Title (car ce Title a un
    second Author valide) ajoutait 1 au compteur sans retirer aucun
    Title de `accepted_pre_trim`. Reproduit ici avec un Title à deux
    Authors, dont un seul au nom suspect (mojibake) : l'Author est
    quarantiné isolément, le Title reste ACCEPTED via son second Author."""
    criteria = BatchCriteria(profile="full", language="FR", documentary_category="history", count=1)
    plan = FullBuildPlan(profile="full", target_titles=1, batches=(criteria,))

    def fake_fetcher(url, *, contact):
        work = _fake_work(
            edition_key="FR0M", isbn13="9781000000000", title="Titre 0",
            author_key="OLFRA", author_name="Auteur Francais",
        )
        # Deuxième Author, nom mojibake (signature "Ã©" — DEV-16.4 §19) :
        # doit être quarantiné SEUL, sans faire chuter le Title puisque
        # "Auteur Francais" reste un lien valide.
        work["author_key"] = ["OLFRA", "OLFRB"]
        work["author_name"] = ["Auteur Francais", "AuteurÃ© Suspect"]
        return {"docs": [work]}

    _patched(monkeypatch, fake_fetcher)

    report = build_full_bundle(
        plan, contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches", bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
    )

    assert report.funnel.accepted_final == 1  # le Title survit via son second Author
    problems = report.funnel.reconcile()
    assert problems == [], problems
    # L'Author quarantiné isolément ne doit PAS gonfler funnel.quarantined
    # (qui reste une population de Titles, cf. docstring FunnelMetrics).
    assert report.funnel.quarantined == 0


def test_trim_and_shortfall_reflected_in_funnel(tmp_path, monkeypatch):
    criteria = BatchCriteria(profile="full", language="FR", documentary_category="history", count=7)
    plan = FullBuildPlan(profile="full", target_titles=3, batches=(criteria,))

    def fake_fetcher(url, *, contact):
        return {"docs": [
            _fake_work(edition_key=f"FR{i}M", isbn13=f"978100000000{i}", title=f"Titre {i}",
                       author_key="OLFRA", author_name="Auteur Francais")
            for i in range(7)
        ]}

    _patched(monkeypatch, fake_fetcher)

    report = build_full_bundle(
        plan, contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches", bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
    )

    assert report.funnel.accepted_pre_trim == 7
    assert report.funnel.trimmed == 4
    assert report.funnel.accepted_final == 3
    assert report.shortfall == 0
    assert report.funnel.reconcile() == []


def test_author_and_work_enrichment_applied_when_records_given(tmp_path, monkeypatch):
    criteria = BatchCriteria(profile="full", language="FR", documentary_category="history", count=1)
    plan = FullBuildPlan(profile="full", target_titles=1, batches=(criteria,))

    def fake_fetcher(url, *, contact):
        return {"docs": [_fake_work(
            edition_key="FR0M", isbn13="9781000000000", title="Titre 0",
            author_key="OLFRA", author_name="Auteur Francais",
            work_key="/works/WFR0M",
        )]}

    _patched(monkeypatch, fake_fetcher)

    author_records = {"OLFRA": {
        "key": "/authors/OLFRA", "name": "Auteur François Réel",
        "bio": "Écrivain français né dans une famille modeste, il est considéré comme l'un des auteurs les plus reconnus de son siècle.",
    }}
    work_records = {"/works/WFR0M": {
        "key": "/works/WFR0M", "description": "Un résumé réel de l'œuvre.",
    }}

    report = build_full_bundle(
        plan, contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches", bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
        author_records=author_records, work_records=work_records,
    )

    assert report.author_enrichment_applied is True
    assert report.work_enrichment_applied is True
    assert report.summary_coverage == 1.0

    import csv
    titles = list(csv.DictReader(open(tmp_path / "bundle" / "titles.csv", encoding="utf-8")))
    assert titles[0]["summary"] == "Un résumé réel de l'œuvre."


def test_covers_pipeline_materializes_and_validates(tmp_path, monkeypatch):
    criteria = BatchCriteria(profile="full", language="FR", documentary_category="history", count=1)
    plan = FullBuildPlan(profile="full", target_titles=1, batches=(criteria,))

    def fake_fetcher(url, *, contact):
        return {"docs": [_fake_work(
            edition_key="FR0M", isbn13="9781000000000", title="Titre 0",
            author_key="OLFRA", author_name="Auteur Francais", cover_id=42,
        )]}

    _patched(monkeypatch, fake_fetcher)

    # A minimal real, valid JPEG (1x1) — built from the SOF0 marker shape
    # the cover_validation.py parser expects, not a fixture library.
    def make_minimal_jpeg(width: int = 300, height: int = 400) -> bytes:
        import struct
        soi = b"\xff\xd8"
        # SOF0 segment: length=11, precision=8, height, width, 1 component
        # — dimensions chosen well within cover_validation.py's real
        # thresholds (MIN 120x160 / MAX 3000x3000, DEV-16.3 §H).
        sof0 = (
            b"\xff\xc0" + struct.pack(">H", 11) + bytes([8])
            + struct.pack(">HH", height, width) + bytes([1, 1, 0x11, 0])
        )
        eoi = b"\xff\xd9"
        return soi + sof0 + eoi

    jpeg_bytes = make_minimal_jpeg()

    covers_dir = tmp_path / "covers"

    def cover_fetcher(cover_id):
        assert cover_id == 42
        return jpeg_bytes

    report = build_full_bundle(
        plan, contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches", bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
        covers_assets_dir=covers_dir, cover_fetcher=cover_fetcher,
    )

    assert report.covers.candidates == 1
    assert report.covers.download_attempted == 1
    assert report.covers.download_successful == 1
    assert report.covers.valid == 1
    assert report.covers.invalid == 0
    assert (covers_dir / "ol-cover-42.jpg").is_file()
    assert report.cover_coverage == 1.0


def test_covers_pipeline_rejects_invalid_download(tmp_path, monkeypatch):
    criteria = BatchCriteria(profile="full", language="FR", documentary_category="history", count=1)
    plan = FullBuildPlan(profile="full", target_titles=1, batches=(criteria,))

    def fake_fetcher(url, *, contact):
        return {"docs": [_fake_work(
            edition_key="FR0M", isbn13="9781000000000", title="Titre 0",
            author_key="OLFRA", author_name="Auteur Francais", cover_id=99,
        )]}

    _patched(monkeypatch, fake_fetcher)
    covers_dir = tmp_path / "covers"

    def cover_fetcher(cover_id):
        return b"<html><body>404 Not Found</body></html>"  # HTML error page, not an image

    report = build_full_bundle(
        plan, contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches", bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
        covers_assets_dir=covers_dir, cover_fetcher=cover_fetcher,
    )

    assert report.covers.invalid == 1
    assert report.covers.valid == 0
    assert not (covers_dir / "ol-cover-99.jpg").exists()
    assert report.cover_coverage == 0.0  # fail-closed: no broken reference ever written


def test_covers_never_written_outside_covers_assets_dir():
    # Sanity check for the DEV-16.5 §13 governance rule: covers land
    # EXACTLY where the caller points them — this module's
    # `covers_assets_dir` parameter has NO default value (so a caller
    # cannot accidentally fall back to some hardcoded location), and the
    # module never imports/reuses `acquisition/openlibrary_covers.py`'s
    # own `primatis-web/...` CLI default.
    import inspect
    from primatis_data_seeding.pipeline import full_build

    signature = inspect.signature(full_build.build_full_bundle)
    assert signature.parameters["covers_assets_dir"].default is None
    source = inspect.getsource(full_build)
    assert 'Path("primatis-web' not in source
    assert "primatis-web/public" not in source


def test_max_cover_downloads_caps_attempts_but_still_counts_candidates(tmp_path, monkeypatch):
    criteria = BatchCriteria(profile="full", language="FR", documentary_category="history", count=3)
    plan = FullBuildPlan(profile="full", target_titles=3, batches=(criteria,))

    def fake_fetcher(url, *, contact):
        return {"docs": [
            _fake_work(edition_key=f"FR{i}M", isbn13=f"978100000000{i}", title=f"Titre {i}",
                       author_key="OLFRA", author_name="Auteur Francais", cover_id=100 + i)
            for i in range(3)
        ]}

    _patched(monkeypatch, fake_fetcher)

    def make_minimal_jpeg(width=300, height=400):
        import struct
        return (
            b"\xff\xd8"
            + b"\xff\xc0" + struct.pack(">H", 11) + bytes([8])
            + struct.pack(">HH", height, width) + bytes([1, 1, 0x11, 0])
            + b"\xff\xd9"
        )

    def cover_fetcher(cover_id):
        return make_minimal_jpeg()

    report = build_full_bundle(
        plan, contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches", bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
        covers_assets_dir=tmp_path / "covers", cover_fetcher=cover_fetcher,
        max_cover_downloads=1,
    )

    assert report.covers.candidates == 3
    assert report.covers.download_attempted == 1
    assert report.covers.valid == 1
