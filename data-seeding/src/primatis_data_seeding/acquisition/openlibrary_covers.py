from __future__ import annotations

import argparse
from pathlib import Path
from typing import Callable
from urllib.request import Request, urlopen


# Open Library Covers API: https://openlibrary.org/dev/docs/api/covers
COVERS_API_URL_TEMPLATE = "https://covers.openlibrary.org/b/id/{cover_id}-L.jpg"


def cover_asset_filename(cover_id: int) -> str:
    if not isinstance(cover_id, int) or isinstance(cover_id, bool) or cover_id <= 0:
        raise ValueError(f"cover_id must be a strictly positive int, got {cover_id!r}.")
    return f"ol-cover-{cover_id}.jpg"


def cover_asset_path(assets_dir: Path, cover_id: int) -> Path:
    return assets_dir / cover_asset_filename(cover_id)


def cover_image_url(cover_id: int) -> str:
    # PRIMATIS internal URL, never the external Open Library URL.
    return f"/covers/catalogue/{cover_asset_filename(cover_id)}"


def resolve_cover_image_url(
    cover_id: int | None,
    *,
    assets_dir: Path,
) -> str | None:
    """Fail-closed resolution: a cover URL is only returned when the local
    asset file actually exists on disk. Title.cover_image_url must never
    point to a file that was not really materialized."""
    if cover_id is None:
        return None
    if not cover_asset_path(assets_dir, cover_id).is_file():
        return None
    return cover_image_url(cover_id)


def fetch_cover_image(
    cover_id: int,
    *,
    contact: str,
    timeout_seconds: int = 30,
) -> bytes:
    if not contact or "@" not in contact:
        raise ValueError(
            "Open Library contact must be an email address for an identified User-Agent."
        )
    request = Request(
        COVERS_API_URL_TEMPLATE.format(cover_id=cover_id),
        headers={
            "Accept": "image/jpeg",
            "User-Agent": f"PRIMATIS-Data-Seeding/0.1 ({contact})",
        },
    )
    with urlopen(request, timeout=timeout_seconds) as response:
        return response.read()


def materialize_cover(
    cover_id: int,
    *,
    assets_dir: Path,
    fetcher: Callable[..., bytes],
    overwrite: bool = False,
) -> Path:
    """Materializes exactly ONE cover asset locally. This is the only
    write path for cover images; it is never invoked automatically for an
    entire bundle/profile — the caller must explicitly name each cover_id
    to materialize (see materialize_covers / the dedicated CLI below)."""
    path = cover_asset_path(assets_dir, cover_id)
    if path.is_file() and not overwrite:
        return path

    content = fetcher(cover_id)
    if not content:
        raise ValueError(f"Empty cover image payload for cover_id={cover_id}.")

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    return path


def materialize_covers(
    cover_ids: list[int],
    *,
    assets_dir: Path,
    fetcher: Callable[..., bytes],
    overwrite: bool = False,
) -> dict[int, Path]:
    """Materializes an EXPLICIT, caller-provided list of cover_ids only.

    There is no "materialize every cover referenced by a bundle" helper in
    this module by design: bulk crawling of the Open Library Covers API is
    a deliberate, separately-authorized step (see DEV-13.19.C §3), never an
    automatic side effect of building a profile bundle.
    """
    return {
        cover_id: materialize_cover(
            cover_id, assets_dir=assets_dir, fetcher=fetcher, overwrite=overwrite
        )
        for cover_id in cover_ids
    }


def _parse_cover_ids(args: argparse.Namespace) -> list[int]:
    raw_ids: list[str] = []
    if args.cover_ids:
        raw_ids.extend(part.strip() for part in args.cover_ids.split(","))
    if args.cover_ids_file:
        raw_ids.extend(
            line.strip()
            for line in args.cover_ids_file.read_text(encoding="utf-8").splitlines()
        )

    cover_ids: list[int] = []
    for raw in raw_ids:
        if not raw:
            continue
        try:
            cover_id = int(raw)
        except ValueError as exc:
            raise SystemExit(f"Invalid cover_id: {raw!r}.") from exc
        if cover_id <= 0:
            raise SystemExit(f"Invalid cover_id: {raw!r} (must be strictly positive).")
        cover_ids.append(cover_id)

    if not cover_ids:
        raise SystemExit(
            "No cover_id provided. Pass --cover-ids and/or --cover-ids-file "
            "with an EXPLICIT, bounded list — this tool never crawls a full "
            "bundle/profile automatically."
        )
    return cover_ids


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Materialize a SMALL, EXPLICIT list of Open Library covers "
            "locally under primatis-web/public/covers/catalogue/. This tool "
            "never derives the list of cover_id automatically from a "
            "profile/bundle — the caller must name each cover_id."
        )
    )
    parser.add_argument(
        "--cover-ids",
        default=None,
        help="Comma-separated list of Open Library cover_id (e.g. 258027,258028).",
    )
    parser.add_argument(
        "--cover-ids-file",
        type=Path,
        default=None,
        help="Text file with one cover_id per line.",
    )
    parser.add_argument(
        "--assets-dir",
        type=Path,
        default=Path("primatis-web/public/covers/catalogue"),
    )
    parser.add_argument(
        "--contact",
        required=True,
        help="Contact e-mail for the Open Library Covers API User-Agent.",
    )
    parser.add_argument("--overwrite", action="store_true")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    cover_ids = _parse_cover_ids(args)

    def fetcher(cover_id: int) -> bytes:
        return fetch_cover_image(cover_id, contact=args.contact)

    materialized = materialize_covers(
        cover_ids,
        assets_dir=args.assets_dir,
        fetcher=fetcher,
        overwrite=args.overwrite,
    )
    for cover_id, path in sorted(materialized.items()):
        print(f"cover_id={cover_id} -> {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
