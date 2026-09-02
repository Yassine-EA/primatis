from __future__ import annotations

import re
import unicodedata

from primatis_data_seeding.mapping.models import PrimatisGenreRow


# Controlled PRIMATIS taxonomy. These are internal catalogue genres, not a copy
# of the unbounded Open Library subject vocabulary.
GENRES: tuple[PrimatisGenreRow, ...] = (
    PrimatisGenreRow("FICTION", "Fiction", "Œuvres de fiction générale."),
    PrimatisGenreRow("SCIENCE_FICTION", "Science-fiction", "Fiction scientifique et spéculative."),
    PrimatisGenreRow("FANTASY", "Fantasy", "Imaginaire, merveilleux et fantasy."),
    PrimatisGenreRow("MYSTERY", "Policier et mystère", "Romans policiers, enquêtes et mystères."),
    PrimatisGenreRow("THRILLER", "Thriller", "Suspense et thrillers."),
    PrimatisGenreRow("ROMANCE", "Romance", "Romans sentimentaux et romance."),
    PrimatisGenreRow("HISTORY", "Histoire", "Histoire et études historiques."),
    PrimatisGenreRow("BIOGRAPHY", "Biographie", "Biographies et autobiographies."),
    PrimatisGenreRow("SCIENCE", "Sciences", "Sciences naturelles et exactes."),
    PrimatisGenreRow("TECHNOLOGY", "Technologie", "Informatique, ingénierie et technologies."),
    PrimatisGenreRow("PHILOSOPHY", "Philosophie", "Philosophie et pensée."),
    PrimatisGenreRow("RELIGION", "Religion", "Religions et études religieuses."),
    PrimatisGenreRow("ART", "Arts", "Arts visuels et histoire de l'art."),
    PrimatisGenreRow("MUSIC", "Musique", "Musique et études musicales."),
    PrimatisGenreRow("POETRY", "Poésie", "Poésie et recueils poétiques."),
    PrimatisGenreRow("DRAMA", "Théâtre", "Théâtre et textes dramatiques."),
    PrimatisGenreRow("CHILDREN", "Jeunesse", "Littérature destinée aux enfants."),
    PrimatisGenreRow("YOUNG_ADULT", "Jeunes adultes", "Littérature destinée aux adolescents et jeunes adultes."),
    PrimatisGenreRow("COMICS", "BD et romans graphiques", "Bandes dessinées et romans graphiques."),
    PrimatisGenreRow("TRAVEL", "Voyage", "Voyages, guides et récits de voyage."),
    PrimatisGenreRow("COOKING", "Cuisine", "Cuisine, gastronomie et recettes."),
    PrimatisGenreRow("HEALTH", "Santé", "Santé et bien-être."),
    PrimatisGenreRow("BUSINESS", "Économie et entreprise", "Économie, gestion et entreprise."),
    PrimatisGenreRow("EDUCATION", "Éducation", "Éducation, pédagogie et apprentissage."),
    PrimatisGenreRow("SOCIAL_SCIENCES", "Sciences sociales", "Sociologie et sciences sociales."),
    PrimatisGenreRow("POLITICS", "Politique", "Politique et sciences politiques."),
)


def _fold(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    without_accents = "".join(
        char for char in normalized if not unicodedata.combining(char)
    )
    return re.sub(r"\s+", " ", without_accents.casefold()).strip()


# Exact aliases only. No substring/fuzzy classification: a source subject must
# normalize exactly to one of these aliases to receive a Genre.
_SUBJECT_ALIASES: dict[str, str] = {
    "fiction": "FICTION",
    "science fiction": "SCIENCE_FICTION",
    "science-fiction": "SCIENCE_FICTION",
    "fantasy": "FANTASY",
    "mystery": "MYSTERY",
    "detective and mystery stories": "MYSTERY",
    "thrillers": "THRILLER",
    "thriller": "THRILLER",
    "romance": "ROMANCE",
    "love stories": "ROMANCE",
    "history": "HISTORY",
    "biography": "BIOGRAPHY",
    "autobiography": "BIOGRAPHY",
    "science": "SCIENCE",
    "technology": "TECHNOLOGY",
    "computers": "TECHNOLOGY",
    "computer science": "TECHNOLOGY",
    "philosophy": "PHILOSOPHY",
    "religion": "RELIGION",
    "art": "ART",
    "music": "MUSIC",
    "poetry": "POETRY",
    "drama": "DRAMA",
    "children's literature": "CHILDREN",
    "juvenile literature": "CHILDREN",
    "young adult fiction": "YOUNG_ADULT",
    "young adult literature": "YOUNG_ADULT",
    "comic books, strips, etc.": "COMICS",
    "graphic novels": "COMICS",
    "travel": "TRAVEL",
    "cooking": "COOKING",
    "cookery": "COOKING",
    "health": "HEALTH",
    "business": "BUSINESS",
    "economics": "BUSINESS",
    "education": "EDUCATION",
    "social sciences": "SOCIAL_SCIENCES",
    "sociology": "SOCIAL_SCIENCES",
    "political science": "POLITICS",
    "politics": "POLITICS",
}

_FOLDED_SUBJECT_ALIASES = {_fold(key): value for key, value in _SUBJECT_ALIASES.items()}


def map_subjects_to_genre_codes(subjects: list[str] | tuple[str, ...]) -> tuple[str, ...]:
    codes = {
        code
        for subject in subjects
        if (code := _FOLDED_SUBJECT_ALIASES.get(_fold(subject))) is not None
    }
    return tuple(sorted(codes))
