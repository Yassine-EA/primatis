package be.primatis.user.web;

import be.primatis.exception.ApiErrorResponse;
import be.primatis.user.UserService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrat REST {@code USER_READ} (DEV-05.4) : liste paginée et détail d'un
 * {@code AppUser}. Reste mince : mapping HTTP, validation des paramètres de
 * pagination, délégation à {@link UserService} — aucune logique métier,
 * aucune transaction ici (frontière transactionnelle portée par le
 * Service).
 *
 * L'autorisation {@code USER_READ} est appliquée par {@code @PreAuthorize}
 * sur {@link UserService}, jamais ici.
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "Liste paginée des utilisateurs",
            description = "Retourne une page d'utilisateurs (USER_READ requis). "
                    + "Pagination 0-based, taille par défaut 20, maximum 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'utilisateurs."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public PageResponse<UserResponse> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return PageResponse.from(userService.listUsers(pageable));
    }

    @Operation(
            summary = "Détail d'un utilisateur",
            description = "Retourne un utilisateur par son identifiant technique (USER_READ requis).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Utilisateur trouvé."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun utilisateur pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }
}
