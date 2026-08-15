package be.primatis.user.web;

import be.primatis.exception.ApiErrorResponse;
import be.primatis.user.MeProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrat REST du profil personnel (DEV-05.9) : {@code GET}/{@code PATCH
 * /api/v1/me/profile}, authentification seule — aucune permission, ownership
 * structurelle (DEV-05.9-DEC-10). Reste mince : mapping HTTP, délégation à
 * {@link MeProfileService} — aucune logique métier ici, aucune transaction
 * (frontière transactionnelle portée par le Service), même convention que
 * {@code UserController}/{@code ResidenceController}.
 */
@RestController
public class MeProfileController {

    private final MeProfileService meProfileService;

    public MeProfileController(MeProfileService meProfileService) {
        this.meProfileService = meProfileService;
    }

    @Operation(
            summary = "Profil personnel de l'utilisateur authentifié",
            description = "Retourne le profil de l'utilisateur authentifié. Aucun paramètre : "
                    + "l'identité est dérivée du JWT, jamais d'un identifiant client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil personnel."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun utilisateur pour l'identité authentifiée.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/me/profile")
    public MeProfileResponse getOwnProfile(Authentication authentication) {
        return meProfileService.getOwnProfile(currentUserId(authentication));
    }

    @Operation(
            summary = "Modification du profil personnel (téléphone uniquement)",
            description = "Met à jour phoneNumber pour l'utilisateur authentifié (sparse PATCH). "
                    + "firstName/lastName/email/accountStatus/données Membership restent en lecture seule. "
                    + "Champ absent, null, vide ou blanc laisse le numéro existant inchangé.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil personnel actualisé."),
            @ApiResponse(responseCode = "400", description = "phoneNumber structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun utilisateur pour l'identité authentifiée.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/api/v1/me/profile")
    public MeProfileResponse updateOwnProfile(
            @Valid @RequestBody UpdateMeProfileRequest request, Authentication authentication) {
        return meProfileService.updateOwnProfile(currentUserId(authentication), request);
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
