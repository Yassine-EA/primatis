"""DEV-17.3 — reconstruction du bundle `full_consolidated` (complétude ciblée).

À partir du bundle DEV-16 (`--candidate`, JAMAIS modifié), écrit un nouveau
bundle dans `--output-dir` :

1. copie des fichiers catalogue/démographie ;
2. micro-nettoyage de texte (Titles / Authors) — `generation/text_cleanup.py` ;
3. enrichissement ciblé `publisher` / `publication_year` (ISBN exacts) ;
4. scénarios relatifs à `--reference-datetime` + états de comptes
   (`dev34_build_consolidated_scenarios.py`) ;
5. corpus Articles / Tags (`generation/articles.py`) ;
6. couvertures noires → placeholder institutionnel (`--repo-root`) ;
7. provenance recalculée (`consolidated_bundle_provenance.json`) et contrôlée.

Idempotent et déterministe (hors hash bcrypt : `users.csv` n'est pas régénéré,
ses hashes sont ceux du bundle DEV-16). Aucun accès réseau, aucun accès base.

Usage :
  dev173_build_targeted_bundle.py --candidate DIR --output-dir DIR --data-dir DIR \
      --repo-root DIR --reference-datetime 2026-09-19T12:00:00Z
"""

from __future__ import annotations

import argparse
import csv
import json
import shutil
import sys
from copy import deepcopy
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import dev34_build_consolidated_scenarios as scenarios_script  # noqa: E402
import dev173_cover_placeholders as placeholders  # noqa: E402
import dev173_enrich_title_metadata as enrichment  # noqa: E402

from primatis_data_seeding.acquisition.provenance import sha256_file
from primatis_data_seeding.export.articles_csv import export_articles_csv
from primatis_data_seeding.generation.articles import build_article_seed
from primatis_data_seeding.generation.reference_datetime import parse_reference_datetime
from primatis_data_seeding.generation.text_cleanup import clean_text
from primatis_data_seeding.generation.title_metadata import ENRICHED

PROVENANCE = "consolidated_bundle_provenance.json"
REGENERATED = set(scenarios_script.SCENARIO_FILES) | {"users.csv"}
NEW_FILES = {
    "articles.csv": "ARTICLE_SEED_DEV173", "tags.csv": "ARTICLE_SEED_DEV173",
    "article_tags.csv": "ARTICLE_SEED_DEV173",
    "title_metadata_enrichment.csv": "DEV173_TRACE", "text_cleanup_changes.csv": "DEV173_TRACE",
    "cover_placeholder_adjustments.csv": "DEV173_TRACE",
}
CLEANUP_FIELDS = ("entity", "identifier", "field", "before", "after")


def read_rows(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        return list(reader.fieldnames or []), list(reader)


def line_terminator(path: Path) -> str:
    """Terminateur de ligne du fichier source (catalogue : `\n`, manifestes : `\r\n`)."""
    return "\r\n" if b"\r\n" in path.read_bytes()[:4096] else "\n"


def write_rows(
    path: Path, fields: list[str], rows: list[dict[str, str]], *, lineterminator: str = "\n",
) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, lineterminator=lineterminator)
        writer.writeheader()
        writer.writerows(rows)


def apply_cleanup(
    rows: list[dict[str, str]], *, entity: str, id_column: str, field: str,
) -> list[dict[str, str]]:
    """Nettoie `field` en place ; retourne les lignes de trace (avant/après)."""
    changes = []
    for row in rows:
        after = clean_text(row[field])
        if after != row[field]:
            changes.append({
                "entity": entity, "identifier": row[id_column], "field": field,
                "before": row[field], "after": after,
            })
            row[field] = after
    return changes


def apply_enrichment(title_rows: list[dict[str, str]], decisions: list[dict[str, str]]) -> dict[str, int]:
    """Écrit publisher / publication_year uniquement si la décision est ENRICHED et la cellule vide."""
    by_isbn = {row["isbn"]: row for row in title_rows}
    applied = {"publisher": 0, "publication_year": 0}
    for decision in decisions:
        if decision["decision"] != ENRICHED:
            continue
        row = by_isbn.get(decision["isbn"])
        column = decision["field"]
        if row is None or row[column].strip():
            continue
        row[column] = decision["value"]
        applied[column] += 1
    return applied


def _rows_count(path: Path) -> int:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return max(sum(1 for _ in csv.reader(handle)) - 1, 0)


def build_provenance(
    candidate_dir: Path, output_dir: Path, *, report: dict,
) -> dict:
    prior = json.loads((candidate_dir / PROVENANCE).read_text(encoding="utf-8"))
    prov = deepcopy(prior)
    old_files = prior["files"]
    files: dict[str, dict] = {}
    for path in sorted(output_dir.glob("*.csv")):
        entry = dict(old_files.get(path.name, {}))
        digest = sha256_file(path)
        if path.name in old_files and old_files[path.name].get("sha256") != digest:
            entry["derived_from_sha256"] = old_files[path.name]["sha256"]
            entry.pop("byte_identical_to_source", None)
        entry["rows"] = _rows_count(path)
        entry["sha256"] = digest
        if path.name in NEW_FILES:
            entry["origin"] = NEW_FILES[path.name]
        elif path.name in REGENERATED:
            entry["origin"] = "SCENARIO_REGENERATED" if path.name != "users.csv" else entry.get("origin")
        files[path.name] = entry
    prov["files"] = files

    prov["reference_datetime"] = report["reference_datetime"]
    prov["scenario_generation"] = {
        "script": "data-seeding/scripts/dev34_build_consolidated_scenarios.py",
        "reference_datetime": report["reference_datetime"],
        "reference_datetime_source": "CLI --reference-datetime (obligatoire, ISO-8601 avec fuseau)",
        "counts": report["scenarios_counts"],
        "settings": report["settings"],
        "produced_rows": report["scenarios"],
        "member_states": report["member_states"],
        "inputs": report["inputs"],
    }
    prov["title_metadata_enrichment"] = {
        "script": "data-seeding/scripts/dev173_enrich_title_metadata.py",
        "trace_file": "title_metadata_enrichment.csv",
        "source": "Open Library — enregistrements d'édition locaux (data-seeding/data), ISBN exact",
        "network_used": False,
        "matched_isbn": report["matched_isbn"],
        "applied": report["enrichment_applied"],
        "not_enriched": ["page_count", "subtitle", "summary"],
    }
    prov["text_cleanup"] = {
        "rule": "\\' -> ' ; \\\" -> \" ; espaces répétés -> une espace ; trim",
        "module": "data-seeding/src/primatis_data_seeding/generation/text_cleanup.py",
        "trace_file": "text_cleanup_changes.csv",
        "titles": report["cleanup"]["titles"], "authors": report["cleanup"]["authors"],
        "author_merges": 0, "source_keys_preserved": True,
    }
    prov["cover_placeholders"] = {
        "script": "data-seeding/scripts/dev173_cover_placeholders.py",
        "operation": placeholders.OPERATION, "source_asset": placeholders.SOURCE_ASSET,
        "isbn": list(placeholders.BLACK_COVER_ISBNS),
        "final_sha256": report["placeholder_sha256"],
        "trace_file": "cover_placeholder_adjustments.csv",
    }
    covers = dict(prov.get("covers", {}))
    covers["upscaled"] = _rows_count(output_dir / "cover_quality_adjustments.csv")
    covers["upscaled_replaced_by_placeholder"] = report["upscale_rows_dropped"]
    prov["covers"] = covers
    prov["articles"] = report["articles"]
    prov["dev17_3"] = {
        "candidate_provenance_sha256": sha256_file(candidate_dir / PROVENANCE),
        "script": "data-seeding/scripts/dev173_build_targeted_bundle.py",
    }
    users = files["users.csv"]
    users["member_states"] = report["member_states"]
    users["password_hashes_regenerated"] = users.get("password_hashes_regenerated", True)
    return prov


def verify_provenance(output_dir: Path) -> list[str]:
    """Retourne la liste des incohérences (vide = cohérent)."""
    problems: list[str] = []
    prov = json.loads((output_dir / PROVENANCE).read_text(encoding="utf-8"))
    listed = prov["files"]
    actual = {p.name for p in output_dir.glob("*.csv")}
    if set(listed) != actual:
        problems.append(f"files mismatch: only_listed={sorted(set(listed) - actual)} only_actual={sorted(actual - set(listed))}")
    for name, entry in listed.items():
        path = output_dir / name
        if path.is_file():
            if entry["sha256"] != sha256_file(path):
                problems.append(f"sha mismatch: {name}")
            if entry["rows"] != _rows_count(path):
                problems.append(f"rows mismatch: {name}")
    return problems


def build_bundle(
    candidate_dir: Path, output_dir: Path, data_dir: Path, repo_root: Path, reference_datetime,
) -> dict:
    if candidate_dir.resolve() == output_dir.resolve():
        raise ValueError("The DEV-16 candidate bundle is immutable: use a different --output-dir.")
    output_dir.mkdir(parents=True, exist_ok=True)
    stale = [p for p in output_dir.iterdir() if p.is_file()]
    for path in stale:  # reconstruction idempotente : sortie recréée à chaque exécution
        path.unlink()

    for path in sorted(candidate_dir.glob("*.csv")):
        if path.name not in REGENERATED:
            shutil.copyfile(path, output_dir / path.name)

    # 2. micro-nettoyage --------------------------------------------------------
    t_eol, a_eol = line_terminator(output_dir / "titles.csv"), line_terminator(output_dir / "authors.csv")
    t_fields, titles = read_rows(output_dir / "titles.csv")
    a_fields, authors = read_rows(output_dir / "authors.csv")
    title_changes = apply_cleanup(titles, entity="title", id_column="isbn", field="title")
    author_changes = apply_cleanup(authors, entity="author", id_column="source_key", field="full_name")

    # 3. enrichissement ciblé ---------------------------------------------------
    catalogue = {row["isbn"] for row in titles if row["isbn"]}
    records = enrichment.collect_records(data_dir, catalogue)
    decisions = enrichment.build_rows(records, reference_datetime.year)
    enrichment.write_rows(output_dir / "title_metadata_enrichment.csv", decisions)
    applied = apply_enrichment(titles, decisions)

    write_rows(output_dir / "titles.csv", t_fields, titles, lineterminator=t_eol)
    write_rows(output_dir / "authors.csv", a_fields, authors, lineterminator=a_eol)
    write_rows(output_dir / "text_cleanup_changes.csv", list(CLEANUP_FIELDS), title_changes + author_changes)

    # 4. scénarios + états de comptes -------------------------------------------
    scenario_report = scenarios_script.build(candidate_dir, output_dir, reference_datetime)

    # 5. Articles / Tags --------------------------------------------------------
    _, users = read_rows(output_dir / "users.csv")
    librarians = tuple(sorted(u["source_key"] for u in users if u["role_code"] == "ROLE_LIBRARIAN"))
    admins = sorted(u["source_key"] for u in users if u["role_code"] == "ROLE_ADMIN")
    article_seed = build_article_seed(
        reference_datetime, librarian_source_keys=librarians, admin_source_key=admins[0],
    )
    export_articles_csv(article_seed, output_dir)

    # 6. couvertures noires -----------------------------------------------------
    placeholder_rows = placeholders.apply_placeholders(repo_root)
    placeholders.write_manifest(output_dir / "cover_placeholder_adjustments.csv", placeholder_rows)
    q_path = output_dir / "cover_quality_adjustments.csv"
    q_eol = line_terminator(q_path)
    q_fields, quality = read_rows(q_path)
    kept = [r for r in quality if r["isbn"] not in placeholders.BLACK_COVER_ISBNS]
    write_rows(q_path, q_fields, kept, lineterminator=q_eol)
    placeholder_sha = {
        isbn: sha256_file(repo_root / placeholders.COVER_DIR / f"{isbn}.jpg")
        for isbn in placeholders.BLACK_COVER_ISBNS
    }

    report = {
        "reference_datetime": reference_datetime.isoformat(),
        "scenarios": scenario_report["scenarios"],
        "scenarios_counts": scenario_report["counts"],
        "settings": scenario_report["settings"],
        "member_states": scenario_report["member_states"],
        "inputs": scenario_report["inputs"],
        "matched_isbn": len(records),
        "enrichment_applied": applied,
        "cleanup": {"titles": len(title_changes), "authors": len(author_changes)},
        "placeholder_sha256": placeholder_sha,
        "upscale_rows_dropped": len(quality) - len(kept),
        "articles": {
            "articles": len(article_seed.articles), "tags": len(article_seed.tags),
            "statuses": {s: sum(1 for a in article_seed.articles if a.article_status == s)
                         for s in ("PUBLISHED", "DRAFT", "ARCHIVED")},
            "files": ["articles.csv", "tags.csv", "article_tags.csv"],
            "notifications_created": 0,
        },
    }

    # 7. provenance -------------------------------------------------------------
    prov = build_provenance(candidate_dir, output_dir, report=report)
    (output_dir / PROVENANCE).write_text(
        json.dumps(prov, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8",
    )
    problems = verify_provenance(output_dir)
    if problems:
        raise AssertionError("Provenance inconsistent: " + "; ".join(problems))
    (output_dir / "dev173_build_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8",
    )
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--candidate", type=Path, required=True, help="Bundle DEV-16 (immuable).")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--data-dir", type=Path, required=True, help="data-seeding/data (sources locales).")
    parser.add_argument("--repo-root", type=Path, required=True, help="Racine du dépôt PRIMATIS.")
    parser.add_argument("--reference-datetime", required=True)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    reference = parse_reference_datetime(args.reference_datetime)
    report = build_bundle(args.candidate, args.output_dir, args.data_dir, args.repo_root, reference)
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
