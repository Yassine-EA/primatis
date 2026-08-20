package be.primatis.fine;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.CopyRepository;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleRepository;
import be.primatis.catalogue.TitleStatus;
import be.primatis.exception.BusinessRuleException;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanRepository;
import be.primatis.loan.LoanStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * Preuve de persistance/concurrence réelle pour DEV-09.7/DEV-09.8 :
 * {@link FineService#confirmExternalPayment}/{@link FineService#cancelFine}
 * portent chacune leur propre frontière {@code @Transactional}
 * (contrairement à {@link FineService#createForLateReturnIfApplicable})
 * — vérifiées ici avec de vraies transactions committées, jamais le
 * mécanisme de rollback simulé des classes {@code @Transactional} de test
 * ({@code FineServiceTests}). Même précédent exact que {@code
 * LoanServiceConcurrencyTests}/{@code ReservationServiceConcurrencyTests} :
 * volontairement SANS {@code @Transactional} de classe, chaque thread
 * positionne son propre {@link SecurityContextHolder} ({@code
 * FINE_MANAGE}, {@code ThreadLocal} par défaut, non hérité par les
 * threads de l'{@link ExecutorService}). Inclut également la preuve
 * d'exclusion mutuelle réelle entre les deux transitions terminales
 * (DEV-09.8, mission §13, DEV-DEC-0049).
 */
@SpringBootTest
@ActiveProfiles("test")
class FineServiceConcurrencyTests {

    @Autowired
    private FineService fineService;

    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TitleRepository titleRepository;

    @Autowired
    private CopyRepository copyRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Mission §12 point 10 : preuve qu'après confirmation, une relecture
     * depuis une transaction totalement séparée (nouvelle connexion
     * logique via {@link TransactionTemplate}) confirme l'état réellement
     * committé — pas seulement l'état en mémoire de la transaction qui a
     * effectué la mutation.
     */
    @Test
    void confirmExternalPaymentIsVisibleFromASeparateTransactionAfterCommit() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long fineId = transactionTemplate.execute(status -> persistUnpaidFine("fine-commit-proof"));

        try {
            authenticateWithFineManage();
            try {
                fineService.confirmExternalPayment(fineId);
            } finally {
                SecurityContextHolder.clearContext();
            }

            Fine reloaded = transactionTemplate.execute(status -> fineRepository.findById(fineId).orElseThrow());
            assertThat(reloaded.getFineStatus()).isEqualTo(FineStatus.PAID);
            assertThat(reloaded.getPaidAt()).isNotNull();
            assertThat(reloaded.getCancelledAt()).isNull();
        } finally {
            cleanup(transactionTemplate, fineId);
        }
    }

    /**
     * Mission §13 : deux confirmations simultanées sur la même Fine
     * {@code UNPAID}. {@code FineRepository.findByIdForUpdate}
     * (verrouillage {@code PESSIMISTIC_WRITE}) garantit qu'un seul thread
     * committe la transition — le second se bloque réellement au niveau
     * PostgreSQL jusqu'au commit du premier, puis sa revalidation
     * post-lock découvre {@code fineStatus == PAID} et rejette proprement
     * ({@code FINE_NOT_PAYABLE}), jamais une violation de contrainte
     * brute ni un second {@code paidAt}.
     */
    @Test
    void concurrentConfirmExternalPaymentOnTheSameFineYieldsExactlyOneSuccessAndOneCleanRejection()
            throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long fineId = transactionTemplate.execute(status -> persistUnpaidFine("fine-concurrency"));
        int threadCount = 2;

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    authenticateWithFineManage();
                    ready.countDown();
                    go.await();
                    try {
                        fineService.confirmExternalPayment(fineId);
                        return "SUCCESS";
                    } catch (BusinessRuleException ex) {
                        return ex.getCode();
                    } finally {
                        SecurityContextHolder.clearContext();
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
            assertThat(outcomeCounts.getOrDefault("FINE_NOT_PAYABLE", 0L)).isEqualTo(1L);

            Fine finalFine = transactionTemplate.execute(status -> fineRepository.findById(fineId).orElseThrow());
            assertThat(finalFine.getFineStatus()).isEqualTo(FineStatus.PAID);
            assertThat(finalFine.getPaidAt()).isNotNull();
        } finally {
            cleanup(transactionTemplate, fineId);
        }
    }

    /**
     * DEV-09.8, mission §11 point 10 : même preuve que {@link
     * #confirmExternalPaymentIsVisibleFromASeparateTransactionAfterCommit},
     * pour l'annulation.
     */
    @Test
    void cancelFineIsVisibleFromASeparateTransactionAfterCommit() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long fineId = transactionTemplate.execute(status -> persistUnpaidFine("fine-cancel-commit-proof"));

        try {
            authenticateWithFineManage();
            try {
                fineService.cancelFine(fineId);
            } finally {
                SecurityContextHolder.clearContext();
            }

            Fine reloaded = transactionTemplate.execute(status -> fineRepository.findById(fineId).orElseThrow());
            assertThat(reloaded.getFineStatus()).isEqualTo(FineStatus.CANCELLED);
            assertThat(reloaded.getCancelledAt()).isNotNull();
            assertThat(reloaded.getPaidAt()).isNull();
        } finally {
            cleanup(transactionTemplate, fineId);
        }
    }

    /**
     * DEV-09.8, mission §12 : même preuve que {@link
     * #concurrentConfirmExternalPaymentOnTheSameFineYieldsExactlyOneSuccessAndOneCleanRejection},
     * pour deux annulations simultanées sur la même Fine {@code UNPAID}.
     */
    @Test
    void concurrentCancelFineOnTheSameFineYieldsExactlyOneSuccessAndOneCleanRejection()
            throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long fineId = transactionTemplate.execute(status -> persistUnpaidFine("fine-cancel-concurrency"));
        int threadCount = 2;

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    authenticateWithFineManage();
                    ready.countDown();
                    go.await();
                    try {
                        fineService.cancelFine(fineId);
                        return "SUCCESS";
                    } catch (BusinessRuleException ex) {
                        return ex.getCode();
                    } finally {
                        SecurityContextHolder.clearContext();
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
            assertThat(outcomeCounts.getOrDefault("FINE_NOT_CANCELLABLE", 0L)).isEqualTo(1L);

            Fine finalFine = transactionTemplate.execute(status -> fineRepository.findById(fineId).orElseThrow());
            assertThat(finalFine.getFineStatus()).isEqualTo(FineStatus.CANCELLED);
            assertThat(finalFine.getCancelledAt()).isNotNull();
        } finally {
            cleanup(transactionTemplate, fineId);
        }
    }

    /**
     * DEV-09.8, mission §13 : {@code payment-confirmation} et {@code
     * cancel} tentés concurremment sur la même Fine {@code UNPAID} —
     * preuve directe de {@code DEV-DEC-0049} (les deux méthodes
     * verrouillent la même ligne via {@code findByIdForUpdate}, donc
     * s'excluent réellement). Aucun gagnant forcé : les deux issues
     * (le paiement gagne, ou l'annulation gagne) sont légitimes selon
     * l'ordre réel d'acquisition du verrou PostgreSQL — seule
     * l'invariance du résultat est vérifiée : exactement un thread
     * réussit, l'autre est rejeté proprement après avoir observé l'état
     * déjà terminal, l'état final est PAID ou CANCELLED (jamais les deux
     * timestamps renseignés simultanément — cohérent avec {@code
     * ck_fine_status_consistency}, V001).
     */
    @Test
    void concurrentPaymentConfirmationAndCancellationOnTheSameFineYieldExactlyOneWinner()
            throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long fineId = transactionTemplate.execute(status -> persistUnpaidFine("fine-payment-vs-cancel"));

        try {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            Future<String> paymentFuture = executor.submit(() -> {
                authenticateWithFineManage();
                ready.countDown();
                go.await();
                try {
                    fineService.confirmExternalPayment(fineId);
                    return "SUCCESS";
                } catch (BusinessRuleException ex) {
                    return ex.getCode();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            Future<String> cancelFuture = executor.submit(() -> {
                authenticateWithFineManage();
                ready.countDown();
                go.await();
                try {
                    fineService.cancelFine(fineId);
                    return "SUCCESS";
                } catch (BusinessRuleException ex) {
                    return ex.getCode();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            ready.await();
            go.countDown();
            String paymentOutcome = paymentFuture.get();
            String cancelOutcome = cancelFuture.get();
            executor.shutdown();

            // Exactement un des deux threads réussit, jamais les deux, jamais aucun.
            long successCount = List.of(paymentOutcome, cancelOutcome).stream()
                    .filter("SUCCESS"::equals).count();
            assertThat(successCount).isEqualTo(1L);

            Fine finalFine = transactionTemplate.execute(status -> fineRepository.findById(fineId).orElseThrow());
            if ("SUCCESS".equals(paymentOutcome)) {
                assertThat(finalFine.getFineStatus()).isEqualTo(FineStatus.PAID);
                assertThat(finalFine.getPaidAt()).isNotNull();
                assertThat(finalFine.getCancelledAt()).isNull();
                assertThat(cancelOutcome).isEqualTo("FINE_NOT_CANCELLABLE");
            } else {
                assertThat(finalFine.getFineStatus()).isEqualTo(FineStatus.CANCELLED);
                assertThat(finalFine.getCancelledAt()).isNotNull();
                assertThat(finalFine.getPaidAt()).isNull();
                assertThat(paymentOutcome).isEqualTo("FINE_NOT_PAYABLE");
            }
            // Jamais les deux timestamps simultanément, quel que soit le gagnant.
            assertThat(finalFine.getPaidAt() != null && finalFine.getCancelledAt() != null).isFalse();
        } finally {
            cleanup(transactionTemplate, fineId);
        }
    }

    private Long persistUnpaidFine(String label) {
        AppUser user = new AppUser();
        user.setEmail(label + "-" + System.nanoTime() + "@primatis.test");
        user.setPasswordHash("hash");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        appUserRepository.save(user);

        Title title = new Title();
        title.setTitle("Titre concurrence Fine");
        title.setLanguage(Language.FR);
        title.setTitleStatus(TitleStatus.ACTIVE);
        title.setCreatedAt(Instant.now());
        title.setUpdatedAt(Instant.now());
        titleRepository.save(title);

        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(label + "-" + System.nanoTime());
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        copyRepository.save(copy);

        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(Instant.now().minusSeconds(3600 * 24 * 10));
        loan.setDueDate(LocalDate.now().minusDays(5));
        loan.setReturnDate(LocalDate.now());
        loan.setLoanStatus(LoanStatus.RETURNED);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        loanRepository.save(loan);

        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(new BigDecimal("5.00"));
        fine.setReason("Motif de test (concurrence)");
        fine.setIssuedAt(Instant.now());
        fine.setFineStatus(FineStatus.UNPAID);
        fineRepository.save(fine);

        return fine.getId();
    }

    private void cleanup(TransactionTemplate transactionTemplate, Long fineId) {
        transactionTemplate.executeWithoutResult(status -> {
            Fine fine = fineRepository.findById(fineId).orElseThrow();
            Loan loan = fine.getLoan();
            Copy copy = loan.getCopy();
            Long titleId = copy.getTitle().getId();
            Long userId = loan.getUser().getId();
            fineRepository.delete(fine);
            fineRepository.flush();
            loanRepository.delete(loan);
            loanRepository.flush();
            copyRepository.delete(copy);
            copyRepository.flush();
            titleRepository.deleteById(titleId);
            appUserRepository.deleteById(userId);
        });
    }

    private static void authenticateWithFineManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("FINE_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
