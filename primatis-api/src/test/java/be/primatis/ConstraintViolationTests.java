package be.primatis;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.fine.Fine;
import be.primatis.fine.FineStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.notification.Notification;
import be.primatis.notification.NotificationStatus;
import be.primatis.notification.NotificationType;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Prouve, contre PostgreSQL réel, que les contraintes structurelles critiques
 * de V001 rejettent effectivement les états invalides — pas seulement que les
 * annotations JPA/Bean Validation les empêchent (aucune Entity PRIMATIS n'en
 * porte : voir be.primatis.*, package main).
 *
 * Chaque test construit d'abord un état valide (persist + flush réussis),
 * puis tente l'état invalide et vérifie que le flush échoue avec
 * org.hibernate.exception.ConstraintViolationException. Les tests utilisent
 * l'EntityManager JPA directement (pas un Repository Spring @Repository) :
 * la traduction Spring (DataIntegrityViolationException) ne s'applique qu'aux
 * beans interceptés par AOP, donc l'exception observée ici est celle que
 * Hibernate construit en traduisant le SQLSTATE PostgreSQL — preuve directe
 * que le rejet vient de la base, pas d'une validation applicative. Aucune
 * assertion n'est faite après l'échec attendu : la transaction de test est
 * de toute façon annulée en fin de méthode (@Transactional), et la
 * transaction PostgreSQL sous-jacente est elle-même invalidée par l'erreur.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConstraintViolationTests {

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // ux_loan_open_copy
    // ---------------------------------------------------------------

    @Test
    void twoOpenLoansForSameCopyAreRejected() {
        AppUser borrower1 = persistUser("cv-loan-user-1@primatis.test");
        AppUser borrower2 = persistUser("cv-loan-user-2@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CV-LOAN-COPY-1");

        persistLoan(borrower1, copy, LoanStatus.ACTIVE);
        entityManager.flush();

        Loan secondOpenLoan = buildLoan(borrower2, copy, LoanStatus.OVERDUE);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(secondOpenLoan);
                    entityManager.flush();
                });
    }

    // ---------------------------------------------------------------
    // ux_reservation_active_user_title
    // ---------------------------------------------------------------

    @Test
    void twoActiveReservationsForSameUserAndTitleAreRejected() {
        AppUser user = persistUser("cv-reservation-user@primatis.test");
        Title title = persistTitle();

        persistReservation(user, title, ReservationStatus.WAITING, null, null);
        entityManager.flush();

        Reservation secondActiveReservation =
                buildReservation(user, title, ReservationStatus.WAITING, null, null);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(secondActiveReservation);
                    entityManager.flush();
                });
    }

    // ---------------------------------------------------------------
    // ux_reservation_ready_assigned_copy
    // ---------------------------------------------------------------

    @Test
    void twoReadyReservationsForSameCopyAreRejected() {
        AppUser user1 = persistUser("cv-ready-user-1@primatis.test");
        AppUser user2 = persistUser("cv-ready-user-2@primatis.test");
        Title title1 = persistTitle();
        Title title2 = persistTitle();
        Copy copy = persistCopy(title1, "CV-READY-COPY-1");
        Instant expiration = Instant.now().plusSeconds(3600);

        persistReservation(user1, title1, ReservationStatus.READY, copy, expiration);
        entityManager.flush();

        Reservation secondReadyReservation =
                buildReservation(user2, title2, ReservationStatus.READY, copy, expiration);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(secondReadyReservation);
                    entityManager.flush();
                });
    }

    // ---------------------------------------------------------------
    // uq_fine_loan_id
    // ---------------------------------------------------------------

    @Test
    void twoFinesForSameLoanAreRejected() {
        AppUser user = persistUser("cv-fine-loan-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CV-FINE-LOAN-COPY-1");
        Loan loan = persistLoan(user, copy, LoanStatus.RETURNED);

        persistFine(loan, FineStatus.UNPAID, BigDecimal.valueOf(5.00), null, null);
        entityManager.flush();

        Fine secondFine = buildFine(loan, FineStatus.UNPAID, BigDecimal.valueOf(7.50), null, null);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(secondFine);
                    entityManager.flush();
                });
    }

    // ---------------------------------------------------------------
    // ck_fine_amount_positive
    // ---------------------------------------------------------------

    @Test
    void fineWithNonPositiveAmountIsRejected() {
        AppUser user = persistUser("cv-fine-amount-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CV-FINE-AMOUNT-COPY-1");
        Loan loan = persistLoan(user, copy, LoanStatus.RETURNED);

        Fine invalidFine = buildFine(loan, FineStatus.UNPAID, BigDecimal.ZERO, null, null);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(invalidFine);
                    entityManager.flush();
                });
    }

    // ---------------------------------------------------------------
    // ck_notification_exactly_one_origin
    // ---------------------------------------------------------------

    @Test
    void notificationWithZeroOriginsIsRejected() {
        AppUser recipient = persistUser("cv-notif-zero-origin-user@primatis.test");

        Notification notification = buildNotification(
                recipient, NotificationType.ARTICLE_PUBLISHED, NotificationStatus.UNREAD, null);
        // aucune origine (loan/reservation/fine/article) affectée : somme = 0.

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(notification);
                    entityManager.flush();
                });
    }

    @Test
    void notificationWithMultipleOriginsIsRejected() {
        AppUser user = persistUser("cv-notif-multi-origin-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CV-NOTIF-MULTI-COPY-1");
        Loan loan = persistLoan(user, copy, LoanStatus.RETURNED);
        Fine fine = persistFine(loan, FineStatus.UNPAID, BigDecimal.valueOf(5.00), null, null);
        entityManager.flush();

        Notification notification = buildNotification(
                user, NotificationType.FINE_ISSUED, NotificationStatus.UNREAD, null);
        notification.setLoan(loan);
        notification.setFine(fine);
        // deux origines affectées simultanément (loan + fine) : somme = 2.

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(notification);
                    entityManager.flush();
                });
    }

    // ---------------------------------------------------------------
    // ck_notification_read_consistency
    // ---------------------------------------------------------------

    @Test
    void notificationReadWithoutReadAtIsRejected() {
        AppUser user = persistUser("cv-notif-read-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CV-NOTIF-READ-COPY-1");
        Loan loan = persistLoan(user, copy, LoanStatus.RETURNED);
        entityManager.flush();

        Notification notification = buildNotification(
                user, NotificationType.LOAN_RETURNED, NotificationStatus.READ, null);
        notification.setLoan(loan);
        // READ exige read_at IS NOT NULL.

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(notification);
                    entityManager.flush();
                });
    }

    // ---------------------------------------------------------------
    // ck_fine_status_consistency
    // ---------------------------------------------------------------

    @Test
    void finePaidStatusWithoutPaidAtIsRejected() {
        AppUser user = persistUser("cv-fine-status-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CV-FINE-STATUS-COPY-1");
        Loan loan = persistLoan(user, copy, LoanStatus.RETURNED);

        Fine invalidFine = buildFine(loan, FineStatus.PAID, BigDecimal.valueOf(5.00), null, null);
        // PAID exige paid_at IS NOT NULL (et cancelled_at IS NULL).

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(invalidFine);
                    entityManager.flush();
                });
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

    private Copy persistCopy(Title title, String inventoryCode) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(inventoryCode);
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }

    private Loan buildLoan(AppUser user, Copy copy, LoanStatus status) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(Instant.now());
        loan.setDueDate(LocalDate.now().plusDays(21));
        loan.setLoanStatus(status);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        return loan;
    }

    private Loan persistLoan(AppUser user, Copy copy, LoanStatus status) {
        Loan loan = buildLoan(user, copy, status);
        entityManager.persist(loan);
        return loan;
    }

    private Reservation buildReservation(
            AppUser user, Title title, ReservationStatus status, Copy assignedCopy, Instant expirationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(Instant.now());
        reservation.setReservationStatus(status);
        reservation.setAssignedCopy(assignedCopy);
        reservation.setExpirationDate(expirationDate);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        return reservation;
    }

    private Reservation persistReservation(
            AppUser user, Title title, ReservationStatus status, Copy assignedCopy, Instant expirationDate) {
        Reservation reservation = buildReservation(user, title, status, assignedCopy, expirationDate);
        entityManager.persist(reservation);
        return reservation;
    }

    private Fine buildFine(Loan loan, FineStatus status, BigDecimal amount, Instant paidAt, Instant cancelledAt) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(amount);
        fine.setReason("Retard de test");
        fine.setIssuedAt(Instant.now());
        fine.setFineStatus(status);
        fine.setPaidAt(paidAt);
        fine.setCancelledAt(cancelledAt);
        return fine;
    }

    private Fine persistFine(Loan loan, FineStatus status, BigDecimal amount, Instant paidAt, Instant cancelledAt) {
        Fine fine = buildFine(loan, status, amount, paidAt, cancelledAt);
        entityManager.persist(fine);
        return fine;
    }

    private Notification buildNotification(
            AppUser recipient, NotificationType type, NotificationStatus status, Instant readAt) {
        Notification notification = new Notification();
        notification.setRecipientUser(recipient);
        notification.setNotificationType(type);
        notification.setTitle("Titre notification test");
        notification.setMessage("Message notification test");
        notification.setNotificationStatus(status);
        notification.setCreatedAt(Instant.now());
        notification.setReadAt(readAt);
        return notification;
    }
}
