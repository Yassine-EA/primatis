package be.primatis.article.web;

import be.primatis.article.ArticleService;
import be.primatis.article.dto.ArticleResponse;
import be.primatis.article.dto.CreateArticleRequest;
import be.primatis.article.dto.StaffArticleSummaryResponse;
import be.primatis.article.dto.UpdateArticleRequest;
import be.primatis.article.dto.UpdateArticleTagsRequest;
import be.primatis.exception.ApiErrorResponse;
import be.primatis.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrat REST staff de gestion des Articles : création/modification
 * {@code DRAFT} (DEV-11.6, {@code ARTICLE_MANAGE}) et publication (DEV-11.7,
 * {@code ARTICLE_PUBLISH}). Reste mince : validation de forme, résolution
 * de l'identité authentifiée, délégation à {@link ArticleService} — aucune
 * règle métier ici. L'autorisation est appliquée par {@code @PreAuthorize}
 * sur {@link ArticleService}, jamais ici (même convention que {@code
 * StaffTitleController}/{@code UserController}).
 *
 * <p>Préfixe {@code /api/v1/staff/articles} (IMPLEMENTATION FREEDOM guidée
 * par le précédent direct {@code StaffTitleController}/{@code
 * /api/v1/staff/titles}) — hors du matcher {@code permitAll} {@code
 * GET /api/v1/articles/**} (SecurityConfig, DEV-11.1 §13), donc authentifié
 * par défaut (règle générale {@code anyRequest().authenticated()}), la
 * permission exacte (ARTICLE_MANAGE ou ARTICLE_PUBLISH selon l'action)
 * restant portée par le Service — aucune modification de
 * {@code SecurityConfig} nécessaire.
 *
 * <p>Transitions de statut ({@code publish}/{@code archive}) exposées comme
 * actions métier explicites ({@code POST .../publish}, {@code POST
 * .../archive}), jamais un {@code PATCH}/{@code PUT} générique de statut
 * (mission DEV-11.7 §5 : « la transition doit être explicite par action
 * métier »). Le hard-delete {@code DRAFT} (DEV-11.8, DEV-DEC-0058) utilise
 * en revanche {@code DELETE}, seule sémantique HTTP cohérente pour une
 * suppression physique. Création sans en-tête {@code Location} : choix
 * délibérément conservé tel quel (même précédent que {@code
 * LoanController}/{@code ReservationController}) malgré l'ajout du détail
 * staff ci-dessous — {@code createDraftArticle} n'a pas été retouché,
 * DEV-11.12A n'ajoutant que la surface de lecture minimale nécessaire au
 * déblocage frontend, jamais une amélioration REST non demandée.
 *
 * <p>{@code GET} staff (DEV-11.12A, corrective) : {@code listStaffArticles}/
 * {@code getStaffArticleById} — tous statuts confondus ({@code DRAFT}/
 * {@code PUBLISHED}/{@code ARCHIVED}), contrairement à la surface publique
 * {@code permitAll} ({@code GET /api/v1/articles/**}, structurellement
 * {@code PUBLISHED}-only). Sans cette lecture, un {@code DRAFT}/{@code
 * ARCHIVED} devenait irrécupérable après un rechargement de page côté
 * frontend staff — voir {@code ArticleService#listStaffArticles} pour la
 * justification complète de la permission ({@code ARTICLE_MANAGE}, jamais
 * {@code ARTICLE_READ}).
 */
@RestController
@RequestMapping("/api/v1/staff/articles")
@Validated
@Tag(name = "Articles", description = "Gestion staff des Articles")
public class StaffArticleController {

    private final ArticleService articleService;

    public StaffArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Operation(
            summary = "Liste staff paginée des Articles",
            description = "Retourne une page d'Articles, tous statuts confondus (DRAFT/PUBLISHED/ARCHIVED, "
                    + "ARTICLE_MANAGE requis) — contrairement à la liste publique, structurellement PUBLISHED "
                    + "uniquement. Pagination 0-based, taille par défaut 20, maximum 100. Tri fixe (updatedAt "
                    + "DESC, id DESC) : aucun paramètre de tri/recherche/filtre client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'Articles, tous statuts."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<StaffArticleSummaryResponse> listStaffArticles(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);
        return PageResponse.from(articleService.listStaffArticles(pageable));
    }

    @Operation(
            summary = "Détail staff d'un Article",
            description = "Retourne le détail d'un Article quel que soit son statut (DRAFT/PUBLISHED/ARCHIVED, "
                    + "ARTICLE_MANAGE requis), avec ses Tags — contrairement au détail public, structurellement "
                    + "PUBLISHED uniquement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article trouvé, quel que soit son statut."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Article pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{articleId}")
    public ArticleResponse getStaffArticleById(@PathVariable Long articleId) {
        return articleService.getStaffArticleById(articleId);
    }

    @Operation(
            summary = "Création staff d'un Article",
            description = "Crée un Article DRAFT (ARTICLE_MANAGE requis). authorUser dérivé de l'identité "
                    + "authentifiée, jamais du corps de la requête. slug généré backend, jamais fourni par le "
                    + "client. content sanitisé et vérifié fonctionnellement non vide avant persistance.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Article DRAFT créé."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Contenu vide après sanitization, titre ne "
                    + "produisant aucun slug valide, ou slug déjà utilisé.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ArticleResponse> createDraftArticle(
            @Valid @RequestBody CreateArticleRequest request, Authentication authentication) {
        ArticleResponse response = articleService.createDraftArticle(request, currentUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Modification staff d'un Article DRAFT ou PUBLISHED",
            description = "Met à jour un Article DRAFT ou PUBLISHED existant (ARTICLE_MANAGE requis) — PATCH "
                    + "sparse (champ absent = inchangé). Un Article ARCHIVED ne peut plus être modifié. Pour un "
                    + "PUBLISHED : articleStatus/publishedAt/slug restent inchangés, aucune nouvelle Notification "
                    + "ARTICLE_PUBLISHED n'est créée. slug jamais recalculé. lastModifiedByUser dérivé de "
                    + "l'identité authentifiée dès qu'un champ est effectivement présent dans le corps de la "
                    + "requête.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article mis à jour."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Article pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Article ARCHIVED, title effacé, content effacé, "
                    + "ou content devenu vide après sanitization.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{articleId}")
    public ArticleResponse updateArticle(
            @PathVariable Long articleId, @RequestBody UpdateArticleRequest request, Authentication authentication) {
        return articleService.updateArticle(articleId, request, currentUserId(authentication));
    }

    @Operation(
            summary = "Publication d'un Article",
            description = "Transition DRAFT → PUBLISHED (ARTICLE_PUBLISH requis, jamais ARTICLE_MANAGE en plus). "
                    + "publishedAt fixé, lastModifiedByUser dérivé de l'identité authentifiée. authorUser/slug/"
                    + "content/summary inchangés. Diffuse ARTICLE_PUBLISHED à chaque AppUser memberStatus=ACTIVE, "
                    + "dans la même transaction que la publication (0 destinataire est un succès valide). Un "
                    + "Article déjà PUBLISHED ou ARCHIVED ne peut pas être republié.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article publié."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_PUBLISH manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Article pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Article déjà PUBLISHED ou ARCHIVED.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{articleId}/publish")
    public ArticleResponse publishArticle(@PathVariable Long articleId, Authentication authentication) {
        return articleService.publishArticle(articleId, currentUserId(authentication));
    }

    @Operation(
            summary = "Archivage d'un Article",
            description = "Transition PUBLISHED → ARCHIVED (ARTICLE_MANAGE requis, jamais ARTICLE_PUBLISH). "
                    + "ARCHIVED est terminal : aucun retour possible. publishedAt/slug/title/content/summary/"
                    + "authorUser inchangés, lastModifiedByUser dérivé de l'identité authentifiée. Aucune "
                    + "Notification créée (aucun NotificationType d'archivage n'existe dans la baseline).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article archivé."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Article pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Article non PUBLISHED (DRAFT ou déjà ARCHIVED).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{articleId}/archive")
    public ArticleResponse archiveArticle(@PathVariable Long articleId, Authentication authentication) {
        return articleService.archiveArticle(articleId, currentUserId(authentication));
    }

    @Operation(
            summary = "Suppression physique d'un Article DRAFT",
            description = "Supprime physiquement un Article DRAFT (ARTICLE_MANAGE requis, jamais ARTICLE_PUBLISH). "
                    + "Un Article PUBLISHED ou ARCHIVED ne peut jamais être supprimé physiquement (DEV-DEC-0058) — "
                    + "seul l'archivage retire un Article publié de la circulation normale.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Article DRAFT supprimé."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Article pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Article non DRAFT (PUBLISHED ou ARCHIVED).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{articleId}")
    public ResponseEntity<Void> deleteDraftArticle(@PathVariable Long articleId) {
        articleService.deleteDraftArticle(articleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Association/dissociation de Tags sur un Article",
            description = "Remplace la sélection complète de Tags d'un Article DRAFT ou PUBLISHED "
                    + "(ARTICLE_MANAGE requis, jamais ARTICLE_PUBLISH). tagIds représente la sélection finale "
                    + "exacte (idempotent) — porte uniquement des identifiants de Tags déjà existants, aucun Tag "
                    + "n'est jamais créé depuis ce contrat. Un Article ARCHIVED ne peut plus être modifié.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sélection de Tags mise à jour."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide (tagIds absent).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Article inexistant, ou au moins un tagId inconnu.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Article ARCHIVED.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{articleId}/tags")
    public ArticleResponse associateTags(
            @PathVariable Long articleId, @Valid @RequestBody UpdateArticleTagsRequest request,
            Authentication authentication) {
        return articleService.associateTags(articleId, request, currentUserId(authentication));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
