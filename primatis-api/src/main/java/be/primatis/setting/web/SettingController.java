package be.primatis.setting.web;

import be.primatis.exception.ApiErrorResponse;
import be.primatis.setting.ApplicationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contrat REST Application Settings (DEV-12.2) : consultation ({@code
 * SETTING_READ}) et modification de la seule valeur ({@code
 * SETTING_MANAGE}) des six paramètres métier globaux existants. Reste
 * mince : mapping HTTP, délégation à {@link ApplicationSettingService} —
 * aucune logique métier, aucune transaction ici.
 *
 * <p>Précédent architectural retenu (DEV-12.1 §11) : {@code UserController},
 * pas {@code Article}/{@code Catalogue} — Application Settings n'a aucun
 * consommateur Member/Librarian, un seul Controller sans préfixe {@code
 * /admin}/{@code /staff}. L'autorisation ({@code SETTING_READ}/{@code
 * SETTING_MANAGE}) est appliquée par {@code @PreAuthorize} sur {@link
 * ApplicationSettingService}, jamais ici.
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingController {

    private final ApplicationSettingService applicationSettingService;

    public SettingController(ApplicationSettingService applicationSettingService) {
        this.applicationSettingService = applicationSettingService;
    }

    @Operation(
            summary = "Liste des paramètres applicatifs",
            description = "Retourne les six paramètres métier globaux existants (SETTING_READ requis), "
                    + "triés par settingKey (ordre déterministe). Collection fixe et bornée : aucune "
                    + "pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des paramètres."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission SETTING_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public List<SettingResponse> listSettings() {
        return applicationSettingService.listSettings();
    }

    @Operation(
            summary = "Modification de la valeur d'un paramètre applicatif",
            description = "Modifie uniquement settingValue d'une clé existante (SETTING_MANAGE requis). "
                    + "settingKey, valueType et description restent inchangés. La nouvelle valeur est "
                    + "validée selon le valueType existant de la clé (entier ou décimal strictement "
                    + "positif) ; aucune borne supplémentaire, aucune cohérence croisée entre clés.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paramètre mis à jour."),
            @ApiResponse(responseCode = "400", description = "settingValue absent ou vide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission SETTING_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun paramètre pour cette clé.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Valeur incompatible avec le valueType de "
                    + "la clé (non numérique, non strictement positive, ou type non pris en charge).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{settingKey}")
    public SettingResponse updateSettingValue(
            @PathVariable String settingKey,
            @Valid @RequestBody UpdateSettingValueRequest request,
            Authentication authentication) {
        Long actingUserId = Long.valueOf(authentication.getName());
        return applicationSettingService.updateSettingValue(settingKey, request.settingValue(), actingUserId);
    }
}
