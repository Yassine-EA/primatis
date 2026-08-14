package be.primatis.security;

import be.primatis.access.Permission;
import be.primatis.access.Role;
import be.primatis.access.RolePermission;
import be.primatis.access.RolePermissionId;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Vérifie contre PostgreSQL réel (primatis_test) le chargement RBAC
 * DEV-03.5 : AppUser → UserRole → Role → RolePermission → Permission.
 *
 * hibernate.generate_statistics est activé uniquement pour cette classe
 * (contexte Spring dédié, comme FlywaySchemaRebuildTests en DEV-02.7) afin
 * de prouver l'absence de N+1 sur le chargement des authorities.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class PrimatisUserDetailsServiceTests {

    @Autowired
    private PrimatisUserDetailsService userDetailsService;

    @PersistenceContext
    private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger().addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        rootLogger().detachAppender(logAppender);
    }

    @Test
    void loadsExistingAppUserByEmail() {
        AppUser user = persistUser("member-load@primatis.test", AccountStatus.ACTIVE, null, "hash-not-relevant");
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("member-load@primatis.test");

        assertThat(principal).isInstanceOf(PrimatisUserPrincipal.class);
        PrimatisUserPrincipal primatisPrincipal = (PrimatisUserPrincipal) principal;
        assertThat(primatisPrincipal.getUserId()).isEqualTo(user.getId());
        assertThat(primatisPrincipal.getUsername()).isEqualTo("member-load@primatis.test");
    }

    @Test
    void unknownEmailThrowsUsernameNotFoundException() {
        assertThatExceptionOfType(UsernameNotFoundException.class)
                .isThrownBy(() -> userDetailsService.loadUserByUsername("absent@primatis.test"));
    }

    @Test
    void passwordHashIsTransportedAndNeverLogged() {
        String hash = "{bcrypt}$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUV1234567";
        persistUser("password-transport@primatis.test", AccountStatus.ACTIVE, null, hash);
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("password-transport@primatis.test");

        assertThat(principal.getPassword()).isEqualTo(hash);
        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(hash);
        }
    }

    @Test
    void activeAccountStatusIsEnabled() {
        persistUser("active-account@primatis.test", AccountStatus.ACTIVE, null, "hash");
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("active-account@primatis.test");

        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    void disabledAccountStatusIsDisabled() {
        persistUser("disabled-account@primatis.test", AccountStatus.DISABLED, null, "hash");
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("disabled-account@primatis.test");

        assertThat(principal.isEnabled()).isFalse();
    }

    @Test
    void blockedMemberStatusDoesNotDisablePrincipal() {
        persistUser("blocked-member@primatis.test", AccountStatus.ACTIVE, MemberStatus.BLOCKED, "hash");
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("blocked-member@primatis.test");

        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    void roleIsPresentInAuthorities() {
        AppUser user = persistUser("role-only@primatis.test", AccountStatus.ACTIVE, null, "hash");
        Role role = persistRole("ROLE_MEMBER");
        persistUserRole(user, role);
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("role-only@primatis.test");

        assertThat(authorityStrings(principal)).containsExactly("ROLE_MEMBER");
    }

    @Test
    void permissionsOfRoleArePresentInAuthorities() {
        AppUser user = persistUser("role-with-permission@primatis.test", AccountStatus.ACTIVE, null, "hash");
        Role role = persistRole("ROLE_LIBRARIAN");
        Permission permission = persistPermission("CATALOGUE_READ");
        persistUserRole(user, role);
        persistRolePermission(role, permission);
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("role-with-permission@primatis.test");

        assertThat(authorityStrings(principal)).containsExactlyInAnyOrder("ROLE_LIBRARIAN", "CATALOGUE_READ");
    }

    @Test
    void multipleRolesProduceUnionOfPermissions() {
        AppUser user = persistUser("multi-role@primatis.test", AccountStatus.ACTIVE, null, "hash");
        Role librarian = persistRole("ROLE_LIBRARIAN");
        Role admin = persistRole("ROLE_ADMIN");
        Permission catalogueRead = persistPermission("CATALOGUE_READ");
        Permission articlePublish = persistPermission("ARTICLE_PUBLISH");
        persistUserRole(user, librarian);
        persistUserRole(user, admin);
        persistRolePermission(librarian, catalogueRead);
        persistRolePermission(admin, articlePublish);
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("multi-role@primatis.test");

        assertThat(authorityStrings(principal)).containsExactlyInAnyOrder(
                "ROLE_LIBRARIAN", "ROLE_ADMIN", "CATALOGUE_READ", "ARTICLE_PUBLISH");
    }

    @Test
    void permissionGrantedByMultipleRolesIsDeduplicated() {
        AppUser user = persistUser("dedup-permission@primatis.test", AccountStatus.ACTIVE, null, "hash");
        Role librarian = persistRole("ROLE_LIBRARIAN");
        Role admin = persistRole("ROLE_ADMIN");
        Permission catalogueRead = persistPermission("CATALOGUE_READ");
        persistUserRole(user, librarian);
        persistUserRole(user, admin);
        persistRolePermission(librarian, catalogueRead);
        persistRolePermission(admin, catalogueRead);
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("dedup-permission@primatis.test");

        List<String> catalogueReadOccurrences = authorityStrings(principal).stream()
                .filter("CATALOGUE_READ"::equals)
                .toList();
        assertThat(catalogueReadOccurrences).hasSize(1);
        assertThat(authorityStrings(principal)).containsExactlyInAnyOrder(
                "ROLE_LIBRARIAN", "ROLE_ADMIN", "CATALOGUE_READ");
    }

    @Test
    void noArtificialAuthoritiesAreInvented() {
        AppUser user = persistUser("no-artificial@primatis.test", AccountStatus.ACTIVE, null, "hash");
        Role member = persistRole("ROLE_MEMBER");
        Permission catalogueRead = persistPermission("CATALOGUE_READ");
        persistUserRole(user, member);
        persistRolePermission(member, catalogueRead);
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("no-artificial@primatis.test");

        // Preuve par exhaustivité : le jeu d'authorities est EXACTEMENT ce
        // qui a été fixturé, ce qui exclut par construction ROLE_VISITOR,
        // toute permission *_SELF, ou tout autre ajout non demandé.
        assertThat(authorityStrings(principal)).containsExactlyInAnyOrder("ROLE_MEMBER", "CATALOGUE_READ");
        assertThat(authorityStrings(principal)).doesNotContain("ROLE_VISITOR");
        assertThat(authorityStrings(principal)).noneMatch(authority -> authority.endsWith("_SELF"));
    }

    @Test
    void loadingUserWithAuthoritiesDoesNotIntroduceNPlusOneQueries() {
        AppUser user = persistUser("n-plus-one@primatis.test", AccountStatus.ACTIVE, null, "hash");
        Role librarian = persistRole("ROLE_LIBRARIAN");
        Role admin = persistRole("ROLE_ADMIN");
        Permission catalogueRead = persistPermission("CATALOGUE_READ");
        Permission articlePublish = persistPermission("ARTICLE_PUBLISH");
        Permission loanManage = persistPermission("LOAN_MANAGE");
        persistUserRole(user, librarian);
        persistUserRole(user, admin);
        persistRolePermission(librarian, catalogueRead);
        persistRolePermission(librarian, articlePublish);
        persistRolePermission(admin, loanManage);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        userDetailsService.loadUserByUsername("n-plus-one@primatis.test");

        // Une requête pour findByEmail + une requête pour la chaîne RBAC
        // jointe = 2 appels JDBC, indépendamment du nombre de rôles/
        // permissions attachés à l'utilisateur.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    private List<String> authorityStrings(UserDetails principal) {
        return principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private AppUser persistUser(String email, AccountStatus accountStatus, MemberStatus memberStatus, String passwordHash) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(accountStatus);
        user.setMemberStatus(memberStatus);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        entityManager.persist(user);
        return user;
    }

    private Role persistRole(String code) {
        // Le code est utilisé tel quel (pas de suffixe d'unicité) : les
        // assertions comparent les authorities à des libellés exacts
        // (ROLE_MEMBER, ROLE_LIBRARIAN...). Chaque test s'exécute dans sa
        // propre transaction annulée (@Transactional) : aucune collision
        // entre méthodes de test.
        Role role = new Role();
        role.setName("Nom " + code);
        role.setCode(code);
        role.setCreatedAt(Instant.now());
        role.setUpdatedAt(Instant.now());
        entityManager.persist(role);
        return role;
    }

    private Permission persistPermission(String code) {
        Permission permission = new Permission();
        permission.setName("Nom " + code);
        permission.setCode(code);
        permission.setCreatedAt(Instant.now());
        permission.setUpdatedAt(Instant.now());
        entityManager.persist(permission);
        return permission;
    }

    private void persistUserRole(AppUser user, Role role) {
        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), role.getId()));
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(Instant.now());
        entityManager.persist(userRole);
    }

    private void persistRolePermission(Role role, Permission permission) {
        RolePermission rolePermission = new RolePermission();
        rolePermission.setId(new RolePermissionId(role.getId(), permission.getId()));
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        rolePermission.setAssignedAt(Instant.now());
        entityManager.persist(rolePermission);
    }

    private Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
