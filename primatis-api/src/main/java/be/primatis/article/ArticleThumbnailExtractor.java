package be.primatis.article;

import be.primatis.config.ArticleMediaProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Extraction de la première image insérée dans {@code Article.content},
 * destinée à la vignette de la liste publique (DEV-ARTICLES-MEDIA-THUMBNAIL) —
 * jamais une nouvelle colonne/table média (décision explicite du
 * développeur, portée strictement limitée : {@code Article.content} reste
 * l'unique source). Composant technique pur, même précédent structurel
 * exact que {@link ArticleSanitizer}/{@code ArticleSlugGenerator} :
 * {@code @Component} pour être injecté par {@code ArticleService}, mais
 * testable directement en dehors de tout contexte Spring.
 *
 * <p>Opère sur {@code content} déjà sanitisé (persisté après passage par
 * {@link ArticleSanitizer}, {@code ArticleService}) — jamais une seconde
 * sanitization ici, uniquement une lecture. La revalidation du {@code src}
 * ci-dessous (préfixe {@code /media/articles/} ou {@code http(s)://}) est
 * une défense en profondeur explicitement demandée (mission §5 : « source
 * rejetée/non valide » comme cas de {@code null} distinct de « pas
 * d'image »), pas une hypothèse que le contenu persisté pourrait être
 * malveillant en usage normal.
 */
@Component
public class ArticleThumbnailExtractor {

    /**
     * @param content HTML potentiellement {@code null}/vide (jamais
     *                requis non-{@code null} contrairement à {@link
     *                ArticleSanitizer#sanitize} : ce composant ne fait
     *                que lire, une entrée absente est un cas normal, pas
     *                une erreur de programmation).
     * @return l'URL de la première {@code <img>} trouvée, ou {@code null}
     *         si aucune image, un {@code src} vide, ou un {@code src} ne
     *         correspondant à aucun motif attendu.
     */
    public String extractFirstImageUrl(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        Element image = Jsoup.parse(content).selectFirst("img");
        if (image == null) {
            return null;
        }

        String src = image.attr("src");
        if (src.isBlank() || !isExpectedImageSrc(src)) {
            return null;
        }

        return src;
    }

    private boolean isExpectedImageSrc(String src) {
        return src.startsWith(ArticleMediaProperties.PUBLIC_PATH_PREFIX)
                || src.startsWith("http://")
                || src.startsWith("https://");
    }
}
