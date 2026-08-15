package be.primatis.user.web;

import be.primatis.exception.ApiErrorResponse;
import be.primatis.user.UserService;
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
 * Contrat REST Users : lecture {@code USER_READ} (DEV-05.4, liste paginée +
 * détail), création administrative (DEV-05.5) et modification (DEV-05.6),
 * les deux dernières sous {@code USER_MANAGE}. Reste mince : mapping HTTP,
 * validation de forme, délégation à {@link UserService} — aucune logique
 * métier, aucune transaction ici (frontière transactionnelle portée par le
 * Service).
 *
 * L'autorisation ({@code USER_READ}/{@code USER_MANAGE}) est appliquée par
 * {@code @PreAuthorize} sur {@link UserService}, jamais ici.
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

    @Operation(
            summary = "Création administrative d'un utilisateur",
            description = "Crée un AppUser avec attribution explicite de rôles (USER_MANAGE requis). "
                    + "Le mot de passe initial et, si ROLE_MEMBER est sélectionné, le memberNumber "
                    + "sont générés par le backend et ne sont jamais fournis par le client.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Utilisateur créé."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email déjà utilisé, rôle inconnu "
                    + "ou données Membership incohérentes.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication,
            UriComponentsBuilder uriComponentsBuilder) {
        Long adminUserId = Long.valueOf(authentication.getName());
        CreateUserResponse response = userService.createUser(request, adminUserId);

        URI location = uriComponentsBuilder.replacePath("/api/v1/users/{id}")
                .buildAndExpand(response.user().id())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @Operation(
            summary = "Modification administrative d'un utilisateur",
            description = "Met à jour un AppUser existant (USER_MANAGE requis) : identité simple, "
                    + "rôles (remplacement intégral de l'ensemble si fourni) et données Membership "
                    + "d'un adhérent déjà existant. Ne modifie jamais email, accountStatus, "
                    + "memberStatus ni memberNumber.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Utilisateur mis à jour."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun utilisateur pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Rôle inconnu, ensemble de rôles vide "
                    + "ou données Membership incohérentes.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    public UserResponse updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return userService.updateUser(id, request, adminUserId);
    }
}
