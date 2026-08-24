package be.primatis.article.web;

import be.primatis.article.ArticleService;
import be.primatis.article.dto.ArticleResponse;
import be.primatis.article.dto.ArticleSummaryResponse;
import be.primatis.exception.ApiErrorResponse;
import be.primatis.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrat REST public d'Article (DEV-11.5) : {@code GET /api/v1/articles}
 * (liste paginée, {@code PUBLISHED} uniquement) et {@code GET
 * /api/v1/articles/{slug}} (détail). Surface {@code permitAll}
 * ({@code SecurityConfig}, déjà en place depuis DEV-03.8/DEV-11.1 §13 —
 * non modifiée ici) — aucune autorisation ici ni dans {@link
 * ArticleService} pour ces deux méthodes, même précédent exact que {@code
 * catalogue.web.TitleController}. Reste mince : validation de forme,
 * construction du {@link Pageable}, délégation à {@link ArticleService} —
 * aucune logique de visibilité {@code PUBLISHED} ici (portée par le
 * Service/Repository).
 *
 * <p>Chemin du lookup par slug : {@code GET /api/v1/articles/{slug}}
 * (IMPLEMENTATION FREEDOM encadrée, OD-ART-12 — DEV-11.1 §31/§32 ;
 * {@code PRIMATIS_CONTEXT_DEV_v1.0.md} §10.17 ne fixe qu'un exemple non
 * contraignant, {@code GET /api/v1/articles/slug/{slug}}). Retenu tel
 * quel : plus court, cohérent avec le commentaire déjà présent sur {@code
 * ArticleRepository.findBySlug} depuis le scaffold initial. Aucun conflit
 * de routage avec un futur {@code POST /api/v1/articles/{articleId}/publish}
 * (DEV-11.7, coding-conventions.md §22.3) : méthodes HTTP différentes
 * (GET vs POST) et profondeur de chemin différente (segment unique vs
 * sous-ressource {@code /publish}) — Spring MVC distingue les deux sans
 * ambiguïté.
 */
@RestController
@RequestMapping("/api/v1/articles")
@Validated
@Tag(name = "Articles", description = "Consultation publique des Articles publiés")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Operation(
            summary = "Liste paginée des Articles publiés",
            description = "Retourne une page d'Articles PUBLISHED (accès public, sans authentification). "
                    + "Pagination 0-based, taille par défaut 20, maximum 100. Tri fixe (publishedAt DESC, id "
                    + "DESC) : aucun paramètre de tri client, aucune recherche ni filtre par Tag dans cette "
                    + "étape (business-rules.md §7.14, DEV-DEC-0061).",
            security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'Articles PUBLISHED."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<ArticleSummaryResponse> listArticles(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
        return PageResponse.from(articleService.listPublishedArticles(pageable));
    }

    @Operation(
            summary = "Détail public d'un Article par slug",
            description = "Retourne le détail d'un Article PUBLISHED (accès public, sans authentification), "
                    + "avec ses Tags. Un slug inexistant ou correspondant à un Article DRAFT/ARCHIVED retourne "
                    + "404 dans tous les cas, jamais 200/403/409 — la surface publique ne distingue pas ces "
                    + "situations.",
            security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article PUBLISHED trouvé."),
            @ApiResponse(responseCode = "404", description = "Aucun Article PUBLISHED pour ce slug.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{slug}")
    public ArticleResponse getArticleBySlug(@PathVariable String slug) {
        return articleService.getPublishedArticleBySlug(slug);
    }
}
