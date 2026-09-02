from primatis_data_seeding.mapping.genres import (
    GENRES,
    map_subjects_to_genre_codes,
)


def test_genre_codes_and_labels_are_unique() -> None:
    codes = [genre.code for genre in GENRES]
    labels = [genre.label for genre in GENRES]

    assert len(codes) == len(set(codes))
    assert len(labels) == len(set(labels))


def test_maps_only_explicit_subject_aliases() -> None:
    assert map_subjects_to_genre_codes(
        ["Science fiction", "History"]
    ) == ("HISTORY", "SCIENCE_FICTION")


def test_mapping_is_case_and_accent_tolerant_but_not_fuzzy() -> None:
    assert map_subjects_to_genre_codes(["POLITICAL SCIENCE"]) == ("POLITICS",)
    assert map_subjects_to_genre_codes(["History of Belgium"]) == ()


def test_duplicate_subjects_do_not_duplicate_genre_links() -> None:
    assert map_subjects_to_genre_codes(
        ["fiction", "Fiction", "FICTION"]
    ) == ("FICTION",)
