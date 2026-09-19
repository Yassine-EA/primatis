"""DEV-17.3 — corpus de démonstration Articles / Tags (fichiers uniquement).

Corpus sobre de 7 Articles (4 PUBLISHED, 2 DRAFT, 1 ARCHIVED) et 10 Tags,
déterministe et relatif à `reference_datetime`. Le contenu n'invente aucun
événement, horaire, partenariat ni annonce institutionnelle ; les repères
historiques cités (Georges Lemaître, patrimoine scientifique belge) sont des
faits établis et généraux.

Contrat backend respecté (DEV-11.x) :

- `content` est du HTML restreint à l'allowlist du `ArticleSanitizer`
  (Jsoup `Safelist.basic()` + `h2/h3/h4/img`) — ici uniquement `h2`, `p`,
  `ul`, `li`, `strong`, `em`, `blockquote` ; sortie déjà « propre » ;
- `slug` = `ArticleSlugGenerator.normalize(title)` (minuscules, NFD sans marques,
  suites non alphanumériques → `-`), unique ;
- DRAFT ⇒ `published_at` NULL ; PUBLISHED/ARCHIVED ⇒ `published_at` ≥ `created_at`
  (`ck_article_published_at_consistency`) ;
- l'auteur est un compte staff seed (ARTICLE_MANAGE) ; aucune notification
  `ARTICLE_PUBLISHED` n'est créée par le seed (un DRAFT reste publiable en direct).
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from datetime import datetime, time, timedelta
from html.parser import HTMLParser

MAX_SLUG_LENGTH = 240
MAX_TITLE_LENGTH = 255
TAG_CODE_MAX = 50
TAG_LABEL_MAX = 100
TAG_DESCRIPTION_MAX = 255

ALLOWED_TAGS = frozenset({"h2", "h3", "h4", "p", "ul", "ol", "li", "strong", "em", "blockquote", "br"})
STATUSES = ("DRAFT", "PUBLISHED", "ARCHIVED")

_COMBINING = re.compile(r"[̀-ͯ]+")
_NON_ALNUM = re.compile(r"[^a-z0-9]+")


@dataclass(frozen=True)
class TagSeed:
    code: str
    label: str
    description: str


@dataclass(frozen=True)
class ArticleSeed:
    source_key: str
    author_user_source_key: str
    last_modified_by_user_source_key: str | None
    title: str
    slug: str
    summary: str
    content: str
    article_status: str
    published_at: datetime | None
    created_at: datetime
    updated_at: datetime
    tag_codes: tuple[str, ...]


@dataclass(frozen=True)
class ArticleSeedResult:
    tags: list[TagSeed]
    articles: list[ArticleSeed]


def slugify(title: str) -> str:
    """Réplique de `ArticleSlugGenerator.normalize` (sans suffixe d'unicité)."""
    decomposed = unicodedata.normalize("NFD", title.strip().lower())
    without_marks = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    hyphenated = _NON_ALNUM.sub("-", without_marks).strip("-")
    slug = hyphenated[:MAX_SLUG_LENGTH].strip("-")
    if not slug:
        raise ValueError(f"Empty slug for title {title!r}.")
    return slug


TAGS: tuple[TagSeed, ...] = (
    TagSeed("SCIENCES", "Sciences", "Sciences exactes et sciences de la nature."),
    TagSeed("ASTRONOMIE", "Astronomie", "Astronomie et cosmologie."),
    TagSeed("GEORGES_LEMAITRE", "Georges Lemaître", "Georges Lemaître et son héritage scientifique."),
    TagSeed("PATRIMOINE", "Patrimoine", "Patrimoine culturel et scientifique."),
    TagSeed("LITTERATURE", "Littérature", "Romans, essais et poésie."),
    TagSeed("NOUVEAUTES", "Nouveautés", "Nouveautés du catalogue et de la bibliothèque."),
    TagSeed("CONSEILS_DE_LECTURE", "Conseils de lecture", "Idées et pistes pour choisir une lecture."),
    TagSeed("BIBLIOTHEQUE", "Bibliothèque", "Fonctionnement et services de la bibliothèque."),
    TagSeed("HISTOIRE", "Histoire", "Histoire et histoire des sciences."),
    TagSeed("CULTURE", "Culture", "Culture générale et découvertes."),
)


_LEMAITRE = """<h2>Un scientifique belge et un prêtre</h2>
<p>Georges Lemaître (1894-1966) était un prêtre catholique belge, physicien et astronome, professeur à l'Université de Louvain. Il est l'un des fondateurs de la cosmologie moderne.</p>
<p>En 1927, il propose une solution des équations de la relativité générale décrivant un univers en expansion, et en tire une relation entre la distance des galaxies et leur vitesse d'éloignement. En 1931, il avance l'idée d'un état initial extrêmement dense, qu'il nomme « atome primitif » : l'une des origines de ce que l'on appelle aujourd'hui le modèle du Big Bang.</p>
<blockquote><p>L'hypothèse de l'atome primitif est restée un jalon de l'histoire de la cosmologie.</p></blockquote>
<h2>Pour aller plus loin</h2>
<p>Le catalogue de la bibliothèque rassemble des ouvrages de vulgarisation en astronomie, en physique et en histoire des sciences. Cherchez par sujet ou par auteur pour prolonger la lecture.</p>"""

_SCIENCES = """<h2>Lire pour comprendre le monde</h2>
<p>La lecture est une porte d'entrée accessible vers les sciences : biographies de chercheurs, ouvrages de vulgarisation, récits d'expériences et beaux livres illustrés.</p>
<ul>
<li><strong>Commencer par une question</strong> : pourquoi le ciel est-il bleu, comment fonctionne le vivant, d'où vient la matière ?</li>
<li><strong>Varier les formats</strong> : un essai, un documentaire illustré et une bande dessinée scientifique n'apportent pas la même chose.</li>
<li><strong>Croiser les points de vue</strong> : histoire, philosophie et pratique de la science se complètent.</li>
</ul>
<p>Le catalogue permet de filtrer par genre (par exemple « Science ») afin de repérer rapidement des lectures adaptées à chaque niveau.</p>"""

_ROMANS = """<h2>Des œuvres qui traversent les époques</h2>
<p>Les romans dits classiques restent des lectures de référence : ils éclairent une époque, une langue et une manière de raconter.</p>
<ul>
<li><em>Les Misérables</em>, de Victor Hugo, grande fresque sociale du XIXe siècle ;</li>
<li><em>Madame Bovary</em>, de Gustave Flaubert, portrait d'une ambition déçue ;</li>
<li><em>Frankenstein</em>, de Mary Shelley, récit fondateur de la science-fiction ;</li>
<li><em>Orgueil et préjugés</em>, de Jane Austen, comédie de mœurs.</li>
</ul>
<p>La disponibilité de chaque titre et de chaque édition est à vérifier dans le catalogue ; une réservation est possible lorsque tous les exemplaires sont empruntés.</p>"""

_SERVICES = """<h2>Ce que PRIMATIS vous permet de faire</h2>
<p>PRIMATIS centralise la gestion de la bibliothèque : catalogue, prêts, réservations et suivi des amendes.</p>
<ul>
<li><strong>Consulter le catalogue</strong> sans compte : recherche, filtres par langue et par genre, fiche détaillée de chaque titre.</li>
<li><strong>Suivre vos prêts</strong> depuis votre espace membre, avec les dates d'échéance.</li>
<li><strong>Réserver un titre</strong> lorsque aucun exemplaire n'est immédiatement disponible ; vous êtes notifié lorsqu'un exemplaire vous est réservé.</li>
<li><strong>Consulter vos amendes</strong> et vos notifications.</li>
</ul>
<p>Les règles exactes de prêt (durée, nombre de réservations) sont paramétrées par la bibliothèque et peuvent évoluer.</p>"""

_NOUVEAUTES = """<h2>Le catalogue s'enrichit</h2>
<p>Cet article présentera les dernières entrées du catalogue, classées par genre et par langue.</p>
<ul>
<li>nouvelles acquisitions en sciences et en histoire ;</li>
<li>romans et bandes dessinées récemment ajoutés ;</li>
<li>ouvrages de référence et guides pratiques.</li>
</ul>
<p>Brouillon en cours de rédaction.</p>"""

_CONSEILS = """<h2>Comment choisir sa prochaine lecture</h2>
<p>Quelques repères pour ne pas rester devant les rayons sans savoir par où commencer.</p>
<ul>
<li>Partir de la dernière lecture appréciée et chercher le même auteur ou le même genre.</li>
<li>Lire la quatrième de couverture et les premières pages avant d'emprunter.</li>
<li>Oser un genre inhabituel : poésie, essai, bande dessinée.</li>
</ul>
<p>Brouillon en cours de relecture.</p>"""

_PATRIMOINE = """<h2>Quelques figures de l'histoire des sciences en Belgique</h2>
<p>Le territoire belge a compté plusieurs savants dont les travaux ont marqué leur discipline.</p>
<ul>
<li><strong>Andreas Vesalius</strong> (Bruxelles, 1514-1564), anatomiste, auteur du <em>De humani corporis fabrica</em> (1543) ;</li>
<li><strong>Gerardus Mercator</strong> (1512-1594), cartographe, dont la projection porte le nom ;</li>
<li><strong>Simon Stevin</strong> (Bruges, 1548-1620), mathématicien et ingénieur ;</li>
<li><strong>Adolphe Quetelet</strong> (1796-1874), astronome et statisticien ;</li>
<li><strong>Georges Lemaître</strong> (1894-1966), physicien et cosmologiste.</li>
</ul>
<p>Article archivé : conservé comme historique de publication.</p>"""


def _at(reference: datetime, days_back: int, hour: int = 9) -> datetime:
    day = (reference - timedelta(days=days_back)).date()
    return datetime.combine(day, time(hour=hour), tzinfo=reference.tzinfo)


def build_article_seed(
    reference_datetime: datetime,
    *,
    librarian_source_keys: tuple[str, ...],
    admin_source_key: str,
) -> ArticleSeedResult:
    """Construit le corpus ; les dates sont relatives à `reference_datetime`."""
    if reference_datetime.tzinfo is None:
        raise ValueError("reference_datetime must be timezone-aware.")
    if len(librarian_source_keys) < 2:
        raise ValueError("At least two staff authors are required.")
    lib = librarian_source_keys

    def published(index, title, summary, content, tags, days_back, author):
        published_at = _at(reference_datetime, days_back)
        return ArticleSeed(
            f"seed-article-{index:03d}", author, author, title, slugify(title), summary,
            content, "PUBLISHED", published_at, published_at - timedelta(days=2),
            published_at, tags,
        )

    def draft(index, title, summary, content, tags, created_back, updated_back, author):
        return ArticleSeed(
            f"seed-article-{index:03d}", author, None, title, slugify(title), summary,
            content, "DRAFT", None, _at(reference_datetime, created_back),
            _at(reference_datetime, updated_back, hour=15), tags,
        )

    published_archived_at = _at(reference_datetime, 120)
    articles = [
        published(1, "Georges Lemaître et l'atome primitif",
                  "Portrait d'un prêtre et scientifique belge, père de l'hypothèse de l'atome primitif.",
                  _LEMAITRE, ("SCIENCES", "ASTRONOMIE", "GEORGES_LEMAITRE", "PATRIMOINE"), 30, lib[0]),
        published(2, "Découvrir les sciences par la lecture",
                  "Comment aborder les sciences avec des livres : pistes et conseils.",
                  _SCIENCES, ("SCIENCES", "CONSEILS_DE_LECTURE"), 21, lib[1]),
        published(3, "Sélection de romans classiques",
                  "Quelques romans classiques à (re)découvrir dans le catalogue.",
                  _ROMANS, ("LITTERATURE", "CONSEILS_DE_LECTURE"), 14, lib[2 % len(lib)]),
        published(4, "Présentation des services de la bibliothèque",
                  "Catalogue, prêts, réservations et notifications : ce que PRIMATIS permet de faire.",
                  _SERVICES, ("BIBLIOTHEQUE",), 45, admin_source_key),
        draft(5, "Nouveautés du catalogue",
              "Les dernières entrées du catalogue (brouillon).",
              _NOUVEAUTES, ("NOUVEAUTES", "BIBLIOTHEQUE"), 3, 2, lib[0]),
        draft(6, "Conseils pour choisir sa prochaine lecture",
              "Repères pour choisir un livre (brouillon).",
              _CONSEILS, ("CONSEILS_DE_LECTURE", "CULTURE"), 6, 5, lib[1]),
        ArticleSeed(
            "seed-article-007", lib[0], admin_source_key, "Patrimoine scientifique belge",
            slugify("Patrimoine scientifique belge"),
            "Quelques figures de l'histoire des sciences en Belgique (article archivé).",
            _PATRIMOINE, "ARCHIVED", published_archived_at,
            published_archived_at - timedelta(days=2), _at(reference_datetime, 40, hour=11),
            ("PATRIMOINE", "HISTOIRE", "SCIENCES"),
        ),
    ]
    result = ArticleSeedResult(list(TAGS), articles)
    validate_article_seed(result)
    return result


class _TagCollector(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.tags: set[str] = set()
        self.attrs: list[tuple[str, str]] = []

    def handle_starttag(self, tag, attrs):
        self.tags.add(tag)
        self.attrs.extend(attrs)

    def handle_endtag(self, tag):
        self.tags.add(tag)


def content_tags(content: str) -> tuple[set[str], list]:
    parser = _TagCollector()
    parser.feed(content)
    parser.close()
    return parser.tags, parser.attrs


def validate_article_seed(result: ArticleSeedResult) -> None:
    tag_codes = [t.code for t in result.tags]
    if len(tag_codes) != len(set(tag_codes)):
        raise ValueError("Duplicate Tag code.")
    for tag in result.tags:
        if not (0 < len(tag.code) <= TAG_CODE_MAX and re.fullmatch(r"[A-Z0-9_]+", tag.code)):
            raise ValueError(f"Invalid Tag code {tag.code!r}.")
        if not (0 < len(tag.label) <= TAG_LABEL_MAX and len(tag.description) <= TAG_DESCRIPTION_MAX):
            raise ValueError(f"Invalid Tag {tag.code!r}.")
    slugs = [a.slug for a in result.articles]
    if len(slugs) != len(set(slugs)):
        raise ValueError("Duplicate Article slug.")
    used_tags: set[str] = set()
    for article in result.articles:
        if article.article_status not in STATUSES:
            raise ValueError("Unknown article_status.")
        if not article.title.strip() or len(article.title) > MAX_TITLE_LENGTH:
            raise ValueError("Invalid Article title.")
        if article.slug != slugify(article.title):
            raise ValueError("Article slug does not match the title.")
        if article.created_at.tzinfo is None or article.updated_at.tzinfo is None:
            raise ValueError("Article timestamps must be timezone-aware.")
        if article.article_status == "DRAFT":
            if article.published_at is not None:
                raise ValueError("DRAFT Article cannot have published_at.")
        else:
            if article.published_at is None or article.published_at < article.created_at:
                raise ValueError("PUBLISHED/ARCHIVED Article requires published_at >= created_at.")
        if article.updated_at < article.created_at:
            raise ValueError("Article updated_at precedes created_at.")
        tags, attrs = content_tags(article.content)
        if not tags <= ALLOWED_TAGS:
            raise ValueError(f"Article content uses tags outside the sanitizer allowlist: {tags - ALLOWED_TAGS}")
        if attrs:
            raise ValueError("Article content must not carry HTML attributes.")
        if "script" in article.content.lower() or "style" in article.content.lower():
            raise ValueError("Article content must not contain script/style.")
        if not re.sub(r"<[^>]+>", "", article.content).strip():
            raise ValueError("Article content must be functionally non-empty.")
        if not article.tag_codes or not set(article.tag_codes) <= set(tag_codes):
            raise ValueError("Article references an unknown Tag.")
        used_tags.update(article.tag_codes)
    if used_tags != set(tag_codes):
        raise ValueError("Every seeded Tag must be used by at least one Article.")
