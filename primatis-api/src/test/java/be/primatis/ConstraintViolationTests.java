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

import static org.assertj.core.api.Assertions.assertThat;
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
    // ck_loan_due_date_after_loan_date / ck_loan_return_date_after_loan_date
    // (DEV-07.10 — référence UTC explicite, migration V005, gate final
    // DEV-07 §8 : loan_date::date dépendait de la timezone de session
    // PostgreSQL, jamais UTC dans cet environnement réel)
    // ---------------------------------------------------------------

    /**
     * Reproduit précisément la fenêtre où le jour calendaire Bruxelles est
     * déjà « demain » alors qu'UTC reste « aujourd'hui » : loanDate =
     * 2026-08-18T23:30:00Z (23:30 UTC le 18 = 01:30 à Bruxelles le 19,
     * CEST). Avant V005, {@code loan_date::date} dépendait de la timezone
     * de session PostgreSQL (Europe/Brussels ici, jamais alignée sur UTC
     * dans {@code application.yml}) et valait 2026-08-19 — strictement
     * supérieur à des due_date/return_date calculés en UTC par
     * {@code LoanService} (2026-08-18, {@code Clock.systemUTC()}),
     * rejetant à tort cette combinaison pourtant légitime (bug réel
     * observé DEV-07.9.1). Après V005
     * ({@code (loan_date AT TIME ZONE 'UTC')::date}), la contrainte vaut
     * 2026-08-18, cohérente avec des due_date/return_date UTC : persist +
     * flush doivent réussir — <b>indépendamment de la timezone de
     * session</b>, vérifiée explicitement ci-dessous et jamais modifiée
     * par ce test (aucun {@code ALTER DATABASE}/{@code ALTER ROLE}), pour
     * prouver que le correctif ne dépend pas d'une coïncidence
     * d'environnement. Valeurs temporelles fixes : déterministe, ne
     * dépend jamais de l'heure réelle d'exécution.
     */
    @Test
    void loanAtBrusselsUtcDayBoundaryAcceptsUtcDueAndReturnDates() {
        assertSessionTimezoneIsStillEuropeBrussels();

        AppUser user = persistUser("cv-loan-utc-boundary-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CV-LOAN-UTC-BOUNDARY-1");

        Instant loanDateAtBrusselsNextDay = Instant.parse("2026-08-18T23:30:00Z");
        LocalDate utcDayOfLoanDate = LocalDate.of(2026, 8, 18);

        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(loanDateAtBrusselsNextDay);
        loan.setDueDate(utcDayOfLoanDate); // == jour UTC exact de loanDate (limite due_date >=)
        loan.setReturnDate(utcDayOfLoanDate); // retour le jour même, en UTC
        loan.setLoanStatus(LoanStatus.RETURNED);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());

        entityManager.persist(loan);
        entityManager.flush(); // ne doit lever aucune ConstraintViolationException
    }

    /**
     * Non-régression (§4) : une due_date strictement antérieure au jour
     * UTC de loanDate reste rejetée après V005 — la conversion UTC
     * explicite ne relâche jamais la règle, elle corrige uniquement sa
     * dépendance à la timezone de session.
     */
    @Test
    void loanWithDueDateBeforeUtcDayOfLoanDateIsStillRejected() {
        AppUser user = persistUser("cv-loan-duedate-before-utc-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CV-LOAN-DUEDATE-BEFORE-UTC-1");

        Loan invalidLoan = new Loan();
        invalidLoan.setUser(user);
        invalidLoan.setCopy(copy);
        invalidLoan.setLoanDate(Instant.parse("2026-08-18T23:30:00Z")); // jour UTC = 2026-08-18
        invalidLoan.setDueDate(LocalDate.of(2026, 8, 17)); // avant le jour UTC de loanDate
        invalidLoan.setLoanStatus(LoanStatus.ACTIVE);
        invalidLoan.setCreatedAt(Instant.now());
        invalidLoan.setUpdatedAt(Instant.now());

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(invalidLoan);
                    entityManager.flush();
                });
    }

    /**
     * Non-régression (§4) : une return_date strictement antérieure au
     * jour UTC de loanDate reste rejetée après V005.
     */
    @Test
    void loanWithReturnDateBeforeUtcDayOfLoanDateIsStillRejected() {
        AppUser user = persistUser("cv-loan-returndate-before-utc-user@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "CV-LOAN-RETURNDATE-BEFORE-UTC-1");

        Loan invalidLoan = new Loan();
        invalidLoan.setUser(user);
        invalidLoan.setCopy(copy);
        invalidLoan.setLoanDate(Instant.parse("2026-08-18T23:30:00Z")); // jour UTC = 2026-08-18
        invalidLoan.setDueDate(LocalDate.of(2026, 9, 8));
        invalidLoan.setReturnDate(LocalDate.of(2026, 8, 17)); // avant le jour UTC de loanDate
        invalidLoan.setLoanStatus(LoanStatus.RETURNED);
        invalidLoan.setCreatedAt(Instant.now());
        invalidLoan.setUpdatedAt(Instant.now());

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> {
                    entityManager.persist(invalidLoan);
                    entityManager.flush();
                });
    }

    /**
     * Preuve explicite (§3 de la mission DEV-07.10) que la session
     * PostgreSQL réelle de cet environnement reste {@code Europe/Brussels}
     * — jamais modifiée par ce correctif ({@code ALTER DATABASE}/
     * {@code ALTER ROLE} explicitement interdits). Si cette assertion
     * échouait un jour (environnement reconfiguré en UTC), les tests
     * ci-dessus resteraient corrects : la conversion explicite
     * {@code AT TIME ZONE 'UTC'} de V005 ne dépend d'aucune timezone de
     * session, quelle qu'elle soit.
     */
    private void assertSessionTimezoneIsStillEuropeBrussels() {
        String timezone = (String) entityManager
                .createNativeQuery("SELECT current_setting('TimeZone')")
                .getSingleResult();
        assertThat(timezone).isEqualTo("Europe/Brussels");
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
