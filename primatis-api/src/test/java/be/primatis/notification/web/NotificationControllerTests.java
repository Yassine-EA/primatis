package be.primatis.notification.web;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.config.JwtProperties;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.notification.Notification;
import be.primatis.notification.NotificationStatus;
import be.primatis.notification.NotificationType;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP self-service Notification (DEV-10.4) : {@code GET
 * /api/v1/me/notifications}, {@code GET
 * /api/v1/me/notifications/unread-count} (DEV-DEC-0051), {@code POST
 * /api/v1/me/notifications/{id}/read}, {@code POST
 * /api/v1/me/notifications/read-all} (DEV-DEC-0052) — même précédent exact
 * que {@code FineControllerTests}. Aucune permission RBAC testée : aucun
 * endpoint Notification n'en requiert (DEV-10.1 §10), contrairement à
 * Fine/Loan/Reservation qui exposent aussi un volet staff. Fixtures créées
 * en transaction committée (MockMvc traverse une vraie requête HTTP),
 * nettoyées explicitement en {@code @AfterEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTests {

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

    private final List<Long> createdNotificationIds = new ArrayList<>();
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
            for (Long notificationId : createdNotificationIds) {
                entityManager.createQuery("DELETE FROM Notification n WHERE n.id = :id")
                        .setParameter("id", notificationId).executeUpdate();
            }
            for (Long loanId : createdLoanIds) {
                entityManager.createQuery("DELETE FROM Loan l WHERE l.id = :id")
                        .setParameter("id", loanId).executeUpdate();
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
    // GET /api/v1/me/notifications
    // ---------------------------------------------------------------

    @Test
    void listOwnNotificationsWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listOwnNotificationsWithoutAnyPermissionIsAuthorized() throws Exception {
        AppUser self = persistUser("controller-notif-own-no-permission");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(get("/api/v1/me/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listOwnNotificationsReturnsEmptyPageRatherThanNotFoundWhenNone() throws Exception {
        AppUser self = persistUser("controller-notif-own-empty");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(get("/api/v1/me/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listOwnNotificationsNeverLeaksAnotherUsersNotification() throws Exception {
        AppUser self = persistUser("controller-notif-own-isolation-self");
        AppUser other = persistUser("controller-notif-own-isolation-other");
        persistNotificationForUser(other, NotificationStatus.UNREAD);
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(get("/api/v1/me/notifications?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listOwnNotificationsExposesFullHistoryIncludingReadStatus() throws Exception {
        AppUser self = persistUser("controller-notif-own-history");
        persistNotificationForUser(self, NotificationStatus.UNREAD);
        persistNotificationForUser(self, NotificationStatus.READ);
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(get("/api/v1/me/notifications?size=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[?(@.notificationStatus=='UNREAD')]").exists())
                .andExpect(jsonPath("$.content[?(@.notificationStatus=='READ')]").exists());
    }

    // ---------------------------------------------------------------
    // GET /api/v1/me/notifications/unread-count (DEV-DEC-0051)
    // ---------------------------------------------------------------

    @Test
    void unreadCountWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unreadCountReturnsOnlyUnreadNotificationsOfTheAuthenticatedUser() throws Exception {
        AppUser self = persistUser("controller-notif-count-self");
        AppUser other = persistUser("controller-notif-count-other");
        persistNotificationForUser(self, NotificationStatus.UNREAD);
        persistNotificationForUser(self, NotificationStatus.UNREAD);
        persistNotificationForUser(self, NotificationStatus.READ);
        persistNotificationForUser(other, NotificationStatus.UNREAD);
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/me/notifications/{id}/read
    // ---------------------------------------------------------------

    @Test
    void markAsReadWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/me/notifications/1/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAsReadOnOwnUnreadNotificationSucceeds() throws Exception {
        AppUser self = persistUser("controller-notif-read-own");
        Notification notification = persistNotificationForUser(self, NotificationStatus.UNREAD);
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(post("/api/v1/me/notifications/" + notification.getId() + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationStatus").value("READ"))
                .andExpect(jsonPath("$.readAt").exists());
    }

    @Test
    void markAsReadOnAlreadyReadNotificationIsIdempotent() throws Exception {
        AppUser self = persistUser("controller-notif-read-idempotent");
        Notification notification = persistNotificationForUser(self, NotificationStatus.READ);
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(post("/api/v1/me/notifications/" + notification.getId() + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationStatus").value("READ"));
    }

    @Test
    void markAsReadOnAnotherUsersNotificationReturnsNotFound() throws Exception {
        AppUser owner = persistUser("controller-notif-read-owner");
        AppUser intruder = persistUser("controller-notif-read-intruder");
        Notification notification = persistNotificationForUser(owner, NotificationStatus.UNREAD);
        String token = signToken(intruder.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(post("/api/v1/me/notifications/" + notification.getId() + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void markAsReadOnUnknownNotificationReturnsNotFound() throws Exception {
        AppUser self = persistUser("controller-notif-read-unknown");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(post("/api/v1/me/notifications/999999999/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/me/notifications/read-all (DEV-DEC-0052)
    // ---------------------------------------------------------------

    @Test
    void markAllAsReadWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/me/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAllAsReadTransitionsAllOwnUnreadNotifications() throws Exception {
        AppUser self = persistUser("controller-notif-markall-self");
        AppUser other = persistUser("controller-notif-markall-other");
        persistNotificationForUser(self, NotificationStatus.UNREAD);
        persistNotificationForUser(self, NotificationStatus.UNREAD);
        Notification otherNotification = persistNotificationForUser(other, NotificationStatus.UNREAD);
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(post("/api/v1/me/notifications/read-all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));

        Notification untouched = entityManager.find(Notification.class, otherNotification.getId());
        assertThat(untouched.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    void markAllAsReadIsIdempotentWhenNoUnreadNotificationExists() throws Exception {
        AppUser self = persistUser("controller-notif-markall-empty");
        String token = signToken(self.getId(), List.of("ROLE_MEMBER"));

        mockMvc.perform(post("/api/v1/me/notifications/read-all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(0));
    }

    // ---------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------

    private String signToken(Long subjectUserId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(String.valueOf(subjectUserId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", roles)
                .claim("permissions", List.of())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private AppUser persistUser(String emailPrefix) {
        Long[] holder = new Long[1];
        transactionTemplate().executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(emailPrefix + "-" + System.nanoTime() + "@primatis.test");
            user.setPasswordHash("hash");
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

    private Notification persistNotificationForUser(AppUser user, NotificationStatus status) {
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
            copy.setInventoryCode("CONTROLLER-NOTIF-" + System.nanoTime());
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
            loan.setLoanDate(Instant.now());
            loan.setDueDate(LocalDate.now().plusDays(21));
            loan.setLoanStatus(LoanStatus.ACTIVE);
            loan.setCreatedAt(Instant.now());
            loan.setUpdatedAt(Instant.now());
            entityManager.persist(loan);
            entityManager.flush();
            createdLoanIds.add(loan.getId());

            Notification notification = new Notification();
            notification.setRecipientUser(managedUser);
            notification.setLoan(loan);
            notification.setNotificationType(NotificationType.LOAN_DUE_SOON);
            notification.setTitle("Titre notification test");
            notification.setMessage("Message notification test");
            notification.setNotificationStatus(status);
            notification.setCreatedAt(Instant.now());
            if (status == NotificationStatus.READ) {
                notification.setReadAt(Instant.now());
            }
            entityManager.persist(notification);
            entityManager.flush();
            createdNotificationIds.add(notification.getId());

            holder[0] = notification.getId();
        });
        return entityManager.find(Notification.class, holder[0]);
    }
}
