"""Multi-batch aggregation into one profile-scale bundle (DEV-16.4 §8).

A single `pipeline/batch.py::run_batch()` call is scoped to ONE
(language, documentary category) selection and produces its own
self-contained artifacts — exactly what DEV-16.3/DEV-16.4 pre-gate
needed to prove the architecture. Building `large` (5000 Titles) means
running SEVERAL such batches (one per language x category quota,
DEV-16.4 §5) and combining them into ONE bundle with the shape
`load/cli.py`/`load/postgres.py::load_catalogue_export` already expects
(`titles.csv`, `authors.csv`, `title_authors.csv`, `title_genres.csv`,
`genres.csv`, `copies.csv`) — the exact same CSV contract `small`/
`medium` already use (`export/catalogue_csv.py`, unchanged).

Deduplication happens ONCE, GLOBALLY, across every batch's candidates —
never per-batch-then-merge (two different batches, e.g. FR/literature
and FR/history, could otherwise both contribute the same edition/ISBN
undetected). `acquire_batch_candidates()` from `pipeline/batch.py`
already supports this via shared `already_seen_editions`/
`already_seen_valid_isbns` sets passed across calls.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from datetime import date, datetime, timezone
from pathlib import Path

from primatis_data_seeding.acquisition.openlibrary import (
    OpenLibraryEditionCandidate,
    write_selected_jsonl,
)
from primatis_data_seeding.deduplication.catalogue import (
    deduplicate_authors,
    deduplicate_editions,
)
from primatis_data_seeding.export.catalogue_csv import export_catalogue_csv
from primatis_data_seeding.generation.copies import (
    PROFILE_COPY_DISTRIBUTIONS,
    CopyGenerationResult,
    generate_copies,
)
from primatis_data_seeding.mapping.catalogue import map_catalogue
from primatis_data_seeding.pipeline.batch import (
    BatchCriteria,
    acquire_batch_candidates,
    fetch_batch_payload,
)
from primatis_data_seeding.acquisition.rate_limit import with_retries
from primatis_data_seeding.pipeline.bundle import (
    load_selected_editions,
    normalize_selected_catalogue,
)
from primatis_data_seeding.provenance.record_provenance import (
    build_record_provenance,
    write_provenance_jsonl,
)
from primatis_data_seeding.quality.quarantine import apply_text_quality_policy


@dataclass(frozen=True)
class LargeBuildPlan:
    profile: str
    target_titles: int
    batches: tuple[BatchCriteria, ...]

    def __post_init__(self) -> None:
        if self.profile not in PROFILE_COPY_DISTRIBUTIONS:
            raise ValueError(f"No Copy distribution defined for profile {self.profile!r}.")
        if self.target_titles <= 0:
            raise ValueError("target_titles must be > 0.")
        if not self.batches:
            raise ValueError("A LargeBuildPlan needs at least one batch.")


@dataclass
class BatchAcquisitionSummary:
    batch_id: str
    criteria: dict
    received_count: int
    contributed_count: int  # after cross-batch dedup, before mapping/quality
    reuse_cache: bool


@dataclass
class LargeBuildReport:
    profile: str
    target_titles: int
    reference_date: str
    built_at: str
    batches: list[BatchAcquisitionSummary]
    total_received: int
    total_normalized: int
    total_accepted_before_trim: int
    total_accepted_final: int
    trimmed_surplus_count: int
    shortfall: int  # > 0 if fewer than target_titles were ever ACCEPTED
    total_rejected: int
    total_quarantined: int
    total_duplicate: int
    total_candidate: int
    copies_generated: int
    copy_distribution: dict
    languages: dict
    documentary_categories: dict
    isbn_coverage: float
    publisher_coverage: float
    year_coverage: float
    page_count_coverage: float
    summary_coverage: float
    genre_coverage: float
    cover_coverage: float
    rejection_reasons: dict
    quarantine_reasons: dict

    def to_dict(self) -> dict:
        payload = asdict(self)
        payload["batches"] = [asdict(b) for b in self.batches]
        return payload


def _coverage(titles: list, attr: str) -> float:
    if not titles:
        return 0.0
    present = sum(1 for t in titles if getattr(t, attr) is not None)
    return round(present / len(titles), 4)


def build_large_bundle(
    plan: LargeBuildPlan,
    *,
    contact: str,
    batches_output_dir: Path,
    bundle_output_dir: Path,
    reference_date: date,
    fetch_limit: int = 200,
    socle_fetch_limit: int = 30,
    refresh: bool = False,
    payload_fetcher=with_retries(fetch_batch_payload, max_attempts=3, backoff_seconds=5.0),
) -> LargeBuildReport:
    """Runs every batch in `plan.batches`, merges + globally dedups their
    candidates, maps + applies the quality/quarantine policy ONCE on the
    combined set, generates Copies (`generate_copies(..., profile=
    plan.profile)` — the EXISTING distribution table, DEV-16.4 §9,
    nothing invented here), and writes the final CSV bundle to
    `bundle_output_dir`.

    If more than `plan.target_titles` end up ACCEPTED, the surplus is
    trimmed DETERMINISTICALLY (sorted by `source_key`, keep the first
    `target_titles`) — never a random/arbitrary cut. If FEWER than
    `plan.target_titles` end up ACCEPTED, `shortfall` is reported > 0 and
    the bundle is written anyway (with the actual accepted count) — the
    caller decides whether to add more batches and rerun, never silently
    padded.
    """
    built_at = datetime.now(timezone.utc).isoformat()
    seen_editions: set[str] = set()
    seen_valid_isbns: set[str] = set()
    all_candidates: list[OpenLibraryEditionCandidate] = []
    batch_summaries: list[BatchAcquisitionSummary] = []
    per_batch_sha256: dict[str, str] = {}

    for criteria in plan.batches:
        before = len(all_candidates)
        acquired = acquire_batch_candidates(
            criteria, contact=contact, output_dir=batches_output_dir,
            reference_date=reference_date, fetch_limit=fetch_limit,
            socle_fetch_limit=socle_fetch_limit, refresh=refresh,
            payload_fetcher=payload_fetcher,
            already_seen_editions=seen_editions,
            already_seen_valid_isbns=seen_valid_isbns,
        )
        all_candidates.extend(acquired.candidates)
        per_batch_sha256[acquired.batch_id] = acquired.payload_sha256_by_label.get("generic", "")
        batch_summaries.append(BatchAcquisitionSummary(
            batch_id=acquired.batch_id,
            criteria=asdict(criteria),
            received_count=acquired.received_count,
            contributed_count=len(all_candidates) - before,
            reuse_cache=acquired.reuse_cache,
        ))

    total_received = len(all_candidates)

    bundle_output_dir.mkdir(parents=True, exist_ok=True)
    selected_path = bundle_output_dir / "selected_large.jsonl"
    write_selected_jsonl(all_candidates, selected_path)
    selected_editions = load_selected_editions(selected_path, expected_count=total_received)

    authors, editions, subjects_by_work_key = normalize_selected_catalogue(selected_editions)

    dedup_authors_result = deduplicate_authors(authors)
    dedup_editions_result = deduplicate_editions(editions)

    mapping_result = map_catalogue(
        dedup_authors_result.kept,
        dedup_editions_result.kept,
        subjects_by_work_key=subjects_by_work_key,
    )

    quality_result = apply_text_quality_policy(mapping_result)

    accepted_before_trim = len(quality_result.titles)
    sorted_titles = sorted(quality_result.titles, key=lambda t: t.source_key)
    final_titles = sorted_titles[: plan.target_titles]
    trimmed_surplus_count = max(0, accepted_before_trim - plan.target_titles)
    shortfall = max(0, plan.target_titles - accepted_before_trim)

    kept_keys = {t.source_key for t in final_titles}
    final_title_authors = [
        link for link in quality_result.title_authors if link.title_source_key in kept_keys
    ]
    referenced_author_keys = {link.author_source_key for link in final_title_authors}
    final_authors = [a for a in quality_result.authors if a.source_key in referenced_author_keys]
    final_title_genres = [
        tg for tg in mapping_result.title_genres if tg.title_source_key in kept_keys
    ]

    # generate_copies() enforces an EXACT match against the profile's
    # fixed distribution (DEV-16.4 §9 — never a partial/approximate
    # distribution). A trimmed/short run (test fixtures, a deliberately
    # smaller pilot plan) legitimately does not reach the profile's
    # exact target — Copies are then simply not generated for that run
    # rather than failing the whole bundle build; the real full-scale
    # run is built to land exactly on `PROFILE_COPY_DISTRIBUTIONS
    # [profile].title_count` by construction (DEV-16.4 report §I).
    expected_title_count = PROFILE_COPY_DISTRIBUTIONS[plan.profile].title_count
    if len(final_titles) == expected_title_count:
        copy_result = generate_copies(final_titles, profile=plan.profile)
    else:
        copy_result = CopyGenerationResult()

    from primatis_data_seeding.mapping.models import CatalogueMappingResult
    export_mapping = CatalogueMappingResult(
        authors=final_authors,
        genres=mapping_result.genres,
        titles=final_titles,
        title_authors=final_title_authors,
        title_genres=final_title_genres,
    )
    export_catalogue_csv(export_mapping, copy_result.copies, bundle_output_dir)

    provenance_records = []
    for title in final_titles:
        decisions = quality_result.field_decisions.get(title.source_key, ())
        provenance_records.append(build_record_provenance(
            source_key=title.source_key,
            batch_id="large-build:" + "+".join(b.batch_id for b in batch_summaries),
            raw_source="openlibrary_search",
            raw_payload_sha256=next(iter(per_batch_sha256.values()), ""),
            acquired_at=built_at,
            normalization_steps=("normalize_selected_catalogue", "map_catalogue"),
            enrichments_applied=(),
            field_decisions=tuple(decisions),
        ))
    write_provenance_jsonl(provenance_records, bundle_output_dir / "provenance.jsonl")

    with (bundle_output_dir / "quarantine.jsonl").open("w", encoding="utf-8") as handle:
        for entry in sorted(quality_result.quarantined, key=lambda e: e.source_key):
            handle.write(json.dumps({
                "source_key": entry.source_key,
                "entity": entry.entity,
                "reason_code": entry.reason_code,
                "detail": entry.detail,
            }, ensure_ascii=False) + "\n")

    from collections import Counter
    languages = Counter(t.language for t in final_titles)
    documentary_categories = Counter(c.documentary_category for c in plan.batches)
    rejection_reasons = Counter(item.code for item in mapping_result.rejections)
    title_quarantine_reasons = Counter(
        e.reason_code for e in quality_result.quarantined if e.entity == "title"
    )
    author_quarantine_reasons = Counter(
        e.reason_code for e in quality_result.quarantined if e.entity == "author"
    )

    genre_coverage = 0.0
    if final_titles:
        titles_with_genre = {tg.title_source_key for tg in final_title_genres}
        genre_coverage = round(
            sum(1 for t in final_titles if t.source_key in titles_with_genre) / len(final_titles), 4,
        )

    report = LargeBuildReport(
        profile=plan.profile,
        target_titles=plan.target_titles,
        reference_date=reference_date.isoformat(),
        built_at=built_at,
        batches=batch_summaries,
        total_received=total_received,
        total_normalized=len(mapping_result.titles) + len(mapping_result.rejections),
        total_accepted_before_trim=accepted_before_trim,
        total_accepted_final=len(final_titles),
        trimmed_surplus_count=trimmed_surplus_count,
        shortfall=shortfall,
        total_rejected=len(mapping_result.rejections),
        total_quarantined=len(quality_result.quarantined),
        total_duplicate=len(dedup_editions_result.duplicates) + len(dedup_authors_result.duplicates),
        total_candidate=len(dedup_editions_result.candidates) + len(dedup_authors_result.candidates),
        copies_generated=len(copy_result.copies),
        copy_distribution=dict(copy_result.titles_by_copy_count),
        languages=dict(languages),
        documentary_categories=dict(documentary_categories),
        isbn_coverage=_coverage(final_titles, "isbn"),
        publisher_coverage=_coverage(final_titles, "publisher"),
        year_coverage=_coverage(final_titles, "publication_year"),
        page_count_coverage=_coverage(final_titles, "page_count"),
        summary_coverage=_coverage(final_titles, "summary"),
        genre_coverage=genre_coverage,
        cover_coverage=_coverage(final_titles, "cover_image_url"),
        rejection_reasons=dict(rejection_reasons),
        quarantine_reasons=dict(title_quarantine_reasons + author_quarantine_reasons),
    )
    (bundle_output_dir / "large_build_report.json").write_text(
        json.dumps(report.to_dict(), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return report
