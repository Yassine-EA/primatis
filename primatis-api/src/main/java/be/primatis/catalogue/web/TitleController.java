package be.primatis.catalogue.web;

import be.primatis.catalogue.CatalogueService;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.dto.TitleDetailResponse;
import be.primatis.catalogue.dto.TitleResponse;
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
 * Contrat REST public du catalogue (DEV-06.4) : {@code GET /api/v1/titles}
 * (liste paginée/recherche) et {@code GET /api/v1/titles/{titleId}} (détail).
 * Surface {@code permitAll} ({@code SecurityConfig}, DEV-03.8) — aucune
 * autorisation ici ni dans {@link CatalogueService} (K.2, résolu pour ce
 * contrat : un utilisateur authentifié voit exactement la même surface
 * qu'un Visitor). Reste mince : validation de forme, construction du
 * {@link Pageable}, délégation à {@link CatalogueService} — aucune logique
 * de visibilité {@code ACTIVE}/{@code WITHDRAWN} ici (portée par le Service).
 */
@RestController
@RequestMapping("/api/v1/titles")
@Validated
@Tag(name = "Catalogue", description = "Consultation publique du catalogue (Titles)")
public class TitleController {

    private final CatalogueService catalogueService;

    public TitleController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @Operation(
            summary = "Recherche paginée du catalogue public",
            description = "Retourne une page de Titles ACTIVE (accès public, sans authentification). "
                    + "Filtres combinables : q (titre, partiel, insensible à la casse), authorId, "
                    + "genreCode, language. Pagination 0-based, taille par défaut 20, maximum 100. "
                    + "Tri fixe (title ASC, id ASC) : aucun paramètre de tri client dans cette étape.",
            security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de Titles ACTIVE."),
            @ApiResponse(responseCode = "400", description = "Paramètres invalides (pagination ou language).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<TitleResponse> searchTitles(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String genreCode,
            @RequestParam(required = false) Language language,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "title", "id"));
        return PageResponse.from(catalogueService.searchPublicTitles(q, authorId, genreCode, language, pageable));
    }

    @Operation(
            summary = "Détail public d'un Title",
            description = "Retourne le détail d'un Title ACTIVE (accès public, sans authentification), "
                    + "avec ses Authors et Genres. Un Title inexistant ou WITHDRAWN retourne 404 dans "
                    + "les deux cas, jamais 200/403/409 — la surface publique ne distingue pas ces deux "
                    + "situations. N'inclut aucun exemplaire (Copy).",
            security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Title ACTIVE trouvé."),
            @ApiResponse(responseCode = "404", description = "Aucun Title ACTIVE pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{titleId}")
    public TitleDetailResponse getTitleById(@PathVariable Long titleId) {
        return catalogueService.getPublicTitleById(titleId);
    }
}
