from pathlib import Path

import pytest

from primatis_data_seeding.acquisition.openlibrary import (
    PROFILE_LANGUAGE_QUOTAS,
    SEARCH_FIELDS,
    build_search_url,
    save_raw_payload,
    select_candidates,
)


SMALL_QUOTAS = PROFILE_LANGUAGE_QUOTAS["small"]
MEDIUM_QUOTAS = PROFILE_LANGUAGE_QUOTAS["medium"]


def edition_work(
    index: int,
    language_code: str,
    *,
    author: bool = True,
    cover_i: int | None = None,
) -> dict:
    work = {
        "key": f"/works/OL{index}W",
        "author_key": [f"OL{index}A"] if author else [],
        "author_name": [f"Author {index}"] if author else [],
        "subject": ["Fiction"],
        "cover_i": cover_i,
        "editions": {
            "docs": [
                {
                    "key": f"/books/OL{index}M",
                    "title": f"Book {index}",
                    "language": [language_code],
                    "isbn_13": [f"978000000{index:04d}"],
                    "publisher": ["Example publisher"],
                    "publish_year": 2000 + index % 20,
                    "number_of_pages": 100 + index,
                }
            ]
        },
    }
    return work


def payloads_for_quotas(quotas) -> dict[str, dict]:
    result = {}
    base = 1
    for language, (code, quota) in quotas.items():
        result[language] = {
            "docs": [edition_work(base + i, code) for i in range(quota + 3)]
        }
        base += quota + 1000
    return result


def test_small_language_quotas_total_exactly_100() -> None:
    assert sum(quota for _, quota in SMALL_QUOTAS.values()) == 100


def test_small_language_projection_is_expected() -> None:
    assert {k: v[1] for k, v in SMALL_QUOTAS.items()} == {
        "FR": 75, "EN": 10, "NL": 8, "DE": 3, "ES": 2, "IT": 1, "LA": 1,
    }


def test_medium_language_quotas_total_exactly_1000() -> None:
    assert sum(quota for _, quota in MEDIUM_QUOTAS.values()) == 1000


def test_medium_language_projection_is_expected() -> None:
    assert {k: v[1] for k, v in MEDIUM_QUOTAS.items()} == {
        "FR": 750, "EN": 100, "NL": 80, "DE": 30, "ES": 20, "IT": 15, "LA": 5,
    }


def test_search_url_is_language_filtered_and_deterministic() -> None:
    url = build_search_url("fre", 300)

    assert "language%3Afre" in url
    assert "sort=key" in url
    assert "limit=300" in url
    assert "editions" in url


def test_selects_exactly_100_real_edition_candidates() -> None:
    selected = select_candidates(payloads_for_quotas(SMALL_QUOTAS), quotas=SMALL_QUOTAS)

    assert len(selected) == 100
    assert len({row.edition_key for row in selected}) == 100
    assert all(row.edition_key.startswith("/books/") for row in selected)


def test_selects_exactly_1000_real_edition_candidates() -> None:
    selected = select_candidates(payloads_for_quotas(MEDIUM_QUOTAS), quotas=MEDIUM_QUOTAS)

    assert len(selected) == 1000
    assert len({row.edition_key for row in selected}) == 1000
    assert all(row.edition_key.startswith("/books/") for row in selected)


def test_selection_rejects_missing_authors() -> None:
    payloads = payloads_for_quotas(SMALL_QUOTAS)
    code, quota = SMALL_QUOTAS["LA"]
    payloads["LA"] = {"docs": [edition_work(999999, code, author=False)]}

    with pytest.raises(ValueError, match="Insufficient valid"):
        select_candidates(payloads, quotas=SMALL_QUOTAS)


def test_selection_rejects_wrong_edition_language() -> None:
    payloads = payloads_for_quotas(SMALL_QUOTAS)
    payloads["IT"] = {"docs": [edition_work(888888, "eng")]}

    with pytest.raises(ValueError, match="Insufficient valid"):
        select_candidates(payloads, quotas=SMALL_QUOTAS)


def test_raw_payload_is_snapshot_with_stable_hash(tmp_path: Path) -> None:
    payload = {"docs": [{"key": "/works/OL1W"}]}

    first = save_raw_payload(payload, tmp_path / "a.json")
    second = save_raw_payload(payload, tmp_path / "b.json")

    assert first == second
    assert len(first) == 64


def test_search_fields_requests_cover_i_explicitly() -> None:
    assert "cover_i" in SEARCH_FIELDS


def test_search_fields_never_uses_wildcard() -> None:
    assert "*" not in SEARCH_FIELDS


def test_cover_id_is_propagated_from_search_payload() -> None:
    payloads = payloads_for_quotas(SMALL_QUOTAS)
    code, quota = SMALL_QUOTAS["LA"]
    payloads["LA"]["docs"][0] = edition_work(999999, code, cover_i=258027)

    selected = select_candidates(payloads, quotas=SMALL_QUOTAS)
    by_key = {row.edition_key: row for row in selected}

    assert by_key["/books/OL999999M"].cover_id == 258027


def test_cover_id_absent_is_none() -> None:
    selected = select_candidates(payloads_for_quotas(SMALL_QUOTAS), quotas=SMALL_QUOTAS)

    assert all(row.cover_id is None for row in selected)


def test_cover_id_non_positive_is_none() -> None:
    payloads = payloads_for_quotas(SMALL_QUOTAS)
    code, quota = SMALL_QUOTAS["LA"]
    payloads["LA"]["docs"][0] = edition_work(999999, code, cover_i=0)

    selected = select_candidates(payloads, quotas=SMALL_QUOTAS)
    by_key = {row.edition_key: row for row in selected}

    assert by_key["/books/OL999999M"].cover_id is None
