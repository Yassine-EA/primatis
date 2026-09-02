from primatis_data_seeding.pipeline.acquisition import BPOST_SOURCE_PAGE


def test_bpost_source_page_is_official_https() -> None:
    assert BPOST_SOURCE_PAGE.startswith("https://www.bpost.be/")
