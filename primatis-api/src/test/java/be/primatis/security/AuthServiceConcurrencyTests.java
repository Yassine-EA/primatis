package be.primatis.security;

import be.primatis.exception.AccountTemporarilyLockedException;
import be.primatis.exception.InvalidCredentialsException;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * Preuves de concurrence réelle pour DEV-03.6, sur PostgreSQL réel.
 *
 * Volontairement SANS @Transactional de classe : chaque thread doit ouvrir
 * sa propre transaction/connexion réelle pour que le verrou PostgreSQL
 * (SELECT ... FOR UPDATE) s'applique effectivement entre threads distincts —
 * contrairement au reste de la suite, un rollback de test partagé ne
 * conviendrait pas ici. Nettoyage manuel explicite en fin de chaque test.
 *
 * Clock figé et partagé (jamais avancé pendant ces tests) via
 * {@link ClockTestConfig} : aucun Thread.sleep(), les assertions sur
 * lockedUntil restent déterministes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ClockTestConfig.class)
class AuthServiceConcurrencyTests {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Preuve de la PRIMITIVE technique uniquement : le verrou pessimiste
     * ({@code PESSIMISTIC_WRITE}, SELECT ... FOR UPDATE) empêche une mise à
     * jour perdue lorsque plusieurs transactions concurrentes font
     * lecture-puis-écriture sur la même ligne app_user.
     *
     * Ce test N'APPELLE PAS {@link AuthService#login}. Il manipule
     * directement {@code AppUserRepository.findByEmailForAuthentication} et
     * incrémente le compteur sans aucune des règles métier du login (pas de
     * vérification de {@code lockedUntil}, pas de seuil à 3, pas de mot de
     * passe comparé). Obtenir exactement {@code threadCount} au final est
     * donc attendu et correct POUR CE QUI EST TESTÉ ICI : la garantie de
     * sérialisation au niveau SQL, pas le comportement du workflow de login.
     * Voir {@link #concurrentWrongPasswordLoginAttemptsStopAtThreeWithoutLosingUpdates()}
     * pour la preuve équivalente au niveau du workflow métier complet.
     */
    @Test
    void concurrentRawCounterIncrementsUnderPessimisticLockDoNotLoseUpdates()
            throws InterruptedException, ExecutionException {
        String email = "concurrency-lock-primitive@primatis.test";
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 8;

        transactionTemplate.executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("Concurrency-Test-Password-15"));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
        });

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    transactionTemplate.executeWithoutResult(status -> {
                        // Verrou pessimiste ciblé : sérialise les threads
                        // concurrents sur cette même ligne app_user.
                        AppUser user = appUserRepository.findByEmailForAuthentication(email).orElseThrow();
                        user.setFailedLoginCount(user.getFailedLoginCount() + 1);
                    });
                    return null;
                }));
            }

            ready.await();
            go.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            AppUser reloaded = appUserRepository.findByEmail(email).orElseThrow();
            // Si le verrou ne protégeait pas la mise à jour, des incréments
            // concurrents seraient perdus et ce total serait < threadCount.
            assertThat(reloaded.getFailedLoginCount()).isEqualTo(threadCount);
        } finally {
            transactionTemplate.executeWithoutResult(status ->
                    appUserRepository.findByEmail(email).ifPresent(appUserRepository::delete));
        }
    }

    /**
     * Preuve du WORKFLOW MÉTIER complet sous concurrence réelle : 8 threads
     * appellent réellement {@link AuthService#login} avec un mauvais mot de
     * passe, en parallèle, sur le même compte.
     *
     * Le verrou pessimiste ciblé (utilisé par {@code AuthService.login} via
     * {@code findByEmailForAuthentication}) sérialise entièrement les 8
     * appels au niveau de la ligne PostgreSQL. L'ORDRE d'arrivée entre
     * threads n'est pas déterministe, mais le RÉSULTAT l'est : il ne dépend
     * que de la position dans la séquence sérialisée (1er/2e/3e échec vs
     * suivants), jamais de l'identité du thread. Avec un Clock figé (jamais
     * avancé), on peut donc affirmer avec certitude :
     * <ul>
     *   <li>exactement 3 appels échouent avec INVALID_CREDENTIALS en ayant
     *       réellement comparé le mot de passe (1er, 2e, 3e échec — le 3e
     *       déclenche le verrouillage mais reste lui-même INVALID_CREDENTIALS,
     *       conformément à la règle) ;</li>
     *   <li>exactement (threadCount - 3) appels échouent avec
     *       ACCOUNT_TEMPORARILY_LOCKED, sans jamais atteindre la comparaison
     *       du mot de passe ;</li>
     *   <li>failedLoginCount final = 3, jamais 8 ;</li>
     *   <li>lockedUntil est renseigné une seule fois et n'est jamais
     *       prolongé par les tentatives suivantes.</li>
     * </ul>
     */
    @Test
    void concurrentWrongPasswordLoginAttemptsStopAtThreeWithoutLosingUpdates()
            throws InterruptedException, ExecutionException {
        String email = "concurrency-login-workflow@primatis.test";
        String wrongPassword = "Wrong-Password-For-Concurrency-Test";
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 8;

        transactionTemplate.executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("Correct-Password-Never-Used-Here-15"));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
        });

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        authService.login(email, wrongPassword);
                        return "UNEXPECTED_SUCCESS";
                    } catch (InvalidCredentialsException ex) {
                        return "INVALID_CREDENTIALS";
                    } catch (AccountTemporarilyLockedException ex) {
                        return "ACCOUNT_TEMPORARILY_LOCKED";
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

            assertThat(outcomeCounts.getOrDefault("UNEXPECTED_SUCCESS", 0L)).isZero();
            assertThat(outcomeCounts.getOrDefault("INVALID_CREDENTIALS", 0L)).isEqualTo(3L);
            assertThat(outcomeCounts.getOrDefault("ACCOUNT_TEMPORARILY_LOCKED", 0L))
                    .isEqualTo((long) (threadCount - 3));

            AppUser reloaded = appUserRepository.findByEmail(email).orElseThrow();
            // Arrêté à 3, jamais 8 : ni mise à jour perdue (< 3) ni excès (> 3).
            assertThat(reloaded.getFailedLoginCount()).isEqualTo(3);
            // lockedUntil renseigné une seule fois (par le 3e échec) et
            // jamais prolongé par les (threadCount - 3) tentatives suivantes.
            assertThat(reloaded.getLockedUntil()).isEqualTo(ClockTestConfig.FIXED_INSTANT.plus(AuthService.LOCK_DURATION));
        } finally {
            transactionTemplate.executeWithoutResult(status ->
                    appUserRepository.findByEmail(email).ifPresent(appUserRepository::delete));
        }
    }
}
