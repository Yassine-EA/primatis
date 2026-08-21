package be.primatis.notification.web;

import be.primatis.exception.ApiErrorResponse;
import be.primatis.notification.NotificationService;
import be.primatis.notification.dto.NotificationMarkAllAsReadResponse;
import be.primatis.notification.dto.NotificationResponse;
import be.primatis.notification.dto.NotificationUnreadCountResponse;
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
 * Contrat REST self-service de consultation et de gestion de lecture des
 * {@code Notification} de l'utilisateur authentifié (DEV-10.4), même
 * convention exacte que {@code fine.web.FineController}/{@code
 * loan.web.LoanController}/{@code reservation.web.ReservationController}.
 * Reste mince : mapping HTTP, validation de forme (pagination), délégation
 * à {@link NotificationService} — aucune logique métier ici.
 *
 * <p>Aucune permission ({@code NOTIFICATION_READ}/{@code
 * NOTIFICATION_MANAGE}) : self-service uniquement, ownership backend
 * (identité JWT), aucun {@code @PreAuthorize} — même principe que {@code
 * /me/loans}/{@code /me/reservations}/{@code /me/fines} (DEV-10.1 §10).
 * Aucun Controller staff Notification, aucun écran global (DEV-10.1 §10,
 * hors périmètre V1).
 */
@RestController
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(
            summary = "Notifications de l'utilisateur authentifié",
            description = "Retourne une page des Notifications de l'utilisateur authentifié, historique complet "
                    + "inclus (UNREAD/READ). Aucun identifiant client : l'utilisateur est dérivé du JWT. Aucune "
                    + "permission requise. Tri par date de création décroissante. Pagination 0-based, taille par "
                    + "défaut 20, maximum 100. Page vide (jamais 404) si l'utilisateur n'a aucune Notification.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de Notifications de l'utilisateur authentifié."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/me/notifications")
    public PageResponse<NotificationResponse> listOwnNotifications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return PageResponse.from(notificationService.listOwnNotifications(currentUserId(authentication), pageable(page, size)));
    }

    @Operation(
            summary = "Nombre de Notifications non lues de l'utilisateur authentifié",
            description = "Retourne le nombre de Notifications de l'utilisateur authentifié dont "
                    + "notificationStatus = UNREAD (DEV-DEC-0051). Aucune permission requise.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compteur UNREAD de l'utilisateur authentifié."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/me/notifications/unread-count")
    public NotificationUnreadCountResponse countUnreadOwnNotifications(Authentication authentication) {
        return new NotificationUnreadCountResponse(notificationService.countUnread(currentUserId(authentication)));
    }

    @Operation(
            summary = "Marquer une Notification comme lue",
            description = "Applique UNREAD -> READ sur une Notification appartenant à l'utilisateur authentifié et "
                    + "renseigne readAt. Idempotent : un second appel sur une Notification déjà READ réussit sans "
                    + "modifier readAt (première lecture matérialisée, jamais la plus récente). Aucune permission "
                    + "requise, ownership backend uniquement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification lue (nouvellement ou déjà lue)."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Notification introuvable ou n'appartenant pas à "
                    + "l'utilisateur authentifié.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/me/notifications/{notificationId}/read")
    public NotificationResponse markAsRead(@PathVariable Long notificationId, Authentication authentication) {
        return notificationService.markAsRead(currentUserId(authentication), notificationId);
    }

    @Operation(
            summary = "Marquer toutes les Notifications comme lues",
            description = "Applique UNREAD -> READ à toutes les Notifications de l'utilisateur authentifié "
                    + "(DEV-DEC-0052), un seul horodatage readAt partagé pour tout le lot. Idempotent : aucune "
                    + "Notification UNREAD restante est un succès (updatedCount = 0). Aucune permission requise, "
                    + "ownership backend uniquement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nombre de Notifications marquées comme lues."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/me/notifications/read-all")
    public NotificationMarkAllAsReadResponse markAllAsRead(Authentication authentication) {
        return notificationService.markAllAsRead(currentUserId(authentication));
    }

    /**
     * Tri par défaut {@code createdAt} décroissant + {@code id} en
     * tie-break déterministe — même précédent exact que {@code
     * LoanController}/{@code ReservationController}/{@code
     * FineController} (DEV-DEC-0031/0036, pattern reconduit à l'identique
     * pour {@code FineController}). DEV-10.1 §19 anticipait déjà cet ordre
     * pour la future API self-service Notification.
     */
    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
