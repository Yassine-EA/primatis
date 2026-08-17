package be.primatis.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie contre PostgreSQL réel les primitives {@link ResidenceRepository}
 * ajoutées en DEV-05.2 (résidence courante, historique, détection de
 * chevauchement). La contrainte {@code ck_residence_dates} et l'index unique
 * partiel {@code ux_residence_current_per_user} restent l'autorité DB ; ces
 * tests vérifient uniquement les nouvelles queries, pas les contraintes
 * elles-mêmes (déjà couvertes par {@code ConstraintViolationTests}).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResidenceRepositoryTests {

    @Autowired
    private ResidenceRepository residenceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findsCurrentResidenceWhenOneExists() {
        AppUser user = persistUser("residence-current@primatis.test");
        Address address = persistAddress();
        Residence current = persistResidence(user, address, LocalDate.of(2026, 1, 1), null);
        entityManager.flush();

        assertThat(residenceRepository.findByUserIdAndEndDateIsNull(user.getId()))
                .isPresent()
                .get()
                .extracting(Residence::getId)
                .isEqualTo(current.getId());
    }

    @Test
    void returnsEmptyWhenNoCurrentResidence() {
        AppUser user = persistUser("residence-no-current@primatis.test");
        Address address = persistAddress();
        persistResidence(user, address, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));
        entityManager.flush();

        assertThat(residenceRepository.findByUserIdAndEndDateIsNull(user.getId())).isEmpty();
    }

    @Test
    void findsHistoryForRequestedUserOnlyInDescendingStartDateOrder() {
        AppUser user = persistUser("residence-history@primatis.test");
        AppUser otherUser = persistUser("residence-history-other@primatis.test");
        Address address = persistAddress();
        Residence oldest = persistResidence(user, address, LocalDate.of(2018, 1, 1), LocalDate.of(2019, 12, 31));
        Residence middle = persistResidence(user, address, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 12, 31));
        Residence current = persistResidence(user, address, LocalDate.of(2022, 1, 1), null);
        persistResidence(otherUser, address, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 12, 31));
        entityManager.flush();

        List<Residence> history = residenceRepository.findByUserIdOrderByStartDateDesc(user.getId());

        assertThat(history)
                .extracting(Residence::getId)
                .containsExactly(current.getId(), middle.getId(), oldest.getId());
    }

    @Test
    void overlapClearlyBeforeIsFalse() {
        AppUser user = persistUser("residence-overlap-before@primatis.test");
        Address address = persistAddress();
        persistResidence(user, address, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30));
        entityManager.flush();

        assertThat(residenceRepository.existsOverlappingPeriod(
                user.getId(), LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 30)))
                .isFalse();
    }

    @Test
    void overlapClearlyAfterIsFalse() {
        AppUser user = persistUser("residence-overlap-after@primatis.test");
        Address address = persistAddress();
        persistResidence(user, address, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 6, 30));
        entityManager.flush();

        assertThat(residenceRepository.existsOverlappingPeriod(
                user.getId(), LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 30)))
                .isFalse();
    }

    @Test
    void overlapPartialIsTrue() {
        AppUser user = persistUser("residence-overlap-partial@primatis.test");
        Address address = persistAddress();
        persistResidence(user, address, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 30));
        entityManager.flush();

        assertThat(residenceRepository.existsOverlappingPeriod(
                user.getId(), LocalDate.of(2021, 4, 1), LocalDate.of(2021, 9, 30)))
                .isTrue();
    }

    @Test
    void overlapCandidateIncludedInExistingIsTrue() {
        AppUser user = persistUser("residence-overlap-included@primatis.test");
        Address address = persistAddress();
        persistResidence(user, address, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 12, 31));
        entityManager.flush();

        assertThat(residenceRepository.existsOverlappingPeriod(
                user.getId(), LocalDate.of(2021, 3, 1), LocalDate.of(2021, 6, 30)))
                .isTrue();
    }

    @Test
    void overlapExistingIncludedInCandidateIsTrue() {
        AppUser user = persistUser("residence-overlap-englobante@primatis.test");
        Address address = persistAddress();
        persistResidence(user, address, LocalDate.of(2021, 3, 1), LocalDate.of(2021, 6, 30));
        entityManager.flush();

        assertThat(residenceRepository.existsOverlappingPeriod(
                user.getId(), LocalDate.of(2021, 1, 1), LocalDate.of(2021, 12, 31)))
                .isTrue();
    }

    @Test
    void overlapAgainstOpenEndedCurrentResidenceIsTrue() {
        AppUser user = persistUser("residence-overlap-current@primatis.test");
        Address address = persistAddress();
        persistResidence(user, address, LocalDate.of(2021, 1, 1), null);
        entityManager.flush();

        assertThat(residenceRepository.existsOverlappingPeriod(
                user.getId(), LocalDate.of(2022, 1, 1), null))
                .isTrue();
    }

    @Test
    void overlapDoesNotMatchAnotherUser() {
        AppUser user = persistUser("residence-overlap-owner@primatis.test");
        AppUser otherUser = persistUser("residence-overlap-stranger@primatis.test");
        Address address = persistAddress();
        persistResidence(user, address, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 12, 31));
        entityManager.flush();

        assertThat(residenceRepository.existsOverlappingPeriod(
                otherUser.getId(), LocalDate.of(2021, 3, 1), LocalDate.of(2021, 6, 30)))
                .isFalse();
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

    private Address persistAddress() {
        Country country = new Country();
        country.setName("Belgique");
        country.setCode("RB" + (System.nanoTime() % 100000));
        entityManager.persist(country);

        City city = new City();
        city.setName("Bruxelles");
        city.setPostalCode("1000");
        city.setCountry(country);
        entityManager.persist(city);

        Address address = new Address();
        address.setCity(city);
        address.setStreet("Rue de test");
        address.setStreetNumber("1");
        entityManager.persist(address);
        return address;
    }

    private Residence persistResidence(AppUser user, Address address, LocalDate startDate, LocalDate endDate) {
        Residence residence = new Residence();
        residence.setUser(user);
        residence.setAddress(address);
        residence.setStartDate(startDate);
        residence.setEndDate(endDate);
        entityManager.persist(residence);
        return residence;
    }
}
