package be.primatis;

import be.primatis.access.Permission;
import be.primatis.access.PermissionRepository;
import be.primatis.access.Role;
import be.primatis.access.RolePermission;
import be.primatis.access.RoleRepository;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.security.PrimatisUserDetailsService;
import be.primatis.security.PrimatisUserPrincipal;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bootstrap RBAC obligatoire (DEV-03.11, migration {@code V002}) : 3 rôles
 * canoniques, 19 permissions canoniques V1, 35 associations RolePermission
 * — matrice fixée par PRIMATIS_CONTEXT_DEV_v1.0 §6.3/§6.4. PostgreSQL réel,
 * aucun H2/Testcontainers.
 *
 * {@code spring.flyway.clean-disabled=false} n'est activé QUE dans le
 * contexte Spring dédié à cette classe ({@code @TestPropertySource} local),
 * comme {@link FlywaySchemaRebuildTests} — mêmes garanties : aucune autre
 * classe de test ne partage ce réglage, {@code primatis_dev} n'est jamais
 * visé.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.clean-disabled=false")
@Transactional
class RbacBootstrapTests {

    private static final Set<String> EXPECTED_PERMISSION_CODES = Set.of(
            "CATALOGUE_READ", "CATALOGUE_MANAGE",
            "COPY_READ", "COPY_MANAGE",
            "LOAN_READ", "LOAN_MANAGE",
            "RESERVATION_READ", "RESERVATION_MANAGE",
            "FINE_READ", "FINE_MANAGE",
            "ARTICLE_READ", "ARTICLE_MANAGE", "ARTICLE_PUBLISH",
            "USER_READ", "USER_MANAGE",
            "ROLE_READ", "ROLE_MANAGE",
            "SETTING_READ", "SETTING_MANAGE");

    private static final Set<String> LIBRARIAN_PERMISSION_CODES = Set.of(
            "CATALOGUE_READ", "CATALOGUE_MANAGE",
            "COPY_READ", "COPY_MANAGE",
            "LOAN_READ", "LOAN_MANAGE",
            "RESERVATION_READ", "RESERVATION_MANAGE",
            "FINE_READ", "FINE_MANAGE",
            "ARTICLE_READ", "ARTICLE_MANAGE", "ARTICLE_PUBLISH",
            "USER_READ");

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PrimatisUserDetailsService userDetailsService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void rbacBootstrapDataIsExactlyCorrectAfterFreshMigration() {
        flyway.clean();
        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();

        // --- Role : exactement 3, jamais ROLE_VISITOR ---
        List<Role> roles = roleRepository.findAll();
        assertThat(roles).hasSize(3);
        Set<String> roleCodes = roles.stream().map(Role::getCode).collect(Collectors.toSet());
        assertThat(roleCodes).containsExactlyInAnyOrder("ROLE_MEMBER", "ROLE_LIBRARIAN", "ROLE_ADMIN");

        // --- Permission : exactement 19, aucune *_SELF ---
        List<Permission> permissions = permissionRepository.findAll();
        assertThat(permissions).hasSize(19);
        Set<String> permissionCodes = permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
        assertThat(permissionCodes).containsExactlyInAnyOrderElementsOf(EXPECTED_PERMISSION_CODES);
        assertThat(permissionCodes).noneMatch(code -> code.endsWith("_SELF"));

        // --- RolePermission : exactement 35, aucun doublon, FK valides ---
        List<RolePermission> associations = entityManager
                .createQuery("SELECT rp FROM RolePermission rp", RolePermission.class)
                .getResultList();
        assertThat(associations).hasSize(35);
        long distinctPairs = associations.stream()
                .map(rp -> rp.getRole().getCode() + "|" + rp.getPermission().getCode())
                .distinct()
                .count();
        assertThat(distinctPairs).isEqualTo(35);
        assertThat(associations).allSatisfy(rp -> {
            assertThat(rp.getRole()).isNotNull();
            assertThat(rp.getPermission()).isNotNull();
        });

        // --- Matrice exacte par rôle ---
        assertThat(permissionCodesOf(associations, "ROLE_MEMBER"))
                .containsExactlyInAnyOrder("CATALOGUE_READ", "ARTICLE_READ");
        assertThat(permissionCodesOf(associations, "ROLE_LIBRARIAN"))
                .containsExactlyInAnyOrderElementsOf(LIBRARIAN_PERMISSION_CODES);
        assertThat(permissionCodesOf(associations, "ROLE_LIBRARIAN"))
                .doesNotContain("USER_MANAGE", "ROLE_READ", "ROLE_MANAGE", "SETTING_READ", "SETTING_MANAGE");
        assertThat(permissionCodesOf(associations, "ROLE_ADMIN"))
                .containsExactlyInAnyOrderElementsOf(EXPECTED_PERMISSION_CODES);

        // --- Aucun bootstrap AppUser/UserRole par cette migration ---
        assertRowCount("app_user", 0);
        assertRowCount("user_role", 0);

        // --- application_setting non altéré (bootstrap V001, DEV-02) ---
        assertRowCount("application_setting", 4);

        // --- Hibernate ddl-auto=validate reste compatible : le contexte a
        // déjà démarré avec ces Entities contre ce schéma (sinon la
        // validation aurait échoué au démarrage) ; cette lecture confirme
        // en plus que le mapping JPA lit correctement les données réellement
        // bootstrapées par Flyway (pas seulement des fixtures de test).
        assertThat(roleRepository.findAll()).isNotEmpty();
    }

    /**
     * Preuve d'une véritable évolution V001 -> V002 (pas seulement une base
     * reconstruite directement jusqu'à latest) : migre d'abord uniquement
     * V001 (aucun bootstrap RBAC), vérifie l'absence de données RBAC, puis
     * complète jusqu'à V002 et vérifie leur apparition.
     */
    @Test
    void migratingFromV001AloneThenToV002IntroducesRbacBootstrap() {
        flyway.clean();

        Flyway v001Only = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("001")
                .load();
        MigrateResult v001Result = v001Only.migrate();
        assertThat(v001Result.success).isTrue();
        assertThat(v001Result.targetSchemaVersion).isEqualTo("001");
        assertRowCount("role", 0);
        assertRowCount("permission", 0);
        assertRowCount("role_permission", 0);

        MigrateResult latestResult = flyway.migrate();
        assertThat(latestResult.success).isTrue();
        assertThat(latestResult.migrationsExecuted).isEqualTo(1);
        assertThat(latestResult.targetSchemaVersion).isEqualTo("002");
        assertRowCount("role", 3);
        assertRowCount("permission", 19);
        assertRowCount("role_permission", 35);

        assertThat(flyway.info().all()).hasSize(2);
    }

    /**
     * Preuve que le bootstrap SQL (V002) et le chargement de sécurité
     * DEV-03.5 utilisent réellement le même contrat : AppUser → UserRole →
     * Role → RolePermission → Permission, jusqu'à
     * {@link PrimatisUserDetailsService}. Aucun compte permanent créé —
     * l'AppUser/UserRole de ce test vivent uniquement dans sa transaction
     * ({@code @Transactional}, annulée après le test) ; seul {@code ROLE_LIBRARIAN}
     * (déjà présent grâce au bootstrap, appliqué au démarrage normal du
     * contexte Spring) est réutilisé tel quel.
     */
    @Test
    void bootstrappedRoleLibrarianIsUsableThroughAuthenticationLoadingContract() {
        Role librarian = roleRepository.findByCode("ROLE_LIBRARIAN")
                .orElseThrow(() -> new IllegalStateException("Bootstrap RBAC manquant : ROLE_LIBRARIAN introuvable"));

        AppUser user = new AppUser();
        user.setEmail("librarian-coherence@primatis.test");
        user.setPasswordHash("hash-not-relevant");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        entityManager.persist(user);

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), librarian.getId()));
        userRole.setUser(user);
        userRole.setRole(librarian);
        userRole.setAssignedAt(Instant.now());
        entityManager.persist(userRole);
        entityManager.flush();

        UserDetails principal = userDetailsService.loadUserByUsername("librarian-coherence@primatis.test");
        assertThat(principal).isInstanceOf(PrimatisUserPrincipal.class);

        List<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Set<String> expectedAuthorities = new java.util.HashSet<>(LIBRARIAN_PERMISSION_CODES);
        expectedAuthorities.add("ROLE_LIBRARIAN");
        assertThat(authorities).containsExactlyInAnyOrderElementsOf(expectedAuthorities);
    }

    private Set<String> permissionCodesOf(List<RolePermission> associations, String roleCode) {
        return associations.stream()
                .filter(rp -> rp.getRole().getCode().equals(roleCode))
                .map(rp -> rp.getPermission().getCode())
                .collect(Collectors.toSet());
    }

    private void assertRowCount(String table, long expected) {
        long count = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM " + table)
                .getSingleResult()).longValue();
        assertThat(count).as("nombre de lignes dans " + table).isEqualTo(expected);
    }
}
