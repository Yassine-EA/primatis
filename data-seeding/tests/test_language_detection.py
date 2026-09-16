from primatis_data_seeding.normalization.language_detection import (
    is_confident_french,
)


def test_none_and_empty_are_not_french():
    assert is_confident_french(None) is False
    assert is_confident_french("") is False


def test_too_short_is_not_confident():
    # Too few tokens to trust any ratio, even if every token were a
    # French stopword.
    assert is_confident_french("le la") is False


def test_clear_french_sentence_is_confident():
    text = (
        "Stendhal était le pseudonyme de l'écrivain français du XIXe "
        "siècle Marie-Henri Beyle. Reconnu pour son analyse fine de la "
        "psychologie de ses personnages, il est considéré comme l'un des "
        "premiers et des plus importants représentants du réalisme."
    )
    assert is_confident_french(text) is True


def test_clear_english_sentence_is_rejected():
    text = (
        "François-Marie Arouet, better known by the pen name Voltaire, "
        "was a French Enlightenment writer and philosopher famous for "
        "his wit and for his advocacy of civil liberties, including "
        "freedom of religion and free trade."
    )
    assert is_confident_french(text) is False


def test_short_german_sentence_is_rejected():
    assert is_confident_french("Deutscher Komponist, Dirigent und Pianist") is False


def test_dutch_sentence_is_rejected_despite_cognate_overlap():
    text = (
        "Theodorus Johannes (Do) Thijssen was een Nederlands schrijver, "
        "onderwijzer en socialistisch politicus. Als kind van een "
        "schoenmaker kende Thijssen het Amsterdamse middenstandsmilieu "
        "rondom de eeuwwisseling uit eigen ervaring."
    )
    assert is_confident_french(text) is False


def test_spanish_sentence_is_rejected():
    text = (
        "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, "
        "no ha mucho tiempo que vivía un hidalgo de los de lanza en "
        "astillero, adarga antigua, rocín flaco y galgo corredor."
    )
    assert is_confident_french(text) is False


def test_italian_sentence_is_rejected():
    text = (
        "Nel mezzo del cammin di nostra vita mi ritrovai per una selva "
        "oscura, che la diritta via era smarrita, ed è cosa dura a dire "
        "quant'era questa selva selvaggia e aspra e forte."
    )
    assert is_confident_french(text) is False


def test_latin_sentence_is_rejected():
    text = (
        "Arma virumque cano, Troiae qui primus ab oris Italiam fato "
        "profugus Laviniaque venit litora, multum ille et terris iactatus "
        "et alto vi superum saevae memorem Iunonis ob iram."
    )
    assert is_confident_french(text) is False


def test_arabic_text_is_rejected():
    text = "منصور فهمي أحد اساتذتة الفلسفة، ولد منصور في إحدى قرى محافظة الدقهلية بمصر"
    assert is_confident_french(text) is False


def test_russian_text_is_rejected():
    text = "Владимир Владимирович Набоков русский и американский писатель поэт"
    assert is_confident_french(text) is False


# --- DEV-16.5.1 §10/§11/§12 — real Full-corpus audit ------------------

def test_creative_commons_by_sa_caption_is_not_french():
    # Real regression: Author "Roddy Doyle" (Full, id=36244) — a pure
    # English photo-attribution caption, previously a FALSE POSITIVE
    # because "sa" (a legitimate short French possessive) matched twice
    # from "BY-SA" (Creative Commons license) appearing both in the
    # visible text and in the URL slug: hits=2, ratio=0.125, cleared the
    # old threshold with zero actual French content.
    text = (
        "Photo: By Christoph Rieger [CC BY-SA 4.0 "
        "(https://creativecommons.org/licenses/by-sa/4.0)], "
        "from Wikimedia Commons"
    )
    assert is_confident_french(text) is False


def test_french_text_using_sa_ses_son_leur_still_confident():
    # Non-regression for the "sa" removal: genuine French possessives
    # elsewhere in the marker set (son/ses/leur) must still carry a
    # real French text past the bar on their own merits.
    text = (
        "Reconnue comme l'une des plus importantes autrices françaises "
        "du XXe siècle, elle a consacré son œuvre et ses romans à "
        "l'analyse fine de la condition humaine, ainsi que leur "
        "réception fut considérable en France."
    )
    assert is_confident_french(text) is True


def test_french_with_substantial_english_translation_is_rejected():
    # Real pattern (Full corpus): a French paragraph immediately
    # followed by a full English translation of the same biography
    # (Wikipedia bilingual concatenation) — DEV-16.5.1 §11: "FR+EN
    # substantiel -> NULL", even though the French half alone would
    # pass comfortably.
    text = (
        "Marcel Proust était un romancier, critique littéraire et "
        "essayiste français surtout connu pour son roman publié en sept "
        "volumes. Il est considéré comme l'un des auteurs les plus "
        "importants du siècle. ---------- Marcel Proust was a French "
        "novelist, literary critic, and essayist best known for his "
        "novel, which was published in seven volumes between 1913 and "
        "1927. He is considered by critics and writers to be one of the "
        "most influential authors of the twentieth century."
    )
    assert is_confident_french(text) is False


def test_french_with_substantial_german_translation_is_rejected():
    # Real pattern (Full corpus, Author "Joseph Gabet") — a French
    # paragraph followed by a substantial German paragraph.
    text = (
        "Joseph Gabet était un missionnaire catholique français de "
        "l'Ordre de la Congrégation de la Mission. Il a beaucoup "
        "voyagé en Chine du Nord et en Mongolie, ainsi qu'au Tibet. "
        "Joseph Gabet war ein deutscher Schriftsteller und Dichter, "
        "geboren im neunzehnten Jahrhundert, gestorben nach seiner "
        "Reise, und wurde bekannt durch sein veroffentlichtes Werk "
        "wahrend jener Zeit."
    )
    assert is_confident_french(text) is False


def test_french_majority_with_short_foreign_book_title_stays_confident():
    # DEV-16.5.1 §11: "FR majoritaire avec quelques noms propres/
    # citations courtes étrangères -> KEEP" — a short English book
    # title embedded in an otherwise purely French biography must not
    # trip the EN-distinctive bar (2 hits / 4% ratio on a long French
    # text is not reachable from 3-4 generic title words).
    text = (
        "Stendhal était le pseudonyme de l'écrivain français Marie-Henri "
        "Beyle, considéré comme l'un des plus importants représentants "
        "du réalisme. Son roman le plus connu, publié sous le titre "
        "anglais 'The Charterhouse of Parma' dans certaines éditions, "
        "reste une référence de la littérature française du XIXe siècle."
    )
    assert is_confident_french(text) is True


def test_pure_german_biography_is_rejected():
    text = (
        "Er war ein deutscher Schriftsteller und Dichter, geboren im "
        "neunzehnten Jahrhundert in einer kleinen Stadt, und wurde vor "
        "allem durch sein veroffentlichtes Werk wahrend seiner spateren "
        "Jahre bekannt, das zwischen mehreren Generationen von Lesern "
        "sehr geschaetzt worden ist."
    )
    assert is_confident_french(text) is False
