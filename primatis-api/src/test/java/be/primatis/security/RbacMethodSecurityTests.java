package be.primatis.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Preuve du mécanisme Method Security RBAC (DEV-03.9) : {@code
 * @EnableMethodSecurity} (SecurityConfig, DEV-03.8) intercepte réellement
 * les appels à {@link RbacMethodSecuritySampleService} via un proxy Spring
 * (AOP), et {@code @PreAuthorize("hasAuthority(...)")} applique strictement
 * les codes de permission canoniques (PRIMATIS_CONTEXT_DEV_v1.0 §6.3),
 * sans contournement codé pour {@code ROLE_ADMIN} et sans permission
 * {@code *_SELF} pour l'ownership (§6.6).
 *
 * L'Authentication est construite manuellement via
 * {@link SecurityContextHolder} ({@link TestingAuthenticationToken} —
 * classe de {@code spring-security-core}, aucune dépendance
 * {@code spring-security-test} nécessaire) plutôt que via un JWT signé :
 * l'objet de ces tests est l'autorisation Method Security elle-même, pas
 * la reconstruction JWT → GrantedAuthority déjà prouvée par
 * {@code SecurityFilterChainTests} (DEV-03.8).
 */
@SpringBootTest
@ActiveProfiles("test")
class RbacMethodSecurityTests {

    @Autowired
    private RbacMethodSecuritySampleService sampleService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String name, String... authorities) {
        List<GrantedAuthority> grantedAuthorities =
                Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        Authentication authentication = new TestingAuthenticationToken(name, null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Représente une requête sans JWT valide : Spring Security peuple par
     * défaut le contexte avec un {@link AnonymousAuthenticationToken}
     * (filtre anonyme actif, non désactivé dans SecurityConfig) plutôt
     * qu'une Authentication nulle — c'est le comportement réel observable
     * en production pour une requête non authentifiée.
     */
    private static void authenticateAsAnonymous() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
    }

    @Test
    void noAuthenticationDeniedForProtectedMethod() {
        authenticateAsAnonymous();
        assertThrows(AccessDeniedException.class, sampleService::manageLoan);
    }

    @Test
    void loanManageAuthorizedWithPermission() {
        authenticateAs("1", "LOAN_MANAGE");
        assertThat(sampleService.manageLoan()).isEqualTo("loan-managed");
    }

    @Test
    void loanManageDeniedWithoutPermission() {
        authenticateAs("1", "CATALOGUE_READ");
        assertThrows(AccessDeniedException.class, sampleService::manageLoan);
    }

    @Test
    void librarianRoleWithoutPermissionDenied() {
        authenticateAs("1", "ROLE_LIBRARIAN");
        assertThrows(AccessDeniedException.class, sampleService::manageLoan);
    }

    @Test
    void adminRoleWithoutPermissionDenied() {
        // ROLE_ADMIN seul, sans LOAN_MANAGE : aucun contournement codé pour Admin.
        authenticateAs("1", "ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, sampleService::manageLoan);
    }

    @Test
    void adminRoleWithPermissionAuthorized() {
        authenticateAs("1", "ROLE_ADMIN", "LOAN_MANAGE");
        assertThat(sampleService.manageLoan()).isEqualTo("loan-managed");
    }

    @Test
    void articleManageDoesNotImplyArticlePublish() {
        authenticateAs("1", "ARTICLE_MANAGE");
        assertThrows(AccessDeniedException.class, sampleService::publishArticle);
    }

    @Test
    void articlePublishAuthorizes() {
        authenticateAs("1", "ARTICLE_PUBLISH");
        assertThat(sampleService.publishArticle()).isEqualTo("article-published");
    }

    @Test
    void catalogueReadAuthorizesRepresentativeRead() {
        authenticateAs("1", "CATALOGUE_READ");
        assertThat(sampleService.readCatalogue()).isEqualTo("catalogue-read");
    }

    @Test
    void catalogueManageAuthorizesRepresentativeManageAndReadAloneDoesNot() {
        authenticateAs("1", "CATALOGUE_MANAGE");
        assertThat(sampleService.manageCatalogue()).isEqualTo("catalogue-managed");

        authenticateAs("1", "CATALOGUE_READ");
        assertThrows(AccessDeniedException.class, sampleService::manageCatalogue);
    }

    @Test
    void unknownPermissionGrantsNothing() {
        authenticateAs("1", "NOT_A_REAL_PERMISSION");
        assertThrows(AccessDeniedException.class, sampleService::manageLoan);
    }

    @Test
    void roleVisitorHasNoSpecialBehavior() {
        // ROLE_VISITOR n'est ni une permission ni un rôle RBAC réel (les
        // Visitors sont non authentifiés) : le présent uniquement pour
        // prouver l'absence de toute implication implicite.
        authenticateAs("1", "ROLE_VISITOR");
        assertThrows(AccessDeniedException.class, sampleService::readCatalogue);
    }

    @Test
    void ownershipDemonstratedWithoutSelfPermission() {
        authenticateAs("7");
        assertThat(sampleService.readOwnLoans(7L, SecurityContextHolder.getContext().getAuthentication()))
                .isEqualTo("own-loans-of-7");
        assertThrows(AccessDeniedException.class,
                () -> sampleService.readOwnLoans(99L, SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    void multipleAuthoritiesOnlyRequestedPermissionDecides() {
        authenticateAs("1", "CATALOGUE_READ", "FINE_READ", "SETTING_READ");
        assertThrows(AccessDeniedException.class, sampleService::manageLoan);

        authenticateAs("1", "CATALOGUE_READ", "FINE_READ", "SETTING_READ", "LOAN_MANAGE");
        assertThat(sampleService.manageLoan()).isEqualTo("loan-managed");
    }

    /**
     * Preuve directe que l'interception provient du proxy Spring construit
     * par {@code @EnableMethodSecurity}, et non d'un comportement implicite
     * de la classe elle-même : le bean injecté par le conteneur est bien un
     * proxy AOP, tandis qu'une instance brute ({@code new ...()}, hors
     * conteneur) n'est protégée par aucun mécanisme et exécute la méthode
     * sans la moindre vérification, quelle que soit l'Authentication (ou
     * son absence).
     */
    @Test
    void methodSecurityAppliesOnlyThroughSpringManagedProxy() {
        assertThat(AopUtils.isAopProxy(sampleService)).isTrue();

        SecurityContextHolder.clearContext();
        RbacMethodSecuritySampleService rawInstance = new RbacMethodSecuritySampleService();
        assertThat(rawInstance.manageLoan()).isEqualTo("loan-managed");
    }
}
