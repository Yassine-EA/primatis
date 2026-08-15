package be.primatis.user;

import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.ResidenceResponse;
import be.primatis.user.web.UpdateResidenceRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Vérifie {@link ResidenceService} (DEV-05.8) contre PostgreSQL réel :
 * workflow première résidence / remplacement (DEV-05.8-DEC-01/05/12),
 * historique (DEC-06/DEC-11), not-found (USER/CITY/résidence courante),
 * conflit de période déterministe (DEC-01), et application réelle de
 * {@code @PreAuthorize("hasAuthority(...)")} via le proxy Spring (même
 * principe que {@code RbacMethodSecurityTests}/{@code UserServiceTests}).
 * La traduction {@code DataIntegrityViolationException} → {@code
 * RESIDENCE_PERIOD_CONFLICT} sous course concurrente réelle est couverte
 * séparément par {@code ResidenceServiceConcurrencyTests} (hors
 * {@code @Transactional} partagé, threads réels indispensables).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResidenceServiceTests {

    @Autowired
    private ResidenceService residenceService;

    @Autowired
    private ResidenceRepository residenceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithUserRead() {
        authenticateWith("USER_READ");
    }

    private static void authenticateWithUserProfileManage() {
        authenticateWith("USER_PROFILE_MANAGE");
    }

    private static void authenticateWith(String... authorities) {
        List<GrantedAuthority> grantedAuthorities =
                List.of(authorities).stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateAsAnonymous() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
    }

    // ---------------------------------------------------------------
    // 1. Première résidence
    // ---------------------------------------------------------------

    @Test
    void firstResidenceIsCreatedWithoutClosingAnything() {
        AppUser user = persistUser("residence-service-first@primatis.test");
        City city = persistCity();
        entityManager.flush();

        ResidenceResponse response = residenceService.replaceOwnResidence(
                user.getId(), new UpdateResidenceRequest(city.getId(), "Rue de test", "1", null, null));

        assertThat(response.startDate()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC));
        assertThat(response.endDate()).isNull();
        assertThat(response.address().street()).isEqualTo("Rue de test");
        assertThat(response.address().city().id()).isEqualTo(city.getId());

        List<Residence> history = residenceRepositoryHistory(user.getId());
        assertThat(history).hasSize(1);
    }

    // ---------------------------------------------------------------
    // 2-3-4. Changement d'adresse : ancienne clôturée à today-1, nouvelle courante
    // ---------------------------------------------------------------

    @Test
    void replacingResidenceClosesOldOneYesterdayAndCreatesNewCurrent() {
        AppUser user = persistUser("residence-service-replace@primatis.test");
        City city = persistCity();
        Residence oldCurrent = persistResidence(user, persistAddress(city), LocalDate.now(java.time.ZoneOffset.UTC).minusDays(30), null);
        entityManager.flush();

        ResidenceResponse response = residenceService.replaceOwnResidence(
                user.getId(), new UpdateResidenceRequest(city.getId(), "Nouvelle rue", "2", "B12", "2e étage"));

        assertThat(response.startDate()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC));
        assertThat(response.endDate()).isNull();
        assertThat(response.address().street()).isEqualTo("Nouvelle rue");

        Residence reloadedOld = entityManager.find(Residence.class, oldCurrent.getId());
        assertThat(reloadedOld.getEndDate()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1));

        // 5. historique conservé (ancienne + nouvelle)
        List<Residence> history = residenceRepositoryHistory(user.getId());
        assertThat(history).hasSize(2);
    }

    // ---------------------------------------------------------------
    // 6. historique startDate DESC
    // ---------------------------------------------------------------

    @Test
    void historyIsOrderedByStartDateDescending() {
        AppUser user = persistUser("residence-service-history-order@primatis.test");
        City city = persistCity();
        Residence oldest = persistResidence(user, persistAddress(city),
                LocalDate.now(java.time.ZoneOffset.UTC).minusDays(60), LocalDate.now(java.time.ZoneOffset.UTC).minusDays(31));
        Residence middle = persistResidence(user, persistAddress(city),
                LocalDate.now(java.time.ZoneOffset.UTC).minusDays(30), LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1));
        Residence current = persistResidence(user, persistAddress(city), LocalDate.now(java.time.ZoneOffset.UTC), null);
        entityManager.flush();

        authenticateWithUserRead();
        List<ResidenceResponse> history = residenceService.getResidenceHistory(user.getId());

        assertThat(history).extracting(ResidenceResponse::id)
                .containsExactly(current.getId(), middle.getId(), oldest.getId());
    }

    // ---------------------------------------------------------------
    // 7. aucune résidence courante -> CURRENT_RESIDENCE_NOT_FOUND
    // ---------------------------------------------------------------

    @Test
    void noCurrentResidenceThrowsCurrentResidenceNotFound() {
        AppUser user = persistUser("residence-service-no-current@primatis.test");
        entityManager.flush();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> residenceService.getOwnCurrentResidence(user.getId()))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("CURRENT_RESIDENCE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // 8. historique vide -> []
    // ---------------------------------------------------------------

    @Test
    void emptyHistoryReturnsEmptyList() {
        AppUser user = persistUser("residence-service-empty-history@primatis.test");
        entityManager.flush();

        authenticateWithUserRead();
        assertThat(residenceService.getResidenceHistory(user.getId())).isEmpty();
    }

    // ---------------------------------------------------------------
    // 9. City inexistante -> CITY_NOT_FOUND
    // ---------------------------------------------------------------

    @Test
    void unknownCityThrowsCityNotFound() {
        AppUser user = persistUser("residence-service-unknown-city@primatis.test");
        entityManager.flush();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> residenceService.replaceOwnResidence(
                        user.getId(), new UpdateResidenceRequest(999_999_999L, "Rue", "1", null, null)))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("CITY_NOT_FOUND"));

        // 12. transaction rollback si échec : aucune Address orpheline créée
        assertThat(residenceRepositoryHistory(user.getId())).isEmpty();
    }

    // ---------------------------------------------------------------
    // 10. user inexistant -> USER_NOT_FOUND
    // ---------------------------------------------------------------

    @Test
    void unknownUserThrowsUserNotFound() {
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> residenceService.getOwnCurrentResidence(999_999_999L))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // 11. deuxième changement le même jour -> RESIDENCE_PERIOD_CONFLICT
    // ---------------------------------------------------------------

    @Test
    void secondReplacementSameDayIsRejected() {
        AppUser user = persistUser("residence-service-same-day@primatis.test");
        City city = persistCity();
        persistResidence(user, persistAddress(city), LocalDate.now(java.time.ZoneOffset.UTC), null);
        entityManager.flush();

        assertThatExceptionOfType(BusinessRuleException.class)
                .isThrownBy(() -> residenceService.replaceOwnResidence(
                        user.getId(), new UpdateResidenceRequest(city.getId(), "Autre rue", "3", null, null)))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("RESIDENCE_PERIOD_CONFLICT"));

        // 12. transaction rollback si échec : la résidence courante existante n'est pas altérée
        Residence stillCurrent = residenceRepository.findByUserIdAndEndDateIsNull(user.getId()).orElseThrow();
        assertThat(stillCurrent.getEndDate()).isNull();
        assertThat(residenceRepositoryHistory(user.getId())).hasSize(1);
    }

    // ---------------------------------------------------------------
    // 15-17. Method security USER_READ / USER_PROFILE_MANAGE
    // ---------------------------------------------------------------

    @Test
    void noAuthenticationDeniedForStaffCurrentResidence() {
        authenticateAsAnonymous();
        assertThrows(AccessDeniedException.class, () -> residenceService.getCurrentResidence(1L));
    }

    @Test
    void userReadAloneDoesNotAuthorizeStaffReplace() {
        authenticateWithUserRead();
        assertThrows(AccessDeniedException.class, () -> residenceService.replaceResidence(
                1L, new UpdateResidenceRequest(1L, "Rue", "1", null, null)));
    }

    @Test
    void userProfileManageAuthorizesStaffReplace() {
        AppUser user = persistUser("residence-service-staff-replace@primatis.test");
        City city = persistCity();
        entityManager.flush();

        authenticateWithUserProfileManage();
        ResidenceResponse response = residenceService.replaceResidence(
                user.getId(), new UpdateResidenceRequest(city.getId(), "Rue staff", "5", null, null));

        assertThat(response.startDate()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC));
    }

    @Test
    void userReadAuthorizesStaffReads() {
        AppUser user = persistUser("residence-service-staff-read@primatis.test");
        City city = persistCity();
        persistResidence(user, persistAddress(city), LocalDate.now(java.time.ZoneOffset.UTC), null);
        entityManager.flush();

        authenticateWithUserRead();
        assertThat(residenceService.getCurrentResidence(user.getId())).isNotNull();
        assertThat(residenceService.getResidenceHistory(user.getId())).hasSize(1);
    }

    // ---------------------------------------------------------------
    // Fixtures minimales
    // ---------------------------------------------------------------

    private List<Residence> residenceRepositoryHistory(Long userId) {
        return entityManager
                .createQuery("SELECT r FROM Residence r WHERE r.user.id = :userId ORDER BY r.startDate DESC",
                        Residence.class)
                .setParameter("userId", userId)
                .getResultList();
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

    private City persistCity() {
        Country country = new Country();
        country.setName("Belgique");
        country.setCode("RS" + (System.nanoTime() % 100000));
        entityManager.persist(country);

        City city = new City();
        city.setName("Bruxelles");
        city.setPostalCode("1000");
        city.setCountry(country);
        entityManager.persist(city);
        return city;
    }

    private Address persistAddress(City city) {
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
