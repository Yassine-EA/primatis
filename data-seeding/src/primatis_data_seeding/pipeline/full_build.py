"""Full-profile multi-batch build (DEV-16.5), extending
`pipeline/large_build.py`'s architecture (proven on the real 5000-Title
Large profile, DEV-16.4) with the additional rigor DEV-16.5 requires:

- **Exact per-Title provenance** (`origin_batch_id`) — DEV-16.4's
  `large_build.py` recorded a single composite batch_id for the whole
  build; here every candidate's ORIGINATING batch is tracked from the
  moment it is first accepted by the cross-batch dedup, and carried
  through to `RecordProvenance` unchanged.
- **Unambiguous funnel** (`FunnelMetrics`) — one linearly walkable chain
  from `source_records_received` to `imported`, each step arithmetically
  reconcilable with the next (DEV-16.5 §7).
- **Optional Authors/Works dump enrichment** wired through
  `normalize_selected_catalogue(author_records=..., work_records=...)`
  (both parameters already existed and were already tested — only the
  ACQUISITION of the dumps for `large` was skipped, DEV-16.4 §Q point 1).
- **Optional covers pipeline** — real download + `cover_validation.py`
  validation + LOCAL storage under `data-seeding/` (never
  `primatis-web/`, which stays strictly read-only per DEV-16 governance
  — see module docstring in the DEV-16.5 report §I for the full
  reasoning). `cover_image_url` in the bundle only ever points to an
  asset that was actually validated and materialized — fail-closed,
  same discipline as `acquisition/openlibrary_covers.py`.

Deliberately a SEPARATE module from `large_build.py` rather than a
rewrite of it: `large_build.py` is exactly what built and validated the
real Large profile now loaded in `primatis_dev` (DEV-16.4) — it stays
untouched so that profile's provenance/reproducibility guarantees are
never put at risk by Full-specific changes.
"""

from __future__ import annotations

import json
from collections import Counter
from dataclasses import asdict, dataclass, field
from datetime import date, datetime, timezone
from pathlib import Path

from primatis_data_seeding.acquisition.openlibrary import (
    OpenLibraryEditionCandidate,
    write_selected_jsonl,
)
from primatis_data_seeding.acquisition.openlibrary_covers import (
    cover_asset_path,
    cover_image_url,
    fetch_cover_image,
)
from primatis_data_seeding.acquisition.rate_limit import RateLimiter, with_retries
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
from primatis_data_seeding.mapping.models import CatalogueMappingResult
from primatis_data_seeding.normalization.cover_validation import (
    validate_cover_candidate,
)
from primatis_data_seeding.pipeline.batch import (
    BatchCriteria,
    acquire_batch_candidates,
    fetch_batch_payload as _default_fetch_batch_payload,
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


@dataclass(frozen=True)
class FullBuildPlan:
    profile: str
    target_titles: int
    batches: tuple[BatchCriteria, ...]

    def __post_init__(self) -> None:
        if self.profile not in PROFILE_COPY_DISTRIBUTIONS:
            raise ValueError(f"No Copy distribution defined for profile {self.profile!r}.")
        if self.target_titles <= 0:
            raise ValueError("target_titles must be > 0.")
        if not self.batches:
            raise ValueError("A FullBuildPlan needs at least one batch.")


@dataclass
class BatchAcquisitionSummary:
    batch_id: str
    criteria: dict
    raw_docs_received: int
    contributed_count: int  # after cross-batch dedup, before mapping/quality
    reuse_cache: bool


# DEV-16.5 §7 — one linearly walkable funnel. Each field's exact
# definition (docstring) is the contract; the report cites this
# docstring verbatim rather than re-deriving definitions ad hoc.
@dataclass
class FunnelMetrics:
    #: Raw Search API docs returned across every query of every batch,
    #: BEFORE `_extract_candidate` validity filtering and BEFORE any
    #: dedup. Two batches can both "receive" the same doc.
    source_records_received: int = 0
    #: After `_extract_candidate` validity filtering AND cross-batch
    #: dedup (edition_key / valid ISBN) — each surviving candidate is
    #: unique across the ENTIRE build, never just within one batch.
    unique_candidates_inter_batch: int = 0
    #: `normalize_selected_catalogue()` output count — 1:1 with
    #: `unique_candidates_inter_batch` (normalization never drops a row;
    #: only later stages reject/quarantine/merge).
    normalized: int = 0
    #: Count kept by `deduplicate_editions()` (its `.kept`) — i.e. after
    #: intra-set SAME_SOURCE_KEY / SAME_VALID_ISBN merges are resolved,
    #: before `map_catalogue()` decides accept/reject.
    validated: int = 0
    #: `map_catalogue()` rejections (TITLE_WITHOUT_AUTHOR /
    #: UNRESOLVED_AUTHOR_REFERENCE) — never imported, never quarantined
    #: (a distinct, more certain failure mode).
    rejected: int = 0
    #: `apply_text_quality_policy()` quarantines (own text OR cascaded
    #: from an Author) — never imported, structurally absent from the
    #: accepted collections.
    quarantined: int = 0
    #: Editions REMOVED by `deduplicate_editions()`'s automatic merge
    #: (`.duplicates` — the losing side of SAME_SOURCE_KEY/
    #: SAME_VALID_ISBN; the winner survives into `validated`).
    merged_losers: int = 0
    #: `quality_result.titles` count — ACCEPTED, before the deterministic
    #: trim to `target_titles`.
    accepted_pre_trim: int = 0
    #: Surplus removed by the deterministic (sorted by `source_key`) trim
    #: — 0 if `accepted_pre_trim <= target_titles`.
    trimmed: int = 0
    #: Final Title count written to the bundle CSVs — always exactly
    #: `target_titles` once `accepted_pre_trim >= target_titles`
    #: (otherwise equals `accepted_pre_trim`, and `shortfall` on the
    #: report is > 0).
    accepted_final: int = 0
    #: Title count actually present in PostgreSQL after a successful
    #: CHECK/APPLY — filled in by the caller AFTER loading (0 here; the
    #: build itself never touches PostgreSQL).
    imported: int = 0

    def reconcile(self) -> list[str]:
        """Returns a list of human-readable arithmetic mismatches, empty
        if the funnel is fully consistent. Never raises — a caller
        decides whether an inconsistency is fatal."""
        problems = []
        if self.normalized != self.unique_candidates_inter_batch:
            problems.append(
                f"normalized ({self.normalized}) != unique_candidates_inter_batch "
                f"({self.unique_candidates_inter_batch})"
            )
        if self.validated + self.merged_losers != self.normalized:
            problems.append(
                f"validated+merged_losers ({self.validated}+{self.merged_losers}) "
                f"!= normalized ({self.normalized})"
            )
        if self.rejected + self.quarantined + self.accepted_pre_trim != self.validated:
            problems.append(
                f"rejected+quarantined+accepted_pre_trim "
                f"({self.rejected}+{self.quarantined}+{self.accepted_pre_trim}) "
                f"!= validated ({self.validated})"
            )
        if self.trimmed + self.accepted_final != self.accepted_pre_trim:
            problems.append(
                f"trimmed+accepted_final ({self.trimmed}+{self.accepted_final}) "
                f"!= accepted_pre_trim ({self.accepted_pre_trim})"
            )
        return problems


@dataclass
class CoverBuildMetrics:
    candidates: int = 0
    download_attempted: int = 0
    download_successful: int = 0
    valid: int = 0
    invalid: int = 0
    duplicates: int = 0
    bytes_total: int = 0
    invalid_reasons: dict = field(default_factory=dict)


@dataclass
class FullBuildReport:
    profile: str
    target_titles: int
    reference_date: str
    built_at: str
    batches: list[BatchAcquisitionSummary]
    funnel: FunnelMetrics
    shortfall: int
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
    biography_coverage: float
    nationality_coverage: float
    rejection_reasons: dict
    quarantine_reasons: dict
    covers: CoverBuildMetrics
    author_enrichment_applied: bool
    work_enrichment_applied: bool

    def to_dict(self) -> dict:
        payload = asdict(self)
        payload["batches"] = [asdict(b) for b in self.batches]
        return payload


def _coverage(rows: list, attr: str) -> float:
    if not rows:
        return 0.0
    present = sum(1 for r in rows if getattr(r, attr) is not None)
    return round(present / len(rows), 4)


def build_full_bundle(
    plan: FullBuildPlan,
    *,
    contact: str,
    batches_output_dir: Path,
    bundle_output_dir: Path,
    reference_date: date,
    fetch_limit: int = 2000,
    socle_fetch_limit: int = 80,
    refresh: bool = False,
    author_records: dict[str, dict] | None = None,
    work_records: dict[str, dict] | None = None,
    covers_assets_dir: Path | None = None,
    cover_fetcher=None,
    cover_rate_limiter: RateLimiter | None = None,
    max_cover_downloads: int | None = None,
    payload_fetcher=with_retries(_default_fetch_batch_payload, max_attempts=3, backoff_seconds=5.0),
) -> FullBuildReport:
    """Runs every batch in `plan.batches`, merges + globally dedups their
    candidates WITH per-candidate origin batch tracking, optionally
    enriches Authors (`author_records`, keyed by canonical author_key)
    and Works (`work_records`, keyed by work_key), applies the quality/
    quarantine policy, optionally materializes+validates covers into
    `covers_assets_dir` (never `primatis-web/`), generates Copies via
    the EXISTING `PROFILE_COPY_DISTRIBUTIONS[plan.profile]` (nothing
    invented), and writes the final CSV bundle + per-Title provenance
    (exact `origin_batch_id`) to `bundle_output_dir`.
    """
    built_at = datetime.now(timezone.utc).isoformat()
    seen_editions: set[str] = set()
    seen_valid_isbns: set[str] = set()
    all_candidates: list[OpenLibraryEditionCandidate] = []
    candidate_origin_batch: dict[str, str] = {}
    batch_summaries: list[BatchAcquisitionSummary] = []
    candidate_batch_sha256: dict[str, str] = {}

    funnel = FunnelMetrics()

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
        for candidate in acquired.candidates:
            candidate_origin_batch.setdefault(candidate.edition_key, acquired.batch_id)
        all_candidates.extend(acquired.candidates)
        candidate_batch_sha256[acquired.batch_id] = acquired.payload_sha256_by_label.get("generic", "")
        funnel.source_records_received += acquired.raw_docs_received
        batch_summaries.append(BatchAcquisitionSummary(
            batch_id=acquired.batch_id,
            criteria=asdict(criteria),
            raw_docs_received=acquired.raw_docs_received,
            contributed_count=len(all_candidates) - before,
            reuse_cache=acquired.reuse_cache,
        ))

    funnel.unique_candidates_inter_batch = len(all_candidates)

    bundle_output_dir.mkdir(parents=True, exist_ok=True)
    selected_path = bundle_output_dir / "selected_full.jsonl"
    write_selected_jsonl(all_candidates, selected_path)
    selected_editions = load_selected_editions(
        selected_path, expected_count=funnel.unique_candidates_inter_batch,
    )

    authors, editions, subjects_by_work_key = normalize_selected_catalogue(
        selected_editions,
        author_records=author_records,
        work_records=work_records,
    )
    funnel.normalized = len(editions)

    dedup_authors_result = deduplicate_authors(authors)
    dedup_editions_result = deduplicate_editions(editions)
    funnel.validated = len(dedup_editions_result.kept)
    funnel.merged_losers = len(dedup_editions_result.duplicates)

    mapping_result = map_catalogue(
        dedup_authors_result.kept,
        dedup_editions_result.kept,
        subjects_by_work_key=subjects_by_work_key,
    )
    funnel.rejected = len(mapping_result.rejections)

    quality_result = apply_text_quality_policy(mapping_result)
    # DEV-16.5 §7 fix : `quality_result.quarantined` mélange les entrées
    # "title" (propre texte OU cascade auteur) ET "author" (propre texte).
    # Le funnel ne doit compter que la population Title (cf. docstring de
    # `FunnelMetrics.quarantined` et `.reconcile()`, qui reconcilient
    # rejected+quarantined+accepted_pre_trim contre `validated`, une
    # quantité d'éditions/Titles) — les quarantaines d'Author isolées
    # (jamais elles-mêmes une ligne de `mapping_result.titles`) auraient
    # sinon faussé l'arithmétique d'un décalage égal au nombre d'Authors
    # quarantinés hors cascade.
    funnel.quarantined = len(
        [entry for entry in quality_result.quarantined if entry.entity == "title"]
    )
    funnel.accepted_pre_trim = len(quality_result.titles)

    sorted_titles = sorted(quality_result.titles, key=lambda t: t.source_key)
    final_titles = sorted_titles[: plan.target_titles]
    funnel.trimmed = max(0, funnel.accepted_pre_trim - plan.target_titles)
    funnel.accepted_final = len(final_titles)
    shortfall = max(0, plan.target_titles - funnel.accepted_pre_trim)

    kept_keys = {t.source_key for t in final_titles}
    final_title_authors = [
        link for link in quality_result.title_authors if link.title_source_key in kept_keys
    ]
    referenced_author_keys = {link.author_source_key for link in final_title_authors}
    final_authors = [a for a in quality_result.authors if a.source_key in referenced_author_keys]
    final_title_genres = [
        tg for tg in mapping_result.title_genres if tg.title_source_key in kept_keys
    ]

    # --- Covers (DEV-16.5 §12/§13) -------------------------------------
    cover_metrics = CoverBuildMetrics()
    edition_by_key = {e.source_key: e for e in dedup_editions_result.kept}
    cover_url_by_title: dict[str, str] = {}
    if covers_assets_dir is not None and cover_fetcher is not None:
        cover_ids_seen: set[int] = set()
        for title in final_titles:
            edition = edition_by_key.get(title.source_key)
            cover_id = edition.cover_id if edition else None
            if cover_id is None:
                continue
            cover_metrics.candidates += 1
            if (
                max_cover_downloads is not None
                and cover_metrics.download_attempted >= max_cover_downloads
                and cover_id not in cover_ids_seen
            ):
                continue  # candidate counted (traceable), download deliberately capped
            if cover_id in cover_ids_seen:
                cover_metrics.duplicates += 1
                if cover_asset_path(covers_assets_dir, cover_id).is_file():
                    cover_url_by_title[title.source_key] = cover_image_url(cover_id)
                continue
            cover_ids_seen.add(cover_id)

            asset_path = cover_asset_path(covers_assets_dir, cover_id)
            if asset_path.is_file():
                cover_url_by_title[title.source_key] = cover_image_url(cover_id)
                continue

            cover_metrics.download_attempted += 1
            if cover_rate_limiter is not None:
                cover_rate_limiter.wait()
            try:
                data = cover_fetcher(cover_id)
            except Exception as exc:  # noqa: BLE001 - a failed download is data, not a crash
                cover_metrics.invalid += 1
                cover_metrics.invalid_reasons[type(exc).__name__] = (
                    cover_metrics.invalid_reasons.get(type(exc).__name__, 0) + 1
                )
                continue
            cover_metrics.download_successful += 1
            result = validate_cover_candidate(data)
            if result.verdict != "OK":
                cover_metrics.invalid += 1
                for issue in result.issues:
                    cover_metrics.invalid_reasons[issue.code] = (
                        cover_metrics.invalid_reasons.get(issue.code, 0) + 1
                    )
                continue
            cover_metrics.valid += 1
            cover_metrics.bytes_total += len(data)
            asset_path.parent.mkdir(parents=True, exist_ok=True)
            asset_path.write_bytes(data)
            cover_url_by_title[title.source_key] = cover_image_url(cover_id)

    if cover_url_by_title:
        final_titles = [
            (t if t.source_key not in cover_url_by_title else
             _with_cover(t, cover_url_by_title[t.source_key]))
            for t in final_titles
        ]

    # --- Copies (existing distribution, nothing invented, DEV-16.5 §18) -
    expected_title_count = PROFILE_COPY_DISTRIBUTIONS[plan.profile].title_count
    if len(final_titles) == expected_title_count:
        copy_result = generate_copies(final_titles, profile=plan.profile)
    else:
        copy_result = CopyGenerationResult()

    export_mapping = CatalogueMappingResult(
        authors=final_authors,
        genres=mapping_result.genres,
        titles=final_titles,
        title_authors=final_title_authors,
        title_genres=final_title_genres,
    )
    export_catalogue_csv(export_mapping, copy_result.copies, bundle_output_dir)

    # --- Provenance (DEV-16.5 §6 — exact origin_batch_id) ---------------
    provenance_records = []
    for title in final_titles:
        decisions = quality_result.field_decisions.get(title.source_key, ())
        origin_batch_id = candidate_origin_batch.get(title.source_key, "")
        provenance_records.append(build_record_provenance(
            source_key=title.source_key,
            batch_id=origin_batch_id,
            raw_source="openlibrary_search",
            raw_payload_sha256=candidate_batch_sha256.get(origin_batch_id, ""),
            acquired_at=built_at,
            normalization_steps=("normalize_selected_catalogue", "map_catalogue"),
            enrichments_applied=tuple(
                name for name, applied in (
                    ("authors_dump", author_records is not None),
                    ("works_dump", work_records is not None),
                ) if applied
            ),
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

    report = FullBuildReport(
        profile=plan.profile,
        target_titles=plan.target_titles,
        reference_date=reference_date.isoformat(),
        built_at=built_at,
        batches=batch_summaries,
        funnel=funnel,
        shortfall=shortfall,
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
        biography_coverage=_coverage(final_authors, "biography"),
        nationality_coverage=_coverage(final_authors, "nationality"),
        rejection_reasons=dict(rejection_reasons),
        quarantine_reasons=dict(title_quarantine_reasons + author_quarantine_reasons),
        covers=cover_metrics,
        author_enrichment_applied=author_records is not None,
        work_enrichment_applied=work_records is not None,
    )
    (bundle_output_dir / "full_build_report.json").write_text(
        json.dumps(report.to_dict(), ensure_ascii=False, indent=2, default=lambda o: asdict(o)) + "\n",
        encoding="utf-8",
    )
    return report


def _with_cover(title, cover_url: str):
    from dataclasses import replace
    return replace(title, cover_image_url=cover_url)
