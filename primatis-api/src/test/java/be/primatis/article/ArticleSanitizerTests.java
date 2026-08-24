package be.primatis.article;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie {@link ArticleSanitizer#sanitize(String)} (DEV-11.4). Test
 * unitaire pur — aucun Spring, aucun PostgreSQL, aucun {@code
 * ArticleService}/{@code Controller}. Ne teste ni {@code HTTP 400}, ni
 * {@code BusinessRuleException} : ces responsabilités appartiennent au
 * futur {@code ArticleService} (mission DEV-11.4 §7).
 */
class ArticleSanitizerTests {

    private final ArticleSanitizer sanitizer = new ArticleSanitizer();

    // ---------------------------------------------------------------
    // Contenu légitime conservé
    // ---------------------------------------------------------------

    @Test
    void keepsSimpleParagraphs() {
        String result = sanitizer.sanitize("<p>Bonjour la bibliothèque.</p>");

        assertThat(result).contains("<p>Bonjour la bibliothèque.</p>");
    }

    @Test
    void keepsHeadingsH2ToH4() {
        String result = sanitizer.sanitize("<h2>Section</h2><h3>Sous-section</h3><h4>Détail</h4>");

        assertThat(result).contains("<h2>Section</h2>").contains("<h3>Sous-section</h3>").contains("<h4>Détail</h4>");
    }

    @Test
    void doesNotAllowH1InBodyContent() {
        // h1 volontairement exclu : réservé au titre d'Article rendu par
        // le frontend, jamais dupliqué dans le corps (Javadoc ArticleSanitizer).
        String result = sanitizer.sanitize("<h1>Titre dupliqué</h1>");

        assertThat(result).doesNotContain("<h1").contains("Titre dupliqué");
    }

    @Test
    void keepsBoldItalicAndUnderlineFormatting() {
        String result = sanitizer.sanitize("<p><b>gras</b> <em>italique</em> <u>souligné</u></p>");

        assertThat(result).contains("<b>gras</b>").contains("<em>italique</em>").contains("<u>souligné</u>");
    }

    @Test
    void keepsUnorderedAndOrderedLists() {
        String result = sanitizer.sanitize("<ul><li>Un</li><li>Deux</li></ul><ol><li>Premier</li></ol>");

        assertThat(result).contains("<ul>").contains("<li>Un</li>").contains("<ol>").contains("<li>Premier</li>");
    }

    @Test
    void keepsBlockquoteAndCode() {
        String result = sanitizer.sanitize("<blockquote>Citation</blockquote><p><code>inline</code></p><pre>bloc</pre>");

        assertThat(result).contains("<blockquote>").contains("<code>inline</code>").contains("<pre>");
    }

    @Test
    void keepsSafeHttpsLink() {
        String result = sanitizer.sanitize("<a href=\"https://primatis.test/infos\">infos</a>");

        assertThat(result).contains("href=\"https://primatis.test/infos\"").contains(">infos<");
    }

    @Test
    void keepsSafeHttpLink() {
        String result = sanitizer.sanitize("<a href=\"http://primatis.test/infos\">infos</a>");

        assertThat(result).contains("href=\"http://primatis.test/infos\"");
    }

    // ---------------------------------------------------------------
    // Contenu dangereux neutralisé
    // ---------------------------------------------------------------

    @Test
    void removesScriptTagAndItsContent() {
        String result = sanitizer.sanitize("<p>Avant</p><script>alert('xss')</script><p>Après</p>");

        assertThat(result).doesNotContain("<script").doesNotContain("alert(");
        assertThat(result).contains("Avant").contains("Après");
    }

    @Test
    void removesStyleTagAndItsContent() {
        String result = sanitizer.sanitize("<style>body{display:none}</style><p>Texte</p>");

        assertThat(result).doesNotContain("<style").doesNotContain("display:none");
    }

    @Test
    void removesOnClickEventHandlerButKeepsText() {
        String result = sanitizer.sanitize("<p onclick=\"alert('xss')\">Cliquable</p>");

        assertThat(result).doesNotContain("onclick").doesNotContain("alert(");
        assertThat(result).contains("Cliquable");
    }

    @Test
    void removesOnErrorEventHandler() {
        String result = sanitizer.sanitize("<p onerror=\"alert('xss')\">Texte</p>");

        assertThat(result).doesNotContain("onerror").doesNotContain("alert(");
    }

    @Test
    void removesJavascriptUriFromLink() {
        String result = sanitizer.sanitize("<a href=\"javascript:alert(1)\">lien</a>");

        assertThat(result).doesNotContain("javascript:");
    }

    @Test
    void removesFtpAndMailtoLinkProtocols() {
        // basic() de jsoup autorise par défaut ftp/mailto : retirés
        // explicitement (Javadoc ArticleSanitizer), seuls http/https restent.
        assertThat(sanitizer.sanitize("<a href=\"ftp://files.test/a\">fichier</a>")).doesNotContain("ftp:");
        assertThat(sanitizer.sanitize("<a href=\"mailto:contact@primatis.test\">contact</a>")).doesNotContain("mailto:");
    }

    @Test
    void removesIframeTag() {
        String result = sanitizer.sanitize("<iframe src=\"https://evil.test\"></iframe><p>Texte</p>");

        assertThat(result).doesNotContain("<iframe").doesNotContain("evil.test");
        assertThat(result).contains("Texte");
    }

    @Test
    void removesObjectEmbedFormInputButtonAudioVideoAndSvgTags() {
        String html = "<object></object><embed/><form><input/><button>go</button></form>"
                + "<audio src=\"a.mp3\"></audio><video src=\"v.mp4\"></video><svg onload=\"alert(1)\"></svg>";

        String result = sanitizer.sanitize(html);

        assertThat(result)
                .doesNotContain("<object").doesNotContain("<embed").doesNotContain("<form")
                .doesNotContain("<input").doesNotContain("<button").doesNotContain("<audio")
                .doesNotContain("<video").doesNotContain("<svg").doesNotContain("alert(");
    }

    @Test
    void removesArbitraryStyleAttribute() {
        String result = sanitizer.sanitize("<p style=\"position:fixed;top:0\">Texte</p>");

        assertThat(result).doesNotContain("style=").contains("Texte");
    }

    @Test
    void removesDataUriFromLink() {
        String result = sanitizer.sanitize("<a href=\"data:text/html,<script>alert(1)</script>\">lien</a>");

        assertThat(result).doesNotContain("data:").doesNotContain("<script").doesNotContain("alert(");
    }

    @Test
    void removesDisallowedTagButKeepsLegitimateText() {
        String result = sanitizer.sanitize("<div>Contenu légitime</div>");

        assertThat(result).doesNotContain("<div").contains("Contenu légitime");
    }

    // ---------------------------------------------------------------
    // Contrat technique
    // ---------------------------------------------------------------

    @Test
    void sanitizesNullThrowsExplicitly() {
        assertThatThrownBy(() -> sanitizer.sanitize(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void sanitizesEmptyStringToEmptyString() {
        assertThat(sanitizer.sanitize("")).isEmpty();
    }

    @Test
    void sanitizesHtmlContainingOnlyDangerousContentToEmptyOrWhitespaceOnly() {
        String result = sanitizer.sanitize("<script>alert(1)</script><style>body{}</style>");

        assertThat(result.strip()).isEmpty();
    }
}
