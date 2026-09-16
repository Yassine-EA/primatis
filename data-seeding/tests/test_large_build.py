from datetime import date

import pytest

from primatis_data_seeding.pipeline.batch import BatchCriteria
from primatis_data_seeding.pipeline.large_build import (
    LargeBuildPlan,
    build_large_bundle,
)


def test_plan_rejects_unknown_profile():
    with pytest.raises(ValueError):
        LargeBuildPlan(
            profile="unknown", target_titles=10,
            batches=(BatchCriteria(profile="unknown", language="FR", documentary_category="history", count=10),),
        )


def test_plan_rejects_empty_batches():
    with pytest.raises(ValueError):
        LargeBuildPlan(profile="large", target_titles=10, batches=())


def test_plan_rejects_non_positive_target():
    with pytest.raises(ValueError):
        LargeBuildPlan(
            profile="large", target_titles=0,
            batches=(BatchCriteria(profile="large", language="FR", documentary_category="history", count=10),),
        )


def _fake_work(*, edition_key, isbn13, title, author_key, author_name, language="fre"):
    return {
        "key": "/works/W" + edition_key,
        "title": title,
        "author_key": [author_key],
        "author_name": [author_name],
        "cover_i": None,
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


def test_build_large_bundle_merges_two_batches_and_writes_csv(tmp_path, monkeypatch):
    criteria_a = BatchCriteria(profile="large", language="FR", documentary_category="history", count=3)
    criteria_b = BatchCriteria(profile="large", language="EN", documentary_category="science", count=3)
    plan = LargeBuildPlan(profile="large", target_titles=6, batches=(criteria_a, criteria_b))

    def fake_fetcher(url, *, contact):
        if "language%3Afre" in url or "language:fre" in url:
            return {"docs": [
                _fake_work(edition_key=f"FR{i}M", isbn13=f"978100000000{i}", title=f"Titre {i}",
                           author_key="OLFRA", author_name="Auteur Francais", language="fre")
                for i in range(3)
            ]}
        return {"docs": [
            _fake_work(edition_key=f"EN{i}M", isbn13=f"978200000000{i}", title=f"Title {i}",
                       author_key="OLENA", author_name="English Author", language="eng")
            for i in range(3)
        ]}

    import primatis_data_seeding.pipeline.batch as batch_module
    monkeypatch.setattr(
        batch_module, "with_retries",
        lambda fn, **kw: fn,  # no retry indirection needed for a fixture fetcher
    )

    from primatis_data_seeding.pipeline.large_build import acquire_batch_candidates
    import primatis_data_seeding.pipeline.large_build as large_build_module

    def patched_acquire(criteria, **kwargs):
        kwargs["payload_fetcher"] = fake_fetcher
        return acquire_batch_candidates(criteria, **kwargs)

    monkeypatch.setattr(large_build_module, "acquire_batch_candidates", patched_acquire)

    report = build_large_bundle(
        plan,
        contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches",
        bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
    )

    assert report.total_received == 6
    assert report.total_accepted_final == 6
    assert report.shortfall == 0
    assert report.trimmed_surplus_count == 0
    assert report.languages == {"FR": 3, "EN": 3}
    assert report.copies_generated == 0  # 6 != the real large distribution target (5000)

    bundle_dir = tmp_path / "bundle"
    for name in ("titles.csv", "authors.csv", "title_authors.csv", "title_genres.csv", "genres.csv", "copies.csv"):
        assert (bundle_dir / name).is_file(), name

    titles_csv = (bundle_dir / "titles.csv").read_text(encoding="utf-8")
    assert titles_csv.count("\n") == 7  # header + 6 titles


def test_build_large_bundle_trims_surplus_deterministically(tmp_path, monkeypatch):
    criteria = BatchCriteria(profile="large", language="FR", documentary_category="history", count=5)
    plan = LargeBuildPlan(profile="large", target_titles=3, batches=(criteria,))

    def fake_fetcher(url, *, contact):
        return {"docs": [
            _fake_work(edition_key=f"FR{i}M", isbn13=f"978100000000{i}", title=f"Titre {i}",
                       author_key="OLFRA", author_name="Auteur Francais", language="fre")
            for i in range(5)
        ]}

    import primatis_data_seeding.pipeline.large_build as large_build_module
    from primatis_data_seeding.pipeline.large_build import acquire_batch_candidates

    def patched_acquire(criteria, **kwargs):
        kwargs["payload_fetcher"] = fake_fetcher
        return acquire_batch_candidates(criteria, **kwargs)

    monkeypatch.setattr(large_build_module, "acquire_batch_candidates", patched_acquire)

    report = build_large_bundle(
        plan,
        contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches",
        bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
    )

    assert report.total_received == 5
    assert report.total_accepted_before_trim == 5
    assert report.total_accepted_final == 3
    assert report.trimmed_surplus_count == 2
    assert report.shortfall == 0

    titles_csv = (tmp_path / "bundle" / "titles.csv").read_text(encoding="utf-8")
    assert titles_csv.count("\n") == 4  # header + 3 titles


def test_build_large_bundle_reports_shortfall_without_padding(tmp_path, monkeypatch):
    criteria = BatchCriteria(profile="large", language="FR", documentary_category="history", count=2)
    plan = LargeBuildPlan(profile="large", target_titles=10, batches=(criteria,))

    def fake_fetcher(url, *, contact):
        return {"docs": [
            _fake_work(edition_key=f"FR{i}M", isbn13=f"978100000000{i}", title=f"Titre {i}",
                       author_key="OLFRA", author_name="Auteur Francais", language="fre")
            for i in range(2)
        ]}

    import primatis_data_seeding.pipeline.large_build as large_build_module
    from primatis_data_seeding.pipeline.large_build import acquire_batch_candidates

    def patched_acquire(criteria, **kwargs):
        kwargs["payload_fetcher"] = fake_fetcher
        return acquire_batch_candidates(criteria, **kwargs)

    monkeypatch.setattr(large_build_module, "acquire_batch_candidates", patched_acquire)

    report = build_large_bundle(
        plan,
        contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches",
        bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
    )

    assert report.total_accepted_final == 2
    assert report.shortfall == 8
    # never padded — CSV reflects the true (short) count, not the target
    titles_csv = (tmp_path / "bundle" / "titles.csv").read_text(encoding="utf-8")
    assert titles_csv.count("\n") == 3  # header + 2 titles


def test_build_large_bundle_dedups_globally_across_batches(tmp_path, monkeypatch):
    # The SAME real edition (same edition_key) offered by two different
    # (language, category) batches must be counted once, not twice.
    criteria_a = BatchCriteria(profile="large", language="FR", documentary_category="history", count=5)
    criteria_b = BatchCriteria(profile="large", language="FR", documentary_category="literature", count=5)
    plan = LargeBuildPlan(profile="large", target_titles=10, batches=(criteria_a, criteria_b))

    shared_work = _fake_work(
        edition_key="SHARED1M", isbn13="9781000000099", title="Titre Partage",
        author_key="OLFRA", author_name="Auteur Francais", language="fre",
    )

    def fake_fetcher(url, *, contact):
        return {"docs": [shared_work]}

    import primatis_data_seeding.pipeline.large_build as large_build_module
    from primatis_data_seeding.pipeline.large_build import acquire_batch_candidates

    def patched_acquire(criteria, **kwargs):
        kwargs["payload_fetcher"] = fake_fetcher
        return acquire_batch_candidates(criteria, **kwargs)

    monkeypatch.setattr(large_build_module, "acquire_batch_candidates", patched_acquire)

    report = build_large_bundle(
        plan,
        contact="dev@primatis.local",
        batches_output_dir=tmp_path / "batches",
        bundle_output_dir=tmp_path / "bundle",
        reference_date=date(2026, 9, 11),
    )

    # Only ONE real candidate ever contributed, despite 2 batches offering it.
    assert report.total_received == 1
    assert report.total_accepted_final == 1
