"""Lightweight French-vs-other language detection for free text.

DEV-16.2 §4.2 / DEV-16.3 Étape C. Purpose-built for exactly one decision
(`Author.biography`): "is this text confidently French?" — never a
general-purpose language identifier, never used to guess the language of
a `Title` (that field is already a validated enum from the source).

Two lightweight approaches were benchmarked on the 17 real biographies
present in `data/bundles/medium/authors.csv` (only one, Stendhal/
OL2786936A, is genuinely French) plus a handful of synthetic sentences
in the other 6 supported languages (see DEV-16.3 report §E for the full
comparison table):

  Approach A (rejected) — French-vs-English stopword-ratio-with-margin,
  built on a broad French function-word list ("de", "la", "un", "en",
  "que", ...). 1/1 true positive, 0/16 false positives on the real
  corpus, but a **false positive on synthetic Spanish text** (the Don
  Quixote opening line): Spanish shares enough short Latin-derived
  function words with French ("de", "la", "en", "un", "que") that an
  EN-only competing hypothesis is not a sufficient guard for a library
  meant to support ES/IT/LA as well.

  Approach B (retained) — a **French-distinctive marker set**, built
  only from words/forms that are NOT common function words in
  Spanish/Italian/Latin ("était", "avec", "dans", "français",
  "écrivain", accented forms, ...), scored as a plain hit-count + ratio
  (no competing-language comparison needed, since the marker set is
  chosen to be distinctive by construction).

Approach B is implemented below. It is intentionally conservative:
too few tokens, too few distinctive hits, or a too-low ratio all resolve
to `False` — never a guess (DEV-16.2 §4.2 rule 3: indeterminate must
become `biography = NULL`, never a translation, never a best-effort
keep).

DEV-16.5.1 §10/§11/§13 — a real-Full-corpus audit (11518 Authors,
14 non-NULL biographies) found THREE distinct real problems with the
DEV-16.3 version of this module, all fixed below:

1. "sa" (French possessive) collided with "BY-SA" (Creative Commons
   license abbreviation): a pure-English photo-attribution caption
   (Author "Roddy Doyle", id=36244) matched twice purely from that
   collision -> removed from `_FR_DISTINCTIVE`.
2. "roman"/"romans" (French "novel(s)") collided with the English
   adjective "Roman" (Roman Empire/Republic/general): a purely English
   biography of "Gaius Julius Caesar" (id=7178325) matched three times
   -> removed from `_FR_DISTINCTIVE`.
3. The DEV-16.2 §4.2 contract ("biographie française fiable et
   propre") requires rejecting a biography that is a real bilingual
   Wikipedia-style concatenation (a full French paragraph immediately
   followed by a full English or German translation of the SAME text —
   observed on Authors "Marcel Proust", "Charles Perrault", "Joseph
   Gabet", "Joseph Delaney", among others) — never a "biographie
   française" in the sense meant, even though the French portion alone
   clears the French bar. A single GLOBAL hit-ratio over the whole text
   under-detects this: when the two languages' paragraphs have very
   different lengths, the shorter (or just less lexically dense)
   language's markers get diluted below the global ratio threshold
   (e.g. Perrault: an 11-marker French block spread across the WHOLE
   235-token text still clears 0.04, while its 4-marker English block
   only reaches ratio=0.017 globally, even though that English
   paragraph is clearly, entirely English on its own). Fixed by
   evaluating each SENTENCE independently (see `_has_substantial_block`)
   rather than only the whole-text ratio: a translated block is made of
   several full sentences, each of which trips the EN/DE marker set on
   its own; a short embedded citation/title is not itself a sentence
   with 2+ distinctive foreign markers.
"""

from __future__ import annotations

import re
import unicodedata

# French-distinctive words/forms: chosen to have low overlap with the
# other 6 PRIMATIS-supported languages' common function/marker words
# (EN, NL, DE, ES, IT, LA) — deliberately excludes short, widely-shared
# Romance function words ("de", "la", "un", "une", "en", "que", "qui")
# that a Spanish or Italian sentence is equally likely to contain, AND
# (DEV-16.5.1) any word later confirmed to collide with a common
# English usage on the real corpus (see module docstring points 1-2).
_FR_DISTINCTIVE: frozenset[str] = frozenset({
    "etait", "etaient", "etre", "avec", "dans", "depuis", "apres",
    "avant", "meme", "memes", "tres", "francais", "francaise",
    "ecrivain", "ecrivaine", "auteur", "auteure",
    "oeuvre", "oeuvres", "ainsi", "alors", "donc", "car", "comme",
    "mais", "considere", "consideree", "reconnu", "reconnue", "siecle",
    "plus", "fut", "furent", "leurs", "leur", "ses", "son",
    "cette", "cet", "ces", "aucune", "aucun", "celui", "celle", "ceux",
    "celles", "pseudonyme",
})

# DEV-16.5.1 §11 — biography contract (DEV-16.2 §4.2) interpreted
# strictly: a biography kept as French must not ALSO carry a
# substantial block in another language. Deliberately excludes bare
# short function words ("the", "of", "a", "und", "der", "was" alone
# would be borderline too, kept only because it is unambiguous and
# never a French word) that a 3-5 word citation/title could plausibly
# contain by chance, AND excludes "editions" (collides with French
# "éditions", a common bibliographic term, once accents are folded).
_EN_DISTINCTIVE: frozenset[str] = frozenset({
    "was", "were", "have", "has", "been", "which", "their", "english",
    "novelist", "novelists", "writer", "writers", "known", "century",
    "considered", "published", "translated", "born", "died", "although",
    "however", "between", "because", "influential", "critics", "essayist",
    "biography", "literature", "literary", "including", "career",
    "school", "university", "college", "children", "family", "written",
    "engineer", "instructor", "professor", "series", "released",
})

_DE_DISTINCTIVE: frozenset[str] = frozenset({
    "und", "wurde", "wurden", "auch", "seine", "seiner", "seinem",
    "einen", "einem", "eines", "deutscher", "deutsche", "deutschen",
    "dichter", "schriftsteller", "geboren", "gestorben", "zwischen",
    "worden", "teilnahm", "wahrend", "spater", "veroffentlicht",
    "jahrhundert", "werk", "werke", "der", "die", "das", "ist", "sind",
    "eine", "einer", "nicht", "sich", "sein", "waren",
})

_TOKEN_RE = re.compile(r"[a-zA-Z]+")

# Naive sentence splitter — a lexical heuristic (not a real tokenizer):
# splits on '.'/'!'/'?' followed by whitespace. Good enough to isolate
# the distinct sentences of a translated block from the surrounding
# French text without needing a real NLP dependency (data-seeding.md
# "Dependency policy").
_SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?])\s+")

# Thresholds validated against the 17-biography medium corpus + the
# 14-biography real Full corpus + synthetic ES/IT/LA/DE/NL/AR/RU
# sentences (DEV-16.3 §E/§C, DEV-16.5.1 §10): the one genuine French
# text scored hits=19, ratio=0.171; every non-French text (including
# the Romance-adjacent synthetic ones) scored hits=0-2, ratio<=0.002.
_MIN_HITS = 2
_MIN_RATIO = 0.04
_MIN_TOKENS = 4

# DEV-16.5.1 §11 — a single sentence carrying this many distinctive
# markers of ANOTHER language is treated as a genuine block in that
# language, not a short citation/proper noun (which, by construction of
# the marker sets above, essentially never accumulates 2 hits within
# one sentence). Below this token count a sentence is too short/noisy
# to judge on its own and is ignored (its tokens still count towards
# the global French ratio above).
_SENTENCE_MIN_HITS = 2
_SENTENCE_MIN_TOKENS = 6


def _fold(text: str) -> str:
    normalized = unicodedata.normalize("NFKD", text)
    return "".join(ch for ch in normalized if not unicodedata.combining(ch))


def _tokenize(text: str) -> list[str]:
    return _TOKEN_RE.findall(_fold(text).lower())


def _is_confident(tokens: list[str], markers: frozenset[str]) -> bool:
    if len(tokens) < _MIN_TOKENS:
        return False
    hits = sum(1 for token in tokens if token in markers)
    ratio = hits / len(tokens)
    return hits >= _MIN_HITS and ratio >= _MIN_RATIO


def _has_substantial_block(text: str, markers: frozenset[str]) -> bool:
    """True when at least one individual sentence of `text` carries
    `_SENTENCE_MIN_HITS`+ distinctive markers of another language — a
    real translated paragraph is made of full sentences that each trip
    this on their own; a short embedded citation/title never forms a
    whole sentence with that many distinctive foreign markers."""
    for sentence in _SENTENCE_SPLIT_RE.split(text):
        tokens = _tokenize(sentence)
        if len(tokens) < _SENTENCE_MIN_TOKENS:
            continue
        hits = sum(1 for token in tokens if token in markers)
        if hits >= _SENTENCE_MIN_HITS:
            return True
    return False


def is_confident_french(text: str | None) -> bool:
    """True only when `text` can be confidently identified as French AND
    does not also carry a substantial English or German block (DEV-16.2
    §4.2 / DEV-16.5.1 §11: "biographie française fiable et propre" —
    never a Wikipedia-style bilingual concatenation kept as-is).

    Conservative by construction: too few tokens, too few/too sparse
    French distinctive-marker hits, or a substantial competing-language
    sentence, all resolve to ``False`` — never a guess, never a partial
    keep.
    """
    if not text:
        return False

    tokens = _tokenize(text)
    if not _is_confident(tokens, _FR_DISTINCTIVE):
        return False

    if _has_substantial_block(text, _EN_DISTINCTIVE):
        return False
    if _has_substantial_block(text, _DE_DISTINCTIVE):
        return False

    return True
