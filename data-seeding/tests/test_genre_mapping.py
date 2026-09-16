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


# DEV-16.3 Étape E: aliases added after measuring real subject frequency
# on the 1000-work medium sample (see mapping/genres.py comment + DEV-16.3
# report §G for the full frequency table).
def test_maps_subject_aliases_added_from_medium_frequency_measurement() -> None:
    assert map_subjects_to_genre_codes(["Histoire"]) == ("HISTORY",)
    assert map_subjects_to_genre_codes(["Politics and government"]) == ("POLITICS",)
    assert map_subjects_to_genre_codes(["Historical Fiction"]) == ("FICTION",)
    assert map_subjects_to_genre_codes(
        ["Fiction, romance, historical, general"]
    ) == ("ROMANCE",)
    assert map_subjects_to_genre_codes(
        ["Fiction, mystery & detective, general"]
    ) == ("MYSTERY",)
    assert map_subjects_to_genre_codes(
        ["Fiction, mystery & detective, police procedural"]
    ) == ("MYSTERY",)
    assert map_subjects_to_genre_codes(["Economic conditions"]) == ("BUSINESS",)
    assert map_subjects_to_genre_codes(["Description and travel"]) == ("TRAVEL",)
    assert map_subjects_to_genre_codes(["Modern Art"]) == ("ART",)


def test_frequent_but_ambiguous_subjects_stay_unmapped() -> None:
    # Deliberately excluded (DEV-16.3 §G): no unambiguous 1:1 Genre exists.
    assert map_subjects_to_genre_codes(["Civilization"]) == ()
    assert map_subjects_to_genre_codes(["Criticism and interpretation"]) == ()
    assert map_subjects_to_genre_codes(["Folklore"]) == ()
    assert map_subjects_to_genre_codes(["Dictionaries"]) == ()
