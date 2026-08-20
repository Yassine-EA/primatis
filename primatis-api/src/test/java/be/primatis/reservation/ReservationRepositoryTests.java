package be.primatis.reservation;

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
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie contre PostgreSQL réel les primitives {@link ReservationRepository}.
 *
 * <p>DEV-07.6 : {@code findWaitingReservationIdsForTitleOrderedByFifo}
 * (candidat FIFO WAITING, projection {@code id} seul — jamais l'entité,
 * cf. Javadoc de la méthode pour le bug réel corrigé) et {@code
 * findByIdForUpdate} (verrouillage sans ambiguïté par identifiant). La
 * preuve de concurrence réelle du couple candidat+verrou est apportée
 * séparément par {@code LoanServiceConcurrencyTests} (workflow complet
 * {@code registerReturn}) — ce fichier vérifie uniquement la requête
 * elle-même : ordre FIFO, tie-break déterministe, exclusion des statuts
 * non WAITING, isolation entre Titles.
 *
 * <p>DEV-08.2 : {@code countByUserIdAndReservationStatusIn} (comptage
 * actif membre), {@code findByUserId} (consultation self paginée) et
 * {@code findExpiredReservationIds} (identification des candidates READY
 * expirées, sans aucune mutation ni scheduler — cf. Javadoc de la
 * méthode).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationRepositoryTests {

    /**
     * Ensemble d'exclusions non vide requis par
     * {@code findWaitingReservationIdsForTitleOrderedByFifo} (DEV-08.6) —
     * {@code 0L} n'est jamais produit par {@code reservation_seq}.
     */
    private static final List<Long> NO_EXCLUSIONS = List.of(0L);

    @Autowired
    private ReservationRepository reservationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // countByUserIdAndReservationStatusIn
    // ---------------------------------------------------------------

    @Test
    void countsOnlyWaitingAndReadyReservationsForTheGivenMember() {
        AppUser user = persistUser("count-active@primatis.test");

        persistReservation(user, persistTitle(), ReservationStatus.WAITING, Instant.now());

        Copy readyCopy = persistCopy(persistTitle(), "COUNT-READY-1");
        persistReadyReservation(user, readyCopy.getTitle(), readyCopy, Instant.now(), Instant.now().plusSeconds(3600));

        Copy fulfilledCopy = persistCopy(persistTitle(), "COUNT-FULFILLED-1");
        Loan loan = persistReturnedLoan(user, fulfilledCopy);
        persistFulfilledReservation(user, fulfilledCopy.getTitle(), fulfilledCopy, loan, Instant.now().minusSeconds(7200));

        persistReservation(user, persistTitle(), ReservationStatus.CANCELLED, Instant.now());
        persistReservation(user, persistTitle(), ReservationStatus.EXPIRED, Instant.now());
        entityManager.flush();

        long activeCount = reservationRepository.countByUserIdAndReservationStatusIn(
                user.getId(), List.of(ReservationStatus.WAITING, ReservationStatus.READY));

        assertThat(activeCount).isEqualTo(2);
    }

    @Test
    void isolatesTheActiveCountBetweenMembers() {
        AppUser userOne = persistUser("count-iso-1@primatis.test");
        AppUser userTwo = persistUser("count-iso-2@primatis.test");
        persistReservation(userOne, persistTitle(), ReservationStatus.WAITING, Instant.now());
        persistReservation(userTwo, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        assertThat(reservationRepository.countByUserIdAndReservationStatusIn(
                userOne.getId(), List.of(ReservationStatus.WAITING, ReservationStatus.READY)))
                .isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // findByUserId
    // ---------------------------------------------------------------

    @Test
    void returnsOnlyReservationsOfTheRequestedUser() {
        AppUser userOne = persistUser("list-self-1@primatis.test");
        AppUser userTwo = persistUser("list-self-2@primatis.test");
        Reservation ownReservation = persistReservation(userOne, persistTitle(), ReservationStatus.WAITING, Instant.now());
        persistReservation(userTwo, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Page<Reservation> page = reservationRepository.findByUserId(userOne.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Reservation::getId).containsExactly(ownReservation.getId());
    }

    @Test
    void appliesRealPaginationOnSelfListing() {
        AppUser user = persistUser("list-self-page@primatis.test");
        persistReservation(user, persistTitle(), ReservationStatus.WAITING, Instant.now());
        persistReservation(user, persistTitle(), ReservationStatus.CANCELLED, Instant.now());
        persistReservation(user, persistTitle(), ReservationStatus.EXPIRED, Instant.now());
        entityManager.flush();

        Page<Reservation> firstPage = reservationRepository.findByUserId(user.getId(), PageRequest.of(0, 2));
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);

        Page<Reservation> secondPage = reservationRepository.findByUserId(user.getId(), PageRequest.of(1, 2));
        assertThat(secondPage.getContent()).hasSize(1);
    }

    @Test
    void respectsTheSortProvidedByPageable() {
        AppUser user = persistUser("list-self-sort@primatis.test");
        Reservation older = persistReservation(
                user, persistTitle(), ReservationStatus.WAITING, Instant.now().minusSeconds(7200));
        Reservation newer = persistReservation(user, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Page<Reservation> page = reservationRepository.findByUserId(
                user.getId(), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "reservationDate")));

        assertThat(page.getContent()).extracting(Reservation::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    // ---------------------------------------------------------------
    // findExpiredReservationIds
    // ---------------------------------------------------------------

    @Test
    void findsReadyReservationsWhoseExpirationDateIsBeforeTheReferenceInstant() {
        AppUser user = persistUser("expired-past@primatis.test");
        Copy copy = persistCopy(persistTitle(), "EXPIRED-PAST-1");
        Instant referenceInstant = Instant.now();
        Reservation pastReady = persistReadyReservation(
                user, copy.getTitle(), copy, Instant.now().minusSeconds(7200), referenceInstant.minusSeconds(3600));
        entityManager.flush();

        List<Long> expiredIds = reservationRepository.findExpiredReservationIds(
                ReservationStatus.READY, referenceInstant, PageRequest.of(0, 20));

        assertThat(expiredIds).containsExactly(pastReady.getId());
    }

    @Test
    void excludesReadyReservationsExpiringInTheFuture() {
        AppUser user = persistUser("expired-future@primatis.test");
        Copy copy = persistCopy(persistTitle(), "EXPIRED-FUTURE-1");
        Instant referenceInstant = Instant.now();
        persistReadyReservation(user, copy.getTitle(), copy, Instant.now(), referenceInstant.plusSeconds(3600));
        entityManager.flush();

        assertThat(reservationRepository.findExpiredReservationIds(
                ReservationStatus.READY, referenceInstant, PageRequest.of(0, 20)))
                .isEmpty();
    }

    @Test
    void includesAReadyReservationExpiringExactlyAtTheReferenceInstant() {
        AppUser user = persistUser("expired-boundary@primatis.test");
        Copy copy = persistCopy(persistTitle(), "EXPIRED-BOUNDARY-1");
        Instant referenceInstant = Instant.now();
        Reservation boundaryReady = persistReadyReservation(
                user, copy.getTitle(), copy, Instant.now().minusSeconds(60), referenceInstant);
        entityManager.flush();

        assertThat(reservationRepository.findExpiredReservationIds(
                ReservationStatus.READY, referenceInstant, PageRequest.of(0, 20)))
                .containsExactly(boundaryReady.getId());
    }

    @Test
    void excludesNonReadyStatusesFromTheExpiredSearchRegardlessOfExpirationDate() {
        AppUser user = persistUser("expired-statuses@primatis.test");
        Instant pastInstant = Instant.now().minusSeconds(3600);
        Instant referenceInstant = Instant.now();

        // WAITING ne porte jamais d'expirationDate en pratique (persisté sans, comme le fait le
        // Service réel) : confirme qu'elle n'est de toute façon jamais candidate.
        persistReservation(user, persistTitle(), ReservationStatus.WAITING, pastInstant);

        Copy fulfilledCopy = persistCopy(persistTitle(), "EXPIRED-STATUSES-FULFILLED-1");
        Loan loan = persistReturnedLoan(user, fulfilledCopy);
        persistFulfilledReservation(user, fulfilledCopy.getTitle(), fulfilledCopy, loan, pastInstant);

        // CANCELLED/EXPIRED : expirationDate volontairement placée dans le passé pour prouver que
        // seul le filtre de statut exclut ces lignes, pas une absence coïncidente de valeur.
        persistReservation(user, persistTitle(), ReservationStatus.CANCELLED, pastInstant, pastInstant);
        persistReservation(user, persistTitle(), ReservationStatus.EXPIRED, pastInstant, pastInstant);
        entityManager.flush();

        assertThat(reservationRepository.findExpiredReservationIds(
                ReservationStatus.READY, referenceInstant, PageRequest.of(0, 20)))
                .isEmpty();
    }

    @Test
    void ordersExpiredCandidatesByExpirationDateThenIdAsTieBreak() {
        AppUser user = persistUser("expired-order@primatis.test");
        Instant referenceInstant = Instant.now();

        Copy laterCopy = persistCopy(persistTitle(), "EXPIRED-ORDER-LATER-1");
        Reservation later = persistReadyReservation(
                user, laterCopy.getTitle(), laterCopy, Instant.now(), referenceInstant.minusSeconds(60));

        Copy earlierCopy = persistCopy(persistTitle(), "EXPIRED-ORDER-EARLIER-1");
        Reservation earlier = persistReadyReservation(
                user, earlierCopy.getTitle(), earlierCopy, Instant.now(), referenceInstant.minusSeconds(3600));
        entityManager.flush();

        List<Long> expiredIds = reservationRepository.findExpiredReservationIds(
                ReservationStatus.READY, referenceInstant, PageRequest.of(0, 20));

        assertThat(expiredIds).containsExactly(earlier.getId(), later.getId());
    }

    // ---------------------------------------------------------------
    // findWaitingReservationIdsForTitleOrderedByFifo
    // ---------------------------------------------------------------

    @Test
    void returnsTheOldestWaitingReservationFirst() {
        AppUser userOne = persistUser("fifo-oldest-1@primatis.test");
        AppUser userTwo = persistUser("fifo-oldest-2@primatis.test");
        Title title = persistTitle();
        Reservation newer = persistReservation(userOne, title, ReservationStatus.WAITING, Instant.now());
        Reservation older = persistReservation(userTwo, title, ReservationStatus.WAITING, Instant.now().minusSeconds(3600));
        entityManager.flush();

        List<Long> candidateIds = reservationRepository.findWaitingReservationIdsForTitleOrderedByFifo(
                title.getId(), ReservationStatus.WAITING, NO_EXCLUSIONS, PageRequest.of(0, 1));

        assertThat(candidateIds).containsExactly(older.getId());
        assertThat(candidateIds).doesNotContain(newer.getId());
    }

    @Test
    void breaksTiesByIdWhenReservationDateIsEqual() {
        AppUser userOne = persistUser("fifo-tie-1@primatis.test");
        AppUser userTwo = persistUser("fifo-tie-2@primatis.test");
        Title title = persistTitle();
        Instant sameInstant = Instant.now();
        Reservation first = persistReservation(userOne, title, ReservationStatus.WAITING, sameInstant);
        Reservation second = persistReservation(userTwo, title, ReservationStatus.WAITING, sameInstant);
        entityManager.flush();

        List<Long> candidateIds = reservationRepository.findWaitingReservationIdsForTitleOrderedByFifo(
                title.getId(), ReservationStatus.WAITING, NO_EXCLUSIONS, PageRequest.of(0, 1));

        assertThat(candidateIds).containsExactly(Math.min(first.getId(), second.getId()));
    }

    @Test
    void excludesNonWaitingReservations() {
        AppUser user = persistUser("fifo-exclude@primatis.test");
        Title title = persistTitle();
        persistReservation(user, title, ReservationStatus.CANCELLED, Instant.now().minusSeconds(3600));
        entityManager.flush();

        assertThat(reservationRepository.findWaitingReservationIdsForTitleOrderedByFifo(
                title.getId(), ReservationStatus.WAITING, NO_EXCLUSIONS, PageRequest.of(0, 1)))
                .isEmpty();
    }

    @Test
    void isolatesTheFifoCandidateBetweenTitles() {
        AppUser userOne = persistUser("fifo-isolation-1@primatis.test");
        AppUser userTwo = persistUser("fifo-isolation-2@primatis.test");
        Title titleOne = persistTitle();
        Title titleTwo = persistTitle();
        Reservation reservationOne = persistReservation(userOne, titleOne, ReservationStatus.WAITING, Instant.now());
        persistReservation(userTwo, titleTwo, ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        List<Long> candidateIds = reservationRepository.findWaitingReservationIdsForTitleOrderedByFifo(
                titleOne.getId(), ReservationStatus.WAITING, NO_EXCLUSIONS, PageRequest.of(0, 1));

        assertThat(candidateIds).containsExactly(reservationOne.getId());
    }

    /**
     * DEV-08.6 : {@code excludedIds} permet de sauter un candidat WAITING
     * resté WAITING (non modifié) sans le réexaminer — même mécanisme que
     * {@code ReservationAssignmentService} pour un candidat métier non
     * admissible.
     */
    @Test
    void excludedIdsSkipsTheGivenCandidateWithoutModifyingIt() {
        AppUser userOne = persistUser("fifo-excluded-1@primatis.test");
        AppUser userTwo = persistUser("fifo-excluded-2@primatis.test");
        Title title = persistTitle();
        Reservation older = persistReservation(userOne, title, ReservationStatus.WAITING, Instant.now().minusSeconds(3600));
        Reservation newer = persistReservation(userTwo, title, ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        List<Long> candidateIds = reservationRepository.findWaitingReservationIdsForTitleOrderedByFifo(
                title.getId(), ReservationStatus.WAITING, List.of(older.getId()), PageRequest.of(0, 1));

        assertThat(candidateIds).containsExactly(newer.getId());
        Reservation reloadedOlder = entityManager.find(Reservation.class, older.getId());
        assertThat(reloadedOlder.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    // ---------------------------------------------------------------
    // findByIdForUpdate
    // ---------------------------------------------------------------

    @Test
    void findByIdForUpdateReturnsTheReservationWhenItExists() {
        AppUser user = persistUser("lock-existing@primatis.test");
        Title title = persistTitle();
        Reservation reservation = persistReservation(user, title, ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Reservation locked = reservationRepository.findByIdForUpdate(reservation.getId()).orElseThrow();

        assertThat(locked.getId()).isEqualTo(reservation.getId());
        assertThat(locked.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void findByIdForUpdateReturnsEmptyForAnUnknownId() {
        assertThat(reservationRepository.findByIdForUpdate(999999999L)).isEmpty();
    }

    // ---------------------------------------------------------------
    // findCancellationSnapshotById (DEV-08.6)
    // ---------------------------------------------------------------

    @Test
    void findCancellationSnapshotByIdReflectsAWaitingReservationWithNoAssignedCopy() {
        AppUser user = persistUser("snapshot-waiting@primatis.test");
        Title title = persistTitle();
        Reservation reservation = persistReservation(user, title, ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        ReservationCancellationSnapshot snapshot =
                reservationRepository.findCancellationSnapshotById(reservation.getId()).orElseThrow();

        assertThat(snapshot.getId()).isEqualTo(reservation.getId());
        assertThat(snapshot.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
        assertThat(snapshot.getAssignedCopyId()).isNull();
        assertThat(snapshot.getUserId()).isEqualTo(user.getId());
    }

    @Test
    void findCancellationSnapshotByIdReflectsAReadyReservationWithItsAssignedCopy() {
        AppUser user = persistUser("snapshot-ready@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SNAPSHOT-READY-1");
        Reservation reservation = persistReadyReservation(user, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();

        ReservationCancellationSnapshot snapshot =
                reservationRepository.findCancellationSnapshotById(reservation.getId()).orElseThrow();

        assertThat(snapshot.getReservationStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(snapshot.getAssignedCopyId()).isEqualTo(copy.getId());
        assertThat(snapshot.getUserId()).isEqualTo(user.getId());
    }

    @Test
    void findCancellationSnapshotByIdIsEmptyForAnUnknownId() {
        assertThat(reservationRepository.findCancellationSnapshotById(999999999L)).isEmpty();
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
        copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }

    private Loan persistReturnedLoan(AppUser user, Copy copy) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        loan.setLoanDate(now);
        loan.setDueDate(today.plusDays(21));
        loan.setReturnDate(today);
        loan.setLoanStatus(LoanStatus.RETURNED);
        loan.setCreatedAt(now);
        loan.setUpdatedAt(now);
        entityManager.persist(loan);
        return loan;
    }

    private Reservation persistReservation(AppUser user, Title title, ReservationStatus status, Instant reservationDate) {
        return persistReservation(user, title, status, reservationDate, null);
    }

    private Reservation persistReservation(
            AppUser user, Title title, ReservationStatus status, Instant reservationDate, Instant expirationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(status);
        reservation.setExpirationDate(expirationDate);
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
}
