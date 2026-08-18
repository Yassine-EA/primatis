package be.primatis.catalogue.web;

import be.primatis.catalogue.CopyService;
import be.primatis.catalogue.dto.CopyResponse;
import be.primatis.catalogue.dto.CreateCopyRequest;
import be.primatis.catalogue.dto.UpdateCopyAvailabilityRequest;
import be.primatis.catalogue.dto.UpdateCopyRequest;
import be.primatis.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Contrat REST staff des {@code Copy} (DEV-06.6) : consultation
 * ({@code COPY_READ}) et gestion ({@code COPY_MANAGE}) des exemplaires d'un
 * Title. Reste mince, autorisation appliquée par {@code @PreAuthorize} sur
 * {@link CopyService}, jamais ici. Aucun {@code DELETE} — la sortie
 * fonctionnelle d'un exemplaire du circuit est déjà représentable par
 * {@code LOST}/{@code OUT_OF_SERVICE}/{@code UNAVAILABLE}.
 *
 * <p>Interdiction absolue DEV-06.6 (§9) : aucun endpoint de ce Controller ne
 * peut écrire {@code ON_LOAN}/{@code RESERVED} — {@link CopyService} refuse
 * ces valeurs, jamais contournable depuis ce Controller.
 */
@RestController
@RequestMapping("/api/v1/staff/titles/{titleId}/copies")
@Tag(name = "Catalogue", description = "Gestion staff des Copies (exemplaires)")
public class StaffCopyController {

    private final CopyService copyService;

    public StaffCopyController(CopyService copyService) {
        this.copyService = copyService;
    }

    @Operation(
            summary = "Liste des exemplaires d'un Title (staff)",
            description = "Retourne tous les exemplaires d'un Title, triés par inventoryCode (COPY_READ "
                    + "requis). Aucune pagination (collection sans limite métier maximale).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des exemplaires (éventuellement vide)."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission COPY_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Title pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public List<CopyResponse> listCopies(@PathVariable Long titleId) {
        return copyService.listCopiesByTitle(titleId);
    }

    @Operation(
            summary = "Détail d'un exemplaire (staff)",
            description = "Retourne un exemplaire par son identifiant, scopé au Title du path (COPY_READ "
                    + "requis). Un Copy inexistant ou appartenant à un autre Title retourne 404 dans les deux "
                    + "cas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exemplaire trouvé."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission COPY_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun exemplaire pour cet identifiant sous ce Title.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{copyId}")
    public CopyResponse getCopyById(@PathVariable Long titleId, @PathVariable Long copyId) {
        return copyService.getCopyById(titleId, copyId);
    }

    @Operation(
            summary = "Création staff d'un exemplaire",
            description = "Crée un Copy pour le Title du path (COPY_MANAGE requis). copyCondition et "
                    + "availabilityStatus sont tous deux obligatoires (aucun défaut caché) ; seuls AVAILABLE et "
                    + "UNAVAILABLE sont acceptables à la création. Un Title WITHDRAWN peut recevoir des Copies.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Exemplaire créé."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission COPY_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun Title pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "inventoryCode déjà utilisé, combinaison "
                    + "copyCondition/availabilityStatus invalide, ou ON_LOAN/RESERVED demandé.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CopyResponse> createCopy(
            @PathVariable Long titleId, @Valid @RequestBody CreateCopyRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        CopyResponse response = copyService.createCopy(titleId, request);

        URI location = uriComponentsBuilder.replacePath("/api/v1/staff/titles/{titleId}/copies/{copyId}")
                .buildAndExpand(titleId, response.id())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @Operation(
            summary = "Modification staff d'un exemplaire",
            description = "Met à jour un Copy existant (COPY_MANAGE requis) — PATCH sparse (champ absent = "
                    + "inchangé). Ne modifie jamais availabilityStatus directement (action dédiée PATCH "
                    + ".../availability) : seul un passage de copyCondition vers LOST/OUT_OF_SERVICE impose "
                    + "UNAVAILABLE automatiquement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exemplaire mis à jour."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission COPY_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun exemplaire pour cet identifiant sous ce Title.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "inventoryCode/copyCondition effacé, ou "
                    + "inventoryCode déjà utilisé par un autre exemplaire.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{copyId}")
    public CopyResponse updateCopy(
            @PathVariable Long titleId, @PathVariable Long copyId, @RequestBody UpdateCopyRequest request) {
        return copyService.updateCopy(titleId, copyId, request);
    }

    @Operation(
            summary = "Disponibilité manuelle d'un exemplaire",
            description = "AVAILABLE ⇄ UNAVAILABLE uniquement (COPY_MANAGE requis) — action explicite de "
                    + "retrait/remise en circuit. ON_LOAN/RESERVED toujours refusés (exclusivement gérés par "
                    + "les futurs workflows Loan/Reservation). AVAILABLE refusé si copyCondition vaut "
                    + "LOST/OUT_OF_SERVICE. Idempotent : appliquer le statut déjà courant est un succès sans "
                    + "effet de bord.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Disponibilité appliquée (transition ou idempotent)."),
            @ApiResponse(responseCode = "400", description = "status absent ou valeur inconnue.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission COPY_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun exemplaire pour cet identifiant sous ce Title.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ON_LOAN/RESERVED demandé, ou AVAILABLE demandé "
                    + "avec copyCondition LOST/OUT_OF_SERVICE.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{copyId}/availability")
    public CopyResponse updateCopyAvailability(
            @PathVariable Long titleId, @PathVariable Long copyId,
            @Valid @RequestBody UpdateCopyAvailabilityRequest request) {
        return copyService.updateCopyAvailability(titleId, copyId, request);
    }
}
