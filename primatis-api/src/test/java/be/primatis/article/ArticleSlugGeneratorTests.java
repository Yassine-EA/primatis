package be.primatis.article;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie {@link ArticleSlugGenerator#normalize(String)} et {@link
 * ArticleSlugGenerator#generateUniqueSlug(String, Predicate)} (DEV-11.4).
 * Test unitaire pur — aucun Spring, aucun PostgreSQL, aucun {@code
 * ArticleRepository} réel (le test d'existence est simulé par un {@link
 * Predicate} en mémoire, mission DEV-11.4 §17 : « ne pas tester une
 * Repository réelle si le helper reste purement algorithmique »).
 */
class ArticleSlugGeneratorTests {

    private final ArticleSlugGenerator generator = new ArticleSlugGenerator();

    // ---------------------------------------------------------------
    // normalize — cas nominaux (exemples de la mission DEV-11.4 §10)
    // ---------------------------------------------------------------

    @Test
    void normalizeConvertsASimpleTitle() {
        assertThat(generator.normalize("Le Petit Prince")).isEqualTo("le-petit-prince");
    }

    @Test
    void normalizeTrimsAndCollapsesMultipleSeparators() {
        assertThat(generator.normalize("  Java & Spring Boot  ")).isEqualTo("java-spring-boot");
    }

    @Test
    void normalizeStripsAccentsToAsciiForm() {
        assertThat(generator.normalize("Été à Bruxelles")).isEqualTo("ete-a-bruxelles");
    }

    @Test
    void normalizeCollapsesMultipleConsecutiveHyphensToOne() {
        assertThat(generator.normalize("foo---bar")).isEqualTo("foo-bar");
    }

    @Test
    void normalizeLowercasesUppercaseTitles() {
        assertThat(generator.normalize("TITRE EN MAJUSCULES")).isEqualTo("titre-en-majuscules");
    }

    @Test
    void normalizeHandlesApostrophesAsSeparators() {
        assertThat(generator.normalize("L'Été de Jean-Paul")).isEqualTo("l-ete-de-jean-paul");
    }

    @Test
    void normalizeRemovesPunctuationKeepingWords() {
        assertThat(generator.normalize("Nouveaux horaires : ouverture, fermeture !"))
                .isEqualTo("nouveaux-horaires-ouverture-fermeture");
    }

    @Test
    void normalizeIsStableForTheSameInput() {
        String first = generator.normalize("Réouverture de la bibliothèque");
        String second = generator.normalize("Réouverture de la bibliothèque");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void normalizeHandlesReasonablyLongTitleWithoutError() {
        String longTitle = "Bibliothèque ".repeat(30).trim();

        String result = generator.normalize(longTitle);

        assertThat(result).isNotEmpty().matches("[a-z0-9-]+");
    }

    // ---------------------------------------------------------------
    // normalize — robustesse / cas limites
    // ---------------------------------------------------------------

    @Test
    void normalizeThrowsOnNullTitle() {
        assertThatThrownBy(() -> generator.normalize(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void normalizeThrowsWhenResultWouldBeEmptyForPunctuationOnlyTitle() {
        assertThatThrownBy(() -> generator.normalize("!!!")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeThrowsWhenResultWouldBeEmptyForHyphensOnlyTitle() {
        assertThatThrownBy(() -> generator.normalize("---")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeThrowsWhenResultWouldBeEmptyForBlankTitle() {
        assertThatThrownBy(() -> generator.normalize("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeThrowsWhenUnicodeContentIsEntirelyStrippedByNormalization() {
        // "🎉🎊" ne contient aucun caractère alphanumérique ASCII après
        // normalisation NFD/suppression des marques combinantes.
        assertThatThrownBy(() -> generator.normalize("🎉🎊")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeTruncatesSlugLongerThanTheDatabaseColumnMargin() {
        String veryLongTitle = "mot ".repeat(100).trim();

        String result = generator.normalize(veryLongTitle);

        assertThat(result.length()).isLessThanOrEqualTo(ArticleSlugGenerator.MAX_BASE_SLUG_LENGTH);
        assertThat(result).doesNotStartWith("-").doesNotEndWith("-");
    }

    // ---------------------------------------------------------------
    // generateUniqueSlug — collision / déterminisme
    // ---------------------------------------------------------------

    @Test
    void generateUniqueSlugReturnsBaseSlugWhenAvailable() {
        String slug = generator.generateUniqueSlug("Mon article", candidate -> false);

        assertThat(slug).isEqualTo("mon-article");
    }

    @Test
    void generateUniqueSlugAppendsDeterministicSuffixWhenBaseIsTaken() {
        Predicate<String> onlyBaseTaken = "mon-article"::equals;

        String slug = generator.generateUniqueSlug("Mon article", onlyBaseTaken);

        assertThat(slug).isEqualTo("mon-article-2");
    }

    @Test
    void generateUniqueSlugAppendsNextSuffixWhenBaseAndFirstSuffixAreTaken() {
        Set<String> taken = Set.of("mon-article", "mon-article-2");

        String slug = generator.generateUniqueSlug("Mon article", taken::contains);

        assertThat(slug).isEqualTo("mon-article-3");
    }

    @Test
    void generateUniqueSlugResolvesMultipleConsecutiveCollisions() {
        Set<String> taken = Set.of("mon-article", "mon-article-2", "mon-article-3", "mon-article-4");

        String slug = generator.generateUniqueSlug("Mon article", taken::contains);

        assertThat(slug).isEqualTo("mon-article-5");
    }

    @Test
    void generateUniqueSlugIsDeterministicForTheSameInputsAndSameExistenceState() {
        Predicate<String> onlyBaseTaken = "mon-article"::equals;

        String first = generator.generateUniqueSlug("Mon article", onlyBaseTaken);
        String second = generator.generateUniqueSlug("Mon article", onlyBaseTaken);

        assertThat(first).isEqualTo(second).isEqualTo("mon-article-2");
    }

    @Test
    void generateUniqueSlugThrowsAfterExhaustingMaxSuffixAttempts() {
        String slug = "mon-article";
        Predicate<String> alwaysTaken = candidate -> true;

        assertThatThrownBy(() -> generator.generateUniqueSlug(slug, alwaysTaken))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generateUniqueSlugThrowsOnNullSlugExistsPredicate() {
        assertThatThrownBy(() -> generator.generateUniqueSlug("Mon article", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void generateUniqueSlugPropagatesNormalizationFailureForPunctuationOnlyTitle() {
        assertThatThrownBy(() -> generator.generateUniqueSlug("!!!", candidate -> false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
