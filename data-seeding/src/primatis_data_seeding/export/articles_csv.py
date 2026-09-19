"""DEV-17.3 — export CSV du corpus Articles / Tags (contrat de chargement)."""

from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

from primatis_data_seeding.generation.articles import ArticleSeedResult

ARTICLE_FIELDS = (
    "source_key", "author_user_source_key", "last_modified_by_user_source_key", "title",
    "slug", "summary", "content", "article_status", "published_at", "created_at", "updated_at",
)
TAG_FIELDS = ("code", "label", "description")
ARTICLE_TAG_FIELDS = ("article_source_key", "tag_code")


@dataclass(frozen=True)
class ArticleExportPaths:
    articles: Path
    tags: Path
    article_tags: Path


def _write(path: Path, fieldnames: tuple[str, ...], rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def export_articles_csv(result: ArticleSeedResult, output_dir: Path) -> ArticleExportPaths:
    paths = ArticleExportPaths(
        output_dir / "articles.csv", output_dir / "tags.csv", output_dir / "article_tags.csv",
    )
    _write(paths.tags, TAG_FIELDS,
           [{"code": t.code, "label": t.label, "description": t.description} for t in result.tags])
    _write(paths.articles, ARTICLE_FIELDS, [{
        "source_key": a.source_key,
        "author_user_source_key": a.author_user_source_key,
        "last_modified_by_user_source_key": a.last_modified_by_user_source_key or "",
        "title": a.title, "slug": a.slug, "summary": a.summary, "content": a.content,
        "article_status": a.article_status,
        "published_at": a.published_at.isoformat() if a.published_at else "",
        "created_at": a.created_at.isoformat(), "updated_at": a.updated_at.isoformat(),
    } for a in result.articles])
    _write(paths.article_tags, ARTICLE_TAG_FIELDS, [
        {"article_source_key": a.source_key, "tag_code": code}
        for a in result.articles for code in a.tag_codes
    ])
    return paths
