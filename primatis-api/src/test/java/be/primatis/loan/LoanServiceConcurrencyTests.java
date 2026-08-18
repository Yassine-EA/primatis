package be.primatis.loan;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.CopyRepository;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleRepository;
import be.primatis.catalogue.TitleStatus;
import be.primatis.exception.BusinessRuleException;
import be.primatis.loan.dto.CreateLoanRequest;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberStatus;
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
 * Preuve de concurrence réelle pour DEV-07.5 : deux appels concurrents à
 * {@link LoanService#registerLoan(CreateLoanRequest)} ciblant le même
 * {@code Copy AVAILABLE} pour deux bénéficiaires distincts ne produisent
 * jamais deux Loans ouverts. Grâce au verrou {@code PESSIMISTIC_WRITE}
 * ciblé sur {@code Copy} (DEV-DEC-0030,
 * {@link CopyRepository#findByIdForUpdate(Long)}), le second thread se
 * bloque réellement au niveau PostgreSQL le temps que le premier committe
 * sa transaction, puis sa revalidation post-lock découvre le Loan ouvert
 * déjà créé par le premier thread ({@code findByCopyIdAndLoanStatusIn}) et
 * rejette proprement ({@code COPY_ALREADY_ON_LOAN}) — jamais une violation
 * de contrainte brute exposée au client. La contrainte structurelle
 * {@code ux_loan_open_copy} (V001) reste la dernière protection si le lock
 * applicatif venait à manquer.
 *
 * Volontairement SANS {@code @Transactional} de classe (chaque thread ouvre
 * sa propre transaction/connexion réelle, même principe que
 * {@code CopyLockConcurrencyTests}/{@code ResidenceServiceConcurrencyTests}).
 * Chaque thread positionne son propre {@link SecurityContextHolder}
 * ({@code LOAN_MANAGE}) car le contexte de sécurité est
 * {@code ThreadLocal} par défaut et n'est pas hérité par les threads de
 * l'{@link ExecutorService}.
 */
@SpringBootTest
@ActiveProfiles("test")
class LoanServiceConcurrencyTests {

    @Autowired
    private LoanService loanService;

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

    @Test
    void concurrentRegisterLoanOnTheSameCopyYieldsExactlyOneSuccessAndOneCleanRejection()
            throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 2;

        Long copyId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Titre concurrence Loan");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);

            Copy copy = new Copy();
            copy.setTitle(title);
            copy.setInventoryCode("LOAN-LOCK-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        List<Long> borrowerIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            Long borrowerId = transactionTemplate.execute(status -> {
                AppUser user = new AppUser();
                user.setEmail("loan-concurrency-" + index + "-" + System.nanoTime() + "@primatis.test");
                user.setPasswordHash("hash");
                user.setFirstName("Prénom");
                user.setLastName("Nom");
                user.setAccountStatus(AccountStatus.ACTIVE);
                user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
                user.setMemberStatus(MemberStatus.ACTIVE);
                user.setRegistrationDate(LocalDate.now().minusYears(1));
                user.setMemberExpirationDate(LocalDate.now().plusYears(1));
                user.setFailedLoginCount(0);
                user.setCreatedAt(Instant.now());
                user.setUpdatedAt(Instant.now());
                appUserRepository.save(user);
                return user.getId();
            });
            borrowerIds.add(borrowerId);
        }

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (Long borrowerId : borrowerIds) {
                futures.add(executor.submit(() -> {
                    authenticateWithLoanManage();
                    ready.countDown();
                    go.await();
                    try {
                        loanService.registerLoan(new CreateLoanRequest(borrowerId, copyId));
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
            assertThat(outcomeCounts.getOrDefault("COPY_ALREADY_ON_LOAN", 0L)).isEqualTo(1L);

            List<Loan> openLoans = transactionTemplate.execute(status ->
                    loanRepository.findByCopyIdAndLoanStatusIn(copyId, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE)));
            assertThat(openLoans).hasSize(1);

            Copy finalCopy = transactionTemplate.execute(status -> copyRepository.findById(copyId).orElseThrow());
            assertThat(finalCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ON_LOAN);
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                loanRepository.findByCopyIdAndLoanStatusIn(
                                copyId, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.RETURNED))
                        .forEach(loanRepository::delete);
                loanRepository.flush();
                Copy copy = copyRepository.findById(copyId).orElseThrow();
                Long titleId = copy.getTitle().getId();
                copyRepository.delete(copy);
                copyRepository.flush();
                titleRepository.deleteById(titleId);
                borrowerIds.forEach(appUserRepository::deleteById);
            });
        }
    }

    private static void authenticateWithLoanManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("LOAN_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
