package be.primatis.user.web;

import be.primatis.access.Role;
import be.primatis.access.RoleRepository;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.config.JwtProperties;
import be.primatis.security.AccessToken;
import be.primatis.security.AuthService;
import be.primatis.security.JwtService;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP {@code USER_READ} (DEV-05.4) : {@code GET /api/v1/users} et
 * {@code GET /api/v1/users/{id}}. JWT signés manuellement (même principe que
 * {@code SecurityFilterChainTests}) pour les scénarios anonymous/sans
 * permission/pagination ; connexion réelle avec rôles réellement
 * bootstrapés (V002, même principe que {@code AuthenticationRbacIntegrationTests})
 * pour prouver l'accès via {@code ROLE_LIBRARIAN}/{@code ROLE_ADMIN}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanupFixtureUsers() {
        transactionTemplate().executeWithoutResult(status -> {
            appUserRepository.findByEmail("controller-list@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("controller-detail@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("e2e-users-librarian@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("e2e-users-admin@primatis.test").ifPresent(this::deleteUserAndRoles);
            // Utilisateurs créés PAR les tests POST : supprimés avant l'admin
            // qui les a créés (fk_user_role_assigned_by ON DELETE RESTRICT).
            appUserRepository.findByEmail("controller-create-librarian@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("controller-create-member@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("controller-create-duplicate-target@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("controller-create-unknown-role@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("controller-create-empty-roles@primatis.test").ifPresent(this::deleteUserAndRoles);
            // Utilisateurs créés/modifiés PAR les tests PATCH : supprimés
            // avant l'admin qui les a créés/modifiés.
            appUserRepository.findByEmail("controller-update-target@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("controller-update-roles-target@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("controller-update-unknown-role-target@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("controller-update-partial-target@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("controller-update-phone-target@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("controller-update-null-firstname-target@primatis.test").ifPresent(this::deleteUserAndRoles);
            appUserRepository.findByEmail("e2e-users-create-admin@primatis.test").ifPresent(this::deleteUserAndRoles);
        });
    }

    // ---------------------------------------------------------------
    // GET /api/v1/users — liste
    // ---------------------------------------------------------------

    @Test
    void listUsersWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsersAuthenticatedWithoutUserReadIsForbidden() throws Exception {
        String token = signToken(List.of(), List.of("CATALOGUE_READ"));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listUsersWithUserReadReturnsDefaultPagedResponse() throws Exception {
        persistUser("controller-list@primatis.test");
        String token = signToken(List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listUsersWithExplicitPaginationIsRespected() throws Exception {
        String token = signToken(List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users?page=0&size=1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void listUsersRejectsNegativePage() throws Exception {
        String token = signToken(List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users?page=-1").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    @Test
    void listUsersRejectsZeroSize() throws Exception {
        String token = signToken(List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users?size=0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    @Test
    void listUsersRejectsSizeAboveMaximum() throws Exception {
        String token = signToken(List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users?size=101").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    /**
     * Distinct de {@link #listUsersRejectsNegativePage} : ici la conversion
     * de type échoue elle-même (Spring MVC binding), avant toute évaluation
     * {@code @Min}/{@code @Max} — {@code MethodArgumentTypeMismatchException},
     * pas {@code ConstraintViolationException}.
     */
    @Test
    void listUsersRejectsNonNumericPage() throws Exception {
        String token = signToken(List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users?page=abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.path").value("/api/v1/users"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("java.lang"))));
    }

    @Test
    void listUsersRejectsNonNumericSize() throws Exception {
        String token = signToken(List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users?size=abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.path").value("/api/v1/users"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("java.lang"))));
    }

    @Test
    void listUsersJsonNeverExposesSensitiveFields() throws Exception {
        persistUser("controller-list@primatis.test");
        String token = signToken(List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='controller-list@primatis.test')].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.email=='controller-list@primatis.test')].failedLoginCount").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.email=='controller-list@primatis.test')].lockedUntil").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.email=='controller-list@primatis.test')].lastLoginAt").doesNotExist());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/users/{id} — détail
    // ---------------------------------------------------------------

    @Test
    void getUserByIdWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserByIdAuthenticatedWithoutUserReadIsForbidden() throws Exception {
        AppUser user = persistUser("controller-detail@primatis.test");
        String token = signToken(List.of(), List.of("CATALOGUE_READ"));

        mockMvc.perform(get("/api/v1/users/" + user.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void getUserByIdWithUserReadAndExistingUserReturnsOk() throws Exception {
        AppUser user = persistUser("controller-detail@primatis.test");
        String token = signToken(List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users/" + user.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("controller-detail@primatis.test"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.memberNumber").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.failedLoginCount").doesNotExist())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist())
                .andExpect(jsonPath("$.lastLoginAt").doesNotExist());
    }

    @Test
    void getUserByIdWithUserReadAndAbsentUserReturnsNotFound() throws Exception {
        String token = signToken(List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users/999999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Bootstrap RBAC réel (V002) : ROLE_LIBRARIAN / ROLE_ADMIN
    // ---------------------------------------------------------------

    /**
     * Chaîne réelle complète (comme {@code AuthenticationRbacIntegrationTests})
     * plutôt qu'un JWT à claims injectés à la main : prouve que les
     * permissions réellement bootstrapées par V002 pour {@code ROLE_LIBRARIAN}
     * incluent bien {@code USER_READ}, sans supposition codée dans le test.
     */
    @Test
    void librarianRoleFromRealBootstrapCanListUsers() throws Exception {
        String email = "e2e-users-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_LIBRARIAN");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk());
    }

    /**
     * Même principe pour {@code ROLE_ADMIN} : aucun contournement codé, la
     * permission {@code USER_READ} provient uniquement du JWT réellement
     * émis à partir du bootstrap V002.
     */
    @Test
    void adminRoleFromRealBootstrapCanListUsers() throws Exception {
        String email = "e2e-users-admin@primatis.test";
        String rawPassword = "Correct-Admin-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_ADMIN");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // POST /api/v1/users — création administrative (DEV-05.5)
    // ---------------------------------------------------------------

    @Test
    void postUsersWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/users").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * ROLE_MEMBER ne porte pas USER_MANAGE (bootstrap V002) : le corps reste
     * structurellement valide pour que le refus provienne réellement de
     * l'autorisation (Service), pas d'une 400 de validation antérieure.
     */
    @Test
    void postUsersAuthenticatedWithRoleMemberWithoutUserManageIsForbidden() throws Exception {
        String token = signToken(List.of("ROLE_MEMBER"), List.of("CATALOGUE_READ"));

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validLibrarianCreationBody("controller-create-should-not-persist-1@primatis.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void postUsersAuthenticatedWithRoleLibrarianIsForbidden() throws Exception {
        String token = signToken(List.of("ROLE_LIBRARIAN"), List.of("USER_READ"));

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validLibrarianCreationBody("controller-create-should-not-persist-2@primatis.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void postUsersWithRolesEmptyIsBadRequest() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "controller-create-empty-roles@primatis.test",
                                  "firstName": "Prénom",
                                  "lastName": "Nom",
                                  "roles": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("roles"));
    }

    /**
     * Chaîne réelle complète (comme {@code librarianRoleFromRealBootstrapCanListUsers})
     * pour la seule création réellement réussie : ROLE_ADMIN provient du
     * bootstrap V002 réel, jamais d'un claim injecté à la main.
     */
    @Test
    void postUsersWithRoleAdminAndNonMemberRoleCreatesUser() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validLibrarianCreationBody("controller-create-librarian@primatis.test")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.user.id").exists())
                .andExpect(jsonPath("$.user.email").value("controller-create-librarian@primatis.test"))
                .andExpect(jsonPath("$.user.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.user.memberNumber").doesNotExist())
                .andExpect(jsonPath("$.user.memberStatus").doesNotExist())
                .andExpect(jsonPath("$.initialPassword").isString())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.failedLoginCount").doesNotExist())
                .andExpect(jsonPath("$.user.lockedUntil").doesNotExist())
                .andExpect(jsonPath("$.user.lastLoginAt").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AppUser persisted = appUserRepository.findByEmail("controller-create-librarian@primatis.test").orElseThrow();
        assertThat(persisted.getMemberNumber()).isNull();
    }

    @Test
    void postUsersWithRoleMemberGeneratesMemberNumber() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "controller-create-member@primatis.test",
                                  "firstName": "Prénom",
                                  "lastName": "Nom",
                                  "roles": ["ROLE_MEMBER"],
                                  "memberStatus": "ACTIVE",
                                  "registrationDate": "2026-01-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.memberNumber").value(org.hamcrest.Matchers.matchesPattern("^M[0-9]{9}$")))
                .andExpect(jsonPath("$.user.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.user.registrationDate").value("2026-01-01"));
    }

    @Test
    void postUsersWithExistingEmailReturnsConflict() throws Exception {
        persistUser("controller-create-duplicate-target@primatis.test");
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validLibrarianCreationBody("controller-create-duplicate-target@primatis.test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void postUsersWithUnknownRoleCodeReturnsConflict() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "controller-create-unknown-role@primatis.test",
                                  "firstName": "Prénom",
                                  "lastName": "Nom",
                                  "roles": ["ROLE_NOT_REAL"]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ROLE_CODE"));

        assertThat(appUserRepository.findByEmail("controller-create-unknown-role@primatis.test")).isEmpty();
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/users/{id} — modification administrative (DEV-05.6)
    // ---------------------------------------------------------------

    @Test
    void patchUserWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/users/1").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Corps structurellement valide pour que le refus provienne réellement
     * de l'autorisation (Service), pas d'une 400 de validation antérieure —
     * même précaution que pour POST (DEV-05.5).
     */
    @Test
    void patchUserAuthenticatedWithRoleMemberWithoutUserManageIsForbidden() throws Exception {
        String token = signToken(List.of("ROLE_MEMBER"), List.of("CATALOGUE_READ"));

        mockMvc.perform(patch("/api/v1/users/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validUpdateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void patchUserAuthenticatedWithRoleLibrarianIsForbidden() throws Exception {
        String token = signToken(List.of("ROLE_LIBRARIAN"), List.of("USER_READ"));

        mockMvc.perform(patch("/api/v1/users/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validUpdateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void patchUserWithRoleAdminUpdatesSimpleFieldsAndNeverExposesSensitiveFields() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");
        AppUser target = persistUser("controller-update-target@primatis.test");

        mockMvc.perform(patch("/api/v1/users/" + target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Prénom modifié",
                                  "lastName": "Nom modifié",
                                  "phoneNumber": "+32 470 22 22 22"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Prénom modifié"))
                .andExpect(jsonPath("$.lastName").value("Nom modifié"))
                .andExpect(jsonPath("$.phoneNumber").value("+32 470 22 22 22"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.failedLoginCount").doesNotExist())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist())
                .andExpect(jsonPath("$.lastLoginAt").doesNotExist());
    }

    /**
     * Preuve HTTP bout-en-bout de la sémantique à trois états (DEV-05.6
     * gate) : la clé JSON absente ({@code lastName} ci-dessous n'apparaît
     * jamais dans le corps) laisse la valeur actuelle strictement intacte —
     * distinct d'une clé présente avec {@code null}.
     */
    @Test
    void patchUserWithOnlyFirstNameKeyLeavesLastNameAndPhoneUnchanged() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");
        AppUser target = persistUser("controller-update-partial-target@primatis.test");
        mockMvc.perform(patch("/api/v1/users/" + target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "phoneNumber": "+32 470 44 44 44"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/users/" + target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Seul prénom modifié"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Seul prénom modifié"))
                // lastName/phoneNumber : clé absente du corps PATCH = inchangé
                .andExpect(jsonPath("$.lastName").value("Nom"))
                .andExpect(jsonPath("$.phoneNumber").value("+32 470 44 44 44"));
    }

    /**
     * Distinct du cas précédent : {@code phoneNumber} présent avec {@code
     * null} explicite efface réellement la valeur.
     */
    @Test
    void patchUserPhoneNumberExplicitNullClearsItViaHttp() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");
        AppUser target = persistUser("controller-update-phone-target@primatis.test");
        mockMvc.perform(patch("/api/v1/users/" + target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "phoneNumber": "+32 470 55 55 55"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/users/" + target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "phoneNumber": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void patchUserFirstNameExplicitNullReturnsConflict() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");
        AppUser target = persistUser("controller-update-null-firstname-target@primatis.test");

        mockMvc.perform(patch("/api/v1/users/" + target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": null
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIRST_NAME_MUST_NOT_BE_BLANK"));
    }

    @Test
    void patchUserReplacesRoleSet() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");
        String createResponseJson = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validLibrarianCreationBody("controller-update-roles-target@primatis.test")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long targetId = ((Number) JsonPath.read(createResponseJson, "$.user.id")).longValue();

        mockMvc.perform(patch("/api/v1/users/" + targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Prénom",
                                  "lastName": "Nom",
                                  "roles": ["ROLE_ADMIN"]
                                }
                                """))
                .andExpect(status().isOk());

        AppUser reloaded = appUserRepository.findByEmail("controller-update-roles-target@primatis.test").orElseThrow();
        List<String> roleCodes = entityManager
                .createQuery("SELECT ur.role.code FROM UserRole ur WHERE ur.id.userId = :userId", String.class)
                .setParameter("userId", reloaded.getId())
                .getResultList();
        assertThat(roleCodes).containsExactly("ROLE_ADMIN");
    }

    @Test
    void patchUserWithAbsentUserReturnsNotFound() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");

        mockMvc.perform(patch("/api/v1/users/999999999")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validUpdateBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void patchUserWithUnknownRoleCodeReturnsConflict() throws Exception {
        String token = createActiveAdminAndGetToken("e2e-users-create-admin@primatis.test");
        AppUser target = persistUser("controller-update-unknown-role-target@primatis.test");

        mockMvc.perform(patch("/api/v1/users/" + target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Prénom",
                                  "lastName": "Nom",
                                  "roles": ["ROLE_NOT_REAL"]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ROLE_CODE"));
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private String validUpdateBody() {
        return """
                {
                  "firstName": "Prénom",
                  "lastName": "Nom"
                }
                """;
    }

    private String validLibrarianCreationBody(String email) {
        return """
                {
                  "email": "%s",
                  "firstName": "Prénom",
                  "lastName": "Nom",
                  "roles": ["ROLE_LIBRARIAN"]
                }
                """.formatted(email);
    }

    private String createActiveAdminAndGetToken(String email) {
        persistActiveUserWithRole(email, "Correct-Admin-Password-2026!", "ROLE_ADMIN");
        Authentication authentication = authService.login(email, "Correct-Admin-Password-2026!");
        AccessToken accessToken = jwtService.generateAccessToken(authentication);
        return accessToken.token();
    }

    private String signToken(List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private AppUser persistUser(String email) {
        AppUser[] holder = new AppUser[1];
        transactionTemplate().executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("Correct-Password-2026!"));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            holder[0] = user;
        });
        return holder[0];
    }

    /**
     * N'utilise jamais un Role recréé par le test : {@code roleCode} doit
     * correspondre à un rôle réellement bootstrapé par V002 (même principe
     * que {@code AuthenticationRbacIntegrationTests}).
     */
    private void persistActiveUserWithRole(String email, String rawPassword, String roleCode) {
        transactionTemplate().executeWithoutResult(status -> {
            Role role = roleRepository.findByCode(roleCode)
                    .orElseThrow(() -> new IllegalStateException("Bootstrap RBAC manquant : " + roleCode));

            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);

            UserRole userRole = new UserRole();
            userRole.setId(new UserRoleId(user.getId(), role.getId()));
            userRole.setUser(user);
            userRole.setRole(role);
            userRole.setAssignedAt(Instant.now());
            entityManager.persist(userRole);
        });
    }

    private void deleteUserAndRoles(AppUser user) {
        entityManager.createQuery("DELETE FROM UserRole ur WHERE ur.id.userId = :userId")
                .setParameter("userId", user.getId())
                .executeUpdate();
        appUserRepository.delete(user);
    }
}
