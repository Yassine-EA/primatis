from primatis_data_seeding.acquisition.openlibrary import select_candidates


def _work(index: int, *, isbns: list[str]) -> dict:
    return {
        "key": f"/works/OL{index}W",
        "author_key": [f"OL{index}A"],
        "author_name": [f"Author {index}"],
        "subject": ["Fiction"],
        "editions": {
            "docs": [{
                "key": f"/books/OL{index}M",
                "title": f"Book {index}",
                "language": ["fre"],
                "isbn": isbns,
            }]
        },
    }


def test_selection_skips_candidate_when_any_valid_isbn_overlaps() -> None:
    quotas = {"FR": ("fre", 2)}
    payloads = {
        "FR": {
            "docs": [
                _work(1, isbns=["2842740971", "9782842740979"]),
                _work(2, isbns=["9782842740979", "2842740971"]),
                _work(3, isbns=["9780306406157"]),
            ]
        }
    }

    selected = select_candidates(payloads, quotas=quotas)

    assert [row.edition_key for row in selected] == [
        "/books/OL1M",
        "/books/OL3M",
    ]


def test_selection_tracks_all_valid_isbns_of_accepted_candidate() -> None:
    quotas = {"FR": ("fre", 3)}
    payloads = {
        "FR": {
            "docs": [
                _work(1, isbns=["2842740971", "9782842740979"]),
                _work(2, isbns=["2842740971"]),
                _work(3, isbns=["9782842740979"]),
                _work(4, isbns=["9780306406157"]),
                _work(5, isbns=["9781861972712"]),
            ]
        }
    }

    selected = select_candidates(payloads, quotas=quotas)

    assert [row.edition_key for row in selected] == [
        "/books/OL1M",
        "/books/OL4M",
        "/books/OL5M",
    ]
