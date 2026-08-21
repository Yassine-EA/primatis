package be.primatis.notification;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie contre PostgreSQL réel les primitives {@link NotificationRepository}
 * ajoutées en DEV-10.3 : {@code findByRecipientUserId} (self-service paginé,
 * futur {@code GET /api/v1/me/notifications}), {@code
 * findByIdAndRecipientUserId} (ownership, futur mark-read individuel), {@code
 * findByRecipientUserIdAndNotificationStatus} (futur mark-all-read,
 * DEV-DEC-0052), {@code countByRecipientUserIdAndNotificationStatus} (compteur
 * UNREAD, DEV-DEC-0051), {@code existsByLoanIdAndNotificationType} (anti-doublon
 * LOAN_DUE_SOON, DEV-DEC-0054). Aucun Service, aucune règle métier, aucun
 * endpoint testés ici — uniquement les primitives Repository elles-mêmes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationRepositoryTests {

    @Autowired
    private NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // findByRecipientUserId (self-service paginé)
    // ---------------------------------------------------------------

    @Test
    void findsAllNotificationsOfARecipientRegardlessOfStatus() {
        AppUser user = persistUser("notif-history-owner@primatis.test");
        Loan loan = persistLoan(user);
        Notification unread = persistNotification(user, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loan);
        Notification read = persistNotification(user, NotificationType.LOAN_RETURNED, NotificationStatus.READ, loan);
        entityManager.flush();

        Page<Notification> page = notificationRepository.findByRecipientUserId(user.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Notification::getId)
                .containsExactlyInAnyOrder(unread.getId(), read.getId());
    }

    @Test
    void isolatesNotificationsBetweenRecipients() {
        AppUser recipientOne = persistUser("notif-isolation-recipient-1@primatis.test");
        AppUser recipientTwo = persistUser("notif-isolation-recipient-2@primatis.test");
        Loan loanOne = persistLoan(recipientOne);
        Loan loanTwo = persistLoan(recipientTwo);
        persistNotification(recipientOne, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loanOne);
        persistNotification(recipientTwo, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loanTwo);
        entityManager.flush();

        Page<Notification> recipientOnePage =
                notificationRepository.findByRecipientUserId(recipientOne.getId(), PageRequest.of(0, 20));

        assertThat(recipientOnePage.getContent()).hasSize(1);
        assertThat(recipientOnePage.getContent().get(0).getRecipientUser().getId()).isEqualTo(recipientOne.getId());
    }

    @Test
    void findByRecipientUserIdReturnsEmptyPageForARecipientWithNoNotification() {
        AppUser user = persistUser("notif-history-none@primatis.test");
        entityManager.flush();

        Page<Notification> page = notificationRepository.findByRecipientUserId(user.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void findByRecipientUserIdRespectsPageSize() {
        AppUser user = persistUser("notif-pagination@primatis.test");
        for (int i = 0; i < 3; i++) {
            Loan loan = persistLoan(user);
            persistNotification(user, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loan);
        }
        entityManager.flush();

        Page<Notification> firstPage =
                notificationRepository.findByRecipientUserId(user.getId(), PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
    }

    @Test
    void findByRecipientUserIdAppliesTheOrderProvidedByTheCaller() {
        AppUser user = persistUser("notif-order@primatis.test");
        Loan loanOldest = persistLoan(user);
        Loan loanNewest = persistLoan(user);
        Notification oldest = persistNotification(user, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loanOldest);
        oldest.setCreatedAt(Instant.now().minusSeconds(60));
        Notification newest = persistNotification(user, NotificationType.LOAN_OVERDUE, NotificationStatus.UNREAD, loanNewest);
        newest.setCreatedAt(Instant.now());
        entityManager.flush();

        Page<Notification> page = notificationRepository.findByRecipientUserId(
                user.getId(), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

        assertThat(page.getContent()).extracting(Notification::getId)
                .containsExactly(newest.getId(), oldest.getId());
    }

    // ---------------------------------------------------------------
    // findByIdAndRecipientUserId (ownership)
    // ---------------------------------------------------------------

    @Test
    void findByIdAndRecipientUserIdReturnsTheNotificationOfTheCorrectRecipient() {
        AppUser user = persistUser("notif-ownership-owner@primatis.test");
        Loan loan = persistLoan(user);
        Notification notification = persistNotification(user, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loan);
        entityManager.flush();

        Optional<Notification> found =
                notificationRepository.findByIdAndRecipientUserId(notification.getId(), user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(notification.getId());
    }

    @Test
    void findByIdAndRecipientUserIdReturnsEmptyForAnotherRecipient() {
        AppUser owner = persistUser("notif-ownership-owner-2@primatis.test");
        AppUser other = persistUser("notif-ownership-other@primatis.test");
        Loan loan = persistLoan(owner);
        Notification notification = persistNotification(owner, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loan);
        entityManager.flush();

        Optional<Notification> found =
                notificationRepository.findByIdAndRecipientUserId(notification.getId(), other.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdAndRecipientUserIdReturnsEmptyForAnUnknownId() {
        AppUser user = persistUser("notif-ownership-unknown@primatis.test");
        entityManager.flush();

        assertThat(notificationRepository.findByIdAndRecipientUserId(999999999L, user.getId())).isEmpty();
    }

    // ---------------------------------------------------------------
    // countByRecipientUserIdAndNotificationStatus (compteur UNREAD, DEV-DEC-0051)
    // ---------------------------------------------------------------

    @Test
    void countsOnlyUnreadNotificationsOfTheRecipient() {
        AppUser user = persistUser("notif-count-owner@primatis.test");
        Loan loanUnread1 = persistLoan(user);
        Loan loanUnread2 = persistLoan(user);
        Loan loanRead = persistLoan(user);
        persistNotification(user, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loanUnread1);
        persistNotification(user, NotificationType.LOAN_OVERDUE, NotificationStatus.UNREAD, loanUnread2);
        persistNotification(user, NotificationType.LOAN_RETURNED, NotificationStatus.READ, loanRead);
        entityManager.flush();

        long count = notificationRepository.countByRecipientUserIdAndNotificationStatus(
                user.getId(), NotificationStatus.UNREAD);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void unreadCountIgnoresNotificationsOfAnotherRecipient() {
        AppUser owner = persistUser("notif-count-owner-2@primatis.test");
        AppUser other = persistUser("notif-count-other@primatis.test");
        Loan loanOwner = persistLoan(owner);
        Loan loanOther = persistLoan(other);
        persistNotification(owner, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loanOwner);
        persistNotification(other, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loanOther);
        entityManager.flush();

        assertThat(notificationRepository.countByRecipientUserIdAndNotificationStatus(
                owner.getId(), NotificationStatus.UNREAD)).isEqualTo(1);
    }

    @Test
    void unreadCountIsZeroForARecipientWithNoNotification() {
        AppUser user = persistUser("notif-count-none@primatis.test");
        entityManager.flush();

        assertThat(notificationRepository.countByRecipientUserIdAndNotificationStatus(
                user.getId(), NotificationStatus.UNREAD)).isZero();
    }

    // ---------------------------------------------------------------
    // findByRecipientUserIdAndNotificationStatus (mark-all-read, DEV-DEC-0052)
    // ---------------------------------------------------------------

    @Test
    void findsOnlyUnreadNotificationsOfTheRecipientForMarkAllAsRead() {
        AppUser user = persistUser("notif-markall-owner@primatis.test");
        Loan loanUnread = persistLoan(user);
        Loan loanRead = persistLoan(user);
        Notification unread = persistNotification(user, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loanUnread);
        persistNotification(user, NotificationType.LOAN_RETURNED, NotificationStatus.READ, loanRead);
        entityManager.flush();

        List<Notification> unreadNotifications = notificationRepository
                .findByRecipientUserIdAndNotificationStatus(user.getId(), NotificationStatus.UNREAD);

        assertThat(unreadNotifications).extracting(Notification::getId).containsExactly(unread.getId());
    }

    @Test
    void findUnreadForMarkAllAsReadIgnoresAnotherRecipient() {
        AppUser owner = persistUser("notif-markall-owner-2@primatis.test");
        AppUser other = persistUser("notif-markall-other@primatis.test");
        Loan loanOwner = persistLoan(owner);
        Loan loanOther = persistLoan(other);
        persistNotification(owner, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loanOwner);
        persistNotification(other, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loanOther);
        entityManager.flush();

        List<Notification> unreadNotifications = notificationRepository
                .findByRecipientUserIdAndNotificationStatus(owner.getId(), NotificationStatus.UNREAD);

        assertThat(unreadNotifications).hasSize(1);
        assertThat(unreadNotifications.get(0).getRecipientUser().getId()).isEqualTo(owner.getId());
    }

    // ---------------------------------------------------------------
    // existsByLoanIdAndNotificationType (anti-doublon LOAN_DUE_SOON, DEV-DEC-0054)
    // ---------------------------------------------------------------

    @Test
    void existsByLoanIdAndNotificationTypeIsTrueWhenALoanDueSoonNotificationExists() {
        AppUser user = persistUser("notif-exists-owner@primatis.test");
        Loan loan = persistLoan(user);
        persistNotification(user, NotificationType.LOAN_DUE_SOON, NotificationStatus.UNREAD, loan);
        entityManager.flush();

        assertThat(notificationRepository.existsByLoanIdAndNotificationType(
                loan.getId(), NotificationType.LOAN_DUE_SOON)).isTrue();
    }

    @Test
    void existsByLoanIdAndNotificationTypeIsFalseWhenOnlyAnotherTypeExistsForTheLoan() {
        AppUser user = persistUser("notif-exists-other-type@primatis.test");
        Loan loan = persistLoan(user);
        persistNotification(user, NotificationType.LOAN_OVERDUE, NotificationStatus.UNREAD, loan);
        entityManager.flush();

        assertThat(notificationRepository.existsByLoanIdAndNotificationType(
                loan.getId(), NotificationType.LOAN_DUE_SOON)).isFalse();
    }

    @Test
    void existsByLoanIdAndNotificationTypeIsFalseForALoanWithNoNotificationAtAll() {
        AppUser user = persistUser("notif-exists-none@primatis.test");
        Loan loan = persistLoan(user);
        entityManager.flush();

        assertThat(notificationRepository.existsByLoanIdAndNotificationType(
                loan.getId(), NotificationType.LOAN_DUE_SOON)).isFalse();
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

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
        copy.setInventoryCode("NOTIF-REPO-" + System.nanoTime());
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }

    private Loan persistLoan(AppUser user) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(persistCopy(persistTitle()));
        loan.setLoanDate(Instant.now());
        loan.setDueDate(LocalDate.now().plusDays(21));
        loan.setLoanStatus(LoanStatus.ACTIVE);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        return loan;
    }

    private Notification persistNotification(
            AppUser recipient, NotificationType type, NotificationStatus status, Loan loan) {
        Notification notification = new Notification();
        notification.setRecipientUser(recipient);
        notification.setLoan(loan);
        notification.setNotificationType(type);
        notification.setTitle("Titre notification test");
        notification.setMessage("Message notification test");
        notification.setNotificationStatus(status);
        notification.setCreatedAt(Instant.now());
        if (status == NotificationStatus.READ) {
            notification.setReadAt(Instant.now());
        }
        entityManager.persist(notification);
        return notification;
    }
}
