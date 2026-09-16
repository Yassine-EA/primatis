"""Centralized textual anomaly detection (DEV-16.2 §6, DEV-16.3 Étape B).

Single module responsible for flagging suspicious text before it reaches
a PRIMATIS catalogue field. Detection only — this module never rewrites
or "repairs" a value; callers decide what to do with a verdict (keep,
null the field, quarantine or reject the record), per the policy fixed
in DEV-16.2 §3.3/§6.4.

Two distinct signal strengths are used on purpose:

- ``MOJIBAKE_SIGNATURE`` / ``REPLACEMENT_CHARACTER`` / ``CONTROL_CHARACTER``
  / ``HTML_RESIDUE`` / ``DISGUISED_EMPTY`` are high-precision: an exact
  substring/character class confirmed to never occur in legitimate
  FR/EN/NL/DE/ES/IT/LA catalogue text. These drive ``QUARANTINE``.
- ``MOJIBAKE_SUSPECT`` is a broader heuristic (a "Ã"/"Â" precursor byte
  immediately followed by a C1 control character, U+0080-U+009F) that
  correlates strongly with UTF-8 text having been mis-decoded as
  Latin-1/cp1252 upstream, but is kept at ``WARNING`` severity — it is
  never used alone to quarantine a record, only to flag it for the
  human sampling review (DEV-16.2 §16).

Never confuse valid non-ASCII Unicode (accented French, Cyrillic,
Arabic, CJK, ...) with mojibake: only the specific byte-corruption
signatures below are flagged, never "contains a non-ASCII character".
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

# Confirmed real mojibake signatures (DEV-16.3 Étape A, primatis_dev
# medium diagnostic, 2026-09-11): UTF-8 bytes for a French/Latin
# character re-interpreted as Latin-1/cp1252 in the Open Library
# Search/Editions API response BEFORE it ever reaches PRIMATIS (proven
# already present in data/raw/openlibrary/medium/search_fr.json and
# data/raw/openlibrary/medium/editions/books_OL12623748M.json).
MOJIBAKE_SIGNATURES: tuple[str, ...] = (
    # French/general
    "Ã©", "Ã¨", "Ã ", "Ã§", "Ã´", "Ã¹", "Ã®", "Ã¯", "Ã«", "Ã¢", "Ãª", "Ã¼", "Ã±",
    # DEV-16.4 §19 real-DB audit addition: "Ã¤" (ä) was confirmed present
    # in an ACCEPTED Large title ("Lenz-ErzÃ¤hlungen...", real German
    # record) and NOT caught by the DEV-16.3 signature list — every other
    # ES/IT/DE lowercase accented-letter mojibake mapping added at the
    # same time on the same reasoning, not just the one instance found.
    "Ã¤", "Ã¶", "Ã¡", "Ã­", "Ã³", "Ãº", "Ã¬", "Ã²", "Ã»",
    "Â«", "Â»",
    "â€™", "â€œ", "â€\x9d", "â€“", "â€”", "â€¦",
)

# Broader precursor pattern: "Ã" or "Â" immediately followed by a C1
# control character (U+0080-U+009F). This is the exact pattern found on
# primatis_dev for "L Â\x8cuvre" (Zola, id=7293) and "Une vie, une
# Â\x9cuvre" (id=7772) — real, confirmed, but kept as WARNING rather
# than an unconditional QUARANTINE trigger because the precise root
# character cannot always be reconstructed with certainty from the
# corrupted form alone.
_MOJIBAKE_PRECURSOR_RE = re.compile(r"[ÃÂ][-]")

_REPLACEMENT_CHARACTER = "�"

# C0 controls except tab/newline/carriage return (already normalized by
# normalize_text's whitespace collapse) + C1 controls (0x80-0x9F, which
# never occur in well-formed UTF-8 text produced by a real editor).
_CONTROL_CHAR_RE = re.compile(
    r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-]"
)

_HTML_RESIDUE_RE = re.compile(r"<[a-zA-Z/][^>]{0,200}>|&[a-zA-Z]{2,8};|&#\d{2,6};")

# Unicode "space separator" categories beyond plain ASCII space/tab,
# e.g. NBSP (U+00A0), various Unicode spaces (U+2000-U+200A), IDEOGRAPHIC
# SPACE (U+3000). normalize_text() already collapses plain \s runs, so a
# value still containing one of these AFTER normalize_text indicates it
# was not collapsed by the standard \s+ pattern in some Python/locale
# configurations — checked explicitly rather than assumed away.
_UNICODE_SPACE_RE = re.compile(
    "[   -     　]"
)


@dataclass(frozen=True)
class TextQualityIssue:
    code: str
    detail: str


@dataclass(frozen=True)
class TextQualityResult:
    verdict: str  # "OK" | "WARNING" | "QUARANTINE" | "REJECT"
    issues: tuple[TextQualityIssue, ...]

    @property
    def ok(self) -> bool:
        return self.verdict == "OK"


def _is_disguised_empty(value: str) -> bool:
    # Non-empty as a raw string, but carries no actual letter/digit —
    # e.g. ".", "-", "  .  ", a lone punctuation mark passed through a
    # source field that is technically "present".
    return not any(ch.isalnum() for ch in value)


def validate_text_quality(
    value: str | None,
    *,
    blocking: bool = False,
) -> TextQualityResult:
    """Detects textual anomalies in a single field value.

    ``blocking=True`` marks a field whose corruption cannot simply be
    nulled (e.g. ``Title.title``, ``Author.full_name``) — a
    ``QUARANTINE``-level issue is escalated to ``REJECT`` in that case,
    per DEV-16.2 §3.3. For a non-blocking (optional) field, the caller
    is expected to null the field on ``QUARANTINE`` rather than drop the
    whole record — this function only detects, it never decides that.
    """
    if value is None:
        return TextQualityResult("OK", ())

    issues: list[TextQualityIssue] = []

    if _REPLACEMENT_CHARACTER in value:
        issues.append(TextQualityIssue(
            "REPLACEMENT_CHARACTER",
            "U+FFFD present — irreversible loss of information already occurred upstream.",
        ))

    for signature in MOJIBAKE_SIGNATURES:
        if signature in value:
            issues.append(TextQualityIssue(
                "MOJIBAKE_SIGNATURE",
                f"Confirmed mojibake byte-sequence signature {signature!r} found.",
            ))
            break  # one confirmed hit is enough to drive the verdict

    if _MOJIBAKE_PRECURSOR_RE.search(value):
        issues.append(TextQualityIssue(
            "MOJIBAKE_SUSPECT",
            "'Ã'/'Â' immediately followed by a C1 control character — "
            "matches the confirmed primatis_dev corruption pattern but "
            "kept at WARNING severity (root character not reconstructible "
            "with certainty).",
        ))

    if _CONTROL_CHAR_RE.search(value):
        issues.append(TextQualityIssue(
            "CONTROL_CHARACTER",
            "Contains a C0/C1 control character outside of normalized whitespace.",
        ))

    if _HTML_RESIDUE_RE.search(value):
        issues.append(TextQualityIssue(
            "HTML_RESIDUE",
            "Contains an HTML tag or entity — catalogue text fields are plain text.",
        ))

    if _UNICODE_SPACE_RE.search(value):
        issues.append(TextQualityIssue(
            "PATHOLOGICAL_WHITESPACE",
            "Contains a Unicode space separator not collapsed by standard whitespace handling.",
        ))

    stripped = value.strip()
    if stripped and _is_disguised_empty(stripped):
        issues.append(TextQualityIssue(
            "DISGUISED_EMPTY",
            "Non-empty string carries no letter/digit — effectively empty.",
        ))

    nfc = unicodedata.normalize("NFC", value)
    if nfc != value:
        issues.append(TextQualityIssue(
            "UNICODE_NOT_NFC",
            "Value is not NFC-normalized (normalize_text() should already prevent this).",
        ))

    if not issues:
        return TextQualityResult("OK", ())

    codes = {issue.code for issue in issues}
    high_precision = codes & {
        "REPLACEMENT_CHARACTER", "MOJIBAKE_SIGNATURE", "CONTROL_CHARACTER",
        "HTML_RESIDUE", "DISGUISED_EMPTY",
    }
    if high_precision:
        verdict = "REJECT" if blocking else "QUARANTINE"
    else:
        # MOJIBAKE_SUSPECT / PATHOLOGICAL_WHITESPACE / UNICODE_NOT_NFC alone
        verdict = "WARNING"

    return TextQualityResult(verdict, tuple(issues))
