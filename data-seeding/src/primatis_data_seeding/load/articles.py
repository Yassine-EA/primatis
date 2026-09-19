"""DEV-17.3 — chargement déterministe des Articles / Tags de démonstration.

Contrat CSV : `tags.csv`, `articles.csv`, `article_tags.csv` (voir
`export/articles_csv.py`). Le chargeur :

- ne dépend d'aucun backend actif (SQL direct, comme les autres chargeurs) ;
- résout `author_user_id` / `last_modified_by_user_id` depuis un compte staff seed
  (e-mail dérivé de la `source_key`) disposant de la permission `ARTICLE_MANAGE` ;
- respecte `published_at` et les CHECK du schéma, ainsi que l'unicité du `slug` ;
- ne crée AUCUNE notification (`ARTICLE_PUBLISHED` reste déclenchée par la
  publication réelle d'un brouillon) ;
- est idempotent : un rechargement remplace les Articles dont le `slug` figure dans
  le CSV, et refuse (échec fermé) si une notification référence l'un d'eux.
"""

from __future__ import annotations

import csv
import re
from dataclasses import dataclass
from pathlib import Path

from psycopg import Connection

from primatis_data_seeding.load.postgres import ADVISORY_LOCK_KEY

SEED_USER_EMAIL_SUFFIX = "@seed.primatis.invalid"
_SOURCE_KEY = re.compile(r"seed-member-(\d{6})")
STATUSES = ("DRAFT", "PUBLISHED", "ARCHIVED")


@dataclass(frozen=True)
class ArticleLoadSummary:
    tags: int
    articles: int
    article_tags: int
    published: int
    drafts: int
    archived: int
    applied: bool


@dataclass(frozen=True)
class ArticleExportPaths:
    articles: Path
    tags: Path
    article_tags: Path


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def _required_paths(root: Path) -> ArticleExportPaths:
    paths = ArticleExportPaths(root / "articles.csv", root / "tags.csv", root / "article_tags.csv")
    missing = [str(p) for p in (paths.articles, paths.tags, paths.article_tags) if not p.is_file()]
    if missing:
        raise ValueError(f"Missing article export file(s): {', '.join(missing)}")
    return paths


def user_email(source_key: str) -> str:
    match = _SOURCE_KEY.fullmatch(source_key)
    if match is None:
        raise ValueError(f"Unsupported seed user source_key: {source_key!r}.")
    return f"member{match.group(1)}{SEED_USER_EMAIL_SUFFIX}"


def validate_rows(
    articles: list[dict[str, str]], tags: list[dict[str, str]], links: list[dict[str, str]],
) -> None:
    """Validations structurelles indépendantes de la base (testables sans PostgreSQL)."""
    tag_codes = [t["code"] for t in tags]
    if len(tag_codes) != len(set(tag_codes)):
        raise ValueError("Duplicate Tag code.")
    keys = [a["source_key"] for a in articles]
    slugs = [a["slug"] for a in articles]
    if len(keys) != len(set(keys)) or len(slugs) != len(set(slugs)):
        raise ValueError("Duplicate Article source_key or slug.")
    for a in articles:
        status = a["article_status"]
        if status not in STATUSES:
            raise ValueError(f"Unknown article_status {status!r}.")
        if not a["title"].strip() or not a["content"].strip() or not a["slug"].strip():
            raise ValueError("Article title, content and slug are required.")
        if status == "DRAFT" and a["published_at"]:
            raise ValueError("DRAFT Article cannot have published_at.")
        if status != "DRAFT":
            if not a["published_at"] or a["published_at"] < a["created_at"]:
                raise ValueError("PUBLISHED/ARCHIVED Article requires published_at >= created_at.")
        user_email(a["author_user_source_key"])
        if a["last_modified_by_user_source_key"]:
            user_email(a["last_modified_by_user_source_key"])
    known_keys = set(keys)
    seen: set[tuple[str, str]] = set()
    for link in links:
        pair = (link["article_source_key"], link["tag_code"])
        if pair[0] not in known_keys or pair[1] not in tag_codes or pair in seen:
            raise ValueError("Invalid or duplicate article_tag link.")
        seen.add(pair)


def _resolve_user(cur, source_key: str) -> int:
    cur.execute(
        """SELECT u.id FROM app_user u
           WHERE u.email = %s
             AND EXISTS (SELECT 1 FROM user_role ur
                         JOIN role_permission rp ON rp.role_id = ur.role_id
                         JOIN permission p ON p.id = rp.permission_id
                         WHERE ur.user_id = u.id AND p.code = 'ARTICLE_MANAGE')""",
        (user_email(source_key),),
    )
    row = cur.fetchone()
    if row is None:
        raise ValueError(f"Article author {source_key!r} is missing or lacks ARTICLE_MANAGE.")
    return int(row[0])


def load_articles(*, conn: Connection, export_dir: Path, apply: bool) -> ArticleLoadSummary:
    paths = _required_paths(export_dir)
    articles = _read_csv(paths.articles)
    tags = _read_csv(paths.tags)
    links = _read_csv(paths.article_tags)
    validate_rows(articles, tags, links)

    with conn.transaction():
        with conn.cursor() as cur:
            cur.execute("SELECT pg_advisory_xact_lock(%s)", (ADVISORY_LOCK_KEY,))
            user_ids = {
                key: _resolve_user(cur, key)
                for key in {a["author_user_source_key"] for a in articles}
                | {a["last_modified_by_user_source_key"] for a in articles if a["last_modified_by_user_source_key"]}
            }
            cur.execute("SELECT slug FROM article WHERE slug = ANY(%s)", ([a["slug"] for a in articles],))
            existing = {row[0] for row in cur.fetchall()}
            if existing:
                cur.execute(
                    """SELECT COUNT(*) FROM notification n JOIN article a ON a.id = n.article_id
                       WHERE a.slug = ANY(%s)""",
                    ([a["slug"] for a in articles],),
                )
                if int(cur.fetchone()[0]):
                    raise ValueError("A seeded Article has Notifications; reload aborted.")

            if apply:
                if existing:
                    cur.execute(
                        """DELETE FROM article_tag WHERE article_id IN
                           (SELECT id FROM article WHERE slug = ANY(%s))""", (list(existing),))
                    cur.execute("DELETE FROM article WHERE slug = ANY(%s)", (list(existing),))
                for tag in tags:
                    cur.execute(
                        """INSERT INTO tag(id, code, label, description)
                           SELECT nextval('tag_seq'), %s, %s, %s
                           WHERE NOT EXISTS (SELECT 1 FROM tag WHERE code = %s)""",
                        (tag["code"], tag["label"], tag["description"] or None, tag["code"]),
                    )
                ids: dict[str, int] = {}
                for a in articles:
                    cur.execute("SELECT nextval('article_seq')")
                    ids[a["source_key"]] = int(cur.fetchone()[0])
                    cur.execute(
                        """INSERT INTO article
                           (id, author_user_id, last_modified_by_user_id, title, content, summary,
                            slug, article_status, published_at, created_at, updated_at)
                           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                        (ids[a["source_key"]], user_ids[a["author_user_source_key"]],
                         user_ids.get(a["last_modified_by_user_source_key"]) if a["last_modified_by_user_source_key"] else None,
                         a["title"], a["content"], a["summary"] or None, a["slug"],
                         a["article_status"], a["published_at"] or None, a["created_at"], a["updated_at"]),
                    )
                for link in links:
                    cur.execute(
                        """INSERT INTO article_tag(article_id, tag_id)
                           SELECT %s, t.id FROM tag t WHERE t.code = %s""",
                        (ids[link["article_source_key"]], link["tag_code"]),
                    )

    count = lambda status: sum(1 for a in articles if a["article_status"] == status)
    return ArticleLoadSummary(
        tags=len(tags), articles=len(articles), article_tags=len(links),
        published=count("PUBLISHED"), drafts=count("DRAFT"), archived=count("ARCHIVED"),
        applied=apply,
    )
