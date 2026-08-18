package be.primatis.loan.web;

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
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.loan.dto.CreateLoanRequest;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationStatus;
import be.primatis.security.AccessToken;
import be.primatis.security.AuthService;
import be.primatis.security.JwtService;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP de consultation des Loans (DEV-07.4) : {@code GET
 * /api/v1/loans} ({@code LOAN_READ}) et {@code GET /api/v1/me/loans}
 * (authentification seule). JWT signés manuellement (même principe que
 * {@code UserControllerTests}) pour les scénarios anonymous/sans
 * permission/pagination ; connexion réelle avec rôles réellement
 * bootstrapés (V002) pour prouver l'accès via {@code ROLE_LIBRARIAN}/
 * {@code ROLE_ADMIN}. Fixtures créées en transaction committée (MockMvc
 * traverse une vraie requête HTTP, pas le contexte de persistance du test),
 * nettoyées explicitement en {@code @AfterEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanControllerTests {

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

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<Long> createdLoanIds = new ArrayList<>();
    private final List<Long> createdReservationIds = new ArrayList<>();
    private final List<Long> createdCopyIds = new ArrayList<>();
    private final List<Long> createdTitleIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanupFixtures() {
        transactionTemplate().executeWithoutResult(status -> {
            for (Long reservationId : createdReservationIds) {
                entityManager.createQuery("DELETE FROM Reservation r WHERE r.id = :id")
                        .setParameter("id", reservationId).executeUpdate();
            }
            for (Long loanId : createdLoanIds) {
                entityManager.createQuery("DELETE FROM Loan l WHERE l.id = :id")
                        .setParameter("id", loanId).executeUpdate();
            }
            for (Long userId : createdUserIds) {
                entityManager.createQuery("DELETE FROM UserRole ur WHERE ur.id.userId = :userId")
                        .setParameter("userId", userId).executeUpdate();
            }
            for (Long copyId : createdCopyIds) {
                entityManager.createQuery("DELETE FROM Copy c WHERE c.id = :id")
                        .setParameter("id", copyId).executeUpdate();
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
    // GET /api/v1/loans — staff
    // ---------------------------------------------------------------

    @Test
    void listLoansWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/loans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listLoansAuthenticatedWithoutLoanReadIsForbidden() throws Exception {
        String token = signToken(1L, List.of(), List.of("CATALOGUE_READ"));

        mockMvc.perform(get("/api/v1/loans").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listLoansWithLoanReadReturnsDefaultPagedResponse() throws Exception {
        String token = signToken(1L, List.of(), List.of("LOAN_READ"));

        mockMvc.perform(get("/api/v1/loans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listLoansExposesExpectedLoanShape() throws Exception {
        AppUser borrower = persistUser("controller-staff-shape@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CONTROLLER-STAFF-SHAPE");
        persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        String token = signToken(1L, List.of(), List.of("LOAN_READ"));

        mockMvc.perform(get("/api/v1/loans?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.borrower.id==" + borrower.getId() + ")].loanStatus")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.content[?(@.borrower.id==" + borrower.getId() + ")].copy.inventoryCode")
                        .value("CONTROLLER-STAFF-SHAPE"))
                .andExpect(jsonPath("$.content[?(@.borrower.id==" + borrower.getId() + ")].borrower.memberNumber")
                        .exists());
    }

    @Test
    void listLoansRejectsNegativePage() throws Exception {
        String token = signToken(1L, List.of(), List.of("LOAN_READ"));

        mockMvc.perform(get("/api/v1/loans?page=-1").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    @Test
    void listLoansRejectsZeroSize() throws Exception {
        String token = signToken(1L, List.of(), List.of("LOAN_READ"));

        mockMvc.perform(get("/api/v1/loans?size=0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    @Test
    void listLoansRejectsSizeAboveMaximum() throws Exception {
        String token = signToken(1L, List.of(), List.of("LOAN_READ"));

        mockMvc.perform(get("/api/v1/loans?size=101").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    /**
     * Chaîne réelle complète (comme {@code UserControllerTests}) : prouve
     * que les permissions réellement bootstrapées par V002 pour {@code
     * ROLE_LIBRARIAN} incluent bien {@code LOAN_READ}.
     */
    @Test
    void librarianRoleFromRealBootstrapCanListLoans() throws Exception {
        String email = "controller-loan-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_LIBRARIAN");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/loans").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoleFromRealBootstrapCanListLoans() throws Exception {
        String email = "controller-loan-admin@primatis.test";
        String rawPassword = "Correct-Admin-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_ADMIN");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/loans").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk());
    }

    /**
     * {@code ROLE_MEMBER} ne porte pas {@code LOAN_READ} (bootstrap V002) :
     * même principe que {@code UserControllerTests} pour {@code USER_READ}.
     */
    @Test
    void memberRoleFromRealBootstrapCannotListLoans() throws Exception {
        String email = "controller-loan-member@primatis.test";
        String rawPassword = "Correct-Member-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_MEMBER");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/loans").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/me/loans — self
    // ---------------------------------------------------------------

    @Test
    void listOwnLoansWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me/loans"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Aucune permission requise pour {@code /me/loans} : un JWT valide sans
     * {@code LOAN_READ} (ni aucune autre permission) suffit.
     */
    @Test
    void listOwnLoansWithoutAnyPermissionIsAuthorized() throws Exception {
        AppUser self = persistUser("controller-self-no-permission@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/loans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listOwnLoansReturnsEmptyPageRatherThanNotFoundWhenNoLoan() throws Exception {
        AppUser self = persistUser("controller-self-empty@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/loans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listOwnLoansNeverLeaksAnotherUsersLoan() throws Exception {
        AppUser self = persistUser("controller-self-isolation-1@primatis.test");
        AppUser other = persistUser("controller-self-isolation-2@primatis.test");
        Title title = persistTitle();
        Copy selfCopy = persistCopy(title, "CONTROLLER-SELF-ISOLATION-1");
        Copy otherCopy = persistCopy(title, "CONTROLLER-SELF-ISOLATION-2");
        persistLoan(self, selfCopy, LoanStatus.ACTIVE, Instant.now());
        persistLoan(other, otherCopy, LoanStatus.ACTIVE, Instant.now());
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/loans?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].borrower.id").value(self.getId()))
                .andExpect(jsonPath("$.content[0].copy.inventoryCode").value("CONTROLLER-SELF-ISOLATION-1"));
    }

    @Test
    void listOwnLoansRejectsNegativePage() throws Exception {
        AppUser self = persistUser("controller-self-invalid-page@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/loans?page=-1").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    @Test
    void listOwnLoansRejectsSizeAboveMaximum() throws Exception {
        AppUser self = persistUser("controller-self-invalid-size@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/loans?size=101").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/loans — staff, LOAN_MANAGE
    // ---------------------------------------------------------------

    @Test
    void registerLoanWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"borrowerUserId\":1,\"copyId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerLoanAuthenticatedWithoutLoanManageIsForbidden() throws Exception {
        String token = signToken(1L, List.of(), List.of("LOAN_READ"));

        mockMvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"borrowerUserId\":1,\"copyId\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void registerLoanRejectsMissingFields() throws Exception {
        String token = signToken(1L, List.of(), List.of("LOAN_MANAGE"));

        mockMvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void registerLoanOnAvailableCopyReturnsCreatedLoan() throws Exception {
        AppUser borrower = persistMember("controller-register-available@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CONTROLLER-REGISTER-AVAILABLE");
        String token = signToken(1L, List.of(), List.of("LOAN_MANAGE"));

        String responseBody = mockMvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLoanRequest(borrower.getId(), copy.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loanStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.returnDate").doesNotExist())
                .andExpect(jsonPath("$.borrower.id").value(borrower.getId()))
                .andExpect(jsonPath("$.copy.id").value(copy.getId()))
                .andReturn().getResponse().getContentAsString();

        Long loanId = objectMapper.readTree(responseBody).get("id").asLong();
        createdLoanIds.add(loanId);
    }

    @Test
    void registerLoanOnUnknownBorrowerReturnsNotFound() throws Exception {
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CONTROLLER-REGISTER-NO-BORROWER");
        String token = signToken(1L, List.of(), List.of("LOAN_MANAGE"));

        mockMvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLoanRequest(999999999L, copy.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void registerLoanOnUnknownCopyReturnsNotFound() throws Exception {
        AppUser borrower = persistMember("controller-register-no-copy@primatis.test");
        String token = signToken(1L, List.of(), List.of("LOAN_MANAGE"));

        mockMvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLoanRequest(borrower.getId(), 999999999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COPY_NOT_FOUND"));
    }

    @Test
    void registerLoanOnUnavailableCopyReturnsConflict() throws Exception {
        AppUser borrower = persistMember("controller-register-unavailable@primatis.test");
        Title title = persistTitle();
        Copy copy = persistUnavailableCopy(title, "CONTROLLER-REGISTER-UNAVAILABLE");
        String token = signToken(1L, List.of(), List.of("LOAN_MANAGE"));

        mockMvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLoanRequest(borrower.getId(), copy.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_NOT_LENDABLE"));
    }

    @Test
    void registerLoanOnReservedCopyFulfillsReadyReservation() throws Exception {
        AppUser borrower = persistMember("controller-register-fulfill@primatis.test");
        Title title = persistTitle();
        Copy copy = persistReservedCopy(title, "CONTROLLER-REGISTER-FULFILL");
        Long reservationId = persistReadyReservation(borrower, title, copy);
        String token = signToken(1L, List.of(), List.of("LOAN_MANAGE"));

        String responseBody = mockMvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLoanRequest(borrower.getId(), copy.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loanStatus").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        Long loanId = objectMapper.readTree(responseBody).get("id").asLong();
        createdLoanIds.add(loanId);

        Reservation reloadedReservation = transactionTemplate()
                .execute(status -> entityManager.find(Reservation.class, reservationId));
        assertThat(reloadedReservation.getReservationStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(reloadedReservation.getFulfilledByLoan().getId()).isEqualTo(loanId);
    }

    /**
     * Chaîne réelle complète : prouve que {@code ROLE_LIBRARIAN} bootstrapé
     * par V002 porte bien {@code LOAN_MANAGE}.
     */
    @Test
    void librarianRoleFromRealBootstrapCanRegisterLoan() throws Exception {
        String email = "controller-loan-manage-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_LIBRARIAN");
        AppUser borrower = persistMember("controller-register-librarian-borrower@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CONTROLLER-REGISTER-LIBRARIAN");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        String responseBody = mockMvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + accessToken.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLoanRequest(borrower.getId(), copy.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        createdLoanIds.add(objectMapper.readTree(responseBody).get("id").asLong());
    }

    /**
     * {@code ROLE_MEMBER} ne porte pas {@code LOAN_MANAGE} (bootstrap V002).
     */
    @Test
    void memberRoleFromRealBootstrapCannotRegisterLoan() throws Exception {
        String email = "controller-loan-manage-member@primatis.test";
        String rawPassword = "Correct-Member-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_MEMBER");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + accessToken.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"borrowerUserId\":1,\"copyId\":1}"))
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
        createdUserIds.add(holder[0].getId());
        return holder[0];
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

    private Title persistTitle() {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status -> {
            Title title = new Title();
            title.setTitle("Titre de test");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            entityManager.persist(title);
            entityManager.flush();
            holder[0] = title.getId();
        });
        createdTitleIds.add(holder[0]);
        return entityManager.find(Title.class, holder[0]);
    }

    private Copy persistCopy(Title title, String inventoryCode) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status -> {
            Title managedTitle = entityManager.find(Title.class, title.getId());
            Copy copy = new Copy();
            copy.setTitle(managedTitle);
            copy.setInventoryCode(inventoryCode);
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            entityManager.persist(copy);
            entityManager.flush();
            holder[0] = copy.getId();
        });
        createdCopyIds.add(holder[0]);
        return entityManager.find(Copy.class, holder[0]);
    }

    private Loan persistLoan(AppUser user, Copy copy, LoanStatus status, Instant loanDate) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status2 -> {
            AppUser managedUser = entityManager.find(AppUser.class, user.getId());
            Copy managedCopy = entityManager.find(Copy.class, copy.getId());
            Loan loan = new Loan();
            loan.setUser(managedUser);
            loan.setCopy(managedCopy);
            loan.setLoanDate(loanDate);
            loan.setDueDate(LocalDate.now().plusDays(21));
            loan.setLoanStatus(status);
            loan.setCreatedAt(Instant.now());
            loan.setUpdatedAt(Instant.now());
            entityManager.persist(loan);
            entityManager.flush();
            holder[0] = loan.getId();
        });
        createdLoanIds.add(holder[0]);
        return entityManager.find(Loan.class, holder[0]);
    }

    private AppUser persistMember(String email) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("Correct-Password-2026!"));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
            user.setMemberStatus(MemberStatus.ACTIVE);
            user.setRegistrationDate(LocalDate.now().minusYears(1));
            user.setMemberExpirationDate(LocalDate.now().plusYears(1));
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            holder[0] = user.getId();
        });
        createdUserIds.add(holder[0]);
        return entityManager.find(AppUser.class, holder[0]);
    }

    private Copy persistUnavailableCopy(Title title, String inventoryCode) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status -> {
            Title managedTitle = entityManager.find(Title.class, title.getId());
            Copy copy = new Copy();
            copy.setTitle(managedTitle);
            copy.setInventoryCode(inventoryCode);
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            entityManager.persist(copy);
            entityManager.flush();
            holder[0] = copy.getId();
        });
        createdCopyIds.add(holder[0]);
        return entityManager.find(Copy.class, holder[0]);
    }

    private Copy persistReservedCopy(Title title, String inventoryCode) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status -> {
            Title managedTitle = entityManager.find(Title.class, title.getId());
            Copy copy = new Copy();
            copy.setTitle(managedTitle);
            copy.setInventoryCode(inventoryCode);
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            entityManager.persist(copy);
            entityManager.flush();
            holder[0] = copy.getId();
        });
        createdCopyIds.add(holder[0]);
        return entityManager.find(Copy.class, holder[0]);
    }

    private Long persistReadyReservation(AppUser user, Title title, Copy assignedCopy) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status -> {
            AppUser managedUser = entityManager.find(AppUser.class, user.getId());
            Title managedTitle = entityManager.find(Title.class, title.getId());
            Copy managedCopy = entityManager.find(Copy.class, assignedCopy.getId());
            Reservation reservation = new Reservation();
            reservation.setUser(managedUser);
            reservation.setTitle(managedTitle);
            reservation.setAssignedCopy(managedCopy);
            reservation.setReservationDate(Instant.now());
            reservation.setExpirationDate(Instant.now().plusSeconds(3600));
            reservation.setReservationStatus(ReservationStatus.READY);
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            entityManager.persist(reservation);
            entityManager.flush();
            holder[0] = reservation.getId();
        });
        createdReservationIds.add(holder[0]);
        return holder[0];
    }
}
