from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import time
from typing import Callable
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from primatis_data_seeding.normalization.isbn import is_valid_isbn, normalize_isbn


OPENLIBRARY_SEARCH_URL = "https://openlibrary.org/search.json"

LanguageQuotas = dict[str, tuple[str, int]]

PROFILE_LANGUAGE_QUOTAS: dict[str, LanguageQuotas] = {
    "small": {
        "FR": ("fre", 75),
        "EN": ("eng", 10),
        "NL": ("dut", 8),
        "DE": ("ger", 3),
        "ES": ("spa", 2),
        "IT": ("ita", 1),
        "LA": ("lat", 1),
    },
    "medium": {
        "FR": ("fre", 750),
        "EN": ("eng", 100),
        "NL": ("dut", 80),
        "DE": ("ger", 30),
        "ES": ("spa", 20),
        "IT": ("ita", 15),
        "LA": ("lat", 5),
    },
}

EDITION_FIELDS = (
    "key",
    "title",
    "subtitle",
    "language",
    "isbn",
    "isbn_10",
    "isbn_13",
    "publisher",
    "publish_date",
    "publish_year",
    "number_of_pages",
)

SEARCH_FIELDS = (
    "key",
    "title",
    "author_name",
    "author_key",
    "subject",
    "cover_i",
    "editions",
    *(f"editions.{field}" for field in EDITION_FIELDS),
)


@dataclass(frozen=True)
class OpenLibraryEditionCandidate:
    language: str
    language_code: str
    work_key: str
    edition_key: str
    title: str
    subtitle: str | None
    author_keys: tuple[str, ...]
    author_names: tuple[str, ...]
    subjects: tuple[str, ...]
    isbn_10: tuple[str, ...]
    isbn_13: tuple[str, ...]
    isbn: tuple[str, ...]
    publishers: tuple[str, ...]
    publish_date: str | None
    publish_year: int | None
    number_of_pages: int | None
    cover_id: int | None


def _as_tuple(value) -> tuple[str, ...]:
    if value is None:
        return ()
    if isinstance(value, list):
        return tuple(str(item) for item in value if item not in (None, ""))
    return (str(value),)


def build_search_url(language_code: str, limit: int) -> str:
    if limit <= 0 or limit > 1000:
        raise ValueError("Open Library Search limit must be between 1 and 1000.")
    params = {
        "q": f"language:{language_code}",
        "fields": ",".join(SEARCH_FIELDS),
        "sort": "key",
        "limit": str(limit),
    }
    return f"{OPENLIBRARY_SEARCH_URL}?{urlencode(params)}"


def fetch_search_payload(
    language_code: str,
    *,
    limit: int,
    contact: str,
    timeout_seconds: int = 30,
) -> dict:
    if not contact or "@" not in contact:
        raise ValueError(
            "Open Library contact must be an email address for an identified User-Agent."
        )

    request = Request(
        build_search_url(language_code, limit),
        headers={
            "Accept": "application/json",
            "User-Agent": f"PRIMATIS-Data-Seeding/0.1 ({contact})",
        },
    )
    with urlopen(request, timeout=timeout_seconds) as response:
        payload = json.load(response)

    if not isinstance(payload, dict) or not isinstance(payload.get("docs"), list):
        raise ValueError("Unexpected Open Library Search API response.")
    return payload


def save_raw_payload(payload: dict, path: Path) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = (
        json.dumps(payload, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    ).encode("utf-8")
    path.write_bytes(encoded)
    return hashlib.sha256(encoded).hexdigest()


def load_raw_payload(path: Path) -> dict:
    if not path.is_file():
        raise ValueError(f"Open Library snapshot not found: {path}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict) or not isinstance(payload.get("docs"), list):
        raise ValueError(f"Invalid Open Library snapshot: {path}")
    return payload


def _normalize_cover_id(value: object) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value if value > 0 else None
    return None


def _extract_candidate(work: dict, *, language: str, language_code: str):
    work_key = str(work.get("key") or "")
    author_keys = _as_tuple(work.get("author_key"))
    author_names = _as_tuple(work.get("author_name"))
    cover_id = _normalize_cover_id(work.get("cover_i"))

    editions = work.get("editions")
    docs = editions.get("docs") if isinstance(editions, dict) else None
    if not isinstance(docs, list) or not docs:
        return None

    edition = docs[0]
    if not isinstance(edition, dict):
        return None

    edition_key = str(edition.get("key") or "")
    title = str(edition.get("title") or "").strip()
    languages = set(_as_tuple(edition.get("language")))

    if not work_key or not edition_key.startswith("/books/") or not title:
        return None
    if language_code not in languages:
        return None
    if not author_keys or not author_names:
        return None

    paired_count = min(len(author_keys), len(author_names))
    if paired_count == 0:
        return None

    publish_year = edition.get("publish_year")
    if isinstance(publish_year, list):
        publish_year = publish_year[0] if publish_year else None
    try:
        publish_year = int(publish_year) if publish_year not in (None, "") else None
    except (TypeError, ValueError):
        publish_year = None

    pages = edition.get("number_of_pages")
    try:
        pages = int(pages) if pages not in (None, "") else None
    except (TypeError, ValueError):
        pages = None

    return OpenLibraryEditionCandidate(
        language=language,
        language_code=language_code,
        work_key=work_key,
        edition_key=edition_key,
        title=title,
        subtitle=(
            str(edition["subtitle"]).strip()
            if edition.get("subtitle") not in (None, "")
            else None
        ),
        author_keys=author_keys[:paired_count],
        author_names=author_names[:paired_count],
        subjects=_as_tuple(work.get("subject")),
        isbn_10=_as_tuple(edition.get("isbn_10")),
        isbn_13=_as_tuple(edition.get("isbn_13")),
        isbn=_as_tuple(edition.get("isbn")),
        publishers=_as_tuple(edition.get("publisher")),
        publish_date=(
            str(edition["publish_date"]).strip()
            if edition.get("publish_date") not in (None, "")
            else None
        ),
        publish_year=publish_year,
        number_of_pages=pages if pages and pages > 0 else None,
        cover_id=cover_id,
    )


def _candidate_valid_isbns(
    candidate: OpenLibraryEditionCandidate,
) -> set[str]:
    valid: set[str] = set()

    for collection in (
        candidate.isbn_13,
        candidate.isbn_10,
        candidate.isbn,
    ):
        for raw in collection:
            normalized = normalize_isbn(raw)
            if normalized and is_valid_isbn(normalized):
                valid.add(normalized)

    return valid


def select_candidates(
    payloads: dict[str, dict],
    *,
    quotas: LanguageQuotas,
) -> list[OpenLibraryEditionCandidate]:
    selected: list[OpenLibraryEditionCandidate] = []
    seen_editions: set[str] = set()
    seen_valid_isbns: set[str] = set()

    for language, (language_code, quota) in quotas.items():
        payload = payloads.get(language)
        if payload is None:
            raise ValueError(f"Missing Open Library payload for language {language}.")

        language_candidates: list[OpenLibraryEditionCandidate] = []
        for work in payload["docs"]:
            candidate = _extract_candidate(
                work, language=language, language_code=language_code
            )
            if candidate is None or candidate.edition_key in seen_editions:
                continue

            valid_isbns = _candidate_valid_isbns(candidate)
            if valid_isbns & seen_valid_isbns:
                continue

            language_candidates.append(candidate)
            seen_editions.add(candidate.edition_key)
            seen_valid_isbns.update(valid_isbns)

            if len(language_candidates) == quota:
                break

        if len(language_candidates) != quota:
            raise ValueError(
                f"Insufficient valid Open Library editions for {language}: "
                f"selected={len(language_candidates)} required={quota}."
            )
        selected.extend(language_candidates)

    expected_total = sum(quota for _, quota in quotas.values())
    if len(selected) != expected_total:
        raise AssertionError(
            "Selection must contain exactly "
            f"{expected_total} editions, got {len(selected)}."
        )
    return selected


def snapshot_paths(raw_dir: Path, *, quotas: LanguageQuotas) -> dict[str, Path]:
    return {
        language: raw_dir / f"search_{language.lower()}.json"
        for language in quotas
    }


def has_complete_snapshot(raw_dir: Path, *, quotas: LanguageQuotas) -> bool:
    return all(
        path.is_file() for path in snapshot_paths(raw_dir, quotas=quotas).values()
    )


def load_snapshot_payloads(raw_dir: Path, *, quotas: LanguageQuotas) -> dict[str, dict]:
    paths = snapshot_paths(raw_dir, quotas=quotas)
    missing = [str(path) for path in paths.values() if not path.is_file()]
    if missing:
        raise ValueError(
            "Incomplete Open Library snapshot; missing: " + ", ".join(missing)
        )
    return {
        language: load_raw_payload(path)
        for language, path in paths.items()
    }


def acquire_openlibrary(
    raw_dir: Path,
    *,
    quotas: LanguageQuotas,
    contact: str,
    profile: str | None = None,
    overfetch_factor: int = 4,
    sleep_seconds: float = 0.4,
    fetcher: Callable[..., dict] = fetch_search_payload,
) -> tuple[list[OpenLibraryEditionCandidate], dict]:
    if overfetch_factor < 2:
        raise ValueError("overfetch_factor must be >= 2.")

    raw_dir.mkdir(parents=True, exist_ok=True)
    payloads: dict[str, dict] = {}
    raw_files: list[dict[str, str]] = []

    for index, (language, (language_code, quota)) in enumerate(quotas.items()):
        limit = min(1000, max(quota * overfetch_factor, quota + 20))
        payload = fetcher(language_code, limit=limit, contact=contact)
        payloads[language] = payload

        path = raw_dir / f"search_{language.lower()}.json"
        digest = save_raw_payload(payload, path)
        raw_files.append({
            "language": language,
            "language_code": language_code,
            "file": str(path),
            "sha256": digest,
        })

        if index < len(quotas) - 1 and sleep_seconds > 0:
            time.sleep(sleep_seconds)

    selected = select_candidates(payloads, quotas=quotas)
    purpose = (
        f"PRIMATIS {profile} profile only; low-volume acquisition"
        if profile
        else "PRIMATIS low-volume acquisition"
    )
    manifest = {
        "source": "Open Library Search API",
        "source_url": OPENLIBRARY_SEARCH_URL,
        "acquired_at": datetime.now(timezone.utc).isoformat(),
        "contact": contact,
        "purpose": purpose,
        "raw_files": raw_files,
        "selected_count": len(selected),
        "language_counts": {
            language: sum(1 for row in selected if row.language == language)
            for language in quotas
        },
    }
    (raw_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return selected, manifest


def reuse_openlibrary_snapshot(
    raw_dir: Path,
    *,
    quotas: LanguageQuotas,
) -> tuple[list[OpenLibraryEditionCandidate], dict]:
    payloads = load_snapshot_payloads(raw_dir, quotas=quotas)
    selected = select_candidates(payloads, quotas=quotas)

    manifest_path = raw_dir / "manifest.json"
    existing_manifest = {}
    if manifest_path.is_file():
        loaded = json.loads(manifest_path.read_text(encoding="utf-8"))
        if isinstance(loaded, dict):
            existing_manifest = loaded

    manifest = {
        **existing_manifest,
        "source": existing_manifest.get("source", "Open Library Search API"),
        "source_url": existing_manifest.get("source_url", OPENLIBRARY_SEARCH_URL),
        "selected_count": len(selected),
        "language_counts": {
            language: sum(1 for row in selected if row.language == language)
            for language in quotas
        },
        "reuse_mode": True,
        "revalidated_at": datetime.now(timezone.utc).isoformat(),
    }
    return selected, manifest


def write_selected_jsonl(
    selected: list[OpenLibraryEditionCandidate],
    path: Path,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in selected:
            handle.write(
                json.dumps(asdict(row), ensure_ascii=False, sort_keys=True) + "\n"
            )
