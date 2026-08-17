package be.primatis.user.web;

import be.primatis.config.JwtProperties;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP profil personnel (DEV-05.9) : {@code GET}/{@code PATCH
 * /api/v1/me/profile}, authentification seule (ownership structurelle,
 * aucune permission). JWT signés manuellement (même principe que {@code
 * ResidenceControllerTests}/{@code UserControllerTests}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeProfileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private AppUserRepository appUserRepository;

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
            appUserRepository.findByEmail("meprofile-controller-a@primatis.test").ifPresent(appUserRepository::delete);
            appUserRepository.findByEmail("meprofile-controller-b@primatis.test").ifPresent(appUserRepository::delete);
            appUserRepository.findByEmail("meprofile-controller-shape@primatis.test").ifPresent(appUserRepository::delete);
        });
    }

    // ---------------------------------------------------------------
    // GET /api/v1/me/profile
    // ---------------------------------------------------------------

    @Test
    void getOwnProfileWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOwnProfileForUnknownUserReturnsUserNotFound() throws Exception {
        String token = signToken(999_999_999L);

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void getOwnProfileReturnsExactContractAndHidesInternalFields() throws Exception {
        AppUser user = persistUser("meprofile-controller-shape@primatis.test");
        String token = signToken(user.getId());

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("meprofile-controller-shape@primatis.test"))
                .andExpect(jsonPath("$.firstName").value("Prénom"))
                .andExpect(jsonPath("$.lastName").value("Nom"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                // DEV-05.9-DEC-01 : jamais roles/permissions/Residence/timestamps/lastLoginAt.
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.permissions").doesNotExist())
                .andExpect(jsonPath("$.residence").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.lastLoginAt").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.failedLoginCount").doesNotExist())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/me/profile
    // ---------------------------------------------------------------

    @Test
    void patchOwnProfileWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/me/profile").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchOwnProfileWithAbsentPhoneNumberIsNoOp() throws Exception {
        AppUser user = persistUser("meprofile-controller-a@primatis.test");
        user.setPhoneNumber("+32470123456");
        transactionTemplate().executeWithoutResult(status -> appUserRepository.save(user));
        String token = signToken(user.getId());

        mockMvc.perform(patch("/api/v1/me/profile").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+32470123456"));
    }

    @Test
    void patchOwnProfileWithExplicitNullPhoneNumberIsNoOp() throws Exception {
        AppUser user = persistUser("meprofile-controller-a@primatis.test");
        user.setPhoneNumber("+32470123456");
        transactionTemplate().executeWithoutResult(status -> appUserRepository.save(user));
        String token = signToken(user.getId());

        mockMvc.perform(patch("/api/v1/me/profile").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"phoneNumber": null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+32470123456"));
    }

    @Test
    void patchOwnProfileWithInvalidPhoneNumberReturnsValidationFailed() throws Exception {
        AppUser user = persistUser("meprofile-controller-a@primatis.test");
        String token = signToken(user.getId());

        mockMvc.perform(patch("/api/v1/me/profile").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"phoneNumber": "not-a-phone-number"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='phoneNumber')]").exists());
    }

    @Test
    void patchOwnProfileWithValidBelgianNumberNormalizesAndReturns200() throws Exception {
        AppUser user = persistUser("meprofile-controller-a@primatis.test");
        String token = signToken(user.getId());

        mockMvc.perform(patch("/api/v1/me/profile").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"phoneNumber": "0470 12 34 56"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+32470123456"));
    }

    /**
     * Preuve d'ownership de bout en bout (aucun {@code {id}} n'existe sur
     * cette route) : deux utilisateurs distincts gèrent chacun leur propre
     * profil sans contamination croisée.
     */
    @Test
    void eachUserCanOnlyManageTheirOwnProfile() throws Exception {
        AppUser userA = persistUser("meprofile-controller-a@primatis.test");
        AppUser userB = persistUser("meprofile-controller-b@primatis.test");
        String tokenA = signToken(userA.getId());
        String tokenB = signToken(userB.getId());

        mockMvc.perform(patch("/api/v1/me/profile").header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("""
                                {"phoneNumber": "0470 12 34 56"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+32470123456"));

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userB.getId()))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
    }

    // ---------------------------------------------------------------
    // Fixtures / helpers
    // ---------------------------------------------------------------

    private String signToken(Long subjectUserId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(String.valueOf(subjectUserId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of())
                .claim("permissions", List.of())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private AppUser persistUser(String email) {
        AppUser[] holder = new AppUser[1];
        transactionTemplate().executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash("hash");
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
}
