package be.primatis.article;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Sanitization du HTML riche d'{@code Article.content} avant persistance
 * (DEV-11.4, business-rules.md §7.6, architecture.md §18). Responsabilité
 * unique : {@code String} HTML brut → {@code String} HTML sanitisé.
 * Composant technique pur, sans dépendance {@code ArticleRepository}/
 * {@code Authentication} — même précédent structurel exact que {@link
 * be.primatis.user.PhoneNumberNormalizer} (DEV-05.9) : {@code @Component}
 * Spring pour être injecté par le futur {@code ArticleService}, mais
 * testable directement en dehors de tout contexte Spring.
 *
 * <p>Bibliothèque retenue : <a href="https://jsoup.org">jsoup</a>
 * (allowlist {@link Safelist}, jamais une denylist — business-rules.md
 * §7.6/§10.7 : « Do not implement a homemade sanitizer »/« Do not rely
 * only on Angular »). Choix documenté au log DEV-11.4 §6 — IMPLEMENTATION
 * FREEDOM encadrée (business-rules.md §11.6, architecture.md §22.8), aucune
 * DEV-DEC créée pour ce choix technique.
 *
 * <p>Ne persiste rien, ne charge aucun {@code Article}, ne connaît ni
 * l'authentification, ni le slug, ni la publication/l'archivage, ni les
 * Notifications — ces responsabilités appartiennent exclusivement au futur
 * {@code ArticleService} (DEV-11.5+). La règle métier « {@code content}
 * reste fonctionnellement non vide après sanitization » n'est PAS
 * appliquée ici (business-rules.md §7.6, mission DEV-11.4 §3) : ce
 * composant retourne fidèlement le résultat de la sanitization, y compris
 * une chaîne vide, laissant au Service le soin de refuser un contenu
 * devenu vide.
 */
@Component
public class ArticleSanitizer {

    /**
     * Allowlist retenue (voir log DEV-11.4 §7 pour la justification
     * complète) : base {@link Safelist#basic()} (paragraphes, gras,
     * italique, souligné, listes, citations, code inline/bloc, liens)
     * complétée de sous-titres éditoriaux {@code h2}/{@code h3}/{@code h4}
     * ({@code h1} volontairement exclu — réservé au titre d'Article rendu
     * par le frontend, jamais dupliqué dans le corps). Protocoles de lien
     * restreints à {@code http}/{@code https} uniquement (retire
     * {@code ftp}/{@code mailto} du défaut {@code basic()}). Aucune image,
     * aucun média (Article media hors V1, business-rules.md §7.9), aucun
     * {@code script}/{@code style}/{@code iframe}/{@code object}/
     * {@code embed}/{@code form}/{@code input}/{@code button}/
     * {@code audio}/{@code video}/{@code svg}, aucun attribut
     * {@code style} arbitraire, aucun gestionnaire d'événement — absents
     * par construction d'une allowlist (jamais retirés explicitement,
     * jamais présents).
     */
    private static final Safelist ARTICLE_CONTENT_ALLOWLIST = Safelist.basic()
            .addTags("h2", "h3", "h4")
            .removeProtocols("a", "href", "ftp", "mailto")
            .addProtocols("a", "href", "http", "https");

    /**
     * @param rawHtml HTML brut, jamais {@code null} (précondition de
     *                programmation — {@code content} est obligatoire dès le
     *                DTO, {@code be.primatis.article.dto.CreateArticleRequest}).
     *                Une chaîne vide est un contenu valide du point de vue
     *                de ce composant.
     * @return le HTML sanitisé, conforme à {@link #ARTICLE_CONTENT_ALLOWLIST}.
     */
    public String sanitize(String rawHtml) {
        Objects.requireNonNull(rawHtml, "rawHtml");
        return Jsoup.clean(rawHtml, ARTICLE_CONTENT_ALLOWLIST);
    }
}
