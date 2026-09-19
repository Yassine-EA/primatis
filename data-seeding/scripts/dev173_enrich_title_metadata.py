"""DEV-17.3 — enrichissement ciblé `publisher` / `publication_year` (HD-11), sources locales.

Parcourt les JSON/JSONL Open Library DÉJÀ présents dans `data-seeding/data/`
(aucun appel réseau) et apparie les enregistrements d'édition aux ISBN du
catalogue par **ISBN exact** (ISBN-10 convertis en ISBN-13), sans similarité de
titre ni d'auteur. Les règles de décision sont dans
`generation/title_metadata.py` : valeur unique, sans conflit ni bruit.

Sortie `title_metadata_enrichment.csv` (déterministe) :
`isbn, field, value, source, source_record, decision` — une ligne par
(ISBN apparié, champ), y compris les rejets tracés (`value` vide).

Usage : dev173_enrich_title_metadata.py <data_dir> <titles.csv> <sortie.csv> <année_max>
"""

from __future__ import annotations

import csv
import json
import re
import sys
from pathlib import Path

from primatis_data_seeding.generation.title_metadata import (
    decide_publication_year,
    decide_publisher,
)

SCANNED_ROOTS = ("raw/openlibrary", "validated", "cache")
_ISBN_13 = re.compile(r"(?<!\d)(97[89]\d{10}|805\d{10})(?!\d)")
_ISBN_10_FIELD = re.compile(r'"isbn_10"\s*:\s*\[([^\]]*)\]')
FIELDS = ("isbn", "field", "value", "source", "source_record", "decision")


def isbn10_to_isbn13(value: str) -> str | None:
    if len(value) != 10 or not value[:9].isdigit() or value[-1] in "Xx":
        return None
    core = "978" + value[:9]
    check = (10 - sum(int(c) * (1 if i % 2 == 0 else 3) for i, c in enumerate(core)) % 10) % 10
    return core + str(check)


def _isbns_of(record: dict) -> set[str]:
    found: set[str] = set()
    for key in ("isbn", "isbn_13", "isbn_10"):
        raw = record.get(key)
        for item in raw if isinstance(raw, list) else ([raw] if raw else []):
            text = str(item).replace("-", "").strip()
            if len(text) == 13 and text.isdigit():
                found.add(text)
            elif len(text) == 10:
                converted = isbn10_to_isbn13(text)
                if converted:
                    found.add(converted)
    return found


def _walk(node, catalogue: set[str], relpath: str, out: dict) -> None:
    if isinstance(node, dict):
        hits = _isbns_of(node) & catalogue
        if hits and ("title" in node or "key" in node):
            for isbn in hits:
                out.setdefault(isbn, []).append((relpath, node))
        for value in node.values():
            _walk(value, catalogue, relpath, out)
    elif isinstance(node, list):
        for value in node:
            _walk(value, catalogue, relpath, out)


def _candidate_isbns(text: str) -> set[str]:
    found = set(_ISBN_13.findall(text))
    for chunk in _ISBN_10_FIELD.findall(text):
        for token in re.findall(r"\d{9}[\dXx]", chunk):
            converted = isbn10_to_isbn13(token)
            if converted:
                found.add(converted)
    return found


def collect_records(data_dir: Path, catalogue: set[str]) -> dict[str, list[tuple[str, dict]]]:
    out: dict[str, list[tuple[str, dict]]] = {}
    for root in SCANNED_ROOTS:
        base = data_dir / root
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*")):
            if path.suffix not in (".json", ".jsonl") or not path.is_file():
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            if not (_candidate_isbns(text) & catalogue):
                continue
            relpath = path.relative_to(data_dir).as_posix()
            lines = text.splitlines() if path.suffix == ".jsonl" else [text]
            for line in lines:
                try:
                    _walk(json.loads(line), catalogue, relpath, out)
                except ValueError:
                    continue
    return out


def build_rows(records: dict[str, list[tuple[str, dict]]], max_year: int) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for isbn in sorted(records):
        entries = records[isbn]
        sources = sorted({relpath for relpath, _ in entries})
        editions = sorted({
            str(rec.get("key")) for _, rec in entries
            if str(rec.get("key", "")).startswith("/books/")
        })
        source = ";".join(sources[:5]) + (f";+{len(sources) - 5}" if len(sources) > 5 else "")
        recs = [rec for _, rec in entries]
        for decision in (
            decide_publisher(isbn, recs),
            decide_publication_year(isbn, recs, max_year=max_year),
        ):
            rows.append({
                "isbn": isbn, "field": decision.field, "value": decision.value,
                "source": source, "source_record": ";".join(editions),
                "decision": decision.decision,
            })
    return rows


def write_rows(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def main() -> int:
    data_dir, titles_csv, output = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    max_year = int(sys.argv[4])
    with titles_csv.open("r", encoding="utf-8", newline="") as handle:
        catalogue = {row["isbn"] for row in csv.DictReader(handle) if row["isbn"]}
    records = collect_records(data_dir, catalogue)
    rows = build_rows(records, max_year)
    write_rows(output, rows)
    enriched = sum(1 for r in rows if r["decision"] == "ENRICHED")
    print(f"matched_isbn={len(records)} rows={len(rows)} enriched={enriched}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
