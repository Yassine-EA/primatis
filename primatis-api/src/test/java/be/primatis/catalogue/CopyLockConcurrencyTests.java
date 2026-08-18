package be.primatis.catalogue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preuve de concurrence réelle pour DEV-07.2 : {@link
 * CopyRepository#findByIdForUpdate(Long)} sérialise effectivement deux
 * transactions concurrentes sur la même ligne {@code copy}, au niveau
 * PostgreSQL — pas seulement au niveau applicatif.
 *
 * Le déroulé est déterministe (aucun {@code sleep} arbitraire), même
 * principe que {@code ResidenceServiceConcurrencyTests}/
 * {@code AuthServiceConcurrencyTests} : deux threads, chacun sa propre
 * transaction/connexion réelle (volontairement SANS {@code @Transactional}
 * de classe), coordonnés par {@link CountDownLatch}. Le thread A acquiert le
 * verrou puis attend un signal explicite avant de relâcher sa transaction ;
 * pendant ce temps, le thread B tente d'acquérir le même verrou — sa requête
 * ne peut pas aboutir tant que A ne relâche pas (comportement PostgreSQL
 * réel, pas une hypothèse de timing). L'assertion de non-complétion du
 * thread B avant la libération du thread A est donc garantie par le SGBD,
 * pas par une supposition de délai.
 */
@SpringBootTest
@ActiveProfiles("test")
class CopyLockConcurrencyTests {

    @Autowired
    private CopyRepository copyRepository;

    @Autowired
    private TitleRepository titleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void findByIdForUpdateSerializesConcurrentAccessToTheSameCopy() throws InterruptedException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        List<String> events = Collections.synchronizedList(new java.util.ArrayList<>());

        Long copyId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Titre verrou");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);

            Copy copy = new Copy();
            copy.setTitle(title);
            copy.setInventoryCode("LOCK-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        CountDownLatch threadALocked = new CountDownLatch(1);
        CountDownLatch releaseThreadA = new CountDownLatch(1);
        CountDownLatch threadBDone = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                copyRepository.findByIdForUpdate(copyId);
                events.add("A_LOCKED");
                threadALocked.countDown();
                try {
                    releaseThreadA.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));

            assertThat(threadALocked.await(10, TimeUnit.SECONDS)).isTrue();

            executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    events.add("B_WAITING");
                    copyRepository.findByIdForUpdate(copyId);
                    events.add("B_LOCKED");
                });
                threadBDone.countDown();
            });

            // Le thread B est réellement bloqué par PostgreSQL tant que A ne
            // relâche pas — cette non-complétion est garantie par le SGBD,
            // pas devinée par un délai arbitraire.
            assertThat(threadBDone.await(500, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(events).contains("A_LOCKED", "B_WAITING").doesNotContain("B_LOCKED");

            releaseThreadA.countDown();

            assertThat(threadBDone.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(events).containsExactly("A_LOCKED", "B_WAITING", "B_LOCKED");
        } finally {
            executor.shutdown();
            transactionTemplate.executeWithoutResult(status -> {
                Copy copy = copyRepository.findById(copyId).orElseThrow();
                Long titleId = copy.getTitle().getId();
                copyRepository.delete(copy);
                copyRepository.flush();
                titleRepository.deleteById(titleId);
            });
        }
    }
}
