from __future__ import annotations

from dataclasses import dataclass, field
import unicodedata
import re
from collections import defaultdict

from primatis_data_seeding.models import NormalizedAuthor, NormalizedEdition


_NON_ALNUM_RE = re.compile(r"[^\w]+", re.UNICODE)


@dataclass(frozen=True)
class DuplicateRecord:
    duplicate_source_key: str
    kept_source_key: str
    reason: str


@dataclass(frozen=True)
class DuplicateCandidate:
    source_keys: tuple[str, ...]
    reason: str
    fingerprint: str


@dataclass(frozen=True)
class DeduplicationConflict:
    source_keys: tuple[str, ...]
    reason: str
    isbn: str | None = None


@dataclass
class EditionDeduplicationResult:
    kept: list[NormalizedEdition] = field(default_factory=list)
    duplicates: list[DuplicateRecord] = field(default_factory=list)
    candidates: list[DuplicateCandidate] = field(default_factory=list)
    conflicts: list[DeduplicationConflict] = field(default_factory=list)


@dataclass
class AuthorDeduplicationResult:
    kept: list[NormalizedAuthor] = field(default_factory=list)
    duplicates: list[DuplicateRecord] = field(default_factory=list)
    candidates: list[DuplicateCandidate] = field(default_factory=list)


def _fold_text(value: str | None) -> str:
    if not value:
        return ""
    normalized = unicodedata.normalize("NFKD", value)
    ascii_like = "".join(ch for ch in normalized if not unicodedata.combining(ch))
    return _NON_ALNUM_RE.sub(" ", ascii_like.casefold()).strip()


def _edition_quality_score(edition: NormalizedEdition) -> tuple[int, str]:
    score = 0
    score += 4 if edition.work_key else 0
    score += 3 if edition.author_keys else 0
    score += 2 if edition.publication_year is not None else 0
    score += 2 if edition.page_count is not None else 0
    score += 2 if edition.publisher else 0
    score += 1 if edition.subtitle else 0

    # Higher completeness wins; lexical source_key breaks ties deterministically.
    return score, edition.source_key


def _select_winner(editions: list[NormalizedEdition]) -> NormalizedEdition:
    return sorted(
        editions,
        key=lambda edition: (-_edition_quality_score(edition)[0], edition.source_key),
    )[0]


def _has_isbn_conflict(editions: list[NormalizedEdition]) -> bool:
    languages = {edition.language for edition in editions}
    titles = {_fold_text(edition.title) for edition in editions}

    # Same ISBN should represent one edition. Divergent language or materially
    # different normalized title indicates suspicious source data.
    return len(languages) > 1 or len(titles) > 1


def _candidate_fingerprint(edition: NormalizedEdition) -> str | None:
    if edition.isbn is not None:
        return None

    authors = "|".join(sorted(edition.author_keys))
    if not authors:
        return None

    fields = (
        _fold_text(edition.title),
        _fold_text(edition.subtitle),
        edition.language,
        str(edition.publication_year or ""),
        _fold_text(edition.publisher),
        authors,
    )

    # This fingerprint only identifies review candidates. It is deliberately
    # NOT an automatic merge key.
    return "||".join(fields)


def deduplicate_editions(
    editions: list[NormalizedEdition],
) -> EditionDeduplicationResult:
    result = EditionDeduplicationResult()

    # Step 1: exact duplicate source records.
    by_source_key: dict[str, list[NormalizedEdition]] = defaultdict(list)
    for edition in editions:
        by_source_key[edition.source_key].append(edition)

    source_unique: list[NormalizedEdition] = []
    for source_key in sorted(by_source_key):
        group = by_source_key[source_key]
        winner = _select_winner(group)
        source_unique.append(winner)

        for duplicate in group:
            if duplicate is winner:
                continue
            result.duplicates.append(
                DuplicateRecord(
                    duplicate_source_key=duplicate.source_key,
                    kept_source_key=winner.source_key,
                    reason="SAME_SOURCE_KEY",
                )
            )

    # Step 2: valid ISBN is a strong automatic duplicate signal.
    without_isbn: list[NormalizedEdition] = []
    by_isbn: dict[str, list[NormalizedEdition]] = defaultdict(list)

    for edition in source_unique:
        if edition.isbn is None:
            without_isbn.append(edition)
        else:
            by_isbn[edition.isbn].append(edition)

    for isbn in sorted(by_isbn):
        group = by_isbn[isbn]

        if len(group) == 1:
            result.kept.append(group[0])
            continue

        if _has_isbn_conflict(group):
            result.conflicts.append(
                DeduplicationConflict(
                    source_keys=tuple(sorted(e.source_key for e in group)),
                    reason="ISBN_METADATA_CONFLICT",
                    isbn=isbn,
                )
            )
            # Conservative behavior: suspicious ISBN group is not selected.
            continue

        winner = _select_winner(group)
        result.kept.append(winner)

        for duplicate in group:
            if duplicate is winner:
                continue
            result.duplicates.append(
                DuplicateRecord(
                    duplicate_source_key=duplicate.source_key,
                    kept_source_key=winner.source_key,
                    reason="SAME_VALID_ISBN",
                )
            )

    # Step 3: records without ISBN are NEVER auto-merged from title/author data.
    # Exact combined fingerprints are only surfaced as review candidates.
    candidate_groups: dict[str, list[NormalizedEdition]] = defaultdict(list)
    for edition in without_isbn:
        result.kept.append(edition)
        fingerprint = _candidate_fingerprint(edition)
        if fingerprint is not None:
            candidate_groups[fingerprint].append(edition)

    for fingerprint in sorted(candidate_groups):
        group = candidate_groups[fingerprint]
        if len(group) > 1:
            result.candidates.append(
                DuplicateCandidate(
                    source_keys=tuple(sorted(e.source_key for e in group)),
                    reason="EXACT_METADATA_CANDIDATE_NO_ISBN",
                    fingerprint=fingerprint,
                )
            )

    result.kept.sort(key=lambda edition: edition.source_key)
    result.duplicates.sort(
        key=lambda duplicate: (
            duplicate.reason,
            duplicate.kept_source_key,
            duplicate.duplicate_source_key,
        )
    )
    result.candidates.sort(key=lambda candidate: candidate.source_keys)
    result.conflicts.sort(key=lambda conflict: conflict.source_keys)

    return result


def _author_candidate_fingerprint(author: NormalizedAuthor) -> str:
    birth = author.birth_date.isoformat() if author.birth_date else ""
    death = author.death_date.isoformat() if author.death_date else ""
    return "||".join((_fold_text(author.full_name), birth, death))


def deduplicate_authors(
    authors: list[NormalizedAuthor],
) -> AuthorDeduplicationResult:
    result = AuthorDeduplicationResult()

    by_source_key: dict[str, list[NormalizedAuthor]] = defaultdict(list)
    for author in authors:
        by_source_key[author.source_key].append(author)

    source_unique: list[NormalizedAuthor] = []
    for source_key in sorted(by_source_key):
        group = by_source_key[source_key]
        # Same Open Library source key is authoritative. Prefer the record
        # carrying more exact dates, then deterministic lexical representation.
        winner = sorted(
            group,
            key=lambda author: (
                -(1 if author.birth_date else 0) - (1 if author.death_date else 0),
                author.full_name,
            ),
        )[0]
        source_unique.append(winner)

        for duplicate in group:
            if duplicate is winner:
                continue
            result.duplicates.append(
                DuplicateRecord(
                    duplicate_source_key=duplicate.source_key,
                    kept_source_key=winner.source_key,
                    reason="SAME_SOURCE_KEY",
                )
            )

    # Names are not unique in PRIMATIS. Similar/exact identity metadata across
    # different Open Library keys is surfaced only as a candidate.
    by_fingerprint: dict[str, list[NormalizedAuthor]] = defaultdict(list)
    for author in source_unique:
        result.kept.append(author)
        by_fingerprint[_author_candidate_fingerprint(author)].append(author)

    for fingerprint in sorted(by_fingerprint):
        group = by_fingerprint[fingerprint]
        if len(group) > 1:
            result.candidates.append(
                DuplicateCandidate(
                    source_keys=tuple(sorted(a.source_key for a in group)),
                    reason="AUTHOR_IDENTITY_CANDIDATE",
                    fingerprint=fingerprint,
                )
            )

    result.kept.sort(key=lambda author: author.source_key)
    result.duplicates.sort(
        key=lambda duplicate: (
            duplicate.reason,
            duplicate.kept_source_key,
            duplicate.duplicate_source_key,
        )
    )
    result.candidates.sort(key=lambda candidate: candidate.source_keys)

    return result
