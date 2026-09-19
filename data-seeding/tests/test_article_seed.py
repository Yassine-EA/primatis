import csv
from dataclasses import replace
from datetime import datetime, timezone
from pathlib import Path

import pytest

from primatis_data_seeding.export.articles_csv import export_articles_csv
from primatis_data_seeding.generation.articles import (
    ALLOWED_TAGS, ArticleSeedResult, build_article_seed, content_tags, slugify, validate_article_seed,
)
from primatis_data_seeding.load import articles as loader

REF = datetime(2026, 9, 19, 12, tzinfo=timezone.utc)
LIBS = tuple(f"seed-member-{i:06d}" for i in range(221, 226))
ADMIN = "seed-member-000226"


@pytest.fixture(scope="module")
def seed():
    return build_article_seed(REF, librarian_source_keys=LIBS, admin_source_key=ADMIN)


def test_volumes_and_statuses(seed) -> None:
    assert len(seed.articles) == 7 and len(seed.tags) == 10
    counts = {s: sum(1 for a in seed.articles if a.article_status == s) for s in ("PUBLISHED", "DRAFT", "ARCHIVED")}
    assert counts == {"PUBLISHED": 4, "DRAFT": 2, "ARCHIVED": 1}
    assert {t.label for t in seed.tags} == {
        "Sciences", "Astronomie", "Georges Lemaître", "Patrimoine", "Littérature", "Nouveautés",
        "Conseils de lecture", "Bibliothèque", "Histoire", "Culture",
    }


def test_published_at_and_ordering_follow_the_check_constraint(seed) -> None:
    for a in seed.articles:
        if a.article_status == "DRAFT":
            assert a.published_at is None
        else:
            assert a.published_at is not None and a.published_at >= a.created_at
        assert a.updated_at >= a.created_at and a.created_at < REF


def test_slugs_unique_and_match_backend_rule(seed) -> None:
    assert len({a.slug for a in seed.articles}) == 7
    assert slugify("Georges Lemaître et l'atome primitif") == "georges-lemaitre-et-l-atome-primitif"
    assert slugify("  Été   à l'œuvre !  ") == "ete-a-l-uvre"
    for a in seed.articles:
        assert a.slug == slugify(a.title)


def test_content_stays_inside_the_sanitizer_allowlist(seed) -> None:
    for a in seed.articles:
        tags, attrs = content_tags(a.content)
        assert tags <= ALLOWED_TAGS and not attrs
        assert "<script" not in a.content.lower() and "style" not in a.content.lower()


def test_seed_is_relative_to_reference_datetime() -> None:
    later = REF.replace(month=10)
    a = build_article_seed(REF, librarian_source_keys=LIBS, admin_source_key=ADMIN)
    b = build_article_seed(later, librarian_source_keys=LIBS, admin_source_key=ADMIN)
    assert [x.slug for x in a.articles] == [x.slug for x in b.articles]
    assert all(y.created_at > x.created_at for x, y in zip(a.articles, b.articles))


def test_naive_reference_is_rejected() -> None:
    with pytest.raises(ValueError, match="timezone"):
        build_article_seed(datetime(2026, 9, 19), librarian_source_keys=LIBS, admin_source_key=ADMIN)


def test_validation_rejects_broken_seed(seed) -> None:
    published = next(a for a in seed.articles if a.article_status == "PUBLISHED")
    draft = next(a for a in seed.articles if a.article_status == "DRAFT")
    cases = {
        "published_at": replace(draft, published_at=REF),
        "allowlist": replace(published, content="<p>x</p><script>alert(1)</script>"),
        "attribute": replace(published, content='<p onclick="x">x</p>'),
        "slug": replace(published, slug="autre"),
        "tag": replace(published, tag_codes=("INCONNU",)),
        "before_created": replace(published, published_at=published.created_at.replace(year=2020)),
    }
    for label, bad in cases.items():
        articles = [bad if a.source_key == bad.source_key else a for a in seed.articles]
        with pytest.raises(ValueError):
            validate_article_seed(ArticleSeedResult(seed.tags, articles))


def test_export_and_loader_validation_roundtrip(seed, tmp_path: Path) -> None:
    export_articles_csv(seed, tmp_path)
    read = lambda n: list(csv.DictReader((tmp_path / n).open(encoding="utf-8", newline="")))
    articles, tags, links = read("articles.csv"), read("tags.csv"), read("article_tags.csv")
    assert (len(articles), len(tags)) == (7, 10) and len(links) == sum(len(a.tag_codes) for a in seed.articles)
    loader.validate_rows(articles, tags, links)
    assert loader.user_email("seed-member-000221") == "member000221@seed.primatis.invalid"
    assert next(a for a in articles if a["article_status"] == "DRAFT")["published_at"] == ""


def test_loader_rejects_inconsistent_rows(seed, tmp_path: Path) -> None:
    export_articles_csv(seed, tmp_path)
    read = lambda n: list(csv.DictReader((tmp_path / n).open(encoding="utf-8", newline="")))
    articles, tags, links = read("articles.csv"), read("tags.csv"), read("article_tags.csv")
    bad = [dict(a) for a in articles]
    next(a for a in bad if a["article_status"] == "DRAFT")["published_at"] = "2026-09-01T09:00:00+00:00"
    with pytest.raises(ValueError, match="DRAFT"):
        loader.validate_rows(bad, tags, links)
    with pytest.raises(ValueError, match="link"):
        loader.validate_rows(articles, tags, links + [{"article_source_key": "seed-article-001", "tag_code": "NOPE"}])
    with pytest.raises(ValueError, match="source_key"):
        loader.user_email("someone-else")


def test_no_notification_is_created_by_the_loader() -> None:
    source = Path(loader.__file__).read_text(encoding="utf-8").lower()
    assert "insert into notification" not in source
