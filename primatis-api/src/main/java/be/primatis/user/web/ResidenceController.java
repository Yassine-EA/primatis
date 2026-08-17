package be.primatis.user.web;

import be.primatis.exception.ApiErrorResponse;
import be.primatis.user.ResidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contrat REST Address/Residence (DEV-05.8) : parcours personnel ({@code
 * /api/v1/me/residence}, authentification seule — ownership structurelle) et
 * parcours staff ({@code /api/v1/users/{id}/residence(s)}, {@code
 * USER_READ}/{@code USER_PROFILE_MANAGE}). Reste mince : mapping HTTP,
 * délégation à {@link ResidenceService} — aucune logique métier ici,
 * aucune transaction (frontière portée par le Service). L'autorisation est
 * appliquée par {@code @PreAuthorize} sur {@link ResidenceService}, jamais
 * ici — même convention que {@code UserController}.
 *
 * Contrat figé DEV-05.8-DEC-09 : aucun endpoint hors de cette liste (pas de
 * {@code POST} de création libre de Residence historique).
 */
@RestController
@Validated
public class ResidenceController {

    private final ResidenceService residenceService;

    public ResidenceController(ResidenceService residenceService) {
        this.residenceService = residenceService;
    }

    @Operation(
            summary = "Résidence courante de l'utilisateur authentifié",
            description = "Retourne la résidence courante de l'utilisateur authentifié. "
                    + "Aucun paramètre : l'identité est dérivée du JWT, jamais d'un identifiant client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résidence courante."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucune résidence courante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/me/residence")
    public ResidenceResponse getOwnResidence(Authentication authentication) {
        return residenceService.getOwnCurrentResidence(currentUserId(authentication));
    }

    @Operation(
            summary = "Définition/remplacement de la résidence courante de l'utilisateur authentifié",
            description = "Crée la première résidence, ou remplace la résidence courante (l'ancienne est "
                    + "clôturée à la veille du jour courant et conservée dans l'historique). "
                    + "startDate/endDate ne sont jamais fournis par le client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résidence définie/remplacée."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ville inconnue pour cityId.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflit de période de résidence.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/api/v1/me/residence")
    public ResidenceResponse replaceOwnResidence(
            @Valid @RequestBody UpdateResidenceRequest request, Authentication authentication) {
        return residenceService.replaceOwnResidence(currentUserId(authentication), request);
    }

    @Operation(
            summary = "Résidence courante d'un utilisateur cible",
            description = "Retourne la résidence courante de l'utilisateur cible (USER_READ requis).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résidence courante."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur inconnu, ou aucune résidence courante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/users/{id}/residence")
    public ResidenceResponse getResidence(@PathVariable Long id) {
        return residenceService.getCurrentResidence(id);
    }

    @Operation(
            summary = "Historique des résidences d'un utilisateur cible",
            description = "Retourne l'historique complet des résidences de l'utilisateur cible, "
                    + "du plus récent au plus ancien (USER_READ requis). Liste vide si aucun historique.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique des résidences (peut être vide)."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur inconnu.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/users/{id}/residences")
    public List<ResidenceResponse> getResidenceHistory(@PathVariable Long id) {
        return residenceService.getResidenceHistory(id);
    }

    @Operation(
            summary = "Définition/remplacement de la résidence courante d'un utilisateur cible",
            description = "Crée la première résidence, ou remplace la résidence courante de l'utilisateur "
                    + "cible (USER_PROFILE_MANAGE requis — distinct de USER_MANAGE). L'ancienne résidence "
                    + "est clôturée à la veille du jour courant et conservée dans l'historique. "
                    + "startDate/endDate ne sont jamais fournis par le client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résidence définie/remplacée."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_PROFILE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur ou ville inconnu(e).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflit de période de résidence.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/api/v1/users/{id}/residence")
    public ResidenceResponse replaceResidence(
            @PathVariable Long id, @Valid @RequestBody UpdateResidenceRequest request) {
        return residenceService.replaceResidence(id, request);
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
