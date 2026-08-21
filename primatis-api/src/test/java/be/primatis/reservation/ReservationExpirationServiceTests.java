package be.primatis.reservation;

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
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie {@link ReservationExpirationService} (DEV-08.7, DEV-DEC-0039)
 * contre PostgreSQL réel : sémantique {@code READY → EXPIRED} stricte,
 * conservation de l'historique {@code assignedCopy}/{@code expirationDate}
 * (même principe que {@code READY → CANCELLED}, DEV-DEC-0038), délégation
 * FIFO à {@link ReservationAssignmentService} (aucune duplication,
 * OD-DEV08-08) et admissibilité réévaluée (DEV-08.1 §11).
 *
 * <p>Appelle {@link ReservationExpirationService#expireReservationIfStillDue}
 * directement (candidate déjà identifiée) plutôt que de repasser par
 * {@link ReservationExpirationService#processExpiredReadyReservations} pour
 * chaque scénario métier — cette dernière n'est testée qu'une fois pour sa
 * seule responsabilité propre (délégation par lot à partir de la requête
 * bornée), en miroir du principe déjà appliqué par
 * {@code ReservationServiceTests} (méthode privée commune non testée en
 * elle-même, testée via ses deux points d'entrée).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationExpirationServiceTests {

    @Autowired
    private ReservationExpirationService reservationExpirationService;

    @Autowired
    private Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // expireReservationIfStillDue — sémantique READY → EXPIRED
    // ---------------------------------------------------------------

    @Test
    void expireReservationIfStillDueTransitionsAReadyReservationPastItsExpirationDateToExpired() {
        AppUser owner = persistMember("expiration-past@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-PAST-1");
        Instant expirationDate = clock.instant().minusSeconds(3600);
        Reservation reservation = persistReadyReservation(owner, title, copy, clock.instant().minusSeconds(7200), expirationDate);
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Reservation reloaded = entityManager.find(Reservation.class, reservation.getId());
        assertThat(reloaded.getReservationStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void expireReservationIfStillDueTransitionsWhenExpirationDateEqualsReferenceInstant() {
        AppUser owner = persistMember("expiration-equal@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-EQUAL-1");
        Instant referenceInstant = clock.instant();
        Reservation reservation = persistReadyReservation(owner, title, copy, referenceInstant.minusSeconds(3600), referenceInstant);
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), referenceInstant);

        Reservation reloaded = entityManager.find(Reservation.class, reservation.getId());
        assertThat(reloaded.getReservationStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void expireReservationIfStillDueLeavesAReadyReservationWithFutureExpirationDateUnchanged() {
        AppUser owner = persistMember("expiration-future@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-FUTURE-1");
        Instant expirationDate = clock.instant().plusSeconds(3600);
        Reservation reservation = persistReadyReservation(owner, title, copy, clock.instant(), expirationDate);
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Reservation reloaded = entityManager.find(Reservation.class, reservation.getId());
        assertThat(reloaded.getReservationStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
    }

    @Test
    void expireReservationIfStillDueNeverTransitionsAWaitingReservation() {
        AppUser owner = persistMember("expiration-waiting@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Reservation reservation = persistReservation(owner, title, ReservationStatus.WAITING, clock.instant());
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Reservation reloaded = entityManager.find(Reservation.class, reservation.getId());
        assertThat(reloaded.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void expireReservationIfStillDueIgnoresAnAlreadyCancelledReservation() {
        AppUser owner = persistMember("expiration-cancelled@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Reservation reservation = persistReservation(owner, title, ReservationStatus.CANCELLED, clock.instant().minusSeconds(7200));
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Reservation reloaded = entityManager.find(Reservation.class, reservation.getId());
        assertThat(reloaded.getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void expireReservationIfStillDueIgnoresAnAlreadyExpiredReservation() {
        AppUser owner = persistMember("expiration-already-expired@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Reservation reservation = persistReservation(owner, title, ReservationStatus.EXPIRED, clock.instant().minusSeconds(7200));
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Reservation reloaded = entityManager.find(Reservation.class, reservation.getId());
        assertThat(reloaded.getReservationStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void expireReservationIfStillDueIgnoresAFulfilledReservation() {
        AppUser owner = persistMember("expiration-fulfilled@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-FULFILLED-1");
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        Loan loan = persistLoan(owner, copy);
        Reservation reservation = persistFulfilledReservation(owner, title, copy, loan, clock.instant().minusSeconds(7200));
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Reservation reloaded = entityManager.find(Reservation.class, reservation.getId());
        assertThat(reloaded.getReservationStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ON_LOAN);
    }

    @Test
    void expireReservationIfStillDueOnUnknownIdDoesNothing() {
        reservationExpirationService.expireReservationIfStillDue(999999999L, clock.instant());
    }

    // ---------------------------------------------------------------
    // expireReservationIfStillDue — historique conservé, updatedAt
    // ---------------------------------------------------------------

    @Test
    void expireReservationIfStillDuePreservesAssignedCopyAndExpirationDate() {
        AppUser owner = persistMember("expiration-history@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-HISTORY-1");
        Instant expirationDate = clock.instant().minusSeconds(3600);
        Reservation reservation = persistReadyReservation(owner, title, copy, clock.instant().minusSeconds(7200), expirationDate);
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Reservation reloaded = entityManager.find(Reservation.class, reservation.getId());
        assertThat(reloaded.getAssignedCopy()).isNotNull();
        assertThat(reloaded.getAssignedCopy().getId()).isEqualTo(copy.getId());
        assertThat(reloaded.getExpirationDate()).isEqualTo(expirationDate);
    }

    @Test
    void expireReservationIfStillDueUpdatesUpdatedAt() {
        AppUser owner = persistMember("expiration-updated-at@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-UPDATED-AT-1");
        Reservation reservation = persistReadyReservation(
                owner, title, copy, clock.instant().minusSeconds(7200), clock.instant().minusSeconds(3600));
        reservation.setUpdatedAt(clock.instant().minusSeconds(7200));
        entityManager.flush();
        Instant before = clock.instant();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Instant after = clock.instant();
        Reservation reloaded = entityManager.find(Reservation.class, reservation.getId());
        assertThat(reloaded.getUpdatedAt()).isBetween(before, after);
    }

    // ---------------------------------------------------------------
    // expireReservationIfStillDue — réaffectation FIFO (OD-DEV08-08)
    // ---------------------------------------------------------------

    @Test
    void expireReservationIfStillDueSetsCopyAvailableWhenNoWaitingReservationExists() {
        AppUser owner = persistMember("expiration-available@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-AVAILABLE-1");
        Reservation reservation = persistReadyReservation(
                owner, title, copy, clock.instant().minusSeconds(7200), clock.instant().minusSeconds(3600));
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void expireReservationIfStillDuePromotesNextAdmissibleWaitingReservationAndKeepsCopyReserved() {
        AppUser expiredOwner = persistMember("expiration-promote-owner@primatis.test", MemberStatus.ACTIVE);
        AppUser nextMember = persistMember("expiration-promote-next@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-PROMOTE-1");
        Reservation reservation = persistReadyReservation(
                expiredOwner, title, copy, clock.instant().minusSeconds(7200), clock.instant().minusSeconds(3600));
        Reservation waitingReservation = persistReservation(nextMember, title, ReservationStatus.WAITING, clock.instant().minusSeconds(60));
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
        Reservation reloadedWaiting = entityManager.find(Reservation.class, waitingReservation.getId());
        assertThat(reloadedWaiting.getReservationStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(reloadedWaiting.getAssignedCopy().getId()).isEqualTo(copy.getId());
        assertThat(reloadedWaiting.getExpirationDate()).isNotNull();

        // DEV-10.6 §16 — cas central : une seule transaction candidate produit à la
        // fois RESERVATION_EXPIRED (propriétaire de la READY expirée) et
        // RESERVATION_READY (bénéficiaire de la promotion FIFO suivante), toutes
        // deux persistées, UNREAD, recipients corrects.
        List<Notification> notifications = entityManager
                .createQuery("SELECT n FROM Notification n WHERE n.reservation.id IN :ids", Notification.class)
                .setParameter("ids", List.of(reservation.getId(), waitingReservation.getId()))
                .getResultList();
        assertThat(notifications).hasSize(2);
        assertThat(notifications).allMatch(n -> n.getNotificationStatus() == NotificationStatus.UNREAD);
        assertThat(notifications).filteredOn(n -> n.getNotificationType() == NotificationType.RESERVATION_EXPIRED)
                .extracting(n -> n.getRecipientUser().getId()).containsExactly(expiredOwner.getId());
        assertThat(notifications).filteredOn(n -> n.getNotificationType() == NotificationType.RESERVATION_READY)
                .extracting(n -> n.getRecipientUser().getId()).containsExactly(nextMember.getId());
    }

    @Test
    void expireReservationIfStillDueSkipsANonAdmissibleCandidateAndPromotesTheNextOne() {
        AppUser expiredOwner = persistMember("expiration-fifo-blocked-owner@primatis.test", MemberStatus.ACTIVE);
        AppUser blockedMember = persistMember("expiration-fifo-blocked-first@primatis.test", MemberStatus.BLOCKED);
        AppUser admissibleMember = persistMember("expiration-fifo-blocked-second@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-FIFO-BLOCKED-1");
        Reservation reservation = persistReadyReservation(
                expiredOwner, title, copy, clock.instant().minusSeconds(7200), clock.instant().minusSeconds(3600));
        Reservation blockedWaiting = persistReservation(blockedMember, title, ReservationStatus.WAITING, clock.instant().minusSeconds(120));
        Reservation admissibleWaiting = persistReservation(admissibleMember, title, ReservationStatus.WAITING, clock.instant().minusSeconds(60));
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Reservation reloadedBlocked = entityManager.find(Reservation.class, blockedWaiting.getId());
        Reservation reloadedAdmissible = entityManager.find(Reservation.class, admissibleWaiting.getId());
        assertThat(reloadedBlocked.getReservationStatus())
                .as("le candidat non admissible n'est jamais modifié")
                .isEqualTo(ReservationStatus.WAITING);
        assertThat(reloadedAdmissible.getReservationStatus()).isEqualTo(ReservationStatus.READY);
    }

    @Test
    void expireReservationIfStillDueReadsHoldHoursDynamicallyWhenPromotingNextReservation() {
        AppUser expiredOwner = persistMember("expiration-hold-owner@primatis.test", MemberStatus.ACTIVE);
        AppUser nextMember = persistMember("expiration-hold-next@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-HOLD-1");
        Reservation reservation = persistReadyReservation(
                expiredOwner, title, copy, clock.instant().minusSeconds(7200), clock.instant().minusSeconds(3600));
        Reservation waitingReservation = persistReservation(nextMember, title, ReservationStatus.WAITING, clock.instant().minusSeconds(60));
        updateReservationReadyHoldHoursSetting(1);
        entityManager.flush();
        Instant before = clock.instant();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Instant after = clock.instant();
        Reservation reloadedWaiting = entityManager.find(Reservation.class, waitingReservation.getId());
        assertThat(reloadedWaiting.getExpirationDate()).isBetween(before.plusSeconds(3600), after.plusSeconds(3600));
    }

    /**
     * Aucune source Reservation n'impose qu'une Fine {@code UNPAID} exclue
     * un candidat WAITING de la promotion FIFO (DEV-08.1 §9/§19) — ne
     * jamais transposer la règle Loan, même vérification déjà faite pour
     * l'annulation (DEV-08.6).
     */
    @Test
    void expireReservationIfStillDuePromotesACandidateDespiteAnUnpaidFine() {
        AppUser expiredOwner = persistMember("expiration-fine-owner@primatis.test", MemberStatus.ACTIVE);
        AppUser candidateWithFine = persistMember("expiration-fine-candidate@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "EXPIRATION-FINE-1");
        Copy otherCopy = persistCopy(persistTitle(), "EXPIRATION-FINE-LOAN-1");
        otherCopy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        Loan loan = persistLoan(candidateWithFine, otherCopy);
        persistFine(loan, FineStatus.UNPAID);
        Reservation reservation = persistReadyReservation(
                expiredOwner, title, copy, clock.instant().minusSeconds(7200), clock.instant().minusSeconds(3600));
        Reservation waitingReservation = persistReservation(candidateWithFine, title, ReservationStatus.WAITING, clock.instant());
        entityManager.flush();

        reservationExpirationService.expireReservationIfStillDue(reservation.getId(), clock.instant());

        Reservation reloadedWaiting = entityManager.find(Reservation.class, waitingReservation.getId());
        assertThat(reloadedWaiting.getReservationStatus()).isEqualTo(ReservationStatus.READY);
    }

    // ---------------------------------------------------------------
    // processExpiredReadyReservations — orchestration par lot
    // ---------------------------------------------------------------

    @Test
    void processExpiredReadyReservationsExpiresAllExpiredReadyCandidatesAcrossDifferentTitles() {
        AppUser ownerOne = persistMember("expiration-batch-owner-1@primatis.test", MemberStatus.ACTIVE);
        AppUser ownerTwo = persistMember("expiration-batch-owner-2@primatis.test", MemberStatus.ACTIVE);
        Title titleOne = persistTitle();
        Title titleTwo = persistTitle();
        Copy copyOne = persistCopy(titleOne, "EXPIRATION-BATCH-1");
        Copy copyTwo = persistCopy(titleTwo, "EXPIRATION-BATCH-2");
        Reservation reservationOne = persistReadyReservation(
                ownerOne, titleOne, copyOne, clock.instant().minusSeconds(7200), clock.instant().minusSeconds(3600));
        Reservation reservationTwo = persistReadyReservation(
                ownerTwo, titleTwo, copyTwo, clock.instant().minusSeconds(7200), clock.instant().minusSeconds(1800));
        entityManager.flush();

        reservationExpirationService.processExpiredReadyReservations(clock.instant());

        Reservation reloadedOne = entityManager.find(Reservation.class, reservationOne.getId());
        Reservation reloadedTwo = entityManager.find(Reservation.class, reservationTwo.getId());
        assertThat(reloadedOne.getReservationStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reloadedTwo.getReservationStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void processExpiredReadyReservationsNeverTouchesAWaitingOrFutureReadyReservation() {
        AppUser waitingOwner = persistMember("expiration-batch-waiting@primatis.test", MemberStatus.ACTIVE);
        AppUser futureOwner = persistMember("expiration-batch-future@primatis.test", MemberStatus.ACTIVE);
        Title title = persistTitle();
        Copy futureCopy = persistCopy(title, "EXPIRATION-BATCH-FUTURE-1");
        Reservation waitingReservation = persistReservation(waitingOwner, title, ReservationStatus.WAITING, clock.instant());
        Reservation futureReadyReservation = persistReadyReservation(
                futureOwner, persistTitle(), futureCopy, clock.instant(), clock.instant().plusSeconds(3600));
        entityManager.flush();

        reservationExpirationService.processExpiredReadyReservations(clock.instant());

        Reservation reloadedWaiting = entityManager.find(Reservation.class, waitingReservation.getId());
        Reservation reloadedFuture = entityManager.find(Reservation.class, futureReadyReservation.getId());
        assertThat(reloadedWaiting.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
        assertThat(reloadedFuture.getReservationStatus()).isEqualTo(ReservationStatus.READY);
    }

    // ---------------------------------------------------------------
    // Fixtures
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

    private AppUser persistMember(String email, MemberStatus memberStatus) {
        AppUser user = persistUser(email);
        user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
        user.setMemberStatus(memberStatus);
        user.setRegistrationDate(LocalDate.now(clock).minusYears(1));
        user.setMemberExpirationDate(LocalDate.now(clock).plusYears(1));
        return user;
    }

    private Title persistTitle() {
        Title title = new Title();
        title.setTitle("Titre de test — expiration");
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
        copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }

    private Reservation persistReservation(AppUser user, Title title, ReservationStatus status, Instant reservationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(status);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private Reservation persistReadyReservation(
            AppUser user, Title title, Copy copy, Instant reservationDate, Instant expirationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(ReservationStatus.READY);
        reservation.setAssignedCopy(copy);
        reservation.setExpirationDate(expirationDate);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private Reservation persistFulfilledReservation(
            AppUser user, Title title, Copy copy, Loan loan, Instant reservationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(ReservationStatus.FULFILLED);
        reservation.setAssignedCopy(copy);
        reservation.setExpirationDate(reservationDate.plusSeconds(3600));
        reservation.setFulfilledByLoan(loan);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private Loan persistLoan(AppUser user, Copy copy) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(clock.instant());
        loan.setDueDate(LocalDate.now(clock).plusDays(21));
        loan.setLoanStatus(LoanStatus.ACTIVE);
        loan.setCreatedAt(clock.instant());
        loan.setUpdatedAt(clock.instant());
        entityManager.persist(loan);
        return loan;
    }

    private Fine persistFine(Loan loan, FineStatus status) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(BigDecimal.TEN);
        fine.setReason("Retard de test");
        fine.setIssuedAt(clock.instant());
        fine.setFineStatus(status);
        entityManager.persist(fine);
        return fine;
    }

    private void updateReservationReadyHoldHoursSetting(int value) {
        entityManager.createQuery("UPDATE ApplicationSetting s SET s.settingValue = :value WHERE s.settingKey = :key")
                .setParameter("value", String.valueOf(value))
                .setParameter("key", "RESERVATION_READY_HOLD_HOURS")
                .executeUpdate();
    }
}
