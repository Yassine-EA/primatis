package be.primatis.setting.web;

import be.primatis.access.Role;
import be.primatis.access.RoleRepository;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.config.JwtProperties;
import be.primatis.security.AccessToken;
import be.primatis.security.AuthService;
import be.primatis.security.JwtService;
import be.primatis.setting.ApplicationSetting;
import be.primatis.setting.ApplicationSettingRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP Application Settings (DEV-12.2) : {@code GET /api/v1/settings}
 * ({@code SETTING_READ}) et {@code PATCH /api/v1/settings/{settingKey}}
 * ({@code SETTING_MANAGE}). Même principe que {@code UserControllerTests} :
 * JWT signés manuellement pour les scénarios anonymous/sans permission/
 * erreurs métier (jamais besoin de résoudre {@code updatedByUser}, la
 * mutation échouant avant) ; connexion réelle avec {@code ROLE_ADMIN}
 * réellement bootstrapé (V002) pour les scénarios de succès, seuls à
 * dérérencer l'utilisateur authentifié.
 *
 * <p>Les six paramètres réels étant des lignes bootstrapées partagées par
 * toute la suite (aucune création/suppression via l'API, DEV-12.1 §22.4),
 * {@link #restoreCanonicalSettings()} restaure systématiquement leurs
 * valeurs et vide {@code updatedByUser} après chaque test — même principe
 * que la suppression des utilisateurs fixtures dans {@code
 * UserControllerTests}, adapté à des lignes non supprimables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingControllerTests {

    private static final Map<String, String> CANONICAL_VALUES = Map.of(
            "LOAN_DURATION_DAYS", "21",
            "MAX_ACTIVE_RESERVATIONS_PER_MEMBER", "10",
            "RESERVATION_READY_HOLD_HOURS", "48",
            "LOAN_DUE_SOON_DAYS", "3",
            "FINE_WEEKLY_RATE", "0.80",
            "FINE_MAX_AMOUNT", "25.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ApplicationSettingRepository applicationSettingRepository;

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
    void cleanup() {
        restoreCanonicalSettings();
        transactionTemplate().executeWithoutResult(status -> {
            applicationSettingRepository.findBySettingKey("AN_UNSUPPORTED_TYPE_SETTING")
                    .ifPresent(applicationSettingRepository::delete);
            appUserRepository.findByEmail("controller-settings-admin@primatis.test")
                    .ifPresent(this::deleteUserAndRoles);
        });
    }

    private void restoreCanonicalSettings() {
        transactionTemplate().executeWithoutResult(status -> {
            for (Map.Entry<String, String> entry : CANONICAL_VALUES.entrySet()) {
                applicationSettingRepository.findBySettingKey(entry.getKey()).ifPresent(setting -> {
                    setting.setSettingValue(entry.getValue());
                    setting.setUpdatedByUser(null);
                });
            }
        });
    }

    // ---------------------------------------------------------------
    // GET /api/v1/settings
    // ---------------------------------------------------------------

    @Test
    void listSettingsWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listSettingsAuthenticatedWithoutSettingReadIsForbidden() throws Exception {
        String token = signToken(List.of(), List.of("CATALOGUE_READ"));

        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listSettingsWithSettingReadReturnsAllSixSettingsOrderedBySettingKey() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_READ"));

        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].settingKey").value("FINE_MAX_AMOUNT"))
                .andExpect(jsonPath("$[1].settingKey").value("FINE_WEEKLY_RATE"))
                .andExpect(jsonPath("$[2].settingKey").value("LOAN_DUE_SOON_DAYS"))
                .andExpect(jsonPath("$[3].settingKey").value("LOAN_DURATION_DAYS"))
                .andExpect(jsonPath("$[4].settingKey").value("MAX_ACTIVE_RESERVATIONS_PER_MEMBER"))
                .andExpect(jsonPath("$[5].settingKey").value("RESERVATION_READY_HOLD_HOURS"));
    }

    @Test
    void listSettingsExposesExpectedFieldsForUntouchedSetting() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_READ"));

        String body = mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.settingKey=='LOAN_DURATION_DAYS')].settingValue").value("21"))
                .andExpect(jsonPath("$[?(@.settingKey=='LOAN_DURATION_DAYS')].valueType").value("INTEGER"))
                .andExpect(jsonPath("$[?(@.settingKey=='LOAN_DURATION_DAYS')].description").exists())
                .andExpect(jsonPath("$[?(@.settingKey=='LOAN_DURATION_DAYS')].updatedAt").exists())
                .andReturn().getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = JsonPath.read(body, "$[?(@.settingKey=='LOAN_DURATION_DAYS')]");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0)).containsEntry("updatedByUser", null);
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/settings/{settingKey}
    // ---------------------------------------------------------------

    @Test
    void patchSettingWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "5"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchSettingAuthenticatedWithSettingReadOnlyIsForbidden() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_READ"));

        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "5"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void patchSettingWithUnrelatedPermissionIsForbidden() throws Exception {
        String token = signToken(List.of(), List.of("CATALOGUE_READ"));

        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "5"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void patchSettingWithMissingKeyReturnsNotFound() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_MANAGE"));

        mockMvc.perform(patch("/api/v1/settings/DOES_NOT_EXIST")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "5"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTING_NOT_FOUND"));
    }

    @Test
    void patchSettingWithMissingSettingValueKeyReturnsBadRequest() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_MANAGE"));

        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("settingValue"));
    }

    @Test
    void patchSettingWithBlankSettingValueReturnsBadRequest() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_MANAGE"));

        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("settingValue"));
    }

    @Test
    void patchSettingRejectsNegativeInteger() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_MANAGE"));

        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "-1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_VALUE_NOT_POSITIVE"));
    }

    @Test
    void patchSettingRejectsZeroInteger() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_MANAGE"));

        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "0"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_VALUE_NOT_POSITIVE"));
    }

    @Test
    void patchSettingRejectsNonNumericInteger() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_MANAGE"));

        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "abc"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_VALUE_NOT_INTEGER"));
    }

    @Test
    void patchSettingRejectsNegativeDecimal() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_MANAGE"));

        mockMvc.perform(patch("/api/v1/settings/FINE_WEEKLY_RATE")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "-0.5"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_VALUE_NOT_POSITIVE"));
    }

    @Test
    void patchSettingRejectsNonNumericDecimal() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_MANAGE"));

        mockMvc.perform(patch("/api/v1/settings/FINE_WEEKLY_RATE")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "abc"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_VALUE_NOT_DECIMAL"));
    }

    @Test
    void patchSettingWithUnsupportedValueTypeReturnsConflict() throws Exception {
        persistUnsupportedTypeSetting();
        String token = signToken(List.of(), List.of("SETTING_MANAGE"));

        mockMvc.perform(patch("/api/v1/settings/AN_UNSUPPORTED_TYPE_SETTING")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "world"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_VALUE_TYPE_NOT_SUPPORTED"));
    }

    @Test
    void patchSettingLeavesSettingUnchangedWhenValidationFails() throws Exception {
        String token = signToken(List.of(), List.of("SETTING_MANAGE"));

        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "-1"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer "
                        + signToken(List.of(), List.of("SETTING_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.settingKey=='LOAN_DUE_SOON_DAYS')].settingValue").value("3"));
    }

    /**
     * Seul scénario de succès nécessitant un utilisateur réellement
     * persisté : {@code updatedByUser} est déréférencé par {@link
     * be.primatis.setting.web.SettingResponse#from}, contrairement aux
     * chemins d'échec ci-dessus qui ne dépassent jamais la validation.
     */
    @Test
    void patchSettingWithRoleAdminUpdatesIntegerValueAndSetsAudit() throws Exception {
        AppUser admin = createActiveAdminAndGetUser("controller-settings-admin@primatis.test");
        String token = tokenFor(admin);

        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "5"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settingKey").value("LOAN_DUE_SOON_DAYS"))
                .andExpect(jsonPath("$.settingValue").value("5"))
                .andExpect(jsonPath("$.valueType").value("INTEGER"))
                .andExpect(jsonPath("$.updatedByUser.id").value(admin.getId()))
                .andExpect(jsonPath("$.updatedByUser.firstName").value(admin.getFirstName()))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void patchSettingWithRoleAdminUpdatesDecimalValue() throws Exception {
        AppUser admin = createActiveAdminAndGetUser("controller-settings-admin@primatis.test");
        String token = tokenFor(admin);

        mockMvc.perform(patch("/api/v1/settings/FINE_WEEKLY_RATE")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "1.25"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settingValue").value("1.25"))
                .andExpect(jsonPath("$.valueType").value("DECIMAL"));
    }

    @Test
    void patchSettingTrimsSurroundingWhitespace() throws Exception {
        AppUser admin = createActiveAdminAndGetUser("controller-settings-admin@primatis.test");
        String token = tokenFor(admin);

        mockMvc.perform(patch("/api/v1/settings/MAX_ACTIVE_RESERVATIONS_PER_MEMBER")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "  15  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settingValue").value("15"));
    }

    @Test
    void patchSettingNeverModifiesSettingKeyValueTypeOrDescription() throws Exception {
        AppUser admin = createActiveAdminAndGetUser("controller-settings-admin@primatis.test");
        String token = tokenFor(admin);
        String originalDescription = applicationSettingRepository.findBySettingKey("LOAN_DUE_SOON_DAYS")
                .orElseThrow().getDescription();

        mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"settingValue": "4"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settingKey").value("LOAN_DUE_SOON_DAYS"))
                .andExpect(jsonPath("$.valueType").value("INTEGER"))
                .andExpect(jsonPath("$.description").value(originalDescription));
    }

    // ---------------------------------------------------------------
    // RBAC réel (V002) : ROLE_ADMIN seul porte SETTING_READ/SETTING_MANAGE
    // ---------------------------------------------------------------

    @Test
    void librarianRoleFromRealBootstrapCannotReadSettings() throws Exception {
        String email = "e2e-settings-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_LIBRARIAN");
        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        try {
            mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + accessToken.token()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        } finally {
            transactionTemplate().executeWithoutResult(status ->
                    appUserRepository.findByEmail(email).ifPresent(this::deleteUserAndRoles));
        }
    }

    @Test
    void adminRoleFromRealBootstrapCanReadAndManageSettings() throws Exception {
        String email = "e2e-settings-admin@primatis.test";
        String rawPassword = "Correct-Admin-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_ADMIN");
        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        try {
            mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + accessToken.token()))
                    .andExpect(status().isOk());

            mockMvc.perform(patch("/api/v1/settings/LOAN_DUE_SOON_DAYS")
                            .header("Authorization", "Bearer " + accessToken.token())
                            .contentType("application/json")
                            .content("""
                                    {"settingValue": "5"}
                                    """))
                    .andExpect(status().isOk());
        } finally {
            // Le PATCH ci-dessus affecte updated_by_user_id à cet admin
            // (fk_application_setting_updated_by_user_id, ON DELETE
            // RESTRICT, DEV-12.1 §8) : restaurer les settings avant de
            // supprimer l'utilisateur, sinon la suppression échoue.
            restoreCanonicalSettings();
            transactionTemplate().executeWithoutResult(status ->
                    appUserRepository.findByEmail(email).ifPresent(this::deleteUserAndRoles));
        }
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

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

    private AppUser createActiveAdminAndGetUser(String email) {
        persistActiveUserWithRole(email, "Correct-Admin-Password-2026!", "ROLE_ADMIN");
        return appUserRepository.findByEmail(email).orElseThrow();
    }

    private String tokenFor(AppUser user) {
        Authentication authentication = authService.login(user.getEmail(), "Correct-Admin-Password-2026!");
        AccessToken accessToken = jwtService.generateAccessToken(authentication);
        return accessToken.token();
    }

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

    private void persistUnsupportedTypeSetting() {
        transactionTemplate().executeWithoutResult(status -> {
            ApplicationSetting setting = new ApplicationSetting();
            setting.setSettingKey("AN_UNSUPPORTED_TYPE_SETTING");
            setting.setSettingValue("hello");
            setting.setValueType("STRING");
            setting.setDescription("Fixture de test DEV-12.2");
            setting.setUpdatedAt(Instant.now());
            entityManager.persist(setting);
        });
    }

    private void deleteUserAndRoles(AppUser user) {
        entityManager.createQuery("DELETE FROM UserRole ur WHERE ur.id.userId = :userId")
                .setParameter("userId", user.getId())
                .executeUpdate();
        appUserRepository.delete(user);
    }
}
