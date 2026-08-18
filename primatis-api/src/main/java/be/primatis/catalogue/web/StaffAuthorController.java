package be.primatis.catalogue.web;

import be.primatis.catalogue.CatalogueManagementService;
import be.primatis.catalogue.dto.AuthorResponse;
import be.primatis.catalogue.dto.CreateAuthorRequest;
import be.primatis.catalogue.dto.UpdateAuthorRequest;
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
 * Contrat REST staff minimal de gestion des {@code Author} (DEV-06.5.1,
 * {@code CATALOGUE_MANAGE}) : rattacher un Author existant à un Title
 * (DEV-06.5) exige de pouvoir le retrouver/créer/corriger côté staff. Aucun
 * {@code DELETE} — contrat fermé. Reste mince, autorisation appliquée par
 * {@code @PreAuthorize} sur {@link CatalogueManagementService}, jamais ici.
 */
@RestController
@RequestMapping("/api/v1/staff/authors")
@Validated
@Tag(name = "Catalogue", description = "Gestion staff des Authors")
public class StaffAuthorController {

    private final CatalogueManagementService catalogueManagementService;

    public StaffAuthorController(CatalogueManagementService catalogueManagementService) {
        this.catalogueManagementService = catalogueManagementService;
    }

    @Operation(
            summary = "Recherche paginée des Authors (staff)",
            description = "Retourne une page d'Authors (CATALOGUE_MANAGE requis). q filtre par fullName, "
                    + "partiel et insensible à la casse (absent = aucun filtre). Pagination 0-based, taille "
                    + "par défaut 20, maximum 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'Authors."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<AuthorResponse> searchAuthors(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "fullName", "id"));
        return PageResponse.from(catalogueManagementService.searchAuthors(q, pageable));
    }

    @Operation(
            summary = "Création staff d'un Author",
            description = "Crée un Author (CATALOGUE_MANAGE requis). fullName n'est jamais traité comme "
                    + "unique : un homonyme existant n'empêche jamais la création.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Author créé."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "birthDate postérieure à deathDate.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<AuthorResponse> createAuthor(
            @Valid @RequestBody CreateAuthorRequest request, UriComponentsBuilder uriComponentsBuilder) {
        AuthorResponse response = catalogueManagementService.createAuthor(request);

        URI location = uriComponentsBuilder.replacePath("/api/v1/staff/authors/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @Operation(
            summary = "Modification staff d'un Author",
            description = "Met à jour un Author existant (CATALOGUE_MANAGE requis) — PATCH sparse (champ "
                    + "absent = inchangé).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Author mis à jour."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Author pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "fullName effacé, ou birthDate postérieure à "
                    + "deathDate.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{authorId}")
    public AuthorResponse updateAuthor(@PathVariable Long authorId, @RequestBody UpdateAuthorRequest request) {
        return catalogueManagementService.updateAuthor(authorId, request);
    }
}
