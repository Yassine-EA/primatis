package be.primatis.loan.web;

import be.primatis.exception.ApiErrorResponse;
import be.primatis.loan.LoanService;
import be.primatis.loan.dto.CreateLoanRequest;
import be.primatis.loan.dto.LoanResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrat REST des Loans : consultation (DEV-07.4, parcours staff {@code
 * /api/v1/loans} {@code LOAN_READ} et parcours personnel {@code
 * /api/v1/me/loans}, authentification seule — ownership structurelle) et
 * enregistrement (DEV-07.5, {@code POST /api/v1/loans}, {@code
 * LOAN_MANAGE}), même convention que {@code UserController}/{@code
 * ResidenceController}. Reste mince : mapping HTTP, validation de forme
 * (pagination, {@code @Valid}), délégation à {@link LoanService} — aucune
 * logique métier ici. L'autorisation ({@code LOAN_READ}/{@code
 * LOAN_MANAGE}) est appliquée par {@code @PreAuthorize} sur
 * {@link LoanService}, jamais ici.
 *
 * Aucun {@code GET /api/v1/loans/{id}} : {@link LoanResponse} (DEV-07.3)
 * expose déjà tout le contenu d'une consultation détail, sans besoin
 * démontré d'un endpoint dédié à ce stade — {@code POST /api/v1/loans}
 * retourne donc {@code 201} sans en-tête {@code Location} (aucune
 * ressource adressable en GET à référencer).
 */
@RestController
@Validated
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @Operation(
            summary = "Liste paginée de tous les prêts",
            description = "Retourne une page de prêts, tous emprunteurs confondus (LOAN_READ requis). "
                    + "Tri par date de prêt décroissante. Pagination 0-based, taille par défaut 20, maximum 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de prêts."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission LOAN_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/loans")
    public PageResponse<LoanResponse> listLoans(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(loanService.listLoans(pageable(page, size)));
    }

    @Operation(
            summary = "Prêts de l'utilisateur authentifié",
            description = "Retourne une page des prêts de l'utilisateur authentifié, historique inclus. "
                    + "Aucun identifiant client : l'utilisateur est dérivé du JWT. Aucune permission requise. "
                    + "Page vide (jamais 404) si l'utilisateur n'a aucun prêt.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de prêts de l'utilisateur authentifié."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/me/loans")
    public PageResponse<LoanResponse> listOwnLoans(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return PageResponse.from(loanService.listOwnLoans(currentUserId(authentication), pageable(page, size)));
    }

    @Operation(
            summary = "Enregistrement d'un prêt",
            description = "Crée un Loan pour un bénéficiaire et un exemplaire donnés (LOAN_MANAGE requis). "
                    + "Vérifie l'éligibilité complète du bénéficiaire (compte, adhésion, amendes impayées, "
                    + "limite de prêts actifs) et la disponibilité de l'exemplaire, puis fait transiter "
                    + "l'exemplaire vers ON_LOAN. Si l'exemplaire est RESERVED pour ce bénéficiaire, la "
                    + "Reservation READY correspondante est automatiquement satisfaite (FULFILLED).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Prêt créé."),
            @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission LOAN_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur ou exemplaire introuvable.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Bénéficiaire inéligible ou exemplaire non prêtable.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/loans")
    public ResponseEntity<LoanResponse> registerLoan(@Valid @RequestBody CreateLoanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.registerLoan(request));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "loanDate", "id"));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
