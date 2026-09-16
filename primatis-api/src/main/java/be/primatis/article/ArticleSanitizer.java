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
     * {@code ftp}/{@code mailto} du défaut {@code basic()}). Aucun
     * {@code script}/{@code style}/{@code iframe}/{@code object}/
     * {@code embed}/{@code form}/{@code input}/{@code button}/
     * {@code audio}/{@code video}/{@code svg}, aucun attribut
     * {@code style} arbitraire, aucun gestionnaire d'événement — absents
     * par construction d'une allowlist (jamais retirés explicitement,
     * jamais présents).
     *
     * <p>{@code img} (DEV-ARTICLES-MEDIA, DEV-DEC-0079 — supersède, sur ce
     * point précis, l'exclusion média de business-rules.md §7.9) :
     * attributs strictement limités à {@code src}/{@code alt} (jamais
     * {@code style}/{@code onerror}/{@code onclick}/{@code srcset}/
     * {@code data-*}/{@code class}/{@code width}/{@code height} — mission
     * §14, aucune justification réelle ne les rend nécessaires ici).
     * Protocoles {@code src} : {@code http}/{@code https} uniquement.
     * {@link Safelist#preserveRelativeLinks} activé : une URL
     * <em>relative</em> (sans protocole — précisément le cas de
     * {@code /media/articles/<uuid>.<ext>}) est acceptée sans être résolue
     * contre une base URI absolue (inexistante ici, {@code
     * Jsoup.clean(rawHtml, ...)} sans base) — vérifié empiriquement
     * nécessaire et suffisant : sans cette option, une tentative initiale
     * d'ajouter explicitement une chaîne vide ({@code ""}) à {@link
     * Safelist#addProtocols} pour représenter « aucun protocole » a
     * échoué à l'exécution ({@code ValidationException: String must not
     * be empty}) — {@code preserveRelativeLinks(true)} est le mécanisme
     * jsoup réel pour ce besoin, pas une entrée de protocole. {@code
     * javascript:}/{@code data:}/{@code file:} restent structurellement
     * exclus (jamais dans la liste de protocoles) — preuve empirique dans
     * {@code ArticleSanitizerTests} (mission §15, DEV-ARTICLES-MEDIA §28).
     */
    private static final Safelist ARTICLE_CONTENT_ALLOWLIST = Safelist.basic()
            .addTags("h2", "h3", "h4", "img")
            .addAttributes("img", "src", "alt")
            .removeProtocols("a", "href", "ftp", "mailto")
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

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
