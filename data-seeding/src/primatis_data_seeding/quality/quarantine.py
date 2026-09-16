"""Wires `text_quality` into the mapping output: ACCEPTED / QUARANTINED
states for a catalogue record (DEV-16.2 §3.3, DEC-16.3-03, DEV-16.3
Étape B/L, DEV-16.4 §4.2 cascade fix).

This is a POST-pass over an already-built `CatalogueMappingResult`
(`mapping/catalogue.py::map_catalogue`) — deliberately additive and
separate rather than folded into `map_catalogue` itself, so the
already-validated `small`/`medium` bundle path (DEV-13, byte-for-byte
reproducibility already proven) stays completely untouched. New
pipelines (DEV-16.3+ batch pipeline) opt into this pass explicitly.

DEC-16.3-03 is enforced structurally here: a QUARANTINED record is
simply never placed in `QualityPolicyResult.titles`/`.authors` — there
is no code path by which a caller could accidentally load one, because
it is not present in the collections a loader would consume.

DEV-16.4 §4.2 fix (cascade): quarantining an Author's `full_name` now
DOES cascade — every `title_author` link to that Author is dropped
(`QualityPolicyResult.title_authors` never references a quarantined
Author), and any Title left with zero remaining valid Author is itself
quarantined (`NO_VALID_AUTHOR_AFTER_QUARANTINE`), never silently kept
with fewer authors than the source data actually provided. A Title with
at least one remaining valid Author stays ACCEPTED, with only the valid
author link(s) kept.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace

from primatis_data_seeding.mapping.models import (
    CatalogueMappingResult,
    PrimatisAuthorRow,
    PrimatisTitleAuthorRow,
    PrimatisTitleRow,
)
from primatis_data_seeding.normalization.text_quality import validate_text_quality
from primatis_data_seeding.provenance.record_provenance import FieldDecision


@dataclass(frozen=True)
class QuarantineEntry:
    source_key: str
    entity: str  # "title" | "author"
    reason_code: str
    detail: str


@dataclass
class QualityPolicyResult:
    titles: list[PrimatisTitleRow] = field(default_factory=list)
    authors: list[PrimatisAuthorRow] = field(default_factory=list)
    title_authors: list[PrimatisTitleAuthorRow] = field(default_factory=list)
    quarantined: list[QuarantineEntry] = field(default_factory=list)
    field_decisions: dict[str, list[FieldDecision]] = field(default_factory=dict)

    def is_quarantined(self, source_key: str) -> bool:
        return any(entry.source_key == source_key for entry in self.quarantined)


def _record_decision(result: QualityPolicyResult, source_key: str, decision: FieldDecision) -> None:
    result.field_decisions.setdefault(source_key, []).append(decision)


def _check_title_text(title: PrimatisTitleRow, result: QualityPolicyResult) -> PrimatisTitleRow | None:
    """Runs the field-level text-quality checks (title/subtitle/summary/
    publisher). Returns None if the Title itself must be quarantined
    (blocking issue on `title`), otherwise a possibly-cleaned copy.
    """
    title_check = validate_text_quality(title.title, blocking=True)
    if title_check.verdict == "REJECT":
        result.quarantined.append(QuarantineEntry(
            source_key=title.source_key,
            entity="title",
            reason_code="TEXT_ENCODING_SUSPECT",
            detail="; ".join(f"{i.code}: {i.detail}" for i in title_check.issues),
        ))
        _record_decision(result, title.source_key, FieldDecision(
            "title", "REJECTED",
            "; ".join(i.code for i in title_check.issues),
            "DEV-16.2 §6.4.a",
        ))
        return None

    cleaned = title
    for attr in ("subtitle", "summary", "publisher"):
        value = getattr(cleaned, attr)
        check = validate_text_quality(value, blocking=False)
        if check.verdict == "QUARANTINE":
            cleaned = replace(cleaned, **{attr: None})
            _record_decision(result, title.source_key, FieldDecision(
                attr, "NULLED",
                "; ".join(i.code for i in check.issues),
                "DEV-16.2 §6.4.a",
            ))
        else:
            _record_decision(result, title.source_key, FieldDecision(
                attr, "KEPT", "No blocking text-quality issue.", "DEV-16.2 §6.4",
            ))
    return cleaned


def _check_author_text(author: PrimatisAuthorRow, result: QualityPolicyResult) -> PrimatisAuthorRow | None:
    """Returns None if the Author must be quarantined (blocking issue on
    `full_name`), otherwise a possibly-cleaned copy (biography nulled if
    suspect).
    """
    name_check = validate_text_quality(author.full_name, blocking=True)
    if name_check.verdict == "REJECT":
        result.quarantined.append(QuarantineEntry(
            source_key=author.source_key,
            entity="author",
            reason_code="TEXT_ENCODING_SUSPECT",
            detail="; ".join(f"{i.code}: {i.detail}" for i in name_check.issues),
        ))
        _record_decision(result, author.source_key, FieldDecision(
            "full_name", "REJECTED",
            "; ".join(i.code for i in name_check.issues),
            "DEV-16.2 §6.4.a",
        ))
        return None

    cleaned = author
    bio_check = validate_text_quality(author.biography, blocking=False)
    if bio_check.verdict == "QUARANTINE":
        cleaned = replace(cleaned, biography=None)
        _record_decision(result, author.source_key, FieldDecision(
            "biography", "NULLED",
            "; ".join(i.code for i in bio_check.issues),
            "DEV-16.2 §6.4.a",
        ))
    return cleaned


def apply_text_quality_policy(mapping_result: CatalogueMappingResult) -> QualityPolicyResult:
    """Runs the text-quality/quarantine policy over an already-mapped
    catalogue, INCLUDING the Author -> Title quarantine cascade
    (DEV-16.4 §4.2). Returns a NEW result — `mapping_result` is never
    mutated.

    Order of operations (each a separate pass, deliberately):
      1. Author text-quality (full_name/biography) -> quarantined Author
         source_keys determined.
      2. Title text-quality (title/subtitle/summary/publisher) ->
         quarantined Title source_keys determined (independently of
         authors at this point).
      3. Cascade: for every Title not already quarantined in step 2,
         drop any `title_author` link to an Author quarantined in step
         1. If zero links remain, quarantine the Title
         (`NO_VALID_AUTHOR_AFTER_QUARANTINE`) and drop it from
         `result.titles`. Otherwise keep the Title with only the
         surviving link(s).
    """
    result = QualityPolicyResult()

    cleaned_authors_by_key: dict[str, PrimatisAuthorRow] = {}
    for author in mapping_result.authors:
        cleaned = _check_author_text(author, result)
        if cleaned is not None:
            cleaned_authors_by_key[cleaned.source_key] = cleaned
            result.authors.append(cleaned)

    quarantined_author_keys = {
        entry.source_key for entry in result.quarantined if entry.entity == "author"
    }

    cleaned_titles_by_key: dict[str, PrimatisTitleRow] = {}
    for title in mapping_result.titles:
        cleaned = _check_title_text(title, result)
        if cleaned is not None:
            cleaned_titles_by_key[cleaned.source_key] = cleaned

    valid_author_links: dict[str, list[str]] = {}
    for link in mapping_result.title_authors:
        if link.title_source_key not in cleaned_titles_by_key:
            continue  # Title already quarantined on its own text — link is moot.
        if link.author_source_key in quarantined_author_keys:
            continue  # DEV-16.4 §4.2: never load a title_author to a quarantined Author.
        valid_author_links.setdefault(link.title_source_key, []).append(link.author_source_key)

    for source_key, title in cleaned_titles_by_key.items():
        remaining_authors = valid_author_links.get(source_key, [])
        if not remaining_authors:
            result.quarantined.append(QuarantineEntry(
                source_key=source_key,
                entity="title",
                reason_code="NO_VALID_AUTHOR_AFTER_QUARANTINE",
                detail="Every Author referenced by this Title was quarantined; "
                       "0 valid Author remains (DEV-16.4 §4.2 cascade).",
            ))
            _record_decision(result, source_key, FieldDecision(
                "authors", "REJECTED",
                "NO_VALID_AUTHOR_AFTER_QUARANTINE",
                "DEV-16.4 §4.2",
            ))
            continue

        result.titles.append(title)
        for author_key in sorted(remaining_authors):
            result.title_authors.append(PrimatisTitleAuthorRow(
                title_source_key=source_key, author_source_key=author_key,
            ))

    result.titles.sort(key=lambda t: t.source_key)
    result.authors.sort(key=lambda a: a.source_key)
    result.title_authors.sort(key=lambda link: (link.title_source_key, link.author_source_key))
    result.quarantined.sort(key=lambda e: (e.entity, e.source_key))
    return result
