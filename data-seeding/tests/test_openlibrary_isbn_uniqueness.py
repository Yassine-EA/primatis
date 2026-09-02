from primatis_data_seeding.acquisition.openlibrary import select_candidates


def _work(index: int, *, isbn: str) -> dict:
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
                "isbn": [isbn],
            }]
        },
    }


def test_selection_skips_second_candidate_with_same_valid_isbn() -> None:
    quotas = {"FR": ("fre", 2)}
    payloads = {
        "FR": {
            "docs": [
                _work(1, isbn="9782842740979"),
                _work(2, isbn="9782842740979"),
                _work(3, isbn="9780306406157"),
            ]
        }
    }

    selected = select_candidates(payloads, quotas=quotas)

    assert [row.edition_key for row in selected] == [
        "/books/OL1M",
        "/books/OL3M",
    ]


def test_selection_does_not_treat_invalid_isbn_as_uniqueness_key() -> None:
    quotas = {"FR": ("fre", 2)}
    payloads = {
        "FR": {
            "docs": [
                _work(1, isbn="9780306406158"),
                _work(2, isbn="9780306406158"),
            ]
        }
    }

    selected = select_candidates(payloads, quotas=quotas)

    assert len(selected) == 2
