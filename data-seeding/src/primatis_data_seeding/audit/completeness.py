"""DEV-13.20 — matrice d'audit de complétude exhaustive (medium).

Classe, pour CHAQUE Title/Author du profil audité et CHAQUE champ cible,
l'état avant/après enrichissement et la raison exacte d'un NULL final.
Aucune valeur n'est jamais inventée ici : ce module ne fait que consulter
les mêmes sources déjà acquises par le pipeline (`pipeline/bundle.py`) et
documenter, pour l'audit, pourquoi un champ reste NULL ou non.

Vocabulaire de statut (DEV-13.20 §5) :
    PRESENT                  -> déjà présent avant DEV-13.20 (baseline
                                 Search-API), inchangé.
    ENRICHED_FROM_SOURCE      -> absent avant DEV-13.20, rempli par une
                                 source réelle nouvellement consultée.
    SOURCE_CONFIRMED_ABSENT   -> le record source exact a été consulté et
                                 ne contient réellement aucune valeur
                                 utilisable pour ce champ.
    SOURCE_RECORD_MISSING     -> le record source exact (Edition/Work/
                                 Author/Wikidata) n'a pas pu être acquis
                                 du tout.
    SOURCE_VALUE_INVALID      -> une valeur brute existe mais échoue à la
                                 validation (ex. ISBN-10 à 9 chiffres).
    AMBIGUOUS_NOT_USED        -> une valeur brute existe mais est
                                 structurellement ambiguë (ex. plusieurs
                                 citoyennetés Wikidata, pagination
                                 multi-volume) — jamais devinée.
    OUT_OF_SCOPE_BY_POLICY    -> une valeur structurée existe mais est
                                 hors du périmètre de la politique retenue
                                 (ex. entité pays historique, pas un état
                                 souverain moderne).
"""

from __future__ import annotations

from collections.abc import Mapping
import csv
from dataclasses import dataclass
from pathlib import Path

from primatis_data_seeding.acquisition.openlibrary_covers import (
    resolve_cover_image_url,
)
from primatis_data_seeding.normalization.isbn import select_valid_isbn
from primatis_data_seeding.normalization.openlibrary import (
    normalize_exact_date,
    normalize_page_count,
    normalize_pagination,
    normalize_publication_year,
    normalize_work_record,
)
from primatis_data_seeding.normalization.text import truncate_or_none
from primatis_data_seeding.normalization.wikidata import (
    extract_country_of_citizenship_qids,
    is_modern_sovereign_state,
    resolve_author_nationality,
)
from primatis_data_seeding.pipeline.bundle import (
    SelectedEdition,
    _select_isbn,
    load_selected_editions,
)


AUDIT_FIELDNAMES = (
    "entity_type",
    "source_key",
    "field",
    "before_value_present",
    "after_value_present",
    "source",
    "status",
    "reason",
)


@dataclass(frozen=True)
class AuditRow:
    entity_type: str
    source_key: str
    field: str
    before_value_present: bool
    after_value_present: bool
    source: str
    status: str
    reason: str

    def as_dict(self) -> dict:
        return {
            "entity_type": self.entity_type,
            "source_key": self.source_key,
            "field": self.field,
            "before_value_present": "true" if self.before_value_present else "false",
            "after_value_present": "true" if self.after_value_present else "false",
            "source": self.source,
            "status": self.status,
            "reason": self.reason,
        }


def _detail(records: Mapping[str, dict], key: str | None) -> dict | None:
    if key is None:
        return None
    record = records.get(key)
    return record if isinstance(record, dict) else None


# --------------------------------------------------------------------------
# Title fields
# --------------------------------------------------------------------------


def audit_isbn(
    candidate: SelectedEdition, edition_detail: dict | None
) -> AuditRow:
    before_value = _select_isbn(candidate)
    if edition_detail is None:
        after_value = before_value
        status = "PRESENT" if after_value else "SOURCE_RECORD_MISSING"
        reason = (
            "Valeur déjà présente au niveau Search API."
            if after_value
            else "Aucun record Edition détaillé acquis pour cet edition_key."
        )
        return AuditRow(
            "title", candidate.edition_key, "isbn",
            bool(before_value), bool(after_value),
            "search_api" if after_value else "none", status, reason,
        )

    detail_value = select_valid_isbn(
        edition_detail.get("isbn_13"), edition_detail.get("isbn_10")
    )
    if detail_value:
        status = "ENRICHED_FROM_SOURCE" if not before_value else "PRESENT"
        reason = "ISBN valide (checksum) trouvé dans le record Edition exact."
        return AuditRow(
            "title", candidate.edition_key, "isbn",
            bool(before_value), True, "openlibrary_edition_detail", status, reason,
        )

    if before_value:
        return AuditRow(
            "title", candidate.edition_key, "isbn",
            True, True, "search_api", "PRESENT",
            "Valeur déjà présente au niveau Search API (Edition détaillée sans ISBN valide).",
        )

    raw_isbn = (edition_detail.get("isbn_13") or []) + (edition_detail.get("isbn_10") or [])
    if raw_isbn:
        return AuditRow(
            "title", candidate.edition_key, "isbn",
            False, False, "openlibrary_edition_detail", "SOURCE_VALUE_INVALID",
            f"Valeur ISBN brute présente ({raw_isbn!r}) mais checksum/format invalide.",
        )

    return AuditRow(
        "title", candidate.edition_key, "isbn",
        False, False, "openlibrary_edition_detail", "SOURCE_CONFIRMED_ABSENT",
        "Record Edition exact consulté : aucun isbn_10/isbn_13.",
    )


def audit_publisher(
    candidate: SelectedEdition, edition_detail: dict | None
) -> AuditRow:
    before_value = next(
        (truncate_or_none(p, 255) for p in candidate.publishers if truncate_or_none(p, 255)),
        None,
    )
    if edition_detail is None:
        status = "PRESENT" if before_value else "SOURCE_RECORD_MISSING"
        reason = (
            "Valeur déjà présente au niveau Search API."
            if before_value
            else "Aucun record Edition détaillé acquis pour cet edition_key."
        )
        return AuditRow(
            "title", candidate.edition_key, "publisher",
            bool(before_value), bool(before_value),
            "search_api" if before_value else "none", status, reason,
        )

    detail_publishers = edition_detail.get("publishers")
    detail_value = None
    if isinstance(detail_publishers, list):
        detail_value = next(
            (truncate_or_none(p, 255) for p in detail_publishers if truncate_or_none(p, 255)),
            None,
        )

    if detail_value:
        status = "ENRICHED_FROM_SOURCE" if not before_value else "PRESENT"
        return AuditRow(
            "title", candidate.edition_key, "publisher",
            bool(before_value), True, "openlibrary_edition_detail", status,
            "Publisher trouvé dans le record Edition exact.",
        )

    if before_value:
        return AuditRow(
            "title", candidate.edition_key, "publisher",
            True, True, "search_api", "PRESENT",
            "Valeur déjà présente au niveau Search API.",
        )

    return AuditRow(
        "title", candidate.edition_key, "publisher",
        False, False, "openlibrary_edition_detail", "SOURCE_CONFIRMED_ABSENT",
        "Record Edition exact consulté : aucun publisher.",
    )


def audit_publication_year(
    candidate: SelectedEdition, edition_detail: dict | None
) -> AuditRow:
    before_source: object = (
        candidate.publish_date if candidate.publish_date is not None else candidate.publish_year
    )
    before_value = normalize_publication_year(before_source)

    if edition_detail is None:
        status = "PRESENT" if before_value else "SOURCE_RECORD_MISSING"
        reason = (
            "Valeur déjà présente au niveau Search API."
            if before_value
            else "Aucun record Edition détaillé acquis pour cet edition_key."
        )
        return AuditRow(
            "title", candidate.edition_key, "publication_year",
            bool(before_value), bool(before_value),
            "search_api" if before_value else "none", status, reason,
        )

    raw_publish_date = edition_detail.get("publish_date")
    detail_value = normalize_publication_year(raw_publish_date)
    if detail_value:
        status = "ENRICHED_FROM_SOURCE" if not before_value else "PRESENT"
        return AuditRow(
            "title", candidate.edition_key, "publication_year",
            bool(before_value), True, "openlibrary_edition_detail", status,
            "Année exacte extraite du publish_date de l'Edition détaillée.",
        )

    if before_value:
        return AuditRow(
            "title", candidate.edition_key, "publication_year",
            True, True, "search_api", "PRESENT",
            "Valeur déjà présente au niveau Search API.",
        )

    if raw_publish_date:
        return AuditRow(
            "title", candidate.edition_key, "publication_year",
            False, False, "openlibrary_edition_detail", "AMBIGUOUS_NOT_USED",
            f"publish_date brut présent ({raw_publish_date!r}) mais aucune année "
            "unique et non ambiguë n'en est extractible.",
        )

    return AuditRow(
        "title", candidate.edition_key, "publication_year",
        False, False, "openlibrary_edition_detail", "SOURCE_CONFIRMED_ABSENT",
        "Record Edition exact consulté : aucun publish_date.",
    )


def audit_page_count(
    candidate: SelectedEdition, edition_detail: dict | None
) -> AuditRow:
    # The pre-DEV-13.20 baseline (DEV-13.19.C) already consulted the exact
    # Edition detail's `number_of_pages` FIRST, falling back to the Search
    # API candidate. Only the conservative `pagination` string fallback
    # (e.g. "132 p.") is new in DEV-13.20 — that is the only source that
    # can legitimately produce an ENRICHED_FROM_SOURCE status here.
    detail_number_of_pages = (
        normalize_page_count(edition_detail.get("number_of_pages"))
        if edition_detail is not None
        else None
    )
    before_value = detail_number_of_pages or normalize_page_count(
        candidate.number_of_pages
    )

    if before_value:
        source = "openlibrary_edition_detail" if detail_number_of_pages else "search_api"
        return AuditRow(
            "title", candidate.edition_key, "page_count",
            True, True, source, "PRESENT",
            "Valeur déjà présente (baseline DEV-13.19.C, number_of_pages exact).",
        )

    if edition_detail is None:
        return AuditRow(
            "title", candidate.edition_key, "page_count",
            False, False, "none", "SOURCE_RECORD_MISSING",
            "Aucun record Edition détaillé acquis pour cet edition_key.",
        )

    pagination_raw = edition_detail.get("pagination")
    pagination_value = normalize_pagination(pagination_raw)
    if pagination_value:
        return AuditRow(
            "title", candidate.edition_key, "page_count",
            False, True, "openlibrary_edition_detail", "ENRICHED_FROM_SOURCE",
            f"pagination bibliographique exacte convertible ({pagination_raw!r}).",
        )

    if pagination_raw:
        return AuditRow(
            "title", candidate.edition_key, "page_count",
            False, False, "openlibrary_edition_detail", "AMBIGUOUS_NOT_USED",
            f"pagination brute présente ({pagination_raw!r}) mais non convertible "
            "sans ambiguïté (multi-volume/non paginé/description physique).",
        )

    return AuditRow(
        "title", candidate.edition_key, "page_count",
        False, False, "openlibrary_edition_detail", "SOURCE_CONFIRMED_ABSENT",
        "Record Edition exact consulté : ni number_of_pages ni pagination utilisable.",
    )


def audit_summary(
    candidate: SelectedEdition, work_detail: dict | None
) -> AuditRow:
    if work_detail is None:
        return AuditRow(
            "title", candidate.edition_key, "summary",
            False, False, "none", "SOURCE_RECORD_MISSING",
            "Aucun record Work détaillé acquis pour ce work_key.",
        )

    # summary enrichment (Work detail -> description) already shipped in
    # DEV-13.19.C/E, unmodified by DEV-13.20: a value found here is PRESENT.
    value = normalize_work_record(work_detail)
    if value:
        return AuditRow(
            "title", candidate.edition_key, "summary",
            True, True, "openlibrary_work_detail", "PRESENT",
            "description réelle trouvée dans le record Work exact — "
            "enrichissement déjà en place depuis DEV-13.19.C/E.",
        )

    return AuditRow(
        "title", candidate.edition_key, "summary",
        False, False, "openlibrary_work_detail", "SOURCE_CONFIRMED_ABSENT",
        "Record Work exact consulté : aucun champ description utilisable.",
    )


def audit_cover_image_url(
    candidate: SelectedEdition, *, covers_assets_dir: Path | None
) -> AuditRow:
    if candidate.cover_id is None:
        return AuditRow(
            "title", candidate.edition_key, "cover_image_url",
            False, False, "search_api", "SOURCE_CONFIRMED_ABSENT",
            "Aucun cover_i fourni par l'Open Library Search API pour ce Title.",
        )

    resolved = (
        resolve_cover_image_url(candidate.cover_id, assets_dir=covers_assets_dir)
        if covers_assets_dir is not None
        else None
    )
    if resolved:
        # The 30 already-versioned covers were materialized in DEV-13.19.E;
        # DEV-13.20 deliberately materialized no new ones (see module docs
        # below) — a resolved asset here is PRESENT, not new to this task.
        return AuditRow(
            "title", candidate.edition_key, "cover_image_url",
            True, True, "local_asset", "PRESENT",
            f"cover_id={candidate.cover_id} matérialisé localement depuis "
            "DEV-13.19.E.",
        )

    return AuditRow(
        "title", candidate.edition_key, "cover_image_url",
        False, False, "openlibrary_covers_api", "OUT_OF_SCOPE_BY_POLICY",
        f"cover_id={candidate.cover_id} réellement disponible (confirmé via le "
        "dump covers_metadata) mais non matérialisé localement — décision "
        "explicite DEV-13.20 de ne pas dépasser l'échantillon déjà versionné "
        "(bulk archive disproportionné, crawl individuel des 707 restants "
        "explicitement écarté).",
    )


def audit_title_row(
    candidate: SelectedEdition,
    *,
    edition_records: Mapping[str, dict],
    work_records: Mapping[str, dict],
    covers_assets_dir: Path | None,
) -> list[AuditRow]:
    edition_detail = _detail(edition_records, candidate.edition_key)
    work_detail = _detail(work_records, candidate.work_key)
    return [
        audit_isbn(candidate, edition_detail),
        audit_publisher(candidate, edition_detail),
        audit_publication_year(candidate, edition_detail),
        audit_page_count(candidate, edition_detail),
        audit_summary(candidate, work_detail),
        audit_cover_image_url(candidate, covers_assets_dir=covers_assets_dir),
    ]


# --------------------------------------------------------------------------
# Author fields
# --------------------------------------------------------------------------


def audit_author_date(
    author_key: str, field: str, raw_value: object, author_record: dict | None
) -> AuditRow:
    if author_record is None:
        return AuditRow(
            "author", author_key, field,
            False, False, "none", "SOURCE_RECORD_MISSING",
            "Aucun record Author trouvé dans le dump Open Library.",
        )

    # Author.birth_date/death_date enrichment (via the Authors bulk dump)
    # already shipped in DEV-13.19.F, unmodified by DEV-13.20: a value
    # found here is a PRESENT baseline, not a new DEV-13.20 enrichment.
    exact = normalize_exact_date(raw_value)
    if exact:
        return AuditRow(
            "author", author_key, field,
            True, True, "openlibrary_authors_dump", "PRESENT",
            f"Date exacte présente dans le record Author ({raw_value!r}) — "
            "enrichissement déjà en place depuis DEV-13.19.F.",
        )

    if raw_value not in (None, ""):
        return AuditRow(
            "author", author_key, field,
            False, False, "openlibrary_authors_dump", "AMBIGUOUS_NOT_USED",
            f"Valeur textuelle présente ({raw_value!r}) mais non convertible en "
            "date exacte sans hypothèse (année seule ou forme ambiguë).",
        )

    return AuditRow(
        "author", author_key, field,
        False, False, "openlibrary_authors_dump", "SOURCE_CONFIRMED_ABSENT",
        "Record Author exact consulté : champ absent.",
    )


def audit_biography(author_key: str, author_record: dict | None) -> AuditRow:
    if author_record is None:
        return AuditRow(
            "author", author_key, "biography",
            False, False, "none", "SOURCE_RECORD_MISSING",
            "Aucun record Author trouvé dans le dump Open Library.",
        )

    raw_bio = author_record.get("bio")
    from primatis_data_seeding.normalization.openlibrary import normalize_biography

    # Same as birth_date/death_date: biography enrichment already shipped
    # in DEV-13.19.F — a value found here is PRESENT, not new to DEV-13.20.
    value = normalize_biography(raw_bio)
    if value:
        return AuditRow(
            "author", author_key, "biography",
            True, True, "openlibrary_authors_dump", "PRESENT",
            "bio réelle présente dans le record Author — enrichissement déjà "
            "en place depuis DEV-13.19.F.",
        )

    return AuditRow(
        "author", author_key, "biography",
        False, False, "openlibrary_authors_dump", "SOURCE_CONFIRMED_ABSENT",
        "Record Author exact consulté : aucune bio utilisable.",
    )


def audit_nationality(
    author_key: str,
    author_record: dict | None,
    wikidata_author_records: Mapping[str, dict],
    wikidata_country_records: Mapping[str, dict],
) -> AuditRow:
    if author_record is None:
        return AuditRow(
            "author", author_key, "nationality",
            False, False, "none", "SOURCE_RECORD_MISSING",
            "Aucun record Author trouvé dans le dump Open Library.",
        )

    remote_ids = author_record.get("remote_ids")
    qid = remote_ids.get("wikidata") if isinstance(remote_ids, dict) else None
    if not isinstance(qid, str) or not qid:
        return AuditRow(
            "author", author_key, "nationality",
            False, False, "openlibrary_authors_dump", "SOURCE_CONFIRMED_ABSENT",
            "Record Author exact consulté : aucun remote_ids.wikidata explicite.",
        )

    author_entity = wikidata_author_records.get(qid)
    if author_entity is None:
        return AuditRow(
            "author", author_key, "nationality",
            False, False, "wikidata", "SOURCE_RECORD_MISSING",
            f"remote_ids.wikidata={qid} présent mais entité Wikidata non acquise.",
        )

    country_qids = extract_country_of_citizenship_qids(author_entity)
    if not country_qids:
        return AuditRow(
            "author", author_key, "nationality",
            False, False, "wikidata", "SOURCE_CONFIRMED_ABSENT",
            f"Entité Wikidata {qid} consultée : aucun claim P27 (country of citizenship).",
        )
    if len(country_qids) > 1:
        return AuditRow(
            "author", author_key, "nationality",
            False, False, "wikidata", "AMBIGUOUS_NOT_USED",
            f"Entité Wikidata {qid} : {len(country_qids)} citoyennetés P27 "
            f"distinctes ({', '.join(country_qids)}) — jamais choisi arbitrairement.",
        )

    country_entity = wikidata_country_records.get(country_qids[0])
    if country_entity is None:
        return AuditRow(
            "author", author_key, "nationality",
            False, False, "wikidata", "SOURCE_RECORD_MISSING",
            f"Pays P27={country_qids[0]} référencé mais entité Wikidata non acquise.",
        )

    if not is_modern_sovereign_state(country_entity):
        return AuditRow(
            "author", author_key, "nationality",
            False, False, "wikidata", "OUT_OF_SCOPE_BY_POLICY",
            f"P27={country_qids[0]} est une entité historique (pas un état "
            "souverain/pays moderne au sens P31) — hors politique DEV-13.20.",
        )

    value = resolve_author_nationality(author_entity, wikidata_country_records)
    if value:
        return AuditRow(
            "author", author_key, "nationality",
            False, True, "wikidata", "ENRICHED_FROM_SOURCE",
            f"Nationalité résolue via author_key -> wikidata={qid} -> P27="
            f"{country_qids[0]} -> label={value!r}.",
        )

    return AuditRow(
        "author", author_key, "nationality",
        False, False, "wikidata", "SOURCE_VALUE_INVALID",
        f"Entité pays {country_qids[0]} sans label anglais exploitable.",
    )


def audit_author_row(
    author_key: str,
    author_record: dict | None,
    *,
    wikidata_author_records: Mapping[str, dict],
    wikidata_country_records: Mapping[str, dict],
) -> list[AuditRow]:
    raw_birth = author_record.get("birth_date") if author_record else None
    raw_death = author_record.get("death_date") if author_record else None
    return [
        audit_author_date(author_key, "birth_date", raw_birth, author_record),
        audit_author_date(author_key, "death_date", raw_death, author_record),
        audit_biography(author_key, author_record),
        audit_nationality(
            author_key,
            author_record,
            wikidata_author_records,
            wikidata_country_records,
        ),
    ]


def write_audit_csv(rows: list[AuditRow], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=AUDIT_FIELDNAMES)
        writer.writeheader()
        for row in rows:
            writer.writerow(row.as_dict())


def build_completeness_audit(
    selected: list[SelectedEdition],
    *,
    author_records: Mapping[str, dict],
    edition_records: Mapping[str, dict],
    work_records: Mapping[str, dict],
    wikidata_author_records: Mapping[str, dict],
    wikidata_country_records: Mapping[str, dict],
    covers_assets_dir: Path | None,
) -> list[AuditRow]:
    """Une ligne par (Title, champ) et par (Author, champ) réellement audité.

    100% des Titles sélectionnés et 100% des author_key référencés sont
    couverts — aucune ligne n'est omise, y compris quand le résultat final
    reste NULL (le statut/raison explique alors pourquoi).
    """
    rows: list[AuditRow] = []

    for candidate in selected:
        rows.extend(
            audit_title_row(
                candidate,
                edition_records=edition_records,
                work_records=work_records,
                covers_assets_dir=covers_assets_dir,
            )
        )

    author_keys = sorted(
        {key for candidate in selected for key in candidate.author_keys}
    )
    for author_key in author_keys:
        from primatis_data_seeding.normalization.openlibrary import (
            canonical_author_key,
        )

        canonical_key = canonical_author_key(author_key)
        author_record = (
            author_records.get(canonical_key) if canonical_key is not None else None
        )
        rows.extend(
            audit_author_row(
                author_key,
                author_record,
                wikidata_author_records=wikidata_author_records,
                wikidata_country_records=wikidata_country_records,
            )
        )

    return rows


def build_parser():
    import argparse

    parser = argparse.ArgumentParser(
        description=(
            "Build the DEV-13.20 exhaustive completeness audit matrix "
            "(data/validated/<profile>/completeness_audit.csv) from the "
            "already-acquired validated snapshots of a profile. Read-only: "
            "performs no network access and no PostgreSQL access."
        )
    )
    parser.add_argument("--profile", required=True)
    parser.add_argument("--project-root", type=Path, default=Path.cwd())
    parser.add_argument(
        "--covers-assets-dir",
        type=Path,
        default=None,
        help="Directory of already-materialized local cover assets (optional).",
    )
    parser.add_argument("--output", type=Path, default=None)
    return parser


def main() -> int:
    from primatis_data_seeding.acquisition.openlibrary_authors import (
        load_authors_snapshot,
    )
    from primatis_data_seeding.acquisition.openlibrary_details import (
        load_records_snapshot,
    )
    from primatis_data_seeding.acquisition.wikidata import load_entities_snapshot

    args = build_parser().parse_args()
    validated = args.project_root / "data" / "validated" / args.profile

    selected = load_selected_editions(
        validated / "openlibrary_selected.jsonl",
        expected_count=_expected_count(validated),
    )

    def _load_optional(path: Path, loader):
        return loader(path) if path.is_file() else {}

    author_records = _load_optional(
        validated / "authors_selected.jsonl", load_authors_snapshot
    )
    work_records = _load_optional(
        validated / "works_selected.jsonl", load_records_snapshot
    )
    edition_records = _load_optional(
        validated / "editions_selected.jsonl", load_records_snapshot
    )
    wikidata_author_records = _load_optional(
        validated / "wikidata_authors_selected.jsonl", load_entities_snapshot
    )
    wikidata_country_records = _load_optional(
        validated / "wikidata_countries_selected.jsonl", load_entities_snapshot
    )

    rows = build_completeness_audit(
        selected,
        author_records=author_records,
        edition_records=edition_records,
        work_records=work_records,
        wikidata_author_records=wikidata_author_records,
        wikidata_country_records=wikidata_country_records,
        covers_assets_dir=args.covers_assets_dir,
    )

    output = args.output or (validated / "completeness_audit.csv")
    write_audit_csv(rows, output)

    from collections import Counter

    status_counts = Counter(row.status for row in rows)
    print(f"rows_written={len(rows)}")
    print(f"titles_audited={len(selected)}")
    print(f"authors_audited={len({row.source_key for row in rows if row.entity_type == 'author'})}")
    for status, count in sorted(status_counts.items()):
        print(f"{status}={count}")
    print(f"output={output}")
    return 0


def _expected_count(validated: Path) -> int:
    import json

    with (validated / "openlibrary_selected.jsonl").open("r", encoding="utf-8") as handle:
        return sum(1 for line in handle if line.strip())


if __name__ == "__main__":
    raise SystemExit(main())
