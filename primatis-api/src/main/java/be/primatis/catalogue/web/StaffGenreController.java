package be.primatis.catalogue.web;

import be.primatis.catalogue.CatalogueManagementService;
import be.primatis.catalogue.dto.CreateGenreRequest;
import be.primatis.catalogue.dto.GenreResponse;
import be.primatis.catalogue.dto.UpdateGenreRequest;
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
 * Contrat REST staff minimal de gestion des {@code Genre} (DEV-06.5.1,
 * {@code CATALOGUE_MANAGE}) — même justification/même contrat fermé que
 * {@link StaffAuthorController}. Aucun {@code GenreType} réintroduit.
 */
@RestController
@RequestMapping("/api/v1/staff/genres")
@Validated
@Tag(name = "Catalogue", description = "Gestion staff des Genres")
public class StaffGenreController {

    private final CatalogueManagementService catalogueManagementService;

    public StaffGenreController(CatalogueManagementService catalogueManagementService) {
        this.catalogueManagementService = catalogueManagementService;
    }

    @Operation(
            summary = "Liste paginée des Genres (staff)",
            description = "Retourne une page de Genres (CATALOGUE_MANAGE requis). Aucun filtre "
                    + "supplémentaire (aucun besoin réel démontré). Pagination 0-based, taille par défaut "
                    + "20, maximum 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de Genres."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<GenreResponse> listGenres(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "label", "id"));
        return PageResponse.from(catalogueManagementService.listGenres(pageable));
    }

    @Operation(
            summary = "Création staff d'un Genre",
            description = "Crée un Genre (CATALOGUE_MANAGE requis). code et label doivent être uniques.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Genre créé."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "code ou label déjà utilisé par un autre Genre.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<GenreResponse> createGenre(
            @Valid @RequestBody CreateGenreRequest request, UriComponentsBuilder uriComponentsBuilder) {
        GenreResponse response = catalogueManagementService.createGenre(request);

        URI location = uriComponentsBuilder.replacePath("/api/v1/staff/genres/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @Operation(
            summary = "Modification staff d'un Genre",
            description = "Met à jour un Genre existant (CATALOGUE_MANAGE requis) — PATCH sparse (champ "
                    + "absent = inchangé). Unicité de code/label revérifiée uniquement en cas de changement "
                    + "réel de valeur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Genre mis à jour."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Genre pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "code/label effacé, ou déjà utilisé par un "
                    + "autre Genre.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{genreId}")
    public GenreResponse updateGenre(@PathVariable Long genreId, @RequestBody UpdateGenreRequest request) {
        return catalogueManagementService.updateGenre(genreId, request);
    }
}
