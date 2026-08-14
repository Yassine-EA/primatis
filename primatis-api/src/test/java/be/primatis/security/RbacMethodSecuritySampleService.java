package be.primatis.security;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Service strictement réservé aux tests de Method Security (DEV-03.9,
 * scope src/test). Aucun Service métier PRIMATIS (Catalogue, Loan,
 * Reservation, Fine, Article, User, Role, Setting) n'existe encore : cette
 * classe ne les anticipe pas et ne code aucune règle métier réelle — elle
 * prouve uniquement le mécanisme {@code @PreAuthorize("hasAuthority(...)")}
 * avec les codes de permission canoniques (PRIMATIS_CONTEXT_DEV_v1.0 §6.3)
 * et documente la convention à appliquer lorsque les Services métier
 * seront réellement implémentés dans les phases suivantes.
 *
 * Convention retenue :
 * <ul>
 *   <li>{@code hasAuthority('<PERMISSION_CODE>')} est le mécanisme
 *   principal — jamais {@code hasRole(...)} pour une règle métier, jamais
 *   de contournement codé pour {@code ROLE_ADMIN} (ses permissions
 *   proviennent uniquement du RBAC, comme tout autre rôle) ;</li>
 *   <li>les parcours personnels (mes Loans, mes Reservations...) ne créent
 *   jamais de permission {@code *_SELF} : ownership vérifiée explicitement
 *   dans le Service à partir de l'identité authentifiée
 *   ({@code Authentication.getName()}, qui correspond au claim JWT
 *   {@code sub} = {@code AppUser.id}), cf. §6.6.</li>
 * </ul>
 */
@Service
@Profile("test")
public class RbacMethodSecuritySampleService {

    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    public String manageLoan() {
        return "loan-managed";
    }

    @PreAuthorize("hasAuthority('CATALOGUE_READ')")
    public String readCatalogue() {
        return "catalogue-read";
    }

    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    public String manageCatalogue() {
        return "catalogue-managed";
    }

    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    public String manageArticle() {
        return "article-managed";
    }

    @PreAuthorize("hasAuthority('ARTICLE_PUBLISH')")
    public String publishArticle() {
        return "article-published";
    }

    /**
     * Volontairement sans {@code @PreAuthorize} : illustre §6.6 (ownership
     * Member) — la protection ne provient pas d'une permission mais d'une
     * vérification explicite d'ownership dans le Service, à partir de
     * l'identité authentifiée.
     */
    public String readOwnLoans(Long ownerId, Authentication authentication) {
        String authenticatedUserId = authentication.getName();
        if (!String.valueOf(ownerId).equals(authenticatedUserId)) {
            throw new AccessDeniedException("L'utilisateur authentifié n'est pas propriétaire de cette ressource.");
        }
        return "own-loans-of-" + ownerId;
    }
}
