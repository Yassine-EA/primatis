"""Documentary-category precision signal (DEV-16.4 §7).

DEV-16.3 showed that filtering a batch by a SINGLE generic English
`subject:<keyword>` term in the Search API query (DOCUMENTARY_CATEGORY_
KEYWORDS in pipeline/batch.py) can still return tangential results (art
catalogs and theatre programs mixed into a "history" query, DEV-16.3
§P). DEV-16.4 adds a second, independent, EXPLAINABLE signal: exact
match (same fold/casefold discipline as `mapping/genres.py`, never
fuzzy/substring) of a candidate's REAL `subject` list against a small
per-category alias set, each entry chosen from subject frequencies
actually measured on the medium sample
(`data/validated/medium/works_selected.jsonl`).

This is deliberately NOT used to silently drop candidates: many
legitimate candidates carry no `subject` field at all in the Search API
response (402/1000 works on the medium sample, DEV-16.3 §G) — a strict
reject would collapse yield without proving the candidate is actually
off-category. It is used as a MEASURED, REPORTED precision signal
(`documentary_category_match_rate` in the batch report) — a human
(DEV-16.2 §16 sampling method) reviews the residual imprecision.

Explicitly forbidden by DEV-16.4 §7 and respected here: no LLM
classification, no fuzzy/substring matching, no unexplainable score.
"""

from __future__ import annotations

import re
import unicodedata

# Multiple exact aliases per category, each chosen because it was
# actually observed at a measurable frequency in the medium sample
# (data/validated/medium/works_selected.jsonl, DEV-16.3 §G / DEV-16.4
# §7 measurement) OR is an unambiguous English Library-of-Congress-style
# subject heading for that category. Never a substring/fuzzy pattern.
DOCUMENTARY_CATEGORY_SUBJECT_ALIASES: dict[str, frozenset[str]] = {
    "literature": frozenset({
        "fiction", "general fiction", "literature", "french literature",
        "english literature", "world literature",
    }),
    "youth": frozenset({
        "juvenile fiction", "juvenile literature", "children's literature",
        "young adult fiction", "young adult literature",
    }),
    "history": frozenset({
        "history", "historiography", "world history", "european history",
    }),
    "science": frozenset({
        "science", "natural history", "physics", "biology", "chemistry",
        "mathematics",
    }),
    "philosophy_religion": frozenset({
        "philosophy", "religion", "ethics", "theology",
    }),
    "social_sciences": frozenset({
        "social sciences", "sociology", "economic conditions",
        "social conditions", "political science", "law and legislation",
        "psychology", "politics and government",
    }),
    "arts": frozenset({
        "art", "architecture", "modern art", "painting", "aesthetics",
        "music",
    }),
    "comics": frozenset({
        "comic books, strips, etc.", "graphic novels", "comics",
    }),
    "documentary": frozenset({
        "description and travel", "manners and customs", "guides",
        "cookery", "travel",
    }),
}


def _fold(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    without_accents = "".join(ch for ch in normalized if not unicodedata.combining(ch))
    return re.sub(r"\s+", " ", without_accents.casefold()).strip()


_FOLDED_ALIASES: dict[str, frozenset[str]] = {
    category: frozenset(_fold(alias) for alias in aliases)
    for category, aliases in DOCUMENTARY_CATEGORY_SUBJECT_ALIASES.items()
}


def matches_documentary_category(
    subjects: list[str] | tuple[str, ...],
    category: str,
) -> bool:
    """True only if at least one of `subjects` matches one of the
    category's aliases EXACTLY (after fold) — never a substring/fuzzy
    match. `category` must be one of DOCUMENTARY_CATEGORY_SUBJECT_ALIASES
    (raises KeyError otherwise, deliberately — an unknown category is a
    caller bug, not a "no match").
    """
    aliases = _FOLDED_ALIASES[category]
    return any(_fold(subject) in aliases for subject in subjects)
