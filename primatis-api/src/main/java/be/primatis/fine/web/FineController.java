package be.primatis.fine.web;

import be.primatis.exception.ApiErrorResponse;
import be.primatis.fine.FineService;
import be.primatis.fine.dto.FineResponse;
import be.primatis.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrat REST de consultation (DEV-09.9), confirmation du paiement
 * externe (DEV-09.7, DEV-DEC-0050) et annulation (DEV-09.8) d'une {@code
 * Fine}, même convention exacte que {@code loan.web.LoanController}/{@code
 * reservation.web.ReservationController}. Reste mince : mapping HTTP,
 * validation de forme (pagination), délégation à {@link FineService} —
 * aucune logique métier ici. L'autorisation ({@code FINE_MANAGE}/{@code
 * FINE_READ}) est appliquée par {@code @PreAuthorize} sur {@link
 * FineService}, jamais ici.
 *
 * <p>Aucun corps de requête pour la confirmation/l'annulation (aucun
 * {@code PaymentConfirmationRequest}/{@code CancelFineRequest} vide
 * introduit). Aucun {@code GET /api/v1/fines/{fineId}} (aucun besoin
 * démontré à ce stade, même raisonnement que {@code LoanController}/
 * {@code ReservationController}, DEV-DEC-0031/DEV-DEC-0036). Aucun
 * filtre/critère de recherche — strictement hors périmètre DEV-09.9.
 */
@RestController
@Validated
public class FineController {

    private final FineService fineService;

    public FineController(FineService fineService) {
        this.fineService = fineService;
    }

    @Operation(
            summary = "Liste paginée de toutes les amendes",
            description = "Retourne une page d'amendes, tous membres confondus (FINE_READ requis). "
                    + "Historique complet (UNPAID/PAID/CANCELLED), jamais filtré. "
                    + "Tri par date d'émission décroissante. Pagination 0-based, taille par défaut 20, maximum 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'amendes."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission FINE_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/fines")
    public PageResponse<FineResponse> listFines(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(fineService.listFines(pageable(page, size)));
    }

    @Operation(
            summary = "Amendes de l'utilisateur authentifié",
            description = "Retourne une page des amendes de l'utilisateur authentifié, historique complet inclus "
                    + "(UNPAID/PAID/CANCELLED). Aucun identifiant client : l'utilisateur est dérivé du JWT. "
                    + "Aucune permission requise. Page vide (jamais 404) si l'utilisateur n'a aucune amende.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'amendes de l'utilisateur authentifié."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/me/fines")
    public PageResponse<FineResponse> listOwnFines(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return PageResponse.from(fineService.listOwnFines(currentUserId(authentication), pageable(page, size)));
    }

    @Operation(
            summary = "Confirmation du paiement externe d'une amende",
            description = "Enregistre la confirmation administrative qu'un règlement a eu lieu hors PRIMATIS "
                    + "(FINE_MANAGE requis). PRIMATIS ne traite aucune transaction financière. "
                    + "Applique UNPAID -> PAID et renseigne paidAt. Seule une Fine UNPAID peut être confirmée.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement confirmé."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission FINE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Amende introuvable.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Amende déjà payée ou annulée.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/fines/{fineId}/payment-confirmation")
    public FineResponse confirmExternalPayment(@PathVariable Long fineId) {
        return fineService.confirmExternalPayment(fineId);
    }

    @Operation(
            summary = "Annulation d'une amende",
            description = "Annule une Fine UNPAID (FINE_MANAGE requis). Applique UNPAID -> CANCELLED et renseigne "
                    + "cancelledAt. La Fine n'est jamais supprimée : conservée comme historique. "
                    + "Une amende déjà payée ou déjà annulée ne peut pas être annulée.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Amende annulée."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission FINE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Amende introuvable.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Amende déjà payée ou déjà annulée.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/fines/{fineId}/cancel")
    public FineResponse cancelFine(@PathVariable Long fineId) {
        return fineService.cancelFine(fineId);
    }

    /**
     * Tri par défaut {@code issuedAt} décroissant + {@code id} en
     * tie-break déterministe — même précédent exact que {@code
     * LoanController}/{@code ReservationController} (DEV-DEC-0031/0036) :
     * aucune règle métier d'ordre Fine n'est fixée par une source, mais le
     * pattern « le plus récent d'abord, tie-break déterministe » est déjà
     * établi deux fois dans le projet — reconduit ici à l'identique plutôt
     * que réinventé.
     */
    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt", "id"));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
