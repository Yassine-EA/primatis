package be.primatis.reservation;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie contre PostgreSQL réel les primitives {@link ReservationRepository}
 * ajoutées en DEV-07.6 : {@code findWaitingReservationIdsForTitleOrderedByFifo}
 * (candidat FIFO WAITING, projection {@code id} seul — jamais l'entité,
 * cf. Javadoc de la méthode pour le bug réel corrigé) et {@code
 * findByIdForUpdate} (verrouillage sans ambiguïté par identifiant). La
 * preuve de concurrence réelle du couple candidat+verrou est apportée
 * séparément par {@code LoanServiceConcurrencyTests} (workflow complet
 * {@code registerReturn}) — ce fichier vérifie uniquement la requête
 * elle-même : ordre FIFO, tie-break déterministe, exclusion des statuts
 * non WAITING, isolation entre Titles.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationRepositoryTests {

    @Autowired
    private ReservationRepository reservationRepository;

    @PersistenceContext
    private EntityManager entityManager;

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
                title.getId(), ReservationStatus.WAITING, PageRequest.of(0, 1));

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
                title.getId(), ReservationStatus.WAITING, PageRequest.of(0, 1));

        assertThat(candidateIds).containsExactly(Math.min(first.getId(), second.getId()));
    }

    @Test
    void excludesNonWaitingReservations() {
        AppUser user = persistUser("fifo-exclude@primatis.test");
        Title title = persistTitle();
        persistReservation(user, title, ReservationStatus.CANCELLED, Instant.now().minusSeconds(3600));
        entityManager.flush();

        assertThat(reservationRepository.findWaitingReservationIdsForTitleOrderedByFifo(
                title.getId(), ReservationStatus.WAITING, PageRequest.of(0, 1)))
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
                titleOne.getId(), ReservationStatus.WAITING, PageRequest.of(0, 1));

        assertThat(candidateIds).containsExactly(reservationOne.getId());
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
}
