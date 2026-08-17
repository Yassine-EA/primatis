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
import be.primatis.user.City;
import be.primatis.user.CityRepository;
import be.primatis.user.Country;
import be.primatis.user.CountryRepository;
import be.primatis.user.Residence;
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
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP Address/Residence (DEV-05.8, contrat figé DEC-09) : parcours
 * {@code /api/v1/me/residence} (ownership structurelle, aucune permission)
 * et parcours {@code /api/v1/users/{id}/residence(s)} ({@code USER_READ}/
 * {@code USER_PROFILE_MANAGE}). JWT signés manuellement (même principe que
 * {@code UserControllerTests}) pour les scénarios anonymous/sans permission ;
 * connexion réelle avec rôles réellement bootstrapés (V002 + V004) pour
 * prouver que {@code USER_PROFILE_MANAGE} est effectivement porté par
 * {@code ROLE_LIBRARIAN}/{@code ROLE_ADMIN} et absent de {@code ROLE_MEMBER}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResidenceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private CountryRepository countryRepository;

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
    void cleanupFixtures() {
        transactionTemplate().executeWithoutResult(status -> {
            deleteUserWithResidences("residence-controller-me@primatis.test");
            deleteUserWithResidences("residence-controller-detail@primatis.test");
            deleteUserWithResidences("residence-controller-target@primatis.test");
            deleteUserWithResidences("residence-controller-conflict@primatis.test");
            deleteUserWithResidences("e2e-residence-librarian@primatis.test");
            deleteUserWithResidences("e2e-residence-admin@primatis.test");
            deleteUserWithResidences("e2e-residence-member-self@primatis.test");
            deleteUserWithResidences("e2e-residence-member-victim@primatis.test");
        });
    }

    /**
     * {@code fk_residence_user_id}/{@code fk_residence_address_id} sont en
     * {@code RESTRICT} (V001) : toute Residence/Address créée par un test
     * pour cet utilisateur doit être supprimée avant l'utilisateur lui-même.
     */
    private void deleteUserWithResidences(String email) {
        appUserRepository.findByEmail(email).ifPresent(user -> {
            entityManager.createQuery("SELECT r FROM Residence r WHERE r.user.id = :userId", Residence.class)
                    .setParameter("userId", user.getId())
                    .getResultList()
                    .forEach(residence -> {
                        Long addressId = residence.getAddress().getId();
                        entityManager.remove(residence);
                        entityManager.flush();
                        entityManager.createQuery("DELETE FROM Address a WHERE a.id = :addressId")
                                .setParameter("addressId", addressId)
                                .executeUpdate();
                    });
            deleteUserAndRoles(user);
        });
    }

    // ---------------------------------------------------------------
    // GET /api/v1/me/residence
    // ---------------------------------------------------------------

    @Test
    void getOwnResidenceWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me/residence"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOwnResidenceWithoutCurrentResidenceReturnsNotFound() throws Exception {
        AppUser user = persistUser("residence-controller-me@primatis.test");
        String token = signToken(user.getId(), List.of(), List.of());

        mockMvc.perform(get("/api/v1/me/residence").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CURRENT_RESIDENCE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // PUT /api/v1/me/residence
    // ---------------------------------------------------------------

    @Test
    void putOwnResidenceWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/me/residence").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putOwnResidenceCreatesFirstResidenceForAuthenticatedUserOnly() throws Exception {
        AppUser user = persistUser("residence-controller-me@primatis.test");
        City city = persistCity();
        String token = signToken(user.getId(), List.of(), List.of());

        mockMvc.perform(put("/api/v1/me/residence").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validResidenceBody(city.getId(), "Rue du Parlement", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(LocalDate.now(java.time.ZoneOffset.UTC).toString()))
                .andExpect(jsonPath("$.endDate").doesNotExist())
                .andExpect(jsonPath("$.address.street").value("Rue du Parlement"))
                .andExpect(jsonPath("$.address.city.id").value(city.getId()))
                .andExpect(jsonPath("$.address.city.country.code").exists());

        mockMvc.perform(get("/api/v1/me/residence").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.street").value("Rue du Parlement"));
    }

    @Test
    void putOwnResidenceRejectsBlankStreet() throws Exception {
        AppUser user = persistUser("residence-controller-me@primatis.test");
        City city = persistCity();
        String token = signToken(user.getId(), List.of(), List.of());

        mockMvc.perform(put("/api/v1/me/residence").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"cityId": %d, "street": "", "streetNumber": "1"}
                                """.formatted(city.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='street')]").exists());
    }

    @Test
    void putOwnResidenceUnknownCityReturnsNotFound() throws Exception {
        AppUser user = persistUser("residence-controller-me@primatis.test");
        String token = signToken(user.getId(), List.of(), List.of());

        mockMvc.perform(put("/api/v1/me/residence").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validResidenceBody(999_999_999L, "Rue", "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CITY_NOT_FOUND"));
    }

    @Test
    void putOwnResidenceTwiceSameDayReturnsConflict() throws Exception {
        AppUser user = persistUser("residence-controller-conflict@primatis.test");
        City city = persistCity();
        String token = signToken(user.getId(), List.of(), List.of());

        mockMvc.perform(put("/api/v1/me/residence").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validResidenceBody(city.getId(), "Première rue", "1")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/me/residence").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validResidenceBody(city.getId(), "Deuxième rue", "2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESIDENCE_PERIOD_CONFLICT"));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/users/{id}/residence, /residences — USER_READ
    // ---------------------------------------------------------------

    @Test
    void getResidenceWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/1/residence"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getResidenceWithoutUserReadIsForbidden() throws Exception {
        AppUser user = persistUser("residence-controller-detail@primatis.test");
        String token = signToken(user.getId(), List.of(), List.of("CATALOGUE_READ"));

        mockMvc.perform(get("/api/v1/users/" + user.getId() + "/residence")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void getResidenceWithUserReadAndNoCurrentResidenceReturnsNotFound() throws Exception {
        AppUser user = persistUser("residence-controller-detail@primatis.test");
        String token = signToken(user.getId(), List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users/" + user.getId() + "/residence")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CURRENT_RESIDENCE_NOT_FOUND"));
    }

    @Test
    void getResidenceHistoryWithNoHistoryReturnsEmptyArray() throws Exception {
        AppUser user = persistUser("residence-controller-detail@primatis.test");
        String token = signToken(user.getId(), List.of(), List.of("USER_READ"));

        mockMvc.perform(get("/api/v1/users/" + user.getId() + "/residences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---------------------------------------------------------------
    // PUT /api/v1/users/{id}/residence — USER_PROFILE_MANAGE
    // ---------------------------------------------------------------

    @Test
    void putResidenceWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/users/1/residence").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * USER_MANAGE seule (sans USER_PROFILE_MANAGE) doit être refusée :
     * preuve que le detournement de USER_MANAGE pour cette écriture,
     * explicitement interdit par DEV-05.8-DEC-08, n'a pas eu lieu.
     */
    @Test
    void putResidenceWithUserManageAloneIsForbidden() throws Exception {
        AppUser target = persistUser("residence-controller-target@primatis.test");
        City city = persistCity();
        String token = signToken(999L, List.of(), List.of("USER_MANAGE"));

        mockMvc.perform(put("/api/v1/users/" + target.getId() + "/residence")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validResidenceBody(city.getId(), "Rue", "1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void putResidenceWithUserProfileManageSucceedsForTargetUser() throws Exception {
        AppUser target = persistUser("residence-controller-target@primatis.test");
        City city = persistCity();
        String token = signToken(999L, List.of(), List.of("USER_PROFILE_MANAGE"));

        mockMvc.perform(put("/api/v1/users/" + target.getId() + "/residence")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validResidenceBody(city.getId(), "Rue du Trône", "22")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.street").value("Rue du Trône"));
    }

    @Test
    void putResidenceUnknownUserReturnsNotFound() throws Exception {
        String token = signToken(999L, List.of(), List.of("USER_PROFILE_MANAGE"));
        City city = persistCity();

        mockMvc.perform(put("/api/v1/users/999999999/residence")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validResidenceBody(city.getId(), "Rue", "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // Bootstrap RBAC réel (V002 + V004) : USER_PROFILE_MANAGE
    // ---------------------------------------------------------------

    /**
     * Chaîne réelle complète (comme {@code UserControllerTests}) : prouve
     * que V004 attribue bien USER_PROFILE_MANAGE à ROLE_LIBRARIAN, sans
     * aucune supposition codée dans le test.
     */
    @Test
    void librarianRoleFromRealBootstrapCanReplaceResidenceOfAnotherUser() throws Exception {
        String librarianEmail = "e2e-residence-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(librarianEmail, rawPassword, "ROLE_LIBRARIAN");
        AppUser target = persistUser("residence-controller-target@primatis.test");
        City city = persistCity();

        Authentication authentication = authService.login(librarianEmail, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(put("/api/v1/users/" + target.getId() + "/residence")
                        .header("Authorization", "Bearer " + accessToken.token())
                        .contentType("application/json")
                        .content(validResidenceBody(city.getId(), "Rue bootstrap librarian", "1")))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoleFromRealBootstrapCanReplaceResidenceOfAnotherUser() throws Exception {
        String adminEmail = "e2e-residence-admin@primatis.test";
        String rawPassword = "Correct-Admin-Password-2026!";
        persistActiveUserWithRole(adminEmail, rawPassword, "ROLE_ADMIN");
        AppUser target = persistUser("residence-controller-target@primatis.test");
        City city = persistCity();

        Authentication authentication = authService.login(adminEmail, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(put("/api/v1/users/" + target.getId() + "/residence")
                        .header("Authorization", "Bearer " + accessToken.token())
                        .contentType("application/json")
                        .content(validResidenceBody(city.getId(), "Rue bootstrap admin", "1")))
                .andExpect(status().isOk());
    }

    /**
     * Preuve d'ownership de bout en bout (DEV-05.8-DEC-07) avec un vrai
     * ROLE_MEMBER bootstrapé : peut définir SA PROPRE résidence via
     * {@code /me/residence}, mais reçoit 403 en tentant d'agir sur un autre
     * utilisateur via {@code /users/{id}/residence} (ROLE_MEMBER ne porte
     * ni USER_READ ni USER_PROFILE_MANAGE selon V002/V004).
     */
    @Test
    void memberRoleFromRealBootstrapCanManageOwnResidenceButNotAnothers() throws Exception {
        String memberEmail = "e2e-residence-member-self@primatis.test";
        String rawPassword = "Correct-Member-Password-2026!";
        persistActiveUserWithRole(memberEmail, rawPassword, "ROLE_MEMBER");
        AppUser victim = persistUser("e2e-residence-member-victim@primatis.test");
        City city = persistCity();

        Authentication authentication = authService.login(memberEmail, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(put("/api/v1/me/residence")
                        .header("Authorization", "Bearer " + accessToken.token())
                        .contentType("application/json")
                        .content(validResidenceBody(city.getId(), "Rue membre", "1")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/users/" + victim.getId() + "/residence")
                        .header("Authorization", "Bearer " + accessToken.token())
                        .contentType("application/json")
                        .content(validResidenceBody(city.getId(), "Tentative interdite", "1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------------------------------------------------------------
    // Fixtures / helpers
    // ---------------------------------------------------------------

    private String validResidenceBody(Long cityId, String street, String streetNumber) {
        return """
                {"cityId": %d, "street": "%s", "streetNumber": "%s"}
                """.formatted(cityId, street, streetNumber);
    }

    private String signToken(Long subjectUserId, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(String.valueOf(subjectUserId))
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

    private City persistCity() {
        City[] holder = new City[1];
        transactionTemplate().executeWithoutResult(status -> {
            Country country = new Country();
            country.setName("Belgique");
            country.setCode("RT" + (System.nanoTime() % 100000));
            countryRepository.save(country);

            City city = new City();
            city.setName("Bruxelles");
            city.setPostalCode("1000");
            city.setCountry(country);
            cityRepository.save(city);
            holder[0] = city;
        });
        return holder[0];
    }

    /**
     * N'utilise jamais un Role recréé par le test : {@code roleCode} doit
     * correspondre à un rôle réellement bootstrapé par V002 (même principe
     * que {@code UserControllerTests}).
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
