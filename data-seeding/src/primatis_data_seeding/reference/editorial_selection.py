"""Loader for the editorial "socle" per language (DEV-16.2 §4,
DEC-16.3-02, DEV-16.3 Étape D).

Reads `data-seeding/reference/catalogue/<language>.toml` — an explicit,
traceable, revisable editorial selection of reference authors per
supported language. Each entry only carries an `author_key` (a real,
verified Open Library identifier — see DEV-16.3 report §F for how each
one was verified) and metadata ABOUT the selection itself; it is never a
source of bibliographic data on its own.

Explicitly NOT a bibliographic dataset: this module produces nothing a
loader could import directly into `author`/`title`. Its only use is to
bias/prioritize a real acquisition batch (`pipeline/batch.py`) toward a
known editorial reference author for a given language — the actual
Title/Edition metadata always still comes from a real bibliographic
source (Open Library), never from this file.
"""

from __future__ import annotations

import tomllib
from dataclasses import dataclass
from pathlib import Path

_SUPPORTED_LANGUAGES = frozenset({"FR", "EN", "NL", "DE", "ES", "IT", "LA"})

_DEFAULT_CATALOGUE_DIR = Path(__file__).resolve().parents[3] / "reference" / "catalogue"


@dataclass(frozen=True)
class EditorialAuthor:
    author_key: str
    full_name: str
    verified_at: str
    verified_via: str
    selection_rationale: str
    work_count_at_verification: int | None = None
    # DEV-16.5 §5: optional diversity metadata (period/form) — not
    # required so pre-existing DEV-16.3 pilot entries without them
    # (none remain after the DEV-16.5 expansion, kept optional for
    # forward compatibility) stay valid.
    period: str | None = None
    form: str | None = None


def load_editorial_selection(
    language: str,
    *,
    catalogue_dir: Path = _DEFAULT_CATALOGUE_DIR,
) -> tuple[EditorialAuthor, ...]:
    """Loads the editorial socle for one supported language.

    Returns an empty tuple if the language has no socle file yet (the
    socle is meant to grow by batches, DEV-16.3 §D — an empty/missing
    file is a legitimate "not populated yet" state, not an error).
    """
    if language not in _SUPPORTED_LANGUAGES:
        raise ValueError(f"Unsupported language: {language!r}.")

    path = catalogue_dir / f"{language.lower()}.toml"
    if not path.is_file():
        return ()

    with path.open("rb") as handle:
        data = tomllib.load(handle)

    authors_data = data.get("authors")
    if not isinstance(authors_data, list):
        raise ValueError(f"Invalid editorial catalogue file: {path} (missing [[authors]]).")

    result = []
    for entry in authors_data:
        try:
            result.append(EditorialAuthor(
                author_key=str(entry["author_key"]),
                full_name=str(entry["full_name"]),
                verified_at=str(entry["verified_at"]),
                verified_via=str(entry["verified_via"]),
                selection_rationale=str(entry["selection_rationale"]),
                work_count_at_verification=(
                    int(entry["work_count_at_verification"])
                    if "work_count_at_verification" in entry
                    else None
                ),
                period=entry.get("period"),
                form=entry.get("form"),
            ))
        except KeyError as exc:
            raise ValueError(f"Invalid editorial author entry in {path}: missing {exc}.") from exc

    if len({a.author_key for a in result}) != len(result):
        raise ValueError(f"Duplicate author_key in editorial catalogue: {path}.")

    return tuple(result)


def load_all_editorial_selections(
    *,
    catalogue_dir: Path = _DEFAULT_CATALOGUE_DIR,
) -> dict[str, tuple[EditorialAuthor, ...]]:
    return {
        language: load_editorial_selection(language, catalogue_dir=catalogue_dir)
        for language in sorted(_SUPPORTED_LANGUAGES)
    }
