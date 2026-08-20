package be.primatis.reservation.web;

import be.primatis.exception.ApiErrorResponse;
import be.primatis.reservation.ReservationService;
import be.primatis.reservation.dto.CreateOwnReservationRequest;
import be.primatis.reservation.dto.CreateReservationRequest;
import be.primatis.reservation.dto.ReservationResponse;
import be.primatis.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrat REST des Reservations : consultation (DEV-08.4, parcours staff
 * {@code /api/v1/reservations} {@code RESERVATION_READ} et parcours
 * personnel {@code /api/v1/me/reservations}, authentification seule —
 * ownership structurelle), création (DEV-08.5, DEV-DEC-0037) et annulation
 * (DEV-08.6, DEV-DEC-0038), même convention exacte que
 * {@code loan.web.LoanController}. Reste mince : mapping HTTP, validation
 * de forme (pagination, {@code @Valid}), délégation à
 * {@link ReservationService} — aucune logique métier ici. L'autorisation
 * ({@code RESERVATION_READ}/{@code RESERVATION_MANAGE}) est appliquée par
 * {@code @PreAuthorize} sur {@link ReservationService}, jamais ici.
 *
 * <p>Aucun {@code GET /api/v1/reservations/{id}} : {@link ReservationResponse}
 * expose déjà tout le contenu d'une consultation détail, sans besoin
 * démontré d'un endpoint dédié à ce stade (OD-DEV08-05, DEV-DEC-0036, même
 * raisonnement que {@code LoanController}/DEV-DEC-0031). Aucun endpoint
 * d'expiration automatique, aucun {@code DELETE}, aucun {@code PATCH}
 * générique — strictement hors périmètre DEV-08.6, différé à DEV-08.7 pour
 * l'expiration.
 */
@RestController
@Validated
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(
            summary = "Liste paginée de toutes les réservations",
            description = "Retourne une page de réservations, tous membres confondus (RESERVATION_READ requis). "
                    + "Tri par date de réservation décroissante. Pagination 0-based, taille par défaut 20, maximum 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de réservations."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission RESERVATION_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/reservations")
    public PageResponse<ReservationResponse> listReservations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(reservationService.listReservations(pageable(page, size)));
    }

    @Operation(
            summary = "Réservations de l'utilisateur authentifié",
            description = "Retourne une page des réservations de l'utilisateur authentifié, historique inclus. "
                    + "Aucun identifiant client : l'utilisateur est dérivé du JWT. Aucune permission requise. "
                    + "Page vide (jamais 404) si l'utilisateur n'a aucune réservation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de réservations de l'utilisateur authentifié."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/me/reservations")
    public PageResponse<ReservationResponse> listOwnReservations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return PageResponse.from(
                reservationService.listOwnReservations(currentUserId(authentication), pageable(page, size)));
    }

    @Operation(
            summary = "Création self-service d'une réservation",
            description = "Crée une Reservation WAITING sur un Title pour l'utilisateur authentifié (DEV-DEC-0037). "
                    + "Aucun identifiant utilisateur dans le corps de la requête : l'identité est dérivée du JWT. "
                    + "Vérifie l'adhésion active, l'existence du Title, l'absence de Copy immédiatement disponible, "
                    + "l'absence de réservation active existante pour ce Title et la limite de réservations actives.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Réservation créée."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Titre introuvable.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Membre inéligible, exemplaire disponible, "
                    + "réservation déjà active ou limite atteinte.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/me/reservations")
    public ResponseEntity<ReservationResponse> createOwnReservation(
            @Valid @RequestBody CreateOwnReservationRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.createOwnReservation(currentUserId(authentication), request));
    }

    @Operation(
            summary = "Création staff d'une réservation",
            description = "Crée une Reservation WAITING sur un Title pour un membre désigné (RESERVATION_MANAGE "
                    + "requis, DEV-DEC-0037). Vérifie l'adhésion active du membre désigné, l'existence du Title, "
                    + "l'absence de Copy immédiatement disponible, l'absence de réservation active existante pour "
                    + "ce Title et la limite de réservations actives.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Réservation créée."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission RESERVATION_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur ou titre introuvable.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Membre inéligible, exemplaire disponible, "
                    + "réservation déjà active ou limite atteinte.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/reservations")
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(request));
    }

    @Operation(
            summary = "Annulation self-service d'une réservation",
            description = "Annule une Reservation WAITING ou READY appartenant à l'utilisateur authentifié "
                    + "(DEV-DEC-0038). Aucune permission requise. Une réservation n'appartenant pas à l'appelant "
                    + "est traitée comme introuvable (404), jamais distinguée d'une réservation inexistante. "
                    + "Statuts terminaux (FULFILLED/CANCELLED/EXPIRED) refusés (409). Aucune suppression physique.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation annulée."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Réservation introuvable ou n'appartenant pas à "
                    + "l'utilisateur authentifié.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Réservation non annulable dans son statut actuel.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/me/reservations/{reservationId}/cancel")
    public ReservationResponse cancelOwnReservation(
            @PathVariable Long reservationId, Authentication authentication) {
        return reservationService.cancelOwnReservation(currentUserId(authentication), reservationId);
    }

    @Operation(
            summary = "Annulation staff d'une réservation",
            description = "Annule une Reservation WAITING ou READY, quel qu'en soit le propriétaire "
                    + "(RESERVATION_MANAGE requis, DEV-DEC-0038). Statuts terminaux "
                    + "(FULFILLED/CANCELLED/EXPIRED) refusés (409). Aucune suppression physique.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation annulée."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission RESERVATION_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Réservation introuvable.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Réservation non annulable dans son statut actuel.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/reservations/{reservationId}/cancel")
    public ReservationResponse cancelReservation(@PathVariable Long reservationId) {
        return reservationService.cancelReservation(reservationId);
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reservationDate", "id"));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
