package be.primatis.fine.web;

import be.primatis.access.Role;
import be.primatis.access.RoleRepository;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.config.JwtProperties;
import be.primatis.fine.Fine;
import be.primatis.fine.FineStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.security.AccessToken;
import be.primatis.security.AuthService;
import be.primatis.security.JwtService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP de consultation (DEV-09.9), confirmation du paiement
 * externe (DEV-09.7, DEV-DEC-0050) et annulation (DEV-09.8) d'une Fine :
 * {@code GET /api/v1/fines} ({@code FINE_READ}), {@code GET
 * /api/v1/me/fines} (authentification seule), {@code POST
 * /api/v1/fines/{fineId}/payment-confirmation} et {@code POST
 * /api/v1/fines/{fineId}/cancel} (toutes deux {@code FINE_MANAGE}) —
 * même précédent exact que {@code ReservationControllerTests}/{@code
 * LoanControllerTests}. JWT signés manuellement pour les scénarios sans
 * permission ; connexion réelle avec rôles réellement bootstrapés (V002)
 * pour prouver l'accès via {@code ROLE_LIBRARIAN}/{@code ROLE_ADMIN} et
 * le refus pour {@code ROLE_MEMBER}. Fixtures créées en transaction
 * committée (MockMvc traverse une vraie requête HTTP), nettoyées
 * explicitement en {@code @AfterEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FineControllerTests {

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

    private final List<Long> createdFineIds = new ArrayList<>();
    private final List<Long> createdLoanIds = new ArrayList<>();
    private final List<Long> createdCopyIds = new ArrayList<>();
    private final List<Long> createdTitleIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanupFixtures() {
        transactionTemplate().executeWithoutResult(status -> {
            for (Long fineId : createdFineIds) {
                entityManager.createQuery("DELETE FROM Fine f WHERE f.id = :id")
                        .setParameter("id", fineId).executeUpdate();
            }
            for (Long loanId : createdLoanIds) {
                entityManager.createQuery("DELETE FROM Loan l WHERE l.id = :id")
                        .setParameter("id", loanId).executeUpdate();
            }
            for (Long copyId : createdCopyIds) {
                entityManager.createQuery("DELETE FROM Copy c WHERE c.id = :id")
                        .setParameter("id", copyId).executeUpdate();
            }
            for (Long userId : createdUserIds) {
                entityManager.createQuery("DELETE FROM UserRole ur WHERE ur.id.userId = :userId")
                        .setParameter("userId", userId).executeUpdate();
            }
            for (Long titleId : createdTitleIds) {
                entityManager.createQuery("DELETE FROM Title t WHERE t.id = :id")
                        .setParameter("id", titleId).executeUpdate();
            }
            for (Long userId : createdUserIds) {
                entityManager.createQuery("DELETE FROM AppUser u WHERE u.id = :id")
                        .setParameter("id", userId).executeUpdate();
            }
        });
    }

    // ---------------------------------------------------------------
    // GET /api/v1/me/fines (DEV-09.9)
    // ---------------------------------------------------------------

    @Test
    void listOwnFinesWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me/fines"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listOwnFinesWithoutAnyPermissionIsAuthorized() throws Exception {
        AppUser self = persistUser("controller-fine-own-no-permission");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/fines").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listOwnFinesReturnsEmptyPageRatherThanNotFoundWhenNoFine() throws Exception {
        AppUser self = persistUser("controller-fine-own-empty");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/fines").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listOwnFinesNeverLeaksAnotherMembersFine() throws Exception {
        AppUser self = persistUser("controller-fine-own-isolation-self");
        AppUser other = persistUser("controller-fine-own-isolation-other");
        persistFineForUser(other, FineStatus.UNPAID);
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/fines?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listOwnFinesExposesFullHistoryIncludingTerminalStatuses() throws Exception {
        AppUser self = persistUser("controller-fine-own-history");
        persistFineForUser(self, FineStatus.UNPAID);
        persistFineForUser(self, FineStatus.PAID);
        persistFineForUser(self, FineStatus.CANCELLED);
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/fines?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[?(@.fineStatus=='UNPAID')]").exists())
                .andExpect(jsonPath("$.content[?(@.fineStatus=='PAID')]").exists())
                .andExpect(jsonPath("$.content[?(@.fineStatus=='CANCELLED')]").exists());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/fines (DEV-09.9, FINE_READ)
    // ---------------------------------------------------------------

    @Test
    void listFinesWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/fines"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listFinesAuthenticatedWithoutFineReadIsForbidden() throws Exception {
        String token = signToken(1L, List.of(), List.of("FINE_MANAGE"));

        mockMvc.perform(get("/api/v1/fines").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listFinesWithFineReadReturnsDefaultPagedResponse() throws Exception {
        String token = signToken(1L, List.of(), List.of("FINE_READ"));

        mockMvc.perform(get("/api/v1/fines").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listFinesExposesMultiUserContent() throws Exception {
        AppUser memberOne = persistUser("controller-fine-staff-shape-1");
        AppUser memberTwo = persistUser("controller-fine-staff-shape-2");
        Fine fineOne = persistFineForUser(memberOne, FineStatus.UNPAID);
        Fine fineTwo = persistFineForUser(memberTwo, FineStatus.PAID);
        String token = signToken(1L, List.of(), List.of("FINE_READ"));

        mockMvc.perform(get("/api/v1/fines?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + fineOne.getId() + ")].borrower.id")
                        .value(memberOne.getId().intValue()))
                .andExpect(jsonPath("$.content[?(@.id==" + fineTwo.getId() + ")].fineStatus")
                        .value("PAID"));
    }

    /**
     * Chaîne réelle complète (comme {@code ReservationControllerTests}) :
     * prouve que {@code ROLE_LIBRARIAN}/{@code ROLE_ADMIN} bootstrapés par
     * V002 portent bien {@code FINE_READ}, et que {@code ROLE_MEMBER} ne
     * le porte pas.
     */
    @Test
    void librarianRoleFromRealBootstrapCanListFines() throws Exception {
        String email = "controller-fine-read-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_LIBRARIAN");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/fines").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoleFromRealBootstrapCanListFines() throws Exception {
        String email = "controller-fine-read-admin@primatis.test";
        String rawPassword = "Correct-Admin-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_ADMIN");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/fines").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk());
    }

    @Test
    void memberRoleFromRealBootstrapCannotListFines() throws Exception {
        String email = "controller-fine-read-member@primatis.test";
        String rawPassword = "Correct-Member-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_MEMBER");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/fines").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // POST /api/v1/fines/{fineId}/payment-confirmation
    // ---------------------------------------------------------------

    @Test
    void confirmExternalPaymentWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/fines/1/payment-confirmation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmExternalPaymentAuthenticatedWithoutFineManageIsForbidden() throws Exception {
        String token = signToken(1L, List.of(), List.of("FINE_READ"));

        mockMvc.perform(post("/api/v1/fines/1/payment-confirmation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void confirmExternalPaymentOnUnpaidFineWithFineManageSucceeds() throws Exception {
        Fine fine = persistFine(FineStatus.UNPAID);
        String token = signToken(1L, List.of(), List.of("FINE_MANAGE"));

        mockMvc.perform(post("/api/v1/fines/" + fine.getId() + "/payment-confirmation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fineStatus").value("PAID"))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist());
    }

    @Test
    void confirmExternalPaymentOnUnknownFineReturnsNotFound() throws Exception {
        String token = signToken(1L, List.of(), List.of("FINE_MANAGE"));

        mockMvc.perform(post("/api/v1/fines/999999999/payment-confirmation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FINE_NOT_FOUND"));
    }

    @Test
    void confirmExternalPaymentOnAlreadyPaidFineReturnsConflict() throws Exception {
        Fine fine = persistFine(FineStatus.PAID);
        String token = signToken(1L, List.of(), List.of("FINE_MANAGE"));

        mockMvc.perform(post("/api/v1/fines/" + fine.getId() + "/payment-confirmation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINE_NOT_PAYABLE"));
    }

    @Test
    void confirmExternalPaymentOnCancelledFineReturnsConflict() throws Exception {
        Fine fine = persistFine(FineStatus.CANCELLED);
        String token = signToken(1L, List.of(), List.of("FINE_MANAGE"));

        mockMvc.perform(post("/api/v1/fines/" + fine.getId() + "/payment-confirmation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINE_NOT_PAYABLE"));
    }

    /**
     * Chaîne réelle complète (comme {@code ReservationControllerTests}) :
     * prouve que {@code ROLE_LIBRARIAN}/{@code ROLE_ADMIN} bootstrapés par
     * V002 portent bien {@code FINE_MANAGE}, et que {@code ROLE_MEMBER} ne
     * le porte pas.
     */
    @Test
    void librarianRoleFromRealBootstrapCanConfirmExternalPayment() throws Exception {
        String email = "controller-fine-manage-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_LIBRARIAN");
        Fine fine = persistFine(FineStatus.UNPAID);

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(post("/api/v1/fines/" + fine.getId() + "/payment-confirmation")
                        .header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fineStatus").value("PAID"));
    }

    @Test
    void adminRoleFromRealBootstrapCanConfirmExternalPayment() throws Exception {
        String email = "controller-fine-manage-admin@primatis.test";
        String rawPassword = "Correct-Admin-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_ADMIN");
        Fine fine = persistFine(FineStatus.UNPAID);

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(post("/api/v1/fines/" + fine.getId() + "/payment-confirmation")
                        .header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fineStatus").value("PAID"));
    }

    @Test
    void memberRoleFromRealBootstrapCannotConfirmExternalPayment() throws Exception {
        String email = "controller-fine-manage-member@primatis.test";
        String rawPassword = "Correct-Member-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_MEMBER");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(post("/api/v1/fines/1/payment-confirmation")
                        .header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // POST /api/v1/fines/{fineId}/cancel (DEV-09.8)
    // ---------------------------------------------------------------

    @Test
    void cancelFineWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/fines/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelFineAuthenticatedWithoutFineManageIsForbidden() throws Exception {
        String token = signToken(1L, List.of(), List.of("FINE_READ"));

        mockMvc.perform(post("/api/v1/fines/1/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void cancelFineOnUnpaidFineWithFineManageSucceeds() throws Exception {
        Fine fine = persistFine(FineStatus.UNPAID);
        String token = signToken(1L, List.of(), List.of("FINE_MANAGE"));

        mockMvc.perform(post("/api/v1/fines/" + fine.getId() + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fineStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists())
                .andExpect(jsonPath("$.paidAt").doesNotExist());
    }

    @Test
    void cancelFineOnUnknownFineReturnsNotFound() throws Exception {
        String token = signToken(1L, List.of(), List.of("FINE_MANAGE"));

        mockMvc.perform(post("/api/v1/fines/999999999/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FINE_NOT_FOUND"));
    }

    @Test
    void cancelFineOnAlreadyCancelledFineReturnsConflict() throws Exception {
        Fine fine = persistFine(FineStatus.CANCELLED);
        String token = signToken(1L, List.of(), List.of("FINE_MANAGE"));

        mockMvc.perform(post("/api/v1/fines/" + fine.getId() + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINE_NOT_CANCELLABLE"));
    }

    @Test
    void cancelFineOnPaidFineReturnsConflict() throws Exception {
        Fine fine = persistFine(FineStatus.PAID);
        String token = signToken(1L, List.of(), List.of("FINE_MANAGE"));

        mockMvc.perform(post("/api/v1/fines/" + fine.getId() + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINE_NOT_CANCELLABLE"));
    }

    @Test
    void librarianRoleFromRealBootstrapCanCancelFine() throws Exception {
        String email = "controller-fine-cancel-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_LIBRARIAN");
        Fine fine = persistFine(FineStatus.UNPAID);

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(post("/api/v1/fines/" + fine.getId() + "/cancel")
                        .header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fineStatus").value("CANCELLED"));
    }

    @Test
    void adminRoleFromRealBootstrapCanCancelFine() throws Exception {
        String email = "controller-fine-cancel-admin@primatis.test";
        String rawPassword = "Correct-Admin-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_ADMIN");
        Fine fine = persistFine(FineStatus.UNPAID);

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(post("/api/v1/fines/" + fine.getId() + "/cancel")
                        .header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fineStatus").value("CANCELLED"));
    }

    @Test
    void memberRoleFromRealBootstrapCannotCancelFine() throws Exception {
        String email = "controller-fine-cancel-member@primatis.test";
        String rawPassword = "Correct-Member-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_MEMBER");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(post("/api/v1/fines/1/cancel")
                        .header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------

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

    private void persistActiveUserWithRole(String email, String rawPassword, String roleCode) {
        Long[] holder = new Long[1];
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

            holder[0] = user.getId();
        });
        createdUserIds.add(holder[0]);
    }

    /**
     * DEV-09.9 : membre self-service (sans amende), utilisé pour signer un
     * JWT propriétaire — distinct de {@link #persistFine} qui crée son
     * propre utilisateur non réutilisable.
     */
    private AppUser persistUser(String emailPrefix) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(emailPrefix + "-" + System.nanoTime() + "@primatis.test");
            user.setPasswordHash(passwordEncoder.encode("Correct-Password-2026!"));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            createdUserIds.add(user.getId());
            holder[0] = user.getId();
        });
        return entityManager.find(AppUser.class, holder[0]);
    }

    /**
     * DEV-09.9 : contrairement à {@link #persistFine}, réutilise un {@code
     * AppUser} fourni plutôt que d'en créer un nouveau — nécessaire pour
     * construire plusieurs Fines appartenant au même membre (consultation
     * ownership).
     */
    private Fine persistFineForUser(AppUser user, FineStatus status) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(txStatus -> {
            AppUser managedUser = entityManager.find(AppUser.class, user.getId());

            Title title = new Title();
            title.setTitle("Titre de test");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            entityManager.persist(title);
            entityManager.flush();
            createdTitleIds.add(title.getId());

            Copy copy = new Copy();
            copy.setTitle(title);
            copy.setInventoryCode("CONTROLLER-FINE-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            entityManager.persist(copy);
            entityManager.flush();
            createdCopyIds.add(copy.getId());

            Loan loan = new Loan();
            loan.setUser(managedUser);
            loan.setCopy(copy);
            loan.setLoanDate(Instant.now().minusSeconds(3600 * 24 * 10));
            loan.setDueDate(LocalDate.now().minusDays(5));
            loan.setReturnDate(LocalDate.now());
            loan.setLoanStatus(LoanStatus.RETURNED);
            loan.setCreatedAt(Instant.now());
            loan.setUpdatedAt(Instant.now());
            entityManager.persist(loan);
            entityManager.flush();
            createdLoanIds.add(loan.getId());

            Fine fine = new Fine();
            fine.setLoan(loan);
            fine.setAmount(new BigDecimal("5.00"));
            fine.setReason("Motif de test");
            fine.setIssuedAt(Instant.now());
            fine.setFineStatus(status);
            if (status == FineStatus.PAID) {
                fine.setPaidAt(Instant.now());
            }
            if (status == FineStatus.CANCELLED) {
                fine.setCancelledAt(Instant.now());
            }
            entityManager.persist(fine);
            entityManager.flush();
            createdFineIds.add(fine.getId());

            holder[0] = fine.getId();
        });
        return entityManager.find(Fine.class, holder[0]);
    }

    private Fine persistFine(FineStatus status) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(txStatus -> {
            AppUser user = new AppUser();
            user.setEmail("controller-fine-" + System.nanoTime() + "@primatis.test");
            user.setPasswordHash(passwordEncoder.encode("Correct-Password-2026!"));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            createdUserIds.add(user.getId());

            Title title = new Title();
            title.setTitle("Titre de test");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            entityManager.persist(title);
            entityManager.flush();
            createdTitleIds.add(title.getId());

            Copy copy = new Copy();
            copy.setTitle(title);
            copy.setInventoryCode("CONTROLLER-FINE-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            entityManager.persist(copy);
            entityManager.flush();
            createdCopyIds.add(copy.getId());

            Loan loan = new Loan();
            loan.setUser(user);
            loan.setCopy(copy);
            loan.setLoanDate(Instant.now().minusSeconds(3600 * 24 * 10));
            loan.setDueDate(LocalDate.now().minusDays(5));
            loan.setReturnDate(LocalDate.now());
            loan.setLoanStatus(LoanStatus.RETURNED);
            loan.setCreatedAt(Instant.now());
            loan.setUpdatedAt(Instant.now());
            entityManager.persist(loan);
            entityManager.flush();
            createdLoanIds.add(loan.getId());

            Fine fine = new Fine();
            fine.setLoan(loan);
            fine.setAmount(new BigDecimal("5.00"));
            fine.setReason("Motif de test");
            fine.setIssuedAt(Instant.now());
            fine.setFineStatus(status);
            if (status == FineStatus.PAID) {
                fine.setPaidAt(Instant.now());
            }
            if (status == FineStatus.CANCELLED) {
                fine.setCancelledAt(Instant.now());
            }
            entityManager.persist(fine);
            entityManager.flush();
            createdFineIds.add(fine.getId());

            holder[0] = fine.getId();
        });
        return entityManager.find(Fine.class, holder[0]);
    }
}
