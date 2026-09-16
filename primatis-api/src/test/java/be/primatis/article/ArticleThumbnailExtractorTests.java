package be.primatis.article;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie {@link ArticleThumbnailExtractor#extractFirstImageUrl(String)}
 * (DEV-ARTICLES-MEDIA-THUMBNAIL). Test unitaire pur — aucun Spring, aucun
 * PostgreSQL, même précédent que {@code ArticleSanitizerTests}.
 */
class ArticleThumbnailExtractorTests {

    private final ArticleThumbnailExtractor extractor = new ArticleThumbnailExtractor();

    @Test
    void returnsTheFirstInternalImageUrl() {
        String content = "<p>Texte</p><img src=\"/media/articles/abc.webp\" alt=\"Illustration\">";

        assertThat(extractor.extractFirstImageUrl(content)).isEqualTo("/media/articles/abc.webp");
    }

    @Test
    void returnsTheFirstAbsoluteHttpsImageUrl() {
        String content = "<img src=\"https://primatis.test/media/articles/abc.webp\" alt=\"ok\">";

        assertThat(extractor.extractFirstImageUrl(content)).isEqualTo("https://primatis.test/media/articles/abc.webp");
    }

    @Test
    void returnsOnlyTheFirstImageWhenSeveralArePresent() {
        String content = "<p>Texte</p>"
                + "<img src=\"/media/articles/first.webp\" alt=\"Première\">"
                + "<p>Suite</p>"
                + "<img src=\"/media/articles/second.webp\" alt=\"Seconde\">";

        assertThat(extractor.extractFirstImageUrl(content)).isEqualTo("/media/articles/first.webp");
    }

    @Test
    void returnsNullWhenNoImageIsPresent() {
        assertThat(extractor.extractFirstImageUrl("<p>Texte sans image</p>")).isNull();
    }

    @Test
    void returnsNullWhenTheImageHasNoSrc() {
        assertThat(extractor.extractFirstImageUrl("<p>Texte</p><img alt=\"sans src\">")).isNull();
    }

    @Test
    void returnsNullWhenTheImageHasAnEmptySrc() {
        assertThat(extractor.extractFirstImageUrl("<img src=\"\" alt=\"vide\">")).isNull();
    }

    @Test
    void returnsNullForAJavascriptUriEvenIfPresentInRawContent() {
        // Défense en profondeur (mission §5) : ce contenu ne peut normalement pas être
        // persisté (ArticleSanitizer le retire avant toute écriture), mais l'extracteur
        // doit rester sûr par lui-même, indépendamment du sanitizer.
        assertThat(extractor.extractFirstImageUrl("<img src=\"javascript:alert(1)\">")).isNull();
    }

    @Test
    void returnsNullForADataUriEvenIfPresentInRawContent() {
        String content = "<img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lE"
                + "QVR42mNkYPhfz0AEYBxVSF+FAAAAAElFTkSuQmCC\">";

        assertThat(extractor.extractFirstImageUrl(content)).isNull();
    }

    @Test
    void returnsNullWhenContentIsNull() {
        assertThat(extractor.extractFirstImageUrl(null)).isNull();
    }

    @Test
    void returnsNullWhenContentIsBlank() {
        assertThat(extractor.extractFirstImageUrl("   ")).isNull();
    }

    @Test
    void returnsNullWhenContentIsEmpty() {
        assertThat(extractor.extractFirstImageUrl("")).isNull();
    }
}
