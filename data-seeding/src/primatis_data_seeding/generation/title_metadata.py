"""DEV-17.3 — règles d'enrichissement `publisher` / `publication_year` (HD-11).

Aucune donnée n'est inventée : une valeur n'est retenue que si tous les
enregistrements d'édition Open Library locaux, appariés par **ISBN exact**,
concordent et si la valeur est exempte de bruit. Sinon la décision est un
rejet tracé (jamais un choix arbitraire entre enregistrements).
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Iterable

from primatis_data_seeding.generation.text_cleanup import clean_text

PUBLISHER_MAX_LENGTH = 255  # title.publisher VARCHAR(255)
MIN_YEAR = 1450

# Motifs de bruit constatés en DEV-17.2 (mentions de distribution, collections,
# annotations d'éditeur, années collées).
_PUBLISHER_NOISE = re.compile(
    r"distribut|\[|series|\bfor\b|dist\.|\(rand\)|^\W|\d{4}", re.IGNORECASE,
)
_FOUR_DIGIT_YEAR = re.compile(r"(?<!\d)(1[4-9]\d\d|20\d\d)(?!\d)")

ENRICHED = "ENRICHED"


@dataclass(frozen=True)
class MetadataDecision:
    isbn: str
    field: str  # "publisher" | "publication_year"
    value: str  # valeur retenue, ou "" en cas de rejet
    decision: str  # ENRICHED | REJECTED_<motif>


def _as_list(value: object) -> list:
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def decide_publisher(isbn: str, records: Iterable[dict]) -> MetadataDecision:
    seen: set[str] = set()
    for record in records:
        for raw in _as_list(record.get("publisher")) + _as_list(record.get("publishers")):
            text = clean_text(str(raw))
            if text:
                seen.add(text)
    if not seen:
        return MetadataDecision(isbn, "publisher", "", "REJECTED_ABSENT")
    if len(seen) > 1:
        return MetadataDecision(isbn, "publisher", "", "REJECTED_CONFLICT")
    (value,) = seen
    if len(value) > PUBLISHER_MAX_LENGTH or _PUBLISHER_NOISE.search(value):
        return MetadataDecision(isbn, "publisher", "", "REJECTED_NOISE")
    return MetadataDecision(isbn, "publisher", value, ENRICHED)


def decide_publication_year(
    isbn: str, records: Iterable[dict], *, max_year: int,
) -> MetadataDecision:
    years: set[int] = set()
    for record in records:
        for raw in _as_list(record.get("publish_date")) + _as_list(record.get("publish_year")):
            found = set(_FOUR_DIGIT_YEAR.findall(str(raw)))
            if len(found) > 1:
                return MetadataDecision(isbn, "publication_year", "", "REJECTED_AMBIGUOUS_DATE")
            years.update(int(year) for year in found)
    if not years:
        return MetadataDecision(isbn, "publication_year", "", "REJECTED_ABSENT")
    if len(years) > 1:
        return MetadataDecision(isbn, "publication_year", "", "REJECTED_CONFLICT")
    (year,) = years
    if year < MIN_YEAR or year > max_year:
        return MetadataDecision(isbn, "publication_year", "", "REJECTED_OUT_OF_RANGE")
    return MetadataDecision(isbn, "publication_year", str(year), ENRICHED)
