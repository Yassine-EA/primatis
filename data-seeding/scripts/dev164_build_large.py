"""DEV-16.4 §8 — real Large acquisition plan (5000 Titles target).

Not a permanent CLI (no argparse wiring) — a one-shot, documented script
run manually during DEV-16.4, kept for reproducibility/traceability
(DEV-16.4 report §C/§D/§E).

Language quotas: proportional to the REAL small/medium quotas already
validated by DEV-13 (FR/EN/NL/DE/ES/IT/LA ~75/10/8/3/2/1/1%), scaled to
5000. Each language quota is split across its real editorial socle
author (DEC-16.3-02) plus 2-4 documentary categories (DEV-16.2 §1),
inflated by ~20% headroom (DEV-16 §8: acquire more RAW than the target
to absorb rejects/quarantine/duplicates, then trim deterministically to
exactly 5000 — never padded).

Real availability confirmed before running this plan (DEV-16.4 report
§D, live Open Library numFound spot-checks, 2026-09-11): every
(language, category) pair used below has at least ~350 (LA/literature)
up to ~1.3M (EN/literature) real matching records — supply is not the
bottleneck anywhere in this plan.
"""

from datetime import date
from pathlib import Path

from primatis_data_seeding.acquisition.rate_limit import RateLimiter, with_retries
from primatis_data_seeding.pipeline.batch import BatchCriteria, fetch_batch_payload
from primatis_data_seeding.pipeline.large_build import LargeBuildPlan, build_large_bundle

CONTACT = "dev16.4-large-build@primatis.local"
REFERENCE_DATE = date(2026, 9, 11)


def _lang_batches(language: str, *, socle: int, categories: dict[str, int]) -> list[BatchCriteria]:
    batches = [BatchCriteria(
        profile="large", language=language, documentary_category="literature",
        count=socle, use_editorial_socle=True,
    )]
    for category, count in categories.items():
        batches.append(BatchCriteria(
            profile="large", language=language, documentary_category=category, count=count,
        ))
    return batches


BATCHES: list[BatchCriteria] = [
    *_lang_batches("FR", socle=60, categories={"literature": 1800, "history": 1200, "science": 840, "arts": 600}),
    *_lang_batches("EN", socle=35, categories={"literature": 300, "history": 265}),
    *_lang_batches("NL", socle=25, categories={"literature": 240, "history": 215}),
    *_lang_batches("DE", socle=25, categories={"literature": 95, "history": 60}),
    *_lang_batches("ES", socle=18, categories={"literature": 60, "history": 42}),
    *_lang_batches("IT", socle=18, categories={"literature": 42, "history": 30}),
    *_lang_batches("LA", socle=12, categories={"literature": 18}),
]

# DEV-16.4 §8 top-up round: the first real run landed at 3307/5000
# ACCEPTED (shortfall=1693) — FR's 4 categories overlapped heavily with
# each other (real observed yield: literature 1800->390, history
# 1200->867, science 840->447, arts 600->51 NEW/non-duplicate
# candidates once cross-batch dedup ran), while every OTHER language hit
# its requested count exactly. Closing the gap with NEW FR categories
# (untapped subject pools, real numFound confirms ample supply) plus a
# modest additional category for the smaller languages — never by
# inflating an already-exhausted (language, category) pair, which
# yields the same close-to-plateau overlap 800/900 (DEV-16.4 report §E).
BATCHES += [
    BatchCriteria(profile="large", language="FR", documentary_category="philosophy_religion", count=1500),
    BatchCriteria(profile="large", language="FR", documentary_category="social_sciences", count=1500),
    BatchCriteria(profile="large", language="FR", documentary_category="youth", count=1200),
    BatchCriteria(profile="large", language="EN", documentary_category="science", count=300),
    BatchCriteria(profile="large", language="NL", documentary_category="science", count=150),
    BatchCriteria(profile="large", language="DE", documentary_category="science", count=100),
    BatchCriteria(profile="large", language="ES", documentary_category="science", count=80),
    BatchCriteria(profile="large", language="IT", documentary_category="science", count=60),
]

PLAN = LargeBuildPlan(profile="large", target_titles=5000, batches=tuple(BATCHES))


if __name__ == "__main__":
    import json
    import sys

    # DEV-16.4 §4.4/§E: the environment showed real, sometimes multi-second
    # sustained connection failures against Open Library (pre-gate
    # calibration + first real Large run attempt) — a more patient retry
    # policy than the library default is used for this real, long-running
    # multi-batch acquisition. RAW is cached per-batch-criteria
    # (DEV-16.4 §4.3), so rerunning this script after a failure costs
    # nothing for batches that already completed.
    resilient_fetcher = with_retries(
        RateLimiter(0.3).wrap(fetch_batch_payload),
        max_attempts=6, backoff_seconds=8.0,
    )

    try:
        report = build_large_bundle(
            PLAN,
            contact=CONTACT,
            batches_output_dir=Path("data/validated/batches"),
            bundle_output_dir=Path("data/bundles/large"),
            reference_date=REFERENCE_DATE,
            fetch_limit=2000,
            socle_fetch_limit=80,
            payload_fetcher=resilient_fetcher,
        )
    except Exception as exc:
        print(f"BUILD FAILED: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise
    print(json.dumps(report.to_dict(), indent=2, ensure_ascii=False))
