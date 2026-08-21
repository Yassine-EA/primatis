package be.primatis.notification;

import be.primatis.article.Article;
import be.primatis.article.ArticleStatus;
import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.Fine;
import be.primatis.fine.FineStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.notification.dto.NotificationMarkAllAsReadResponse;
import be.primatis.notification.dto.NotificationResponse;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie {@link NotificationService} (DEV-10.4) : création par origine
 * (recipient dérivé, état initial, compatibilité type/origine),
 * idempotence {@code LOAN_DUE_SOON} (DEV-DEC-0054), self-service (lecture
 * individuelle, mark-all-read DEV-DEC-0052, compteur UNREAD DEV-DEC-0051,
 * consultation paginée). Appelle directement {@code NotificationService},
 * jamais depuis un Controller ni un domaine métier (aucun événement
 * Loan/Reservation/Fine réel câblé — DEV-10.5+). Classe {@code
 * @Transactional} (rollback de test), même pattern exact que {@code
 * FineServiceTests} : les instants {@code createdAt}/{@code readAt} issus
 * du {@code Clock} injecté (production, {@code Clock.systemUTC()}) sont
 * vérifiés par bornage {@code isBetween(before, after)} et par cohérence
 * interne (même valeur partagée dans un même lot), jamais par une valeur
 * fixe — même convention que {@code FineServiceTests#confirmExternalPaymentSetsPaidAtCloseToNow}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTests {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // createForLoan
    // ---------------------------------------------------------------

    @Test
    void createForLoanPersistsLoanReturnedWithRecipientDerivedFromLoanAndInitialUnreadState() {
        AppUser user = persistUser("notif-svc-loan-returned@primatis.test");
        Loan loan = persistLoan(user, LoanStatus.RETURNED);

        Instant before = Instant.now();
        NotificationResponse response = notificationService.createForLoan(
                loan, NotificationType.LOAN_RETURNED, "Retour enregistré", "Votre prêt a été retourné.");
        Instant after = Instant.now();

        assertThat(response.notificationType()).isEqualTo(NotificationType.LOAN_RETURNED);
        assertThat(response.notificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(response.readAt()).isNull();
        assertThat(response.originId()).isEqualTo(loan.getId());
        assertThat(response.createdAt()).isBetween(before.minusSeconds(1), after.plusSeconds(5));

        Notification persisted = entityManager.find(Notification.class, response.id());
        assertThat(persisted.getRecipientUser().getId()).isEqualTo(user.getId());
        assertThat(persisted.getLoan().getId()).isEqualTo(loan.getId());
        assertThat(persisted.getReservation()).isNull();
        assertThat(persisted.getFine()).isNull();
        assertThat(persisted.getArticle()).isNull();
    }

    @Test
    void createForLoanRejectsTypeIncompatibleWithLoanOrigin() {
        AppUser user = persistUser("notif-svc-loan-invalid-type@primatis.test");
        Loan loan = persistLoan(user, LoanStatus.RETURNED);
        long before = countAllNotifications();

        assertThatThrownBy(() -> notificationService.createForLoan(
                loan, NotificationType.FINE_ISSUED, "Titre", "Message"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(countAllNotifications()).isEqualTo(before);
    }

    @Test
    void createForLoanRejectsBlankTitle() {
        AppUser user = persistUser("notif-svc-loan-blank-title@primatis.test");
        Loan loan = persistLoan(user, LoanStatus.RETURNED);

        assertThatThrownBy(() -> notificationService.createForLoan(
                loan, NotificationType.LOAN_RETURNED, "  ", "Message"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createForLoanRejectsBlankMessage() {
        AppUser user = persistUser("notif-svc-loan-blank-message@primatis.test");
        Loan loan = persistLoan(user, LoanStatus.RETURNED);

        assertThatThrownBy(() -> notificationService.createForLoan(
                loan, NotificationType.LOAN_RETURNED, "Titre", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------
    // createLoanDueSoonIfAbsent (DEV-DEC-0054)
    // ---------------------------------------------------------------

    @Test
    void createLoanDueSoonIfAbsentCreatesTheFirstNotification() {
        AppUser user = persistUser("notif-svc-due-soon-first@primatis.test");
        Loan loan = persistLoan(user, LoanStatus.ACTIVE);

        Optional<NotificationResponse> response = notificationService.createLoanDueSoonIfAbsent(
                loan, "Échéance proche", "Votre prêt arrive à échéance dans 3 jours.");

        assertThat(response).isPresent();
        assertThat(response.get().notificationType()).isEqualTo(NotificationType.LOAN_DUE_SOON);
        assertThat(notificationRepository.existsByLoanIdAndNotificationType(loan.getId(), NotificationType.LOAN_DUE_SOON))
                .isTrue();
    }

    @Test
    void createLoanDueSoonIfAbsentIsANoOpWhenOneAlreadyExistsForTheLoan() {
        AppUser user = persistUser("notif-svc-due-soon-dup@primatis.test");
        Loan loan = persistLoan(user, LoanStatus.ACTIVE);
        notificationService.createLoanDueSoonIfAbsent(loan, "Échéance proche", "Message initial.");
        entityManager.flush();
        long countAfterFirst = countAllNotifications();

        Optional<NotificationResponse> secondAttempt = notificationService.createLoanDueSoonIfAbsent(
                loan, "Échéance proche", "Message rejoué par le futur scheduler.");

        assertThat(secondAttempt).isEmpty();
        assertThat(countAllNotifications()).isEqualTo(countAfterFirst);
    }

    // ---------------------------------------------------------------
    // createForReservation
    // ---------------------------------------------------------------

    @Test
    void createForReservationPersistsReservationCreatedWithRecipientDerivedFromReservation() {
        AppUser user = persistUser("notif-svc-reservation-created@primatis.test");
        Title title = persistTitle();
        Reservation reservation = persistReservation(user, title);

        NotificationResponse response = notificationService.createForReservation(
                reservation, NotificationType.RESERVATION_CREATED, "Réservation créée", "Votre réservation a été enregistrée.");

        assertThat(response.notificationType()).isEqualTo(NotificationType.RESERVATION_CREATED);
        assertThat(response.notificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(response.originId()).isEqualTo(reservation.getId());

        Notification persisted = entityManager.find(Notification.class, response.id());
        assertThat(persisted.getRecipientUser().getId()).isEqualTo(user.getId());
        assertThat(persisted.getReservation().getId()).isEqualTo(reservation.getId());
    }

    @Test
    void createForReservationRejectsTypeIncompatibleWithReservationOrigin() {
        AppUser user = persistUser("notif-svc-reservation-invalid@primatis.test");
        Title title = persistTitle();
        Reservation reservation = persistReservation(user, title);

        assertThatThrownBy(() -> notificationService.createForReservation(
                reservation, NotificationType.LOAN_RETURNED, "Titre", "Message"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------
    // createForFine
    // ---------------------------------------------------------------

    @Test
    void createForFinePersistsFineIssuedWithRecipientDerivedFromLoanBorrower() {
        AppUser user = persistUser("notif-svc-fine-issued@primatis.test");
        Loan loan = persistLoan(user, LoanStatus.RETURNED);
        Fine fine = persistFine(loan, FineStatus.UNPAID);

        NotificationResponse response = notificationService.createForFine(
                fine, NotificationType.FINE_ISSUED, "Amende émise", "Une amende vous a été appliquée.");

        assertThat(response.notificationType()).isEqualTo(NotificationType.FINE_ISSUED);
        assertThat(response.originId()).isEqualTo(fine.getId());

        Notification persisted = entityManager.find(Notification.class, response.id());
        assertThat(persisted.getRecipientUser().getId()).isEqualTo(user.getId());
        assertThat(persisted.getFine().getId()).isEqualTo(fine.getId());
    }

    @Test
    void createForFineRejectsTypeIncompatibleWithFineOrigin() {
        AppUser user = persistUser("notif-svc-fine-invalid@primatis.test");
        Loan loan = persistLoan(user, LoanStatus.RETURNED);
        Fine fine = persistFine(loan, FineStatus.UNPAID);

        assertThatThrownBy(() -> notificationService.createForFine(
                fine, NotificationType.RESERVATION_READY, "Titre", "Message"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------
    // createForArticle
    // ---------------------------------------------------------------

    @Test
    void createForArticlePersistsArticlePublishedForTheGivenRecipient() {
        AppUser author = persistUser("notif-svc-article-author@primatis.test");
        AppUser recipient = persistUser("notif-svc-article-recipient@primatis.test");
        Article article = persistArticle(author);

        NotificationResponse response = notificationService.createForArticle(
                article, recipient, "Nouvel article", "Un nouvel article a été publié.");

        assertThat(response.notificationType()).isEqualTo(NotificationType.ARTICLE_PUBLISHED);
        assertThat(response.originId()).isEqualTo(article.getId());

        Notification persisted = entityManager.find(Notification.class, response.id());
        assertThat(persisted.getRecipientUser().getId()).isEqualTo(recipient.getId());
        assertThat(persisted.getArticle().getId()).isEqualTo(article.getId());
    }

    // ---------------------------------------------------------------
    // markAsRead — individuel, idempotence READ -> READ, ownership
    // ---------------------------------------------------------------

    @Test
    void marksAnUnreadNotificationAsReadAndSetsReadAt() {
        AppUser user = persistUser("notif-svc-read-unread@primatis.test");
        Loan loan = persistLoan(user, LoanStatus.RETURNED);
        NotificationResponse created = notificationService.createForLoan(
                loan, NotificationType.LOAN_RETURNED, "Titre", "Message");

        Instant before = Instant.now();
        NotificationResponse read = notificationService.markAsRead(user.getId(), created.id());
        Instant after = Instant.now();

        assertThat(read.notificationStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(read.readAt()).isBetween(before.minusSeconds(1), after.plusSeconds(5));
    }

    @Test
    void secondMarkAsReadOnAnAlreadyReadNotificationIsIdempotentAndKeepsTheOriginalReadAt() {
        AppUser user = persistUser("notif-svc-read-idempotent@primatis.test");
        Loan loan = persistLoan(user, LoanStatus.RETURNED);
        NotificationResponse created = notificationService.createForLoan(
                loan, NotificationType.LOAN_RETURNED, "Titre", "Message");
        NotificationResponse firstRead = notificationService.markAsRead(user.getId(), created.id());

        NotificationResponse secondRead = notificationService.markAsRead(user.getId(), created.id());

        assertThat(secondRead.notificationStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(secondRead.readAt()).isEqualTo(firstRead.readAt());
    }

    @Test
    void markAsReadOnAnotherUsersNotificationThrowsResourceNotFound() {
        AppUser owner = persistUser("notif-svc-read-owner@primatis.test");
        AppUser other = persistUser("notif-svc-read-other@primatis.test");
        Loan loan = persistLoan(owner, LoanStatus.RETURNED);
        NotificationResponse created = notificationService.createForLoan(
                loan, NotificationType.LOAN_RETURNED, "Titre", "Message");

        assertThatThrownBy(() -> notificationService.markAsRead(other.getId(), created.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        Notification untouched = entityManager.find(Notification.class, created.id());
        assertThat(untouched.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    void markAsReadOnAnUnknownNotificationThrowsResourceNotFound() {
        AppUser user = persistUser("notif-svc-read-unknown@primatis.test");

        assertThatThrownBy(() -> notificationService.markAsRead(user.getId(), 999999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------
    // markAllAsRead (DEV-DEC-0052)
    // ---------------------------------------------------------------

    @Test
    void marksAllUnreadNotificationsOfTheUserAsReadWithASharedReadAt() {
        AppUser user = persistUser("notif-svc-markall-owner@primatis.test");
        Loan loanOne = persistLoan(user, LoanStatus.RETURNED);
        Loan loanTwo = persistLoan(user, LoanStatus.ACTIVE);
        notificationService.createForLoan(loanOne, NotificationType.LOAN_RETURNED, "Titre 1", "Message 1");
        notificationService.createLoanDueSoonIfAbsent(loanTwo, "Titre 2", "Message 2");

        NotificationMarkAllAsReadResponse result = notificationService.markAllAsRead(user.getId());

        assertThat(result.updatedCount()).isEqualTo(2);
        Page<Notification> page = notificationRepository.findByRecipientUserId(user.getId(), PageRequest.of(0, 20));
        List<Instant> readAtValues = page.getContent().stream().map(Notification::getReadAt).distinct().toList();
        assertThat(readAtValues).hasSize(1);
        assertThat(page.getContent()).allMatch(n -> n.getNotificationStatus() == NotificationStatus.READ);
    }

    @Test
    void markAllAsReadPreservesTheReadAtOfAlreadyReadNotifications() {
        AppUser user = persistUser("notif-svc-markall-preserve@primatis.test");
        Loan loanRead = persistLoan(user, LoanStatus.RETURNED);
        Loan loanUnread = persistLoan(user, LoanStatus.ACTIVE);
        NotificationResponse alreadyRead = notificationService.createForLoan(
                loanRead, NotificationType.LOAN_RETURNED, "Titre", "Message");
        NotificationResponse firstRead = notificationService.markAsRead(user.getId(), alreadyRead.id());
        notificationService.createLoanDueSoonIfAbsent(loanUnread, "Titre 2", "Message 2");

        notificationService.markAllAsRead(user.getId());

        Notification stillReadFromBefore = entityManager.find(Notification.class, alreadyRead.id());
        assertThat(stillReadFromBefore.getReadAt()).isEqualTo(firstRead.readAt());
    }

    @Test
    void markAllAsReadDoesNotAffectAnotherUsersNotifications() {
        AppUser owner = persistUser("notif-svc-markall-owner-2@primatis.test");
        AppUser other = persistUser("notif-svc-markall-other@primatis.test");
        Loan loanOwner = persistLoan(owner, LoanStatus.ACTIVE);
        Loan loanOther = persistLoan(other, LoanStatus.ACTIVE);
        notificationService.createLoanDueSoonIfAbsent(loanOwner, "Titre", "Message");
        NotificationResponse otherNotification =
                notificationService.createLoanDueSoonIfAbsent(loanOther, "Titre", "Message").orElseThrow();

        notificationService.markAllAsRead(owner.getId());

        Notification untouched = entityManager.find(Notification.class, otherNotification.id());
        assertThat(untouched.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    void markAllAsReadIsIdempotentWhenNoUnreadNotificationExists() {
        AppUser user = persistUser("notif-svc-markall-empty@primatis.test");

        NotificationMarkAllAsReadResponse result = notificationService.markAllAsRead(user.getId());

        assertThat(result.updatedCount()).isZero();
    }

    // ---------------------------------------------------------------
    // countUnread (DEV-DEC-0051) / listOwnNotifications
    // ---------------------------------------------------------------

    @Test
    void countUnreadCountsOnlyUnreadNotificationsOfTheCurrentUser() {
        AppUser user = persistUser("notif-svc-count-owner@primatis.test");
        Loan loanUnread = persistLoan(user, LoanStatus.ACTIVE);
        Loan loanRead = persistLoan(user, LoanStatus.RETURNED);
        notificationService.createLoanDueSoonIfAbsent(loanUnread, "Titre", "Message");
        NotificationResponse toRead = notificationService.createForLoan(
                loanRead, NotificationType.LOAN_RETURNED, "Titre", "Message");
        notificationService.markAsRead(user.getId(), toRead.id());

        assertThat(notificationService.countUnread(user.getId())).isEqualTo(1);
    }

    @Test
    void listOwnNotificationsReturnsAPagedResultScopedToTheCurrentUser() {
        AppUser owner = persistUser("notif-svc-list-owner@primatis.test");
        AppUser other = persistUser("notif-svc-list-other@primatis.test");
        Loan loanOwner = persistLoan(owner, LoanStatus.RETURNED);
        Loan loanOther = persistLoan(other, LoanStatus.RETURNED);
        notificationService.createForLoan(loanOwner, NotificationType.LOAN_RETURNED, "Titre", "Message");
        notificationService.createForLoan(loanOther, NotificationType.LOAN_RETURNED, "Titre", "Message");

        Page<NotificationResponse> page = notificationService.listOwnNotifications(owner.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).originId()).isEqualTo(loanOwner.getId());
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private long countAllNotifications() {
        return ((Number) entityManager.createQuery("SELECT count(n) FROM Notification n").getSingleResult()).longValue();
    }

    private AppUser persistUser(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        entityManager.persist(user);
        return user;
    }

    private Title persistTitle() {
        Title title = new Title();
        title.setTitle("Titre de test");
        title.setLanguage(Language.FR);
        title.setTitleStatus(TitleStatus.ACTIVE);
        title.setCreatedAt(Instant.now());
        title.setUpdatedAt(Instant.now());
        entityManager.persist(title);
        return title;
    }

    private Copy persistCopy(Title title) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode("NOTIF-SVC-" + System.nanoTime());
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }

    private Loan persistLoan(AppUser user, LoanStatus status) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(persistCopy(persistTitle()));
        loan.setLoanDate(Instant.now());
        loan.setDueDate(LocalDate.now().plusDays(21));
        if (status == LoanStatus.RETURNED) {
            loan.setReturnDate(LocalDate.now());
        }
        loan.setLoanStatus(status);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        return loan;
    }

    private Reservation persistReservation(AppUser user, Title title) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(Instant.now());
        reservation.setReservationStatus(ReservationStatus.WAITING);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private Fine persistFine(Loan loan, FineStatus status) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(java.math.BigDecimal.valueOf(5.00));
        fine.setReason("Retard de test");
        fine.setIssuedAt(Instant.now());
        fine.setFineStatus(status);
        entityManager.persist(fine);
        return fine;
    }

    private Article persistArticle(AppUser author) {
        Article article = new Article();
        article.setAuthorUser(author);
        article.setTitle("Article de test");
        article.setContent("<p>Contenu de test.</p>");
        article.setSlug("article-test-" + System.nanoTime());
        article.setArticleStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(Instant.now());
        article.setCreatedAt(Instant.now());
        article.setUpdatedAt(Instant.now());
        entityManager.persist(article);
        return article;
    }
}
