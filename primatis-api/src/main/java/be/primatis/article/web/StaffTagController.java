package be.primatis.article.web;

import be.primatis.article.TagService;
import be.primatis.article.dto.CreateTagRequest;
import be.primatis.article.dto.TagResponse;
import be.primatis.article.dto.UpdateTagRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Contrat REST staff de gestion des {@code Tag} comme ressource séparée
 * (DEV-11.9, {@code ARTICLE_MANAGE}, DEV-DEC-0060) — même contrat/mêmes
 * conventions que {@link be.primatis.catalogue.web.StaffGenreController}
 * (liste paginée, création, modification). Contrairement au précédent Genre
 * (volontairement sans {@code DELETE}), expose un {@code DELETE} explicite
 * — voir {@link TagService} pour la justification de cette divergence.
 * Reste mince : validation de forme, délégation à {@link TagService} —
 * aucune règle métier ici.
 */
@RestController
@RequestMapping("/api/v1/staff/tags")
@Validated
@Tag(name = "Articles", description = "Gestion staff des Tags")
public class StaffTagController {

    private final TagService tagService;

    public StaffTagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(
            summary = "Liste paginée des Tags (staff)",
            description = "Retourne une page de Tags (ARTICLE_MANAGE requis). Aucun filtre supplémentaire. "
                    + "Pagination 0-based, taille par défaut 20, maximum 100, tri label ASC puis id ASC.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de Tags."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<TagResponse> listTags(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "label", "id"));
        return PageResponse.from(tagService.listTags(pageable));
    }

    @Operation(
            summary = "Création staff d'un Tag",
            description = "Crée un Tag (ARTICLE_MANAGE requis). code doit être unique et stable (jamais "
                    + "modifiable ensuite).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tag créé."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "code déjà utilisé par un autre Tag.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TagResponse> createTag(
            @Valid @RequestBody CreateTagRequest request, UriComponentsBuilder uriComponentsBuilder) {
        TagResponse response = tagService.createTag(request);

        URI location = uriComponentsBuilder.replacePath("/api/v1/staff/tags/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @Operation(
            summary = "Modification staff d'un Tag",
            description = "Met à jour un Tag existant (ARTICLE_MANAGE requis) — PATCH sparse (champ absent = "
                    + "inchangé). code n'est jamais modifiable (absent du contrat).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tag mis à jour."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Tag pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "label effacé.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{tagId}")
    public TagResponse updateTag(@PathVariable Long tagId, @RequestBody UpdateTagRequest request) {
        return tagService.updateTag(tagId, request);
    }

    @Operation(
            summary = "Suppression staff d'un Tag",
            description = "Supprime physiquement un Tag non utilisé (ARTICLE_MANAGE requis). Un Tag encore "
                    + "associé à au moins un Article ne peut pas être supprimé (fk_article_tag_tag_id).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tag supprimé."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Tag pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Tag encore associé à au moins un Article.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long tagId) {
        tagService.deleteTag(tagId);
        return ResponseEntity.noContent().build();
    }
}
