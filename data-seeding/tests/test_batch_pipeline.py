from datetime import date

import pytest

from primatis_data_seeding.pipeline.batch import (
    BatchCriteria,
    build_batch_search_url,
    build_query_plan,
    compute_batch_id,
    run_batch,
    select_batch_candidates,
)


def test_batch_criteria_rejects_unsupported_language():
    with pytest.raises(ValueError):
        BatchCriteria(profile="large", language="PT", documentary_category="history", count=10)


def test_batch_criteria_rejects_unsupported_category():
    with pytest.raises(ValueError):
        BatchCriteria(profile="large", language="FR", documentary_category="nope", count=10)


def test_batch_criteria_rejects_non_positive_count():
    with pytest.raises(ValueError):
        BatchCriteria(profile="large", language="FR", documentary_category="history", count=0)


def test_batch_id_is_deterministic_and_readable():
    criteria = BatchCriteria(profile="large", language="IT", documentary_category="history", count=15)
    batch_id = compute_batch_id(criteria, date(2026, 9, 11))
    assert batch_id == "2026-09-11-large-it-history-0015"
    # same inputs -> same id, always
    assert compute_batch_id(criteria, date(2026, 9, 11)) == batch_id


def test_batch_id_distinguishes_socle_from_non_socle():
    # DEV-16.4 §4.3: criteria that differ only by use_editorial_socle
    # must never collide on the same cache/output directory.
    plain = BatchCriteria(profile="large", language="FR", documentary_category="history", count=15)
    socle = BatchCriteria(
        profile="large", language="FR", documentary_category="history", count=15,
        use_editorial_socle=True,
    )
    assert compute_batch_id(plain, date(2026, 9, 11)) != compute_batch_id(socle, date(2026, 9, 11))


def test_batch_search_url_uses_the_documentary_category_filter():
    # DEV-16.4 §4.1 bugfix: the URL actually used by run_batch() must
    # carry the category filter — this was defined but never wired in
    # DEV-16.3 (see module docstring).
    criteria = BatchCriteria(profile="large", language="FR", documentary_category="science", count=10)
    url = build_batch_search_url(criteria, limit=50)
    assert "subject%3Ascience" in url or "subject:science" in url
    assert "language%3Afre" in url or "language:fre" in url


def test_batch_search_url_with_author_key_drops_category_filter():
    criteria = BatchCriteria(profile="large", language="FR", documentary_category="science", count=10)
    url = build_batch_search_url(criteria, limit=20, author_key="OL107571A")
    assert "author_key%3AOL107571A" in url
    # "subject" legitimately still appears as a `fields=` list entry
    # (Search API result field) — only the `q=` FILTER must drop it.
    assert "subject%3A" not in url and "subject:" not in url


def test_query_plan_without_socle_is_generic_only():
    criteria = BatchCriteria(profile="large", language="FR", documentary_category="history", count=10)
    plan = build_query_plan(criteria, fetch_limit=50)
    assert [entry.label for entry in plan] == ["generic"]


def test_query_plan_with_socle_lists_real_pilot_authors_first(tmp_path=None):
    # Uses the REAL committed reference/catalogue/fr.toml (Victor Hugo,
    # DEV-16.3 §F) — no fixture override, exercises the shipped file.
    criteria = BatchCriteria(
        profile="large", language="FR", documentary_category="history", count=10,
        use_editorial_socle=True,
    )
    plan = build_query_plan(criteria, fetch_limit=50)
    assert plan[-1].label == "generic"
    socle_entries = plan[:-1]
    assert len(socle_entries) >= 1
    assert any("OL107571A" in entry.label for entry in socle_entries)  # Victor Hugo
    for entry in socle_entries:
        assert entry.author_key is not None
        assert "author_key" in entry.url


def _fake_work(*, edition_key, isbn13, title, author_key="OL1A", author_name="Author One",
                subjects=None, language="fre"):
    return {
        "key": "/works/W" + edition_key,
        "title": title,
        "author_key": [author_key],
        "author_name": [author_name],
        "cover_i": None,
        "subject": subjects or [],
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


def _fake_payload(n=3):
    return {
        "docs": [
            _fake_work(edition_key=f"OL{i}M", isbn13=f"978000000000{i}", title=f"Titre {i}")
            for i in range(n)
        ],
    }


def test_select_batch_candidates_respects_count_and_dedup():
    criteria = BatchCriteria(profile="large", language="FR", documentary_category="history", count=2)
    payload = _fake_payload(5)
    candidates = select_batch_candidates(payload, criteria)
    assert len(candidates) == 2
    assert len({c.edition_key for c in candidates}) == 2


def test_run_batch_end_to_end_with_fixture_payload(tmp_path):
    criteria = BatchCriteria(profile="large", language="FR", documentary_category="history", count=3)
    calls: list[str] = []

    def fake_fetcher(url, *, contact):
        calls.append(url)
        return _fake_payload(3)

    report = run_batch(
        criteria,
        contact="dev@primatis.local",
        output_dir=tmp_path,
        reference_date=date(2026, 9, 11),
        payload_fetcher=fake_fetcher,
    )

    assert report.received_count == 3
    assert report.accepted_count == 3
    assert report.quarantined_count == 0
    assert report.rejected_count == 0
    assert report.languages == {"FR": 3}
    assert report.from_cache is False
    assert len(calls) == 1
    assert "subject%3Ahistory" in calls[0] or "subject:history" in calls[0]

    batch_dir = tmp_path / report.batch_id
    assert (batch_dir / "raw_generic.json").is_file()
    assert (batch_dir / "raw_manifest.json").is_file()
    assert (batch_dir / "selected.jsonl").is_file()
    assert (batch_dir / "provenance.jsonl").is_file()
    assert (batch_dir / "quarantine.jsonl").is_file()
    assert (batch_dir / "batch_report.json").is_file()

    provenance_lines = (batch_dir / "provenance.jsonl").read_text(encoding="utf-8").splitlines()
    assert len(provenance_lines) == 3


def test_run_batch_quarantines_corrupted_titles_and_keeps_them_out_of_accepted(tmp_path):
    criteria = BatchCriteria(profile="large", language="FR", documentary_category="history", count=2)

    def fake_fetcher(url, *, contact):
        clean = _fake_work(edition_key="OL1M", isbn13="9780000000001", title="Un titre propre")
        corrupted = _fake_work(
            edition_key="OL2M", isbn13="9780000000002",
            title="L Â\x8cuvre, Emile Zola",  # real confirmed corrupted value
        )
        return {"docs": [clean, corrupted]}

    report = run_batch(
        criteria,
        contact="dev@primatis.local",
        output_dir=tmp_path,
        reference_date=date(2026, 9, 11),
        payload_fetcher=fake_fetcher,
    )

    assert report.received_count == 2
    assert report.accepted_count == 1
    assert report.quarantined_count == 1
    assert report.quarantine_reasons.get("TEXT_ENCODING_SUSPECT") == 1

    quarantine_lines = (tmp_path / report.batch_id / "quarantine.jsonl").read_text(
        encoding="utf-8"
    ).splitlines()
    assert len(quarantine_lines) == 1

    import json
    quarantined = json.loads(quarantine_lines[0])
    assert quarantined["entity"] == "title"
    assert quarantined["reason_code"] == "TEXT_ENCODING_SUSPECT"

    # DEC-16.3-03: never present in the CSV-importable/accepted path.
    accepted_titles_text = (tmp_path / report.batch_id / "batch_report.json").read_text(
        encoding="utf-8"
    )
    assert "L Â\x8cuvre" not in accepted_titles_text


def test_run_batch_is_idempotent_when_rerun_with_identical_inputs(tmp_path):
    criteria = BatchCriteria(profile="large", language="FR", documentary_category="history", count=3)
    calls: list[str] = []

    def fake_fetcher(url, *, contact):
        calls.append(url)
        return _fake_payload(3)

    report1 = run_batch(
        criteria, contact="dev@primatis.local", output_dir=tmp_path,
        reference_date=date(2026, 9, 11), payload_fetcher=fake_fetcher,
    )
    report2 = run_batch(
        criteria, contact="dev@primatis.local", output_dir=tmp_path,
        reference_date=date(2026, 9, 11), payload_fetcher=fake_fetcher,
    )

    assert report1.batch_id == report2.batch_id
    assert report1.accepted_count == report2.accepted_count == 3

    # DEV-16.4 §4.3: the second run must reuse the cached RAW payload —
    # the fetcher must be called EXACTLY ONCE across both runs.
    assert len(calls) == 1
    assert report2.from_cache is True
    assert report1.from_cache is False

    # Idempotent on every SUBSTANTIVE field; `acquired_at` legitimately
    # differs between two separate real runs (DEV-16.2 §10.2 provenance
    # timestamp), so it is excluded from this comparison on purpose.
    dict1 = report1.to_dict()
    dict2 = report2.to_dict()
    dict1.pop("acquired_at")
    dict2.pop("acquired_at")
    dict1.pop("from_cache")
    dict2.pop("from_cache")
    assert dict1 == dict2


def test_run_batch_refresh_forces_a_new_fetch(tmp_path):
    criteria = BatchCriteria(profile="large", language="FR", documentary_category="history", count=3)
    calls: list[str] = []

    def fake_fetcher(url, *, contact):
        calls.append(url)
        return _fake_payload(3)

    run_batch(
        criteria, contact="dev@primatis.local", output_dir=tmp_path,
        reference_date=date(2026, 9, 11), payload_fetcher=fake_fetcher,
    )
    report2 = run_batch(
        criteria, contact="dev@primatis.local", output_dir=tmp_path,
        reference_date=date(2026, 9, 11), payload_fetcher=fake_fetcher,
        refresh=True,
    )

    assert len(calls) == 2  # --refresh forced a real second fetch
    assert report2.from_cache is False


def test_run_batch_cache_is_invalidated_when_count_changes(tmp_path):
    # DEV-16.4 §4.3: changing a criterion (count here) must not silently
    # reuse a cache built for a different count.
    criteria_a = BatchCriteria(profile="large", language="FR", documentary_category="history", count=3)
    criteria_b = BatchCriteria(profile="large", language="FR", documentary_category="history", count=5)
    calls: list[str] = []

    def fake_fetcher(url, *, contact):
        calls.append(url)
        return _fake_payload(5)

    run_batch(
        criteria_a, contact="dev@primatis.local", output_dir=tmp_path,
        reference_date=date(2026, 9, 11), payload_fetcher=fake_fetcher,
    )
    run_batch(
        criteria_b, contact="dev@primatis.local", output_dir=tmp_path,
        reference_date=date(2026, 9, 11), payload_fetcher=fake_fetcher,
    )

    # Different batch_id (count is part of it) -> different directory ->
    # a real second fetch, never a false cache hit.
    assert len(calls) == 2


def test_run_batch_with_socle_prioritizes_the_reference_author(tmp_path):
    # Uses the REAL fr.toml socle (DEV-16.5 §5 expansion: several real
    # authors, Victor Hugo/OL107571A among them) — the query PLAN size
    # is derived from the real file rather than hardcoded, so this test
    # does not need updating every time the socle grows.
    from primatis_data_seeding.reference.editorial_selection import (
        load_editorial_selection,
    )

    fr_socle = load_editorial_selection("FR")
    assert len(fr_socle) >= 1

    criteria = BatchCriteria(
        profile="large", language="FR", documentary_category="history", count=3,
        use_editorial_socle=True,
    )
    urls_called: list[str] = []

    def fake_fetcher(url, *, contact):
        urls_called.append(url)
        if "author_key%3AOL107571A" in url:
            # A real Victor Hugo work, returned only for HIS socle query.
            return {"docs": [_fake_work(
                edition_key="HUGO1M", isbn13="9782070409228",
                title="Les Misérables", author_key="OL107571A", author_name="Victor Hugo",
            )]}
        if "author_key%3A" in url:
            return {"docs": []}  # other socle authors: no fixture data for this test
        return _fake_payload(5)

    report = run_batch(
        criteria, contact="dev@primatis.local", output_dir=tmp_path,
        reference_date=date(2026, 9, 11), payload_fetcher=fake_fetcher,
    )

    # 1 query per socle author + 1 generic query, whatever the socle size.
    assert len(urls_called) == len(fr_socle) + 1
    assert any("author_key%3AOL107571A" in u for u in urls_called)
    assert report.socle_candidate_counts.get("OL107571A") == 1
    assert report.accepted_count == 3  # 1 from Hugo's socle query + 2 filled generically

    selected_text = (tmp_path / report.batch_id / "selected.jsonl").read_text(encoding="utf-8")
    assert "Les Mis" in selected_text  # the socle author's real work made it through
