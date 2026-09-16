"""DEV-16.5 §17 — real Full acquisition plan (15000 Titles target).

Language quotas: canonical proportions already used since DEV-13 for
small/medium (FR/EN/NL/DE/ES/IT/LA ~75/10/8/3/2/1/1 %), scaled to 15000
(FR 11250, EN 1500, NL 1200, DE 450, ES 300, IT 150, LA 150 — sums to
15000). NOT `Large x 3` (DEV-16.5 §1): Large's actual achieved
distribution had drifted from these canonical proportions because only
4 categories were used per language and FR alone absorbed most of the
volume, causing the real inter-category overlap collapse documented in
DEV-16.4 §D.3 (FR literature/history/science/arts: 1800->390,
1200->867, 840->447, 600->51 NEW candidates — heavy recoupement once
the same French classics get returned by several `subject:` filters
under Open Library's `sort=key` ordering).

DEV-16.5 §15 mitigation, applied FROM THE START (not discovered after
a failed first pass, unlike Large): FR is spread across ALL 9
documentary categories from the first run, each requested well above
its proportional share to compensate for the DEMONSTRATED overlap
ratio (DEV-16.4: yields ranged 8.5%-72% depending on how "exhausted"
the language's candidate pool already was by prior categories in the
SAME run). Other languages, which hit their Large quotas almost
exactly on the first try (EN/NL/DE/ES/IT/LA all matched their
requested count 1:1 in DEV-16.4 §E), get a smaller ~15-20% headroom.
"""

from datetime import date
from pathlib import Path

from primatis_data_seeding.pipeline.batch import BatchCriteria
from primatis_data_seeding.pipeline.full_build import FullBuildPlan, build_full_bundle

CONTACT = "dev16.5-full-build@primatis.local"
REFERENCE_DATE = date(2026, 9, 11)


def _lang_batches(language: str, *, socle: int, categories: dict[str, int]) -> list[BatchCriteria]:
    batches = [BatchCriteria(
        profile="full", language=language, documentary_category="literature",
        count=socle, use_editorial_socle=True,
    )]
    for category, count in categories.items():
        batches.append(BatchCriteria(
            profile="full", language=language, documentary_category=category, count=count,
        ))
    return batches


BATCHES: list[BatchCriteria] = [
    *_lang_batches("FR", socle=100, categories={
        # DEV-16.5 top-up round 2 (real yields observed round 1, out of
        # fetch_limit=2500 raw docs per category — literature 32%,
        # history 43%, science 18%, arts 76%, philosophy_religion 44%,
        # social_sciences 30%, youth 76%, documentary 88%, comics 57% —
        # counts raised here, proportionally more for low-yield
        # categories, to close the real 4089-Title shortfall without
        # re-querying an already-saturated (language, category) pair
        # blindly (DEV-16.2 §1.2 / DEV-16.4 §D.3 lesson).
        "literature": 7000, "history": 6000, "science": 5500, "arts": 3000,
        "philosophy_religion": 4000, "social_sciences": 5500, "youth": 2200,
        "documentary": 1800, "comics": 1800,
    }),
    # DEV-16.5 top-up round 3: FR plateaued around ~6800-7000 net
    # contributed across 9 categories despite two rounds of raising
    # counts/fetch_limit (round 1: 6979, round 2 with +80% counts and
    # +80% fetch_limit: 6760 — a REAL, measured diminishing/negative
    # marginal return, not assumed) — real evidence that this
    # category-based approach has a practical ceiling around here for
    # FR specifically. Rather than keep forcing an already-saturated
    # language, the remaining ~4000 Titles are sourced from the OTHER
    # 6 languages, all of which comfortably EXCEEDED their canonical-
    # proportion target on the very first attempt (DEV-16.5 §14/§15 —
    # measured, not assumed) — extended here across more documentary
    # categories (not just literature/history/science) for the same
    # diversity reasons as FR, at counts still well below any observed
    # saturation point for these languages.
    *_lang_batches("EN", socle=50, categories={
        "literature": 700, "history": 500, "science": 400,
        "arts": 500, "philosophy_religion": 500, "social_sciences": 500,
        "youth": 500, "documentary": 400, "comics": 300,
    }),
    *_lang_batches("NL", socle=40, categories={
        "literature": 600, "history": 400, "science": 300,
        "arts": 400, "philosophy_religion": 300, "youth": 300,
    }),
    *_lang_batches("DE", socle=30, categories={
        "literature": 250, "history": 200, "science": 250,
        "arts": 250, "philosophy_religion": 200,
    }),
    *_lang_batches("ES", socle=25, categories={
        "literature": 150, "history": 150, "science": 200,
        "arts": 200, "social_sciences": 150,
    }),
    *_lang_batches("IT", socle=20, categories={
        "literature": 100, "history": 80, "science": 150,
        "arts": 150,
    }),
    *_lang_batches("LA", socle=20, categories={
        "literature": 100, "history": 60,
    }),
]

PLAN = FullBuildPlan(profile="full", target_titles=15000, batches=tuple(BATCHES))


if __name__ == "__main__":
    import argparse
    import json
    import sys

    from primatis_data_seeding.acquisition.openlibrary_authors import load_authors_snapshot
    from primatis_data_seeding.acquisition.openlibrary_works import load_works_snapshot
    from primatis_data_seeding.acquisition.openlibrary_covers import fetch_cover_image
    from primatis_data_seeding.acquisition.rate_limit import RateLimiter, with_retries
    from primatis_data_seeding.pipeline.batch import fetch_batch_payload

    parser = argparse.ArgumentParser()
    parser.add_argument("--with-enrichment", action="store_true")
    parser.add_argument("--with-covers", action="store_true")
    parser.add_argument("--max-covers", type=int, default=None)
    args = parser.parse_args()

    resilient_fetcher = with_retries(
        RateLimiter(0.3).wrap(fetch_batch_payload),
        max_attempts=10, backoff_seconds=10.0,
    )

    author_records = None
    work_records = None
    if args.with_enrichment:
        author_records = load_authors_snapshot(Path("data/validated/full/authors_selected.jsonl"))
        work_records = load_works_snapshot(Path("data/validated/full/works_selected.jsonl"))
        print(f"enrichment: {len(author_records)} authors, {len(work_records)} works", file=sys.stderr)

    covers_assets_dir = None
    cover_fetcher = None
    cover_rate_limiter = None
    if args.with_covers:
        covers_assets_dir = Path("data/covers/full")
        cover_rate_limiter = RateLimiter(0.3)
        resilient_cover_fetch = with_retries(
            fetch_cover_image, max_attempts=5, backoff_seconds=6.0,
        )

        def cover_fetcher(cover_id: int) -> bytes:
            return resilient_cover_fetch(cover_id, contact=CONTACT)

    try:
        report = build_full_bundle(
            PLAN,
            contact=CONTACT,
            batches_output_dir=Path("data/validated/batches"),
            bundle_output_dir=Path("data/bundles/full"),
            reference_date=REFERENCE_DATE,
            fetch_limit=4500,
            socle_fetch_limit=150,
            payload_fetcher=resilient_fetcher,
            author_records=author_records,
            work_records=work_records,
            covers_assets_dir=covers_assets_dir,
            cover_fetcher=cover_fetcher,
            cover_rate_limiter=cover_rate_limiter,
            max_cover_downloads=args.max_covers,
        )
    except Exception as exc:
        print(f"BUILD FAILED: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise
    print(json.dumps(report.to_dict(), indent=2, ensure_ascii=False))
