package be.primatis.user;

import be.primatis.exception.BusinessRuleException;
import be.primatis.user.web.UpdateResidenceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preuve de concurrence réelle pour DEV-05.8 (§2 de l'autorisation
 * d'implémentation) : deux threads tentent simultanément de créer la
 * "première résidence" du même utilisateur (aucune résidence courante au
 * départ, donc aucun des deux contrôles applicatifs de
 * {@code ResidenceService.doReplaceResidence} — comparaison de dates,
 * {@code existsOverlappingPeriod} — ne peut détecter le conflit à l'avance :
 * les deux lectures se font avant que l'un ou l'autre n'ait écrit). Seule la
 * contrainte PostgreSQL {@code ux_residence_current_per_user} peut donc
 * arbitrer. Ce test prouve que la course concurrente ne produit ni double
 * résidence courante ni HTTP 500 exposé : exactement un succès et un rejet
 * propre {@code RESIDENCE_PERIOD_CONFLICT} (traduction ciblée de {@link
 * org.springframework.dao.DataIntegrityViolationException}, §2 de
 * l'autorisation — aucun verrou pessimiste introduit).
 *
 * Volontairement SANS {@code @Transactional} de classe : chaque thread doit
 * ouvrir sa propre transaction/connexion réelle pour que la contrainte
 * PostgreSQL arbitre effectivement entre deux transactions concurrentes
 * distinctes (même principe que {@code AuthServiceConcurrencyTests}).
 * Nettoyage manuel explicite en fin de test.
 */
@SpringBootTest
@ActiveProfiles("test")
class ResidenceServiceConcurrencyTests {

    @Autowired
    private ResidenceService residenceService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ResidenceRepository residenceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentFirstResidenceCreationYieldsExactlyOneSuccessAndOneCleanConflict()
            throws InterruptedException, ExecutionException {
        String email = "residence-concurrency-first@primatis.test";
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 2;

        Long userId = transactionTemplate.execute(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash("hash");
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            return user.getId();
        });

        Long cityId = transactionTemplate.execute(status -> {
            Country country = new Country();
            country.setName("Belgique");
            country.setCode("RC" + (System.nanoTime() % 100000));
            countryRepository.save(country);

            City city = new City();
            city.setName("Bruxelles");
            city.setPostalCode("1000");
            city.setCountry(country);
            cityRepository.save(city);
            return city.getId();
        });

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                int threadIndex = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        residenceService.replaceOwnResidence(userId, new UpdateResidenceRequest(
                                cityId, "Rue concurrente", String.valueOf(threadIndex), null, null));
                        return "SUCCESS";
                    } catch (BusinessRuleException ex) {
                        return ex.getCode();
                    }
                }));
            }

            ready.await();
            go.countDown();
            Map<String, Long> outcomeCounts = new HashMap<>();
            for (Future<String> future : futures) {
                outcomeCounts.merge(future.get(), 1L, Long::sum);
            }
            executor.shutdown();

            assertThat(outcomeCounts.getOrDefault("SUCCESS", 0L)).isEqualTo(1L);
            assertThat(outcomeCounts.getOrDefault("RESIDENCE_PERIOD_CONFLICT", 0L)).isEqualTo(1L);

            List<Residence> finalResidences = transactionTemplate.execute(status ->
                    residenceRepository.findByUserIdOrderByStartDateDesc(userId));
            assertThat(finalResidences).hasSize(1);
            assertThat(finalResidences.get(0).getEndDate()).isNull();
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                List<Residence> residences = residenceRepository.findByUserIdOrderByStartDateDesc(userId);
                List<Long> addressIds = residences.stream().map(r -> r.getAddress().getId()).toList();
                residences.forEach(residenceRepository::delete);
                residenceRepository.flush();
                addressIds.forEach(addressRepository::deleteById);
                addressRepository.flush();
                appUserRepository.deleteById(userId);
                Long countryId = cityRepository.findById(cityId).orElseThrow().getCountry().getId();
                cityRepository.deleteById(cityId);
                cityRepository.flush();
                countryRepository.deleteById(countryId);
            });
        }
    }
}
