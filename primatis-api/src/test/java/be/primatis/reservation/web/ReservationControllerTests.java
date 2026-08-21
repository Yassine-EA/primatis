package be.primatis.reservation.web;

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
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationStatus;
import be.primatis.reservation.dto.CreateOwnReservationRequest;
import be.primatis.reservation.dto.CreateReservationRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP de consultation des Reservations (DEV-08.4) : {@code GET
 * /api/v1/reservations} ({@code RESERVATION_READ}) et {@code GET
 * /api/v1/me/reservations} (authentification seule) — même précédent exact
 * que {@code LoanControllerTests} (DEV-07.4). JWT signés manuellement pour
 * les scénarios anonymous/sans permission/pagination ; connexion réelle
 * avec rôles réellement bootstrapés (V002) pour prouver l'accès via
 * {@code ROLE_LIBRARIAN}/{@code ROLE_ADMIN} et le refus pour
 * {@code ROLE_MEMBER}. Fixtures créées en transaction committée (MockMvc
 * traverse une vraie requête HTTP), nettoyées explicitement en
 * {@code @AfterEach}. Aucun test POST/PATCH/DELETE : DEV-08.4 est
 * strictement consultation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationControllerTests {

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
                // DEV-10.6 : création/annulation crée désormais RESERVATION_CREATED/
                // RESERVATION_CANCELLED (fk_notification_reservation_id, ON DELETE RESTRICT)
                // — supprimée avant la Reservation.
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.id = :id")
                        .setParameter("id", reservationId).executeUpdate();
            }
            for (Long reservationId : createdReservationIds) {
                entityManager.createQuery("DELETE FROM Reservation r WHERE r.id = :id")
                        .setParameter("id", reservationId).executeUpdate();
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
    // GET /api/v1/reservations — staff
    // ---------------------------------------------------------------

    @Test
    void listReservationsWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReservationsAuthenticatedWithoutReservationReadIsForbidden() throws Exception {
        String token = signToken(1L, List.of(), List.of("CATALOGUE_READ"));

        mockMvc.perform(get("/api/v1/reservations").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listReservationsWithReservationReadReturnsDefaultPagedResponse() throws Exception {
        String token = signToken(1L, List.of(), List.of("RESERVATION_READ"));

        mockMvc.perform(get("/api/v1/reservations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listReservationsExposesExpectedReservationShape() throws Exception {
        AppUser member = persistUser("controller-staff-shape@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CONTROLLER-STAFF-SHAPE");
        persistReadyReservation(member, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        String token = signToken(1L, List.of(), List.of("RESERVATION_READ"));

        mockMvc.perform(get("/api/v1/reservations?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.member.id==" + member.getId() + ")].reservationStatus")
                        .value("READY"))
                .andExpect(jsonPath("$.content[?(@.member.id==" + member.getId() + ")].assignedCopy.inventoryCode")
                        .value("CONTROLLER-STAFF-SHAPE"))
                .andExpect(jsonPath("$.content[?(@.member.id==" + member.getId() + ")].title.title")
                        .value("Titre de test"));
    }

    @Test
    void listReservationsRejectsNegativePage() throws Exception {
        String token = signToken(1L, List.of(), List.of("RESERVATION_READ"));

        mockMvc.perform(get("/api/v1/reservations?page=-1").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    @Test
    void listReservationsRejectsZeroSize() throws Exception {
        String token = signToken(1L, List.of(), List.of("RESERVATION_READ"));

        mockMvc.perform(get("/api/v1/reservations?size=0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    @Test
    void listReservationsRejectsSizeAboveMaximum() throws Exception {
        String token = signToken(1L, List.of(), List.of("RESERVATION_READ"));

        mockMvc.perform(get("/api/v1/reservations?size=101").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    /**
     * Chaîne réelle complète (comme {@code LoanControllerTests}) : prouve
     * que les permissions réellement bootstrapées par V002 pour {@code
     * ROLE_LIBRARIAN} incluent bien {@code RESERVATION_READ}.
     */
    @Test
    void librarianRoleFromRealBootstrapCanListReservations() throws Exception {
        String email = "controller-reservation-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_LIBRARIAN");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/reservations").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoleFromRealBootstrapCanListReservations() throws Exception {
        String email = "controller-reservation-admin@primatis.test";
        String rawPassword = "Correct-Admin-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_ADMIN");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/reservations").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isOk());
    }

    /**
     * {@code ROLE_MEMBER} ne porte pas {@code RESERVATION_READ} (bootstrap
     * V002) : même principe que {@code LoanControllerTests} pour
     * {@code LOAN_READ}.
     */
    @Test
    void memberRoleFromRealBootstrapCannotListReservations() throws Exception {
        String email = "controller-reservation-member@primatis.test";
        String rawPassword = "Correct-Member-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_MEMBER");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(get("/api/v1/reservations").header("Authorization", "Bearer " + accessToken.token()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/me/reservations — self
    // ---------------------------------------------------------------

    @Test
    void listOwnReservationsWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me/reservations"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Aucune permission requise pour {@code /me/reservations} : un JWT
     * valide sans {@code RESERVATION_READ} (ni aucune autre permission)
     * suffit.
     */
    @Test
    void listOwnReservationsWithoutAnyPermissionIsAuthorized() throws Exception {
        AppUser self = persistUser("controller-self-no-permission@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/reservations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listOwnReservationsReturnsEmptyPageRatherThanNotFoundWhenNoReservation() throws Exception {
        AppUser self = persistUser("controller-self-empty@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/reservations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listOwnReservationsNeverLeaksAnotherUsersReservation() throws Exception {
        AppUser self = persistUser("controller-self-isolation-1@primatis.test");
        AppUser other = persistUser("controller-self-isolation-2@primatis.test");
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        persistReservation(other, persistTitle(), ReservationStatus.WAITING, Instant.now());
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/reservations?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].member.id").value(self.getId()));
    }

    @Test
    void listOwnReservationsRejectsNegativePage() throws Exception {
        AppUser self = persistUser("controller-self-invalid-page@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/reservations?page=-1").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    @Test
    void listOwnReservationsRejectsSizeAboveMaximum() throws Exception {
        AppUser self = persistUser("controller-self-invalid-size@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(get("/api/v1/me/reservations?size=101").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/me/reservations — self (DEV-08.5, DEV-DEC-0037)
    // ---------------------------------------------------------------

    @Test
    void createOwnReservationWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/me/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createOwnReservationAsAuthenticatedMemberSucceeds() throws Exception {
        AppUser self = persistMember("controller-create-self@primatis.test");
        Title title = persistTitle();
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        String responseBody = mockMvc.perform(post("/api/v1/me/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOwnReservationRequest(title.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationStatus").value("WAITING"))
                .andExpect(jsonPath("$.member.id").value(self.getId()))
                .andExpect(jsonPath("$.assignedCopy").doesNotExist())
                .andExpect(jsonPath("$.fulfilledByLoanId").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        createdReservationIds.add(objectMapper.readTree(responseBody).get("id").asLong());
    }

    /**
     * Le corps de la requête self ne contient jamais {@code userId} —
     * confirmé structurellement (le DTO n'expose que {@code titleId}) : la
     * Reservation créée appartient toujours au membre authentifié, jamais à
     * un tiers, quel que soit le contenu envoyé (JSON additionnel ignoré si
     * présent, jamais interprété comme une désignation de membre).
     */
    @Test
    void createOwnReservationNeverAllowsDesignatingAnotherMemberEvenIfExtraJsonFieldIsSent() throws Exception {
        AppUser self = persistMember("controller-create-self-ownership@primatis.test", "M100000001");
        AppUser other = persistMember("controller-create-self-other@primatis.test", "M100000002");
        Title title = persistTitle();
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        String responseBody = mockMvc.perform(post("/api/v1/me/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleId\":" + title.getId() + ",\"userId\":" + other.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.member.id").value(self.getId()))
                .andReturn().getResponse().getContentAsString();

        createdReservationIds.add(objectMapper.readTree(responseBody).get("id").asLong());
    }

    @Test
    void createOwnReservationRejectsMissingTitleId() throws Exception {
        AppUser self = persistMember("controller-create-self-missing@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(post("/api/v1/me/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createOwnReservationOnUnknownTitleReturnsNotFound() throws Exception {
        AppUser self = persistMember("controller-create-self-unknown-title@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(post("/api/v1/me/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleId\":999999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TITLE_NOT_FOUND"));
    }

    @Test
    void createOwnReservationOnTitleWithAvailableCopyReturnsConflict() throws Exception {
        AppUser self = persistMember("controller-create-self-copy-available@primatis.test");
        Title title = persistTitle();
        persistAvailableCopy(title, "CONTROLLER-CREATE-SELF-AVAILABLE");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(post("/api/v1/me/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOwnReservationRequest(title.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_COPY_AVAILABLE"));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/reservations — staff (DEV-08.5, DEV-DEC-0037)
    // ---------------------------------------------------------------

    @Test
    void createReservationWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"titleId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReservationAuthenticatedWithoutReservationManageIsForbidden() throws Exception {
        String token = signToken(1L, List.of(), List.of("RESERVATION_READ"));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"titleId\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void createReservationRejectsMissingFields() throws Exception {
        String token = signToken(1L, List.of(), List.of("RESERVATION_MANAGE"));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createReservationForDesignatedMemberSucceeds() throws Exception {
        AppUser member = persistMember("controller-create-staff@primatis.test");
        Title title = persistTitle();
        String token = signToken(1L, List.of(), List.of("RESERVATION_MANAGE"));

        String responseBody = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(member.getId(), title.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationStatus").value("WAITING"))
                .andExpect(jsonPath("$.member.id").value(member.getId()))
                .andReturn().getResponse().getContentAsString();

        createdReservationIds.add(objectMapper.readTree(responseBody).get("id").asLong());
    }

    @Test
    void createReservationOnUnknownUserReturnsNotFound() throws Exception {
        Title title = persistTitle();
        String token = signToken(1L, List.of(), List.of("RESERVATION_MANAGE"));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(999999999L, title.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    /**
     * Chaîne réelle complète (comme {@code LoanControllerTests}) : prouve
     * que {@code ROLE_LIBRARIAN}/{@code ROLE_ADMIN} bootstrapés par V002
     * portent bien {@code RESERVATION_MANAGE}, et que {@code ROLE_MEMBER}
     * ne le porte pas.
     */
    @Test
    void librarianRoleFromRealBootstrapCanCreateReservation() throws Exception {
        String email = "controller-reservation-manage-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_LIBRARIAN");
        AppUser member = persistMember("controller-create-librarian-member@primatis.test");
        Title title = persistTitle();

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        String responseBody = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + accessToken.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(member.getId(), title.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        createdReservationIds.add(objectMapper.readTree(responseBody).get("id").asLong());
    }

    @Test
    void memberRoleFromRealBootstrapCannotCreateReservation() throws Exception {
        String email = "controller-reservation-manage-member@primatis.test";
        String rawPassword = "Correct-Member-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_MEMBER");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + accessToken.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"titleId\":1}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // POST /api/v1/me/reservations/{id}/cancel — self (DEV-08.6, DEV-DEC-0038)
    // ---------------------------------------------------------------

    @Test
    void cancelOwnReservationWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/me/reservations/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelOwnReservationOnOwnWaitingReservationSucceeds() throws Exception {
        AppUser self = persistMember("controller-cancel-self-waiting@primatis.test");
        Reservation reservation = persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(post("/api/v1/me/reservations/" + reservation.getId() + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("CANCELLED"));
    }

    @Test
    void cancelOwnReservationOnAnotherMembersReservationReturnsNotFound() throws Exception {
        AppUser self = persistMember("controller-cancel-self-other-self@primatis.test", "M100000003");
        AppUser other = persistMember("controller-cancel-self-other-owner@primatis.test", "M100000004");
        Reservation reservation = persistReservation(other, persistTitle(), ReservationStatus.WAITING, Instant.now());
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(post("/api/v1/me/reservations/" + reservation.getId() + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void cancelOwnReservationOnUnknownReservationReturnsNotFound() throws Exception {
        AppUser self = persistMember("controller-cancel-self-unknown@primatis.test");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(post("/api/v1/me/reservations/999999999/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void cancelOwnReservationOnAlreadyCancelledReservationReturnsConflict() throws Exception {
        AppUser self = persistMember("controller-cancel-self-terminal@primatis.test");
        Reservation reservation = persistReservation(self, persistTitle(), ReservationStatus.CANCELLED, Instant.now());
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"), List.of());

        mockMvc.perform(post("/api/v1/me/reservations/" + reservation.getId() + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_CANCELLABLE"));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/reservations/{id}/cancel — staff (DEV-08.6, DEV-DEC-0038)
    // ---------------------------------------------------------------

    @Test
    void cancelReservationWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelReservationAsStaffWithReservationManageSucceeds() throws Exception {
        AppUser member = persistMember("controller-cancel-staff-manage@primatis.test");
        Reservation reservation = persistReservation(member, persistTitle(), ReservationStatus.WAITING, Instant.now());
        String token = signToken(1L, List.of(), List.of("RESERVATION_MANAGE"));

        mockMvc.perform(post("/api/v1/reservations/" + reservation.getId() + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("CANCELLED"));
    }

    @Test
    void cancelReservationAuthenticatedWithOnlyReservationReadIsForbidden() throws Exception {
        String token = signToken(1L, List.of(), List.of("RESERVATION_READ"));

        mockMvc.perform(post("/api/v1/reservations/1/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void memberRoleFromRealBootstrapCannotCancelReservationAsStaff() throws Exception {
        String email = "controller-reservation-cancel-manage-member@primatis.test";
        String rawPassword = "Correct-Member-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_MEMBER");

        Authentication authentication = authService.login(email, rawPassword);
        AccessToken accessToken = jwtService.generateAccessToken(authentication);

        mockMvc.perform(post("/api/v1/reservations/1/cancel")
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

    private Copy persistAvailableCopy(Title title, String inventoryCode) {
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

    private AppUser persistMember(String email) {
        return persistMember(email, String.format("M%09d", System.nanoTime() % 1_000_000_000L));
    }

    private AppUser persistMember(String email, String memberNumber) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("Correct-Password-2026!"));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setMemberNumber(memberNumber);
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

    private Reservation persistReservation(AppUser user, Title title, ReservationStatus status, Instant reservationDate) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status2 -> {
            AppUser managedUser = entityManager.find(AppUser.class, user.getId());
            Title managedTitle = entityManager.find(Title.class, title.getId());
            Reservation reservation = new Reservation();
            reservation.setUser(managedUser);
            reservation.setTitle(managedTitle);
            reservation.setReservationDate(reservationDate);
            reservation.setReservationStatus(status);
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            entityManager.persist(reservation);
            entityManager.flush();
            holder[0] = reservation.getId();
        });
        createdReservationIds.add(holder[0]);
        return entityManager.find(Reservation.class, holder[0]);
    }

    private Reservation persistReadyReservation(
            AppUser user, Title title, Copy copy, Instant reservationDate, Instant expirationDate) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status2 -> {
            AppUser managedUser = entityManager.find(AppUser.class, user.getId());
            Title managedTitle = entityManager.find(Title.class, title.getId());
            Copy managedCopy = entityManager.find(Copy.class, copy.getId());
            Reservation reservation = new Reservation();
            reservation.setUser(managedUser);
            reservation.setTitle(managedTitle);
            reservation.setReservationDate(reservationDate);
            reservation.setReservationStatus(ReservationStatus.READY);
            reservation.setAssignedCopy(managedCopy);
            reservation.setExpirationDate(expirationDate);
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            entityManager.persist(reservation);
            entityManager.flush();
            holder[0] = reservation.getId();
        });
        createdReservationIds.add(holder[0]);
        return entityManager.find(Reservation.class, holder[0]);
    }
}
