from pathlib import Path

import pytest

from primatis_data_seeding.acquisition.openlibrary_covers import (
    cover_asset_filename,
    cover_asset_path,
    cover_image_url,
    materialize_cover,
    materialize_covers,
    resolve_cover_image_url,
)


def test_cover_asset_filename_is_deterministic() -> None:
    assert cover_asset_filename(258027) == "ol-cover-258027.jpg"


def test_cover_asset_filename_rejects_non_positive_id() -> None:
    with pytest.raises(ValueError):
        cover_asset_filename(0)
    with pytest.raises(ValueError):
        cover_asset_filename(-1)


def test_cover_image_url_is_internal_and_deterministic() -> None:
    assert cover_image_url(258027) == "/covers/catalogue/ol-cover-258027.jpg"


def test_cover_asset_path_is_scoped_to_assets_dir(tmp_path: Path) -> None:
    assert cover_asset_path(tmp_path, 258027) == tmp_path / "ol-cover-258027.jpg"


def test_resolve_cover_image_url_is_none_without_cover_id(tmp_path: Path) -> None:
    assert resolve_cover_image_url(None, assets_dir=tmp_path) is None


def test_resolve_cover_image_url_is_none_when_asset_missing(tmp_path: Path) -> None:
    # Fail-closed: no local file materialized yet -> NULL, never the
    # external Open Library URL, never a guess.
    assert resolve_cover_image_url(258027, assets_dir=tmp_path) is None


def test_resolve_cover_image_url_when_asset_present(tmp_path: Path) -> None:
    (tmp_path / "ol-cover-258027.jpg").write_bytes(b"fake-jpeg-bytes")

    assert (
        resolve_cover_image_url(258027, assets_dir=tmp_path)
        == "/covers/catalogue/ol-cover-258027.jpg"
    )


def test_materialize_cover_writes_via_injected_fetcher(tmp_path: Path) -> None:
    calls: list[int] = []

    def fetcher(cover_id: int) -> bytes:
        calls.append(cover_id)
        return b"fake-jpeg-bytes"

    path = materialize_cover(258027, assets_dir=tmp_path, fetcher=fetcher)

    assert path == tmp_path / "ol-cover-258027.jpg"
    assert path.read_bytes() == b"fake-jpeg-bytes"
    assert calls == [258027]


def test_materialize_cover_skips_network_when_already_present(tmp_path: Path) -> None:
    (tmp_path / "ol-cover-258027.jpg").write_bytes(b"already-there")
    calls: list[int] = []

    def fetcher(cover_id: int) -> bytes:
        calls.append(cover_id)
        return b"should-not-be-used"

    path = materialize_cover(258027, assets_dir=tmp_path, fetcher=fetcher)

    assert path.read_bytes() == b"already-there"
    assert calls == []


def test_materialize_cover_overwrite_forces_refetch(tmp_path: Path) -> None:
    (tmp_path / "ol-cover-258027.jpg").write_bytes(b"stale")

    def fetcher(cover_id: int) -> bytes:
        return b"fresh"

    path = materialize_cover(
        258027, assets_dir=tmp_path, fetcher=fetcher, overwrite=True
    )

    assert path.read_bytes() == b"fresh"


def test_materialize_cover_rejects_empty_payload(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        materialize_cover(258027, assets_dir=tmp_path, fetcher=lambda cover_id: b"")


def test_materialize_covers_only_processes_the_explicit_list(tmp_path: Path) -> None:
    # No implicit "materialize everything referenced by a bundle" behavior:
    # exactly (and only) the ids passed in are ever touched.
    calls: list[int] = []

    def fetcher(cover_id: int) -> bytes:
        calls.append(cover_id)
        return f"bytes-{cover_id}".encode()

    result = materialize_covers([1, 2], assets_dir=tmp_path, fetcher=fetcher)

    assert sorted(calls) == [1, 2]
    assert set(result) == {1, 2}
    assert (tmp_path / "ol-cover-1.jpg").is_file()
    assert (tmp_path / "ol-cover-2.jpg").is_file()
    assert not (tmp_path / "ol-cover-3.jpg").is_file()
