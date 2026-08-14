package be.primatis.config;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller strictement réservé aux tests de {@code SecurityFilterChain}
 * (scope src/test, DEV-03.8/DEV-03.10). Expose des routes correspondant
 * exactement aux patterns configurés (login/titles/articles/protected) pour
 * prouver concrètement le comportement des matchers — jamais un Controller
 * métier de production.
 */
@RestController
class SecurityFilterChainTestController {

    @PostMapping("/api/v1/auth/login")
    public String login() {
        return "login-ok";
    }

    @GetMapping("/api/v1/titles/sample")
    public String getTitles() {
        return "titles-ok";
    }

    @PostMapping("/api/v1/titles/sample")
    public String createTitle() {
        return "titles-created";
    }

    @GetMapping("/api/v1/articles/sample")
    public String getArticles() {
        return "articles-ok";
    }

    @PatchMapping("/api/v1/articles/sample")
    public String patchArticle() {
        return "articles-updated";
    }

    @GetMapping("/api/v1/protected/sample")
    public String protectedSample() {
        return "protected-ok";
    }

    /**
     * Expose les authorities reconstruites par Spring Security à partir du
     * JWT, pour vérifier directement la conversion roles/permissions (pas
     * de @PreAuthorize métier ici, hors périmètre DEV-03.8 — juste une
     * lecture de l'Authentication déjà construite par le filtre).
     */
    @GetMapping("/api/v1/protected/authorities")
    public List<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    /**
     * Route protégée par Method Security (DEV-03.9/03.10) — permet de
     * prouver que le refus {@code @PreAuthorize} (levé après l'entrée dans
     * Spring MVC, jamais vu par {@code ExceptionTranslationFilter}) produit
     * bien le même contrat public {@code 403 / ACCESS_DENIED} qu'un refus
     * de la SecurityFilterChain, via le
     * {@code @ExceptionHandler(AccessDeniedException.class)} dédié de
     * {@code GlobalExceptionHandler}.
     */
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    @GetMapping("/api/v1/protected/loan-manage-only")
    public String loanManageOnly() {
        return "loan-manage-ok";
    }

    /**
     * Réservée aux permissions d'administration (DEV-03.11 : seul
     * ROLE_ADMIN possède ROLE_MANAGE) — utilisée par DEV-03.13 pour prouver
     * qu'un ROLE_LIBRARIAN/ROLE_MEMBER réellement authentifié via un JWT
     * bootstrapé se voit refuser cette opération, tandis qu'un ROLE_ADMIN
     * y est autorisé grâce à la permission réellement présente dans son
     * JWT (jamais un contournement codé).
     */
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @GetMapping("/api/v1/protected/role-manage-only")
    public String roleManageOnly() {
        return "role-manage-ok";
    }
}
