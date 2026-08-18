package be.primatis.catalogue.web;

import be.primatis.catalogue.CatalogueService;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.TitleStatus;
import be.primatis.catalogue.dto.CreateTitleRequest;
import be.primatis.catalogue.dto.TitleDetailResponse;
import be.primatis.catalogue.dto.TitleResponse;
import be.primatis.catalogue.dto.UpdateTitleRequest;
import be.primatis.catalogue.dto.UpdateTitleStatusRequest;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Contrat REST staff du catalogue (DEV-06.5) : consultation ({@code ACTIVE}
 * et {@code WITHDRAWN}), création et modification de {@code Title}, sous
 * {@code CATALOGUE_MANAGE}. Reste mince : validation de forme, construction
 * du {@link Pageable}, délégation à {@link CatalogueService} — aucune règle
 * métier ici. L'autorisation est appliquée par {@code @PreAuthorize} sur
 * {@link CatalogueService}, jamais ici (même convention que
 * {@code UserController}).
 *
 * <p>K.1 (FIGÉE DEV-06.5) : aucune suppression physique de {@code Title} —
 * aucun {@code DELETE} n'existe sur cette ressource. Le retrait fonctionnel
 * passe par {@code PATCH .../status} ({@code WITHDRAWN}), réversible.
 */
@RestController
@RequestMapping("/api/v1/staff/titles")
@Validated
@Tag(name = "Catalogue", description = "Gestion staff du catalogue (Titles)")
public class StaffTitleController {

    private final CatalogueService catalogueService;

    public StaffTitleController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @Operation(
            summary = "Recherche paginée du catalogue (staff)",
            description = "Retourne une page de Titles, ACTIVE et WITHDRAWN inclus (CATALOGUE_MANAGE requis). "
                    + "Filtres combinables : q, authorId, genreCode, language, titleStatus (absent = ACTIVE et "
                    + "WITHDRAWN tous deux visibles). Pagination 0-based, taille par défaut 20, maximum 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de Titles."),
            @ApiResponse(responseCode = "400", description = "Paramètres invalides (pagination, language ou titleStatus).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<TitleResponse> searchTitles(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String genreCode,
            @RequestParam(required = false) Language language,
            @RequestParam(required = false) TitleStatus titleStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "title", "id"));
        return PageResponse.from(
                catalogueService.searchStaffTitles(q, authorId, genreCode, language, titleStatus, pageable));
    }

    @Operation(
            summary = "Détail staff d'un Title",
            description = "Retourne le détail d'un Title quel que soit son statut, ACTIVE ou WITHDRAWN "
                    + "(CATALOGUE_MANAGE requis). N'inclut aucun exemplaire (Copy).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Title trouvé."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Title pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{titleId}")
    public TitleDetailResponse getTitleById(@PathVariable Long titleId) {
        return catalogueService.getStaffTitleById(titleId);
    }

    @Operation(
            summary = "Création staff d'un Title",
            description = "Crée un Title ACTIVE avec au moins un Author existant (CATALOGUE_MANAGE requis). "
                    + "TitleStatus toujours ACTIVE à la création, imposé backend. Aucune création automatique "
                    + "d'Author/Genre : chaque identifiant fourni doit référencer une ressource existante.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Title créé."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Author ou Genre référencé inexistant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ISBN déjà utilisé par un autre Title.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TitleDetailResponse> createTitle(
            @Valid @RequestBody CreateTitleRequest request, UriComponentsBuilder uriComponentsBuilder) {
        TitleDetailResponse response = catalogueService.createTitle(request);

        URI location = uriComponentsBuilder.replacePath("/api/v1/staff/titles/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @Operation(
            summary = "Modification staff d'un Title",
            description = "Met à jour un Title existant (CATALOGUE_MANAGE requis) — PATCH sparse (champ absent "
                    + "= inchangé). Ne modifie jamais titleStatus (action dédiée PATCH .../status). authorIds, "
                    + "s'il est fourni, ne peut pas être vide (≥1 Author toujours requis).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Title mis à jour."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Title, Author ou Genre référencé inexistant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "title/language effacé, authorIds vide, ou ISBN "
                    + "déjà utilisé par un autre Title.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{titleId}")
    public TitleDetailResponse updateTitle(@PathVariable Long titleId, @RequestBody UpdateTitleRequest request) {
        return catalogueService.updateTitle(titleId, request);
    }

    @Operation(
            summary = "Transition TitleStatus",
            description = "ACTIVE ⇄ WITHDRAWN (CATALOGUE_MANAGE requis). Idempotent : appliquer le statut déjà "
                    + "courant est un succès sans effet de bord. WITHDRAWN n'est jamais terminal et ne supprime "
                    + "aucune donnée (Authors/Genres/Copies conservés).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "TitleStatus appliqué (transition ou idempotent)."),
            @ApiResponse(responseCode = "400", description = "status absent ou valeur inconnue.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Title pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{titleId}/status")
    public TitleDetailResponse updateTitleStatus(
            @PathVariable Long titleId, @Valid @RequestBody UpdateTitleStatusRequest request) {
        return catalogueService.updateTitleStatus(titleId, request);
    }
}
