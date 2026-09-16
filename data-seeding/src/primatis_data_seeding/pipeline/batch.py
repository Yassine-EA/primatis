"""Batch collection & validation pipeline (DEV-16.3 Étape/§13-§18,
DEV-16.4 pre-gate fixes §4.1/§4.3).

A "batch" is the unit of controlled, traceable acquisition demanded by
DEV-16.2 §11/§14: one (profile, language, documentary category, target
count) selection, run end to end (RAW -> NORMALIZED -> VALIDATED ->
ACCEPTED/REJECTED/QUARANTINED/MERGED -> report), independent of the
`small`/`medium` bundle path (`pipeline/bundle.py`), which stays
completely untouched — this module is additive, not a replacement.

Maximum reuse of already-validated pipeline internals on purpose:
Open Library candidate parsing (`acquisition/openlibrary.py::
_extract_candidate`) and the `SelectedEdition` JSONL round-trip
(`write_selected_jsonl`/`load_selected_editions`/
`normalize_selected_catalogue`, all from `pipeline/bundle.py`) are the
exact same functions `small`/`medium` already use — only the SELECTION
criteria (language + documentary category, not a fixed global quota
table) are new, per DEC-16.3-01/DEC-16.3-09.

DEV-16.4 §4.1 correction (editorial socle wiring): `BatchCriteria.
use_editorial_socle` biases selection toward the real Open Library
`author_key` values already verified in `reference/catalogue/*.toml`
(DEV-16.3 §F) — via a targeted `author_key:<key> language:<code>` Search
API query — WITHOUT ever loading a socle TOML entry as bibliographic
data itself; every record still comes from a genuine Open Library
response, parsed by the exact same `_extract_candidate` as any other
candidate.

DEV-16.4 §4.3 correction (RAW replay/cache): a rerun of `run_batch()`
with an IDENTICAL `BatchCriteria` + `reference_date` + `fetch_limit`
reuses the RAW payload(s) already saved on disk and performs NO new
Search API call — `refresh=True` forces a real re-fetch. Any change to
the criteria (language, category, count, profile, socle flag) changes
`batch_id` and/or the recorded query manifest, so a stale cache is never
silently reused across different criteria.

DEV-16.4 §4.1 bugfix note: DEV-16.3's `run_batch()` defined
`build_batch_search_url()` (with the `subject:` documentary-category
filter) but never actually called it — the default `payload_fetcher`
was `fetch_search_payload`, which builds its OWN url via
`acquisition/openlibrary.py::build_search_url()` (language only, no
category). The two DEV-16.3 pilot batches were therefore filtered by
LANGUAGE ONLY, not by documentary category — corrected here. See
DEV-16.4 report §4.1 for the full account; not hidden.
"""

from __future__ import annotations

import json
from collections import Counter
from dataclasses import asdict, dataclass, field
from datetime import date, datetime, timezone
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from primatis_data_seeding.acquisition.rate_limit import with_retries
from primatis_data_seeding.acquisition.openlibrary import (
    OPENLIBRARY_SEARCH_URL,
    SEARCH_FIELDS,
    OpenLibraryEditionCandidate,
    _candidate_valid_isbns,
    _extract_candidate,
    save_raw_payload,
    write_selected_jsonl,
)
from primatis_data_seeding.deduplication.catalogue import (
    deduplicate_authors,
    deduplicate_editions,
)
from primatis_data_seeding.mapping.catalogue import map_catalogue
from primatis_data_seeding.mapping.documentary_categories import (
    matches_documentary_category,
)
from primatis_data_seeding.pipeline.bundle import (
    load_selected_editions,
    normalize_selected_catalogue,
)
from primatis_data_seeding.provenance.record_provenance import (
    build_record_provenance,
    write_provenance_jsonl,
)
from primatis_data_seeding.quality.quarantine import apply_text_quality_policy
from primatis_data_seeding.reference.editorial_selection import (
    load_editorial_selection,
)

# DEV-16.2 §1: documentary categories, mapped to a plain-English Open
# Library subject search keyword. A pilot/demonstration mapping — real
# quota BOUNDS per category/language remain a DEV-16.4+ decision after
# the measurement DEC-16.3-01 calls for (never invented here).
DOCUMENTARY_CATEGORY_KEYWORDS: dict[str, str] = {
    "literature": "fiction",
    "youth": "juvenile",
    "history": "history",
    "science": "science",
    "philosophy_religion": "philosophy",
    "social_sciences": "social sciences",
    "arts": "art",
    "comics": "comics",
    "documentary": "travel",
}

LANGUAGE_CODES: dict[str, str] = {
    "FR": "fre", "EN": "eng", "NL": "dut", "DE": "ger",
    "ES": "spa", "IT": "ita", "LA": "lat",
}


@dataclass(frozen=True)
class BatchCriteria:
    profile: str  # small/medium/large/full — DB-targeting label only (config/profiles.toml)
    language: str  # one of LANGUAGE_CODES
    documentary_category: str  # one of DOCUMENTARY_CATEGORY_KEYWORDS
    count: int
    use_editorial_socle: bool = False  # DEV-16.4 §4.1

    def __post_init__(self) -> None:
        if self.language not in LANGUAGE_CODES:
            raise ValueError(f"Unsupported language: {self.language!r}.")
        if self.documentary_category not in DOCUMENTARY_CATEGORY_KEYWORDS:
            raise ValueError(f"Unsupported documentary category: {self.documentary_category!r}.")
        if self.count <= 0:
            raise ValueError("count must be > 0.")


def compute_batch_id(criteria: BatchCriteria, reference_date: date) -> str:
    """Deterministic, human-readable — never an opaque UUID (DEV-16.2 §13)."""
    socle_suffix = "-socle" if criteria.use_editorial_socle else ""
    return (
        f"{reference_date.isoformat()}-{criteria.profile}-"
        f"{criteria.language.lower()}-{criteria.documentary_category}-"
        f"{criteria.count:04d}{socle_suffix}"
    )


def build_batch_search_url(
    criteria: BatchCriteria,
    *,
    limit: int,
    author_key: str | None = None,
) -> str:
    """Builds the real Search API URL for one query of a batch.

    `author_key`, when given, replaces the generic `subject:<category>`
    filter with a targeted `author_key:<key>` filter — used ONLY to
    prioritize a real, already-verified editorial-socle author
    (DEV-16.4 §4.1); the category filter is dropped for that query
    because a canonical reference author's own bibliography should not
    be further narrowed by a generic English subject keyword.
    """
    language_code = LANGUAGE_CODES[criteria.language]
    if author_key is not None:
        q = f"author_key:{author_key} language:{language_code}"
    else:
        keyword = DOCUMENTARY_CATEGORY_KEYWORDS[criteria.documentary_category]
        q = f"language:{language_code} subject:{keyword}"
    params = {
        "q": q,
        "fields": ",".join(SEARCH_FIELDS),
        "sort": "key",
        "limit": str(limit),
    }
    return f"{OPENLIBRARY_SEARCH_URL}?{urlencode(params)}"


def fetch_batch_payload(url: str, *, contact: str, timeout_seconds: int = 30) -> dict:
    """Performs the real HTTP GET for one already-built Search API URL —
    same discipline as `acquisition/openlibrary.py::fetch_search_payload`
    (identified User-Agent required, payload shape validated), just
    parameterized by a pre-built `url` instead of building it internally
    (so `pipeline/batch.py` controls the exact query, category filter
    included — DEV-16.4 §4.1 bugfix).
    """
    if not contact or "@" not in contact:
        raise ValueError(
            "Open Library contact must be an email address for an identified User-Agent."
        )
    request = Request(
        url,
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


@dataclass(frozen=True)
class QueryPlanEntry:
    label: str  # "generic" | "socle:<author_key>"
    url: str
    author_key: str | None = None


def build_query_plan(
    criteria: BatchCriteria,
    *,
    fetch_limit: int,
    socle_fetch_limit: int = 20,
) -> list[QueryPlanEntry]:
    """One query per editorial-socle author (if `use_editorial_socle`),
    PLUS always the generic (language, category) query — socle queries
    are listed FIRST so their candidates are naturally prioritized by
    `select_batch_candidates_from_payloads()` (first-seen wins the
    dedup race, same discipline as everywhere else in this pipeline).
    """
    plan: list[QueryPlanEntry] = []
    if criteria.use_editorial_socle:
        for author in load_editorial_selection(criteria.language):
            plan.append(QueryPlanEntry(
                label=f"socle:{author.author_key}",
                url=build_batch_search_url(criteria, limit=socle_fetch_limit, author_key=author.author_key),
                author_key=author.author_key,
            ))
    plan.append(QueryPlanEntry(
        label="generic",
        url=build_batch_search_url(criteria, limit=fetch_limit),
    ))
    return plan


def select_batch_candidates(
    payload: dict,
    criteria: BatchCriteria,
    *,
    already_seen_editions: set[str] | None = None,
    already_seen_valid_isbns: set[str] | None = None,
    remaining: int | None = None,
) -> list[OpenLibraryEditionCandidate]:
    """Parses a raw Search API payload into up to `remaining` (default
    `criteria.count`) unique candidates — same de-duplication-by-
    edition_key/valid-ISBN discipline as `acquisition/openlibrary.py::
    select_candidates`. `already_seen_*` allow accumulating a dedup
    state ACROSS multiple payloads (socle queries + generic query),
    mutated in place so the caller's running state stays authoritative.
    """
    language_code = LANGUAGE_CODES[criteria.language]
    seen_editions = already_seen_editions if already_seen_editions is not None else set()
    seen_valid_isbns = already_seen_valid_isbns if already_seen_valid_isbns is not None else set()
    limit = criteria.count if remaining is None else remaining

    selected: list[OpenLibraryEditionCandidate] = []
    for work in payload.get("docs", []):
        candidate = _extract_candidate(work, language=criteria.language, language_code=language_code)
        if candidate is None or candidate.edition_key in seen_editions:
            continue
        valid_isbns = _candidate_valid_isbns(candidate)
        if valid_isbns & seen_valid_isbns:
            continue
        selected.append(candidate)
        seen_editions.add(candidate.edition_key)
        seen_valid_isbns.update(valid_isbns)
        if len(selected) == limit:
            break

    return selected


@dataclass
class BatchReport:
    batch_id: str
    source: str
    acquired_at: str
    criteria: BatchCriteria
    received_count: int
    normalized_count: int
    accepted_count: int
    rejected_count: int
    quarantined_count: int
    duplicate_count: int
    candidate_count: int  # ambiguous_match + edition_variant, never merged
    from_cache: bool = False
    socle_candidate_counts: dict[str, int] = field(default_factory=dict)
    languages: dict[str, int] = field(default_factory=dict)
    documentary_categories: dict[str, int] = field(default_factory=dict)
    documentary_category_match_rate: float | None = None
    isbn_coverage: float = 0.0
    publisher_coverage: float = 0.0
    year_coverage: float = 0.0
    page_count_coverage: float = 0.0
    summary_coverage: float = 0.0
    genre_coverage: float = 0.0
    cover_coverage: float = 0.0
    text_quality_anomalies: dict[str, int] = field(default_factory=dict)
    rejection_reasons: dict[str, int] = field(default_factory=dict)
    quarantine_reasons: dict[str, int] = field(default_factory=dict)

    def to_dict(self) -> dict:
        payload = asdict(self)
        payload["criteria"] = asdict(self.criteria)
        return payload


def _coverage(titles: list, attr: str) -> float:
    if not titles:
        return 0.0
    present = sum(1 for t in titles if getattr(t, attr) is not None)
    return round(present / len(titles), 4)


def build_batch_report(
    *,
    criteria: BatchCriteria,
    batch_id: str,
    acquired_at: str,
    received_count: int,
    mapping_result,
    dedup_editions_result,
    dedup_authors_result,
    quality_result,
    from_cache: bool = False,
    socle_candidate_counts: dict[str, int] | None = None,
    subjects_by_work_key: dict[str, tuple[str, ...]] | None = None,
    edition_to_work_key: dict[str, str | None] | None = None,
) -> BatchReport:
    accepted_titles = quality_result.titles
    title_quarantine_reasons = Counter(
        entry.reason_code for entry in quality_result.quarantined if entry.entity == "title"
    )
    author_quarantine_reasons = Counter(
        entry.reason_code for entry in quality_result.quarantined if entry.entity == "author"
    )
    quarantine_reasons = title_quarantine_reasons + author_quarantine_reasons

    rejection_reasons = Counter(item.code for item in mapping_result.rejections)

    genre_coverage = 0.0
    if accepted_titles:
        titles_with_genre = {tg.title_source_key for tg in mapping_result.title_genres}
        covered = sum(1 for t in accepted_titles if t.source_key in titles_with_genre)
        genre_coverage = round(covered / len(accepted_titles), 4)

    languages = Counter(t.language for t in accepted_titles)

    # DEV-16.4 §7: precision documentaire — exact-alias signal, never
    # used to reject, only measured/reported (see documentary_categories.py).
    documentary_category_match_rate: float | None = None
    if accepted_titles and subjects_by_work_key is not None and edition_to_work_key is not None:
        matched = 0
        for title in accepted_titles:
            work_key = edition_to_work_key.get(title.source_key)
            subjects = subjects_by_work_key.get(work_key, ()) if work_key else ()
            if matches_documentary_category(subjects, criteria.documentary_category):
                matched += 1
        documentary_category_match_rate = round(matched / len(accepted_titles), 4)

    text_quality_anomalies: Counter = Counter()
    for decisions in quality_result.field_decisions.values():
        for decision in decisions:
            if decision.outcome != "KEPT":
                for code in decision.reason.split("; "):
                    text_quality_anomalies[code] += 1

    return BatchReport(
        batch_id=batch_id,
        source="openlibrary_search",
        acquired_at=acquired_at,
        criteria=criteria,
        received_count=received_count,
        normalized_count=len(mapping_result.titles) + len(mapping_result.rejections),
        accepted_count=len(accepted_titles),
        rejected_count=len(mapping_result.rejections),
        quarantined_count=len(quality_result.quarantined),
        duplicate_count=len(dedup_editions_result.duplicates) + len(dedup_authors_result.duplicates),
        candidate_count=len(dedup_editions_result.candidates) + len(dedup_authors_result.candidates),
        from_cache=from_cache,
        socle_candidate_counts=socle_candidate_counts or {},
        languages=dict(languages),
        documentary_categories={criteria.documentary_category: len(accepted_titles)},
        documentary_category_match_rate=documentary_category_match_rate,
        isbn_coverage=_coverage(accepted_titles, "isbn"),
        publisher_coverage=_coverage(accepted_titles, "publisher"),
        year_coverage=_coverage(accepted_titles, "publication_year"),
        page_count_coverage=_coverage(accepted_titles, "page_count"),
        summary_coverage=_coverage(accepted_titles, "summary"),
        genre_coverage=genre_coverage,
        cover_coverage=_coverage(accepted_titles, "cover_image_url"),
        text_quality_anomalies=dict(text_quality_anomalies),
        rejection_reasons=dict(rejection_reasons),
        quarantine_reasons=dict(quarantine_reasons),
    )


def _raw_manifest_path(batch_dir: Path) -> Path:
    return batch_dir / "raw_manifest.json"


def _raw_payload_path(batch_dir: Path, label: str) -> Path:
    safe_label = label.replace(":", "_").replace("/", "_")
    return batch_dir / f"raw_{safe_label}.json"


def _manifest_matches(
    manifest: dict,
    *,
    criteria: BatchCriteria,
    reference_date: date,
    query_plan: list[QueryPlanEntry],
) -> bool:
    return (
        manifest.get("criteria") == asdict(criteria)
        and manifest.get("reference_date") == reference_date.isoformat()
        and manifest.get("queries") == [
            {"label": entry.label, "url": entry.url} for entry in query_plan
        ]
    )


@dataclass
class AcquiredBatch:
    batch_id: str
    batch_dir: Path
    acquired_at: str
    candidates: list[OpenLibraryEditionCandidate]
    received_count: int
    reuse_cache: bool
    socle_candidate_counts: dict[str, int]
    payload_sha256_by_label: dict[str, str]
    # DEV-16.5 §7 funnel: raw Search API docs actually returned, BEFORE
    # `_extract_candidate` validity filtering and BEFORE any dedup —
    # the true `source_records_received` count for this batch.
    raw_docs_received: int = 0


def acquire_batch_candidates(
    criteria: BatchCriteria,
    *,
    contact: str,
    output_dir: Path,
    reference_date: date,
    fetch_limit: int = 50,
    socle_fetch_limit: int = 20,
    refresh: bool = False,
    payload_fetcher=with_retries(fetch_batch_payload, max_attempts=3, backoff_seconds=5.0),
    already_seen_editions: set[str] | None = None,
    already_seen_valid_isbns: set[str] | None = None,
) -> AcquiredBatch:
    """RAW -> selected candidates for ONE batch — the acquisition half of
    `run_batch()`, factored out so `build_large_bundle()` (DEV-16.4 §8)
    can acquire several batches and dedup/map/quality/export them
    together in ONE global pass, while `run_batch()` keeps its existing
    single-batch, self-contained behavior (used unchanged by every
    DEV-16.3/16.4 pre-gate test and pilot).

    `already_seen_editions`/`already_seen_valid_isbns`, when given
    (mutated in place), extend the dedup discipline ACROSS batches —
    `build_large_bundle()` passes the SAME sets to every
    `acquire_batch_candidates()` call so two batches never contribute
    the same edition/ISBN twice.

    DEV-16.4 §4.3: with `refresh=False` (default), an IDENTICAL rerun
    (same criteria/reference_date/fetch_limit) reuses the RAW payload(s)
    already on disk under `output_dir/<batch_id>/` and calls
    `payload_fetcher` ZERO times. `refresh=True` forces a real re-fetch
    of every query in the plan.
    """
    batch_id = compute_batch_id(criteria, reference_date)
    batch_dir = output_dir / batch_id
    batch_dir.mkdir(parents=True, exist_ok=True)
    acquired_at = datetime.now(timezone.utc).isoformat()

    query_plan = build_query_plan(criteria, fetch_limit=fetch_limit, socle_fetch_limit=socle_fetch_limit)

    manifest_path = _raw_manifest_path(batch_dir)
    reuse_cache = False
    if not refresh and manifest_path.is_file():
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            manifest = {}
        if _manifest_matches(manifest, criteria=criteria, reference_date=reference_date, query_plan=query_plan):
            reuse_cache = all(
                _raw_payload_path(batch_dir, entry.label).is_file() for entry in query_plan
            )

    payloads: list[tuple[QueryPlanEntry, dict]] = []
    payload_sha256_by_label: dict[str, str] = {}

    if reuse_cache:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        payload_sha256_by_label = manifest.get("sha256", {})
        for entry in query_plan:
            raw_path = _raw_payload_path(batch_dir, entry.label)
            payloads.append((entry, json.loads(raw_path.read_text(encoding="utf-8"))))
    else:
        for entry in query_plan:
            payload = payload_fetcher(entry.url, contact=contact)
            raw_path = _raw_payload_path(batch_dir, entry.label)
            sha256 = save_raw_payload(payload, raw_path)
            payload_sha256_by_label[entry.label] = sha256
            payloads.append((entry, payload))
        manifest_path.write_text(
            json.dumps({
                "criteria": asdict(criteria),
                "reference_date": reference_date.isoformat(),
                "queries": [{"label": e.label, "url": e.url} for e in query_plan],
                "sha256": payload_sha256_by_label,
                "acquired_at": acquired_at,
            }, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    seen_editions = already_seen_editions if already_seen_editions is not None else set()
    seen_valid_isbns = already_seen_valid_isbns if already_seen_valid_isbns is not None else set()
    all_candidates: list[OpenLibraryEditionCandidate] = []
    socle_candidate_counts: dict[str, int] = {}
    raw_docs_received = sum(len(payload.get("docs", [])) for _entry, payload in payloads)

    for entry, payload in payloads:
        remaining = criteria.count - len(all_candidates)
        if remaining <= 0:
            break
        candidates = select_batch_candidates(
            payload, criteria,
            already_seen_editions=seen_editions,
            already_seen_valid_isbns=seen_valid_isbns,
            remaining=remaining,
        )
        if entry.author_key is not None:
            socle_candidate_counts[entry.author_key] = len(candidates)
        all_candidates.extend(candidates)

    return AcquiredBatch(
        batch_id=batch_id,
        batch_dir=batch_dir,
        acquired_at=acquired_at,
        candidates=all_candidates,
        received_count=len(all_candidates),
        reuse_cache=reuse_cache,
        socle_candidate_counts=socle_candidate_counts,
        payload_sha256_by_label=payload_sha256_by_label,
        raw_docs_received=raw_docs_received,
    )


def run_batch(
    criteria: BatchCriteria,
    *,
    contact: str,
    output_dir: Path,
    reference_date: date,
    fetch_limit: int = 50,
    socle_fetch_limit: int = 20,
    refresh: bool = False,
    payload_fetcher=with_retries(fetch_batch_payload, max_attempts=3, backoff_seconds=5.0),
) -> BatchReport:
    """Runs one batch end to end: RAW -> NORMALIZED -> VALIDATED ->
    ACCEPTED/REJECTED/QUARANTINED/MERGED -> report + provenance.

    `payload_fetcher(url, *, contact)` is injectable (default: the real
    Open Library Search API call, `fetch_batch_payload`) — tests supply
    a fixture payload, exactly like the existing `fetcher` parameter of
    `acquisition/openlibrary_details.py::acquire_records`.
    """
    acquired = acquire_batch_candidates(
        criteria, contact=contact, output_dir=output_dir, reference_date=reference_date,
        fetch_limit=fetch_limit, socle_fetch_limit=socle_fetch_limit, refresh=refresh,
        payload_fetcher=payload_fetcher,
    )
    batch_id = acquired.batch_id
    batch_dir = acquired.batch_dir
    acquired_at = acquired.acquired_at
    all_candidates = acquired.candidates
    received_count = acquired.received_count
    reuse_cache = acquired.reuse_cache
    socle_candidate_counts = acquired.socle_candidate_counts
    payload_sha256_by_label = acquired.payload_sha256_by_label

    selected_path = batch_dir / "selected.jsonl"
    write_selected_jsonl(all_candidates, selected_path)
    selected_editions = load_selected_editions(selected_path, expected_count=received_count)

    authors, editions, subjects_by_work_key = normalize_selected_catalogue(selected_editions)

    dedup_authors_result = deduplicate_authors(authors)
    dedup_editions_result = deduplicate_editions(editions)

    mapping_result = map_catalogue(
        dedup_authors_result.kept,
        dedup_editions_result.kept,
        subjects_by_work_key=subjects_by_work_key,
    )

    quality_result = apply_text_quality_policy(mapping_result)

    # Primary RAW source recorded in provenance is the first query that
    # actually contributed at least one ACCEPTED candidate's raw payload
    # sha256 is not tracked per-candidate at this granularity yet — the
    # batch-level sha256 (generic query, always issued) is used as the
    # representative RAW hash, consistent with DEV-16.3 §K.
    representative_sha256 = payload_sha256_by_label.get("generic", "")

    provenance_records = []
    for title in quality_result.titles:
        decisions = quality_result.field_decisions.get(title.source_key, ())
        provenance_records.append(build_record_provenance(
            source_key=title.source_key,
            batch_id=batch_id,
            raw_source="openlibrary_search",
            raw_payload_sha256=representative_sha256,
            acquired_at=acquired_at,
            normalization_steps=("normalize_selected_catalogue", "map_catalogue"),
            enrichments_applied=(),
            field_decisions=tuple(decisions),
        ))
    write_provenance_jsonl(provenance_records, batch_dir / "provenance.jsonl")

    quarantine_path = batch_dir / "quarantine.jsonl"
    with quarantine_path.open("w", encoding="utf-8") as handle:
        for entry in sorted(quality_result.quarantined, key=lambda e: e.source_key):
            handle.write(json.dumps({
                "source_key": entry.source_key,
                "entity": entry.entity,
                "reason_code": entry.reason_code,
                "detail": entry.detail,
                "batch_id": batch_id,
            }, ensure_ascii=False) + "\n")

    report = build_batch_report(
        criteria=criteria,
        batch_id=batch_id,
        acquired_at=acquired_at,
        received_count=received_count,
        mapping_result=mapping_result,
        dedup_editions_result=dedup_editions_result,
        dedup_authors_result=dedup_authors_result,
        quality_result=quality_result,
        from_cache=reuse_cache,
        socle_candidate_counts=socle_candidate_counts,
        subjects_by_work_key=subjects_by_work_key,
        edition_to_work_key={e.source_key: e.work_key for e in dedup_editions_result.kept},
    )
    (batch_dir / "batch_report.json").write_text(
        json.dumps(report.to_dict(), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return report
