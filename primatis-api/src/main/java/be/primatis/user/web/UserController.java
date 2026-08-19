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
 * détail), création administrative (DEV-05.5), modification (DEV-05.6) et
 * transitions {@code AccountStatus}/{@code MemberStatus} (DEV-05.7) — ces
 * six dernières opérations sous {@code USER_MANAGE}. Reste mince : mapping
 * HTTP, validation de forme, délégation à {@link UserService} — aucune
 * logique métier, aucune transaction ici (frontière transactionnelle
 * portée par le Service).
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
                    + "Pagination 0-based, taille par défaut 20, maximum 100. "
                    + "Paramètre optionnel q (DEV-07.9.1) : recherche insensible à la casse, "
                    + "par sous-chaîne, sur memberNumber/firstName/lastName/email. "
                    + "q absent ou blanc conserve exactement le comportement paginé existant.")
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
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String q) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return PageResponse.from(userService.listUsers(pageable, q));
    }

    @Operation(
            summary = "Détail d'un utilisateur",
            description = "Retourne un utilisateur et ses rôles actuels par son identifiant "
                    + "technique (USER_READ requis).")
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
    public UserDetailResponse getUserById(@PathVariable Long id) {
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

    @Operation(
            summary = "Changement d'AccountStatus",
            description = "AccountStatus ACTIVE ⇄ DISABLED (USER_MANAGE requis). Idempotent : "
                    + "appliquer le statut déjà courant est un succès sans effet de bord. N'affecte "
                    + "jamais MemberStatus. DISABLED interdit l'authentification future ; un JWT déjà "
                    + "émis reste valide jusqu'à son expiration naturelle (aucune révocation JWT "
                    + "dans PRIMATIS).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AccountStatus appliqué (transition ou idempotent)."),
            @ApiResponse(responseCode = "400", description = "status absent ou valeur inconnue.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun utilisateur pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{id}/account-status")
    public UserResponse updateAccountStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateAccountStatusRequest request) {
        return userService.updateAccountStatus(id, request);
    }

    @Operation(
            summary = "Blocage d'un adhérent (ou mise à jour du motif)",
            description = "MemberStatus → BLOCKED (USER_MANAGE requis), depuis ACTIVE ou EXPIRED. "
                    + "Idempotent sur le statut si déjà BLOCKED : remplace alors uniquement "
                    + "blockedReason. blockedReason obligatoire. Réservé aux utilisateurs ayant déjà "
                    + "été adhérents.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adhérent bloqué (ou motif mis à jour)."),
            @ApiResponse(responseCode = "400", description = "blockedReason manquant ou vide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun utilisateur pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Utilisateur n'ayant jamais été adhérent.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{id}/membership/block")
    public UserResponse blockMember(@PathVariable Long id, @Valid @RequestBody BlockMembershipRequest request) {
        return userService.blockMember(id, request);
    }

    @Operation(
            summary = "Déblocage d'un adhérent",
            description = "MemberStatus BLOCKED → ACTIVE ou EXPIRED selon memberExpirationDate "
                    + "(USER_MANAGE requis). blockedReason systématiquement effacé. Toujours "
                    + "autorisé depuis BLOCKED, jamais refusé pour cause d'expiration.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adhérent débloqué."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun utilisateur pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Adhérent non bloqué ou utilisateur "
                    + "n'ayant jamais été adhérent.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{id}/membership/unblock")
    public UserResponse unblockMember(@PathVariable Long id) {
        return userService.unblockMember(id);
    }

    @Operation(
            summary = "Réactivation d'une adhésion expirée",
            description = "MemberStatus EXPIRED → ACTIVE (USER_MANAGE requis), uniquement si "
                    + "memberExpirationDate n'est plus dans le passé. Ne renouvelle jamais la date "
                    + "elle-même (PATCH /api/v1/users/{id} au préalable si nécessaire) : le "
                    + "renouvellement complet reste hors scope V1.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adhésion réactivée."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission USER_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Aucun utilisateur pour cet identifiant.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Adhérent non expiré, memberExpirationDate "
                    + "toujours dans le passé, ou utilisateur n'ayant jamais été adhérent.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{id}/membership/reactivate")
    public UserResponse reactivateMembership(@PathVariable Long id) {
        return userService.reactivateMembership(id);
    }
}
