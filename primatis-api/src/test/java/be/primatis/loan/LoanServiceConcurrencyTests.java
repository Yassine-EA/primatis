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
import be.primatis.exception.ConflictException;
import be.primatis.fine.Fine;
import be.primatis.fine.FineRepository;
import be.primatis.fine.FineStatus;
import be.primatis.loan.dto.CreateLoanRequest;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationRepository;
import be.primatis.reservation.ReservationStatus;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 *
 * <p>{@code maximum-pool-size} relevé à 25 via {@code @TestPropertySource}
 * (jamais dans {@code application-test.yml}, qui reste au défaut HikariCP
 * pour le reste de la suite) : réservé à la preuve de l'épuisement du
 * garde-fou de retry FIFO (DEV-07.6, addendum final), qui maintient 20
 * verrous PostgreSQL simultanés (un par thread « grabber », chacun sa
 * propre connexion) le temps de la preuve. Cette propriété fait de cette
 * classe la clé d'un contexte Spring distinct, mis en cache séparément par
 * {@code TestContextManager} — la taille de pool des autres classes de
 * test n'est jamais affectée.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=25")
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
    private ReservationRepository reservationRepository;

    @Autowired
    private FineRepository fineRepository;

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

    /**
     * Preuve de concurrence réelle pour DEV-07.6 (§13.A) : deux appels
     * concurrents à {@link LoanService#registerReturn(Long)} sur le même
     * Loan ne produisent jamais deux retours effectifs. {@code
     * LoanRepository.findByIdForUpdate} charge et verrouille en une seule
     * opération — le second thread se bloque réellement au niveau
     * PostgreSQL jusqu'au commit du premier, puis découvre {@code
     * loanStatus == RETURNED} et rejette proprement ({@code
     * LOAN_ALREADY_RETURNED}), jamais une violation de contrainte brute.
     *
     * <p><b>DEV-09.6</b> : le Loan est désormais délibérément en retard
     * ({@code dueDate} dans le passé) — preuve complémentaire, sans
     * infrastructure de concurrence supplémentaire (mission §17), qu'une
     * seule Fine est créée malgré la tentative concurrente : le thread
     * perdant est rejeté par le verrou {@code Loan} <em>avant</em> d'
     * atteindre {@code FineService} (jamais exécuté deux fois), et {@code
     * uq_fine_loan_id} (V001) reste le filet de sécurité structurel ultime.
     */
    @Test
    void concurrentRegisterReturnOnTheSameLoanYieldsExactlyOneSuccessAndOneCleanRejection()
            throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 2;

        Long borrowerId = transactionTemplate.execute(status -> persistMember("return-lock"));

        Long copyId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Titre concurrence Return");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);

            Copy copy = new Copy();
            copy.setTitle(title);
            copy.setInventoryCode("RETURN-LOCK-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        Long loanId = transactionTemplate.execute(status -> {
            Loan loan = new Loan();
            loan.setUser(appUserRepository.findById(borrowerId).orElseThrow());
            loan.setCopy(copyRepository.findById(copyId).orElseThrow());
            loan.setLoanDate(Instant.now().minusSeconds(3600 * 24 * 10));
            loan.setDueDate(LocalDate.now().minusDays(5));
            loan.setLoanStatus(LoanStatus.ACTIVE);
            loan.setCreatedAt(Instant.now());
            loan.setUpdatedAt(Instant.now());
            loanRepository.save(loan);
            return loan.getId();
        });

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    authenticateWithLoanManage();
                    ready.countDown();
                    go.await();
                    try {
                        loanService.registerReturn(loanId);
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
            assertThat(outcomeCounts.getOrDefault("LOAN_ALREADY_RETURNED", 0L)).isEqualTo(1L);

            Loan finalLoan = transactionTemplate.execute(status -> loanRepository.findById(loanId).orElseThrow());
            assertThat(finalLoan.getLoanStatus()).isEqualTo(LoanStatus.RETURNED);

            // DEV-09.6 : exactement une Fine créée malgré la tentative concurrente.
            List<Fine> fines = transactionTemplate.execute(status ->
                    fineRepository.findByLoanId(loanId).map(List::of).orElseGet(List::of));
            assertThat(fines).hasSize(1);
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                fineRepository.findByLoanId(loanId).ifPresent(fineRepository::delete);
                fineRepository.flush();
                loanRepository.deleteById(loanId);
                loanRepository.flush();
                Copy copy = copyRepository.findById(copyId).orElseThrow();
                Long titleId = copy.getTitle().getId();
                copyRepository.delete(copy);
                copyRepository.flush();
                titleRepository.deleteById(titleId);
                appUserRepository.deleteById(borrowerId);
            });
        }
    }

    /**
     * Preuve de concurrence réelle pour DEV-07.6 (§7/§13.B) : deux Copies
     * différents du même Title sont retournés simultanément alors qu'une
     * seule Reservation {@code WAITING} existe pour ce Title. Le candidat
     * FIFO est identifié (non verrouillé) puis verrouillé individuellement
     * par {@code id} ({@code ReservationRepository.findByIdForUpdate}) —
     * le second thread se bloque sur cette ligne précise jusqu'au commit du
     * premier, puis découvre {@code reservationStatus != WAITING} et
     * traite le Copy comme s'il n'y avait aucune Reservation admissible
     * (§ limite documentée du log DEV-07.6 : aucune nouvelle recherche
     * retentée dans cette étape). Résultat attendu : exactement une
     * assignation {@code READY}, un seul Copy {@code RESERVED}, l'autre
     * {@code AVAILABLE}, aucune violation de contrainte brute.
     */
    @Test
    void concurrentReturnsOfTwoCopiesOfTheSameTitleAssignTheSingleWaitingReservationExactlyOnce()
            throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long borrowerOneId = transactionTemplate.execute(status -> persistMember("return-fifo-borrower-1"));
        Long borrowerTwoId = transactionTemplate.execute(status -> persistMember("return-fifo-borrower-2"));
        Long waitingUserId = transactionTemplate.execute(status -> persistMember("return-fifo-waiting"));

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Titre concurrence Return FIFO");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyOneId = transactionTemplate.execute(status -> persistOnLoanCopy(titleId, "RETURN-FIFO-1"));
        Long copyTwoId = transactionTemplate.execute(status -> persistOnLoanCopy(titleId, "RETURN-FIFO-2"));
        Long loanOneId = transactionTemplate.execute(status -> persistActiveLoan(borrowerOneId, copyOneId));
        Long loanTwoId = transactionTemplate.execute(status -> persistActiveLoan(borrowerTwoId, copyTwoId));

        Long reservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.findById(waitingUserId).orElseThrow());
            reservation.setTitle(titleRepository.findById(titleId).orElseThrow());
            reservation.setReservationDate(Instant.now());
            reservation.setReservationStatus(ReservationStatus.WAITING);
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
        });

        List<Long> loanIds = List.of(loanOneId, loanTwoId);
        try {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (Long loanId : loanIds) {
                futures.add(executor.submit(() -> {
                    authenticateWithLoanManage();
                    ready.countDown();
                    go.await();
                    try {
                        loanService.registerReturn(loanId);
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
            for (Future<String> future : futures) {
                assertThat(future.get()).isEqualTo("SUCCESS");
            }
            executor.shutdown();

            Copy finalCopyOne = transactionTemplate.execute(status -> copyRepository.findById(copyOneId).orElseThrow());
            Copy finalCopyTwo = transactionTemplate.execute(status -> copyRepository.findById(copyTwoId).orElseThrow());
            Reservation finalReservation =
                    transactionTemplate.execute(status -> reservationRepository.findById(reservationId).orElseThrow());

            List<AvailabilityStatus> finalStatuses = List.of(
                    finalCopyOne.getAvailabilityStatus(), finalCopyTwo.getAvailabilityStatus());
            assertThat(finalStatuses).containsExactlyInAnyOrder(
                    AvailabilityStatus.RESERVED, AvailabilityStatus.AVAILABLE);
            assertThat(finalReservation.getReservationStatus()).isEqualTo(ReservationStatus.READY);

            Long reservedCopyId = finalCopyOne.getAvailabilityStatus() == AvailabilityStatus.RESERVED
                    ? copyOneId : copyTwoId;
            assertThat(finalReservation.getAssignedCopy().getId()).isEqualTo(reservedCopyId);
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                reservationRepository.deleteById(reservationId);
                reservationRepository.flush();
                loanIds.forEach(loanRepository::deleteById);
                loanRepository.flush();
                copyRepository.deleteById(copyOneId);
                copyRepository.deleteById(copyTwoId);
                copyRepository.flush();
                titleRepository.deleteById(titleId);
                appUserRepository.deleteById(borrowerOneId);
                appUserRepository.deleteById(borrowerTwoId);
                appUserRepository.deleteById(waitingUserId);
            });
        }
    }

    /**
     * Preuve de concurrence réelle pour le correctif de retry FIFO
     * (DEV-07.6, addendum) — Test A : deux Copies différents du même Title
     * sont retournés simultanément alors que **deux** Reservations {@code
     * WAITING} existent pour ce Title (contrairement à
     * {@link #concurrentReturnsOfTwoCopiesOfTheSameTitleAssignTheSingleWaitingReservationExactlyOnce},
     * qui n'en a qu'une). La règle métier finale exige que les deux
     * Reservations deviennent {@code READY}, chacune assignée à un Copy
     * distinct — jamais un double assignment, jamais une Reservation
     * ignorée alors qu'une autre encore {@code WAITING} existe. Le thread
     * perdant la course sur la Reservation la plus ancienne (FIFO) doit
     * retenter et obtenir la seconde grâce au retry borné introduit par ce
     * correctif.
     */
    @Test
    void concurrentReturnsOfTwoCopiesOfTheSameTitleWithTwoWaitingReservationsPromoteBothDistinctly()
            throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long borrowerOneId = transactionTemplate.execute(status -> persistMember("return-fifo2-borrower-1"));
        Long borrowerTwoId = transactionTemplate.execute(status -> persistMember("return-fifo2-borrower-2"));
        Long waitingUserOneId = transactionTemplate.execute(status -> persistMember("return-fifo2-waiting-1"));
        Long waitingUserTwoId = transactionTemplate.execute(status -> persistMember("return-fifo2-waiting-2"));

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Titre concurrence Return FIFO deux reservations");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyOneId = transactionTemplate.execute(status -> persistOnLoanCopy(titleId, "RETURN-FIFO2-1"));
        Long copyTwoId = transactionTemplate.execute(status -> persistOnLoanCopy(titleId, "RETURN-FIFO2-2"));
        Long loanOneId = transactionTemplate.execute(status -> persistActiveLoan(borrowerOneId, copyOneId));
        Long loanTwoId = transactionTemplate.execute(status -> persistActiveLoan(borrowerTwoId, copyTwoId));

        Long reservationOneId = transactionTemplate.execute(status ->
                persistWaitingReservation(waitingUserOneId, titleId, Instant.now().minusSeconds(3600)));
        Long reservationTwoId = transactionTemplate.execute(status ->
                persistWaitingReservation(waitingUserTwoId, titleId, Instant.now().minusSeconds(1800)));

        List<Long> loanIds = List.of(loanOneId, loanTwoId);
        try {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (Long loanId : loanIds) {
                futures.add(executor.submit(() -> {
                    authenticateWithLoanManage();
                    ready.countDown();
                    go.await();
                    try {
                        loanService.registerReturn(loanId);
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
            for (Future<String> future : futures) {
                assertThat(future.get()).isEqualTo("SUCCESS");
            }
            executor.shutdown();

            Copy finalCopyOne = transactionTemplate.execute(status -> copyRepository.findById(copyOneId).orElseThrow());
            Copy finalCopyTwo = transactionTemplate.execute(status -> copyRepository.findById(copyTwoId).orElseThrow());
            Reservation finalReservationOne = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationOneId).orElseThrow());
            Reservation finalReservationTwo = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationTwoId).orElseThrow());

            assertThat(finalCopyOne.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
            assertThat(finalCopyTwo.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
            assertThat(finalReservationOne.getReservationStatus()).isEqualTo(ReservationStatus.READY);
            assertThat(finalReservationTwo.getReservationStatus()).isEqualTo(ReservationStatus.READY);

            Long assignedCopyForReservationOne = finalReservationOne.getAssignedCopy().getId();
            Long assignedCopyForReservationTwo = finalReservationTwo.getAssignedCopy().getId();
            assertThat(assignedCopyForReservationOne).isNotEqualTo(assignedCopyForReservationTwo);
            assertThat(List.of(assignedCopyForReservationOne, assignedCopyForReservationTwo))
                    .containsExactlyInAnyOrder(copyOneId, copyTwoId);
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                reservationRepository.deleteById(reservationOneId);
                reservationRepository.deleteById(reservationTwoId);
                reservationRepository.flush();
                loanIds.forEach(loanRepository::deleteById);
                loanRepository.flush();
                copyRepository.deleteById(copyOneId);
                copyRepository.deleteById(copyTwoId);
                copyRepository.flush();
                titleRepository.deleteById(titleId);
                appUserRepository.deleteById(borrowerOneId);
                appUserRepository.deleteById(borrowerTwoId);
                appUserRepository.deleteById(waitingUserOneId);
                appUserRepository.deleteById(waitingUserTwoId);
            });
        }
    }

    /**
     * Preuve de concurrence réelle pour le correctif de retry FIFO
     * (DEV-07.6, addendum) — Test B : force déterministiquement le scénario
     * que le retry est censé traiter — le candidat FIFO identifié
     * (Reservation la plus ancienne) devient non-{@code WAITING} entre son
     * identification (non verrouillée) et sa revalidation verrouillée,
     * alors qu'une seconde Reservation {@code WAITING} admissible existe
     * encore. Un thread « compétiteur » verrouille explicitement la
     * Reservation la plus ancienne et la fait passer {@code CANCELLED}
     * (transition métier autorisée, business-rules.md §4.12), retenant sa
     * transaction ouverte le temps nécessaire pour prouver que
     * {@code registerReturn} se bloque réellement dessus (même principe
     * que {@code CopyLockConcurrencyTests}). Une fois le compétiteur
     * libéré, {@code registerReturn} doit retenter et promouvoir la
     * seconde Reservation.
     */
    @Test
    void concurrentReturnRetriesToTheNextWaitingReservationWhenTheFirstCandidateIsRacedAway()
            throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long borrowerId = transactionTemplate.execute(status -> persistMember("return-retry-borrower"));
        Long waitingUserOneId = transactionTemplate.execute(status -> persistMember("return-retry-waiting-1"));
        Long waitingUserTwoId = transactionTemplate.execute(status -> persistMember("return-retry-waiting-2"));

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Titre concurrence Return retry");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyId = transactionTemplate.execute(status -> persistOnLoanCopy(titleId, "RETURN-RETRY"));
        Long loanId = transactionTemplate.execute(status -> persistActiveLoan(borrowerId, copyId));

        // R1 = candidat FIFO naturel (plus ancienne) ; R2 = candidat de repli attendu après retry.
        Long reservationOneId = transactionTemplate.execute(status ->
                persistWaitingReservation(waitingUserOneId, titleId, Instant.now().minusSeconds(3600)));
        Long reservationTwoId = transactionTemplate.execute(status ->
                persistWaitingReservation(waitingUserTwoId, titleId, Instant.now().minusSeconds(1800)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch competitorLocked = new CountDownLatch(1);
            CountDownLatch releaseCompetitor = new CountDownLatch(1);
            CountDownLatch returnDone = new CountDownLatch(1);
            List<String> outcome = Collections.synchronizedList(new ArrayList<>());

            Future<?> competitorFuture = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                Reservation reservation = reservationRepository.findByIdForUpdate(reservationOneId).orElseThrow();
                reservation.setReservationStatus(ReservationStatus.CANCELLED);
                reservation.setUpdatedAt(Instant.now());
                competitorLocked.countDown();
                try {
                    releaseCompetitor.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));

            assertThat(competitorLocked.await(10, TimeUnit.SECONDS)).isTrue();

            executor.submit(() -> {
                authenticateWithLoanManage();
                try {
                    loanService.registerReturn(loanId);
                    outcome.add("SUCCESS");
                } catch (BusinessRuleException ex) {
                    outcome.add(ex.getCode());
                } finally {
                    SecurityContextHolder.clearContext();
                    returnDone.countDown();
                }
            });

            // registerReturn est réellement bloqué par PostgreSQL sur le verrou de R1 tant
            // que le compétiteur ne committe pas — non une supposition de délai.
            assertThat(returnDone.await(500, TimeUnit.MILLISECONDS)).isFalse();

            releaseCompetitor.countDown();
            competitorFuture.get(10, TimeUnit.SECONDS);

            assertThat(returnDone.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(outcome).containsExactly("SUCCESS");

            Reservation finalReservationOne = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationOneId).orElseThrow());
            Reservation finalReservationTwo = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationTwoId).orElseThrow());
            Copy finalCopy = transactionTemplate.execute(status -> copyRepository.findById(copyId).orElseThrow());

            assertThat(finalReservationOne.getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(finalReservationTwo.getReservationStatus()).isEqualTo(ReservationStatus.READY);
            assertThat(finalReservationTwo.getAssignedCopy().getId()).isEqualTo(copyId);
            assertThat(finalCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
        } finally {
            executor.shutdown();
            transactionTemplate.executeWithoutResult(status -> {
                reservationRepository.deleteById(reservationOneId);
                reservationRepository.deleteById(reservationTwoId);
                reservationRepository.flush();
                loanRepository.deleteById(loanId);
                loanRepository.flush();
                copyRepository.deleteById(copyId);
                copyRepository.flush();
                titleRepository.deleteById(titleId);
                appUserRepository.deleteById(borrowerId);
                appUserRepository.deleteById(waitingUserOneId);
                appUserRepository.deleteById(waitingUserTwoId);
            });
        }
    }

    private Long persistWaitingReservation(Long userId, Long titleId, Instant reservationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(appUserRepository.findById(userId).orElseThrow());
        reservation.setTitle(titleRepository.findById(titleId).orElseThrow());
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(ReservationStatus.WAITING);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        reservationRepository.save(reservation);
        return reservation.getId();
    }

    private Long persistMember(String label) {
        AppUser user = new AppUser();
        user.setEmail(label + "-" + System.nanoTime() + "@primatis.test");
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
    }

    private Long persistOnLoanCopy(Long titleId, String inventoryCode) {
        Copy copy = new Copy();
        copy.setTitle(titleRepository.findById(titleId).orElseThrow());
        copy.setInventoryCode(inventoryCode + "-" + System.nanoTime());
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        copyRepository.save(copy);
        return copy.getId();
    }

    private Long persistActiveLoan(Long userId, Long copyId) {
        Loan loan = new Loan();
        loan.setUser(appUserRepository.findById(userId).orElseThrow());
        loan.setCopy(copyRepository.findById(copyId).orElseThrow());
        loan.setLoanDate(Instant.now().minusSeconds(3600));
        loan.setDueDate(LocalDate.now().plusDays(21));
        loan.setLoanStatus(LoanStatus.ACTIVE);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        loanRepository.save(loan);
        return loan.getId();
    }

    /**
     * Preuve de concurrence réelle pour le correctif ultime de sûreté du
     * retry FIFO (DEV-07.6, addendum final) : force déterministiquement
     * l'épuisement complet du garde-fou ({@code
     * MAX_RESERVATION_ASSIGNMENT_ATTEMPTS = 20} tentatives, chacune
     * découvrant réellement un candidat racine par un tiers distinct — pas
     * une supposition, une preuve). Distingue explicitement ce cas de
     * « aucune Reservation WAITING » (qui produirait légitimement {@code
     * AVAILABLE}) : ici, un candidat {@code WAITING} est trouvé à
     * <em>chaque</em> tentative, mais toujours raciné avant verrouillage.
     *
     * <p>20 Reservations {@code WAITING} distinctes sont chacune
     * pré-verrouillées (PESSIMISTIC_WRITE, transaction dédiée par
     * Reservation) par 20 threads « grabber » indépendants, tous
     * effectivement verrouillés (confirmé) <em>avant</em> le démarrage de
     * {@code registerReturn}. Cette pré-acquisition garantit qu'à chaque
     * itération du retry, le candidat FIFO suivant est déjà verrouillé par
     * son grabber dédié — jamais une course incertaine entre {@code
     * registerReturn} et un grabber pour la même ressource. Les 20
     * grabbers sont ensuite libérés un par un, dans l'ordre FIFO exact,
     * chaque libération étant précédée d'une preuve de blocage réel
     * ({@code returnDone.await(500ms)} → {@code false}, même principe que
     * {@code CopyLockConcurrencyTests}) — jamais une supposition de délai.
     *
     * <p>Résultat attendu : {@link ConflictException} ({@code
     * RESERVATION_ASSIGNMENT_CONTENTION}) après la 20e tentative,
     * provoquant le rollback complet de la transaction {@code
     * registerReturn} — Loan toujours {@code ACTIVE} (jamais {@code
     * RETURNED}), Copy toujours {@code ON_LOAN} (jamais {@code
     * AVAILABLE}), les 20 Reservations restent dans l'état laissé par
     * leurs grabbers respectifs ({@code CANCELLED}, committé
     * indépendamment de la transaction de {@code registerReturn}).
     */
    @Test
    void concurrentReturnThrowsContentionAndRollsBackWhenAllAttemptsAreRaced()
            throws InterruptedException, ExecutionException, TimeoutException {
        final int maxAttempts = 20; // doit rester synchronisé avec LoanService.MAX_RESERVATION_ASSIGNMENT_ATTEMPTS
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long borrowerId = transactionTemplate.execute(status -> persistMember("return-exhaust-borrower"));
        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Titre concurrence Return exhaustion");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });
        Long copyId = transactionTemplate.execute(status -> persistOnLoanCopy(titleId, "RETURN-EXHAUST"));
        Long loanId = transactionTemplate.execute(status -> persistActiveLoan(borrowerId, copyId));

        List<Long> waitingUserIds = new ArrayList<>();
        List<Long> reservationIds = new ArrayList<>();
        for (int i = 0; i < maxAttempts; i++) {
            int index = i;
            Long userId = transactionTemplate.execute(status -> persistMember("return-exhaust-waiting-" + index));
            waitingUserIds.add(userId);
            long secondsAgo = 3600L - index;
            Long reservationId = transactionTemplate.execute(status ->
                    persistWaitingReservation(userId, titleId, Instant.now().minusSeconds(secondsAgo)));
            reservationIds.add(reservationId);
        }

        ExecutorService executor = Executors.newFixedThreadPool(maxAttempts + 1);
        try {
            CountDownLatch[] locked = new CountDownLatch[maxAttempts];
            CountDownLatch[] release = new CountDownLatch[maxAttempts];
            for (int i = 0; i < maxAttempts; i++) {
                locked[i] = new CountDownLatch(1);
                release[i] = new CountDownLatch(1);
            }

            List<Future<?>> grabberFutures = new ArrayList<>();
            for (int i = 0; i < maxAttempts; i++) {
                int round = i;
                grabberFutures.add(executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                    Reservation reservation =
                            reservationRepository.findByIdForUpdate(reservationIds.get(round)).orElseThrow();
                    reservation.setReservationStatus(ReservationStatus.CANCELLED);
                    reservation.setUpdatedAt(Instant.now());
                    locked[round].countDown();
                    try {
                        release[round].await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                })));
            }

            // Les 20 grabbers doivent tous détenir réellement leur verrou (transactions
            // ouvertes, non committées) avant que registerReturn ne démarre — sinon la
            // première itération pourrait courir contre un grabber pas encore prêt.
            for (int i = 0; i < maxAttempts; i++) {
                assertThat(locked[i].await(10, TimeUnit.SECONDS)).isTrue();
            }

            CountDownLatch returnDone = new CountDownLatch(1);
            List<String> outcome = Collections.synchronizedList(new ArrayList<>());
            executor.submit(() -> {
                authenticateWithLoanManage();
                try {
                    loanService.registerReturn(loanId);
                    outcome.add("SUCCESS");
                } catch (ConflictException ex) {
                    outcome.add(ex.getCode());
                } catch (BusinessRuleException ex) {
                    outcome.add(ex.getCode());
                } finally {
                    SecurityContextHolder.clearContext();
                    returnDone.countDown();
                }
            });

            for (int round = 0; round < maxAttempts; round++) {
                assertThat(returnDone.await(500, TimeUnit.MILLISECONDS))
                        .as("registerReturn doit être réellement bloqué sur le verrou du candidat FIFO n°" + round)
                        .isFalse();
                release[round].countDown();
            }

            for (Future<?> grabberFuture : grabberFutures) {
                grabberFuture.get(10, TimeUnit.SECONDS);
            }

            assertThat(returnDone.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(outcome).containsExactly("RESERVATION_ASSIGNMENT_CONTENTION");

            Loan finalLoan = transactionTemplate.execute(status -> loanRepository.findById(loanId).orElseThrow());
            Copy finalCopy = transactionTemplate.execute(status -> copyRepository.findById(copyId).orElseThrow());
            assertThat(finalLoan.getLoanStatus()).isEqualTo(LoanStatus.ACTIVE);
            assertThat(finalLoan.getReturnDate()).isNull();
            assertThat(finalCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ON_LOAN);

            List<Reservation> finalReservations = transactionTemplate.execute(status ->
                    reservationIds.stream().map(id -> reservationRepository.findById(id).orElseThrow()).toList());
            assertThat(finalReservations).allSatisfy(reservation ->
                    assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED));
        } finally {
            executor.shutdown();
            transactionTemplate.executeWithoutResult(status -> {
                reservationIds.forEach(reservationRepository::deleteById);
                reservationRepository.flush();
                loanRepository.deleteById(loanId);
                loanRepository.flush();
                copyRepository.deleteById(copyId);
                copyRepository.flush();
                titleRepository.deleteById(titleId);
                appUserRepository.deleteById(borrowerId);
                waitingUserIds.forEach(appUserRepository::deleteById);
            });
        }
    }

    /**
     * Preuve d'atomicité transactionnelle réelle pour DEV-09.6 (mission
     * §14) : un échec de {@code FineService.createForLateReturnIfApplicable}
     * (ici forcé via l'anomalie anti-doublon — même précédent exact que
     * {@link #concurrentReturnThrowsContentionAndRollsBackWhenAllAttemptsAreRaced},
     * qui force {@code ConflictException} pour prouver le même type de
     * rollback complet) ne doit laisser <b>aucune</b> mutation Loan/Copy
     * commitée. Un seul thread suffit ici (pas un scénario de concurrence à
     * proprement parler) : ce qui compte est que {@code registerReturn}
     * s'exécute dans sa propre transaction réelle (classe volontairement
     * sans {@code @Transactional}, comme le reste du fichier), commitée ou
     * rollback pour de vrai — jamais le mécanisme de rollback simulé en fin
     * de test des classes {@code @Transactional} (qui ne permettrait pas
     * d'observer un rollback partiel à l'intérieur du test lui-même).
     *
     * <p>Fixture délibérément anormale (jamais atteignable par le workflow
     * normal — {@code registerReturn} refuse déjà tout second retour via
     * {@code LOAN_ALREADY_RETURNED} avant d'atteindre la création de Fine) :
     * une Fine existe déjà pour un Loan encore {@code ACTIVE}, simulant
     * directement l'anomalie structurelle que {@link
     * be.primatis.fine.FineService} refuse (voir Javadoc de la méthode).
     */
    @Test
    void registerReturnRollsBackEntirelyWhenFineCreationFailsForALateReturn() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long borrowerId = transactionTemplate.execute(status -> persistMember("return-fine-rollback"));

        Long copyId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Titre rollback Fine");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);

            Copy copy = new Copy();
            copy.setTitle(title);
            copy.setInventoryCode("RETURN-FINE-ROLLBACK-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        Long loanId = transactionTemplate.execute(status -> {
            Loan loan = new Loan();
            loan.setUser(appUserRepository.findById(borrowerId).orElseThrow());
            loan.setCopy(copyRepository.findById(copyId).orElseThrow());
            loan.setLoanDate(Instant.now().minusSeconds(3600 * 24 * 10));
            loan.setDueDate(LocalDate.now().minusDays(5));
            loan.setLoanStatus(LoanStatus.ACTIVE);
            loan.setCreatedAt(Instant.now());
            loan.setUpdatedAt(Instant.now());
            loanRepository.save(loan);

            // Anomalie délibérée : une Fine existe déjà pour ce Loan encore ACTIVE
            // (structurellement impossible via le workflow normal), pour forcer
            // FineService à lever IllegalStateException pendant registerReturn.
            Fine fine = new Fine();
            fine.setLoan(loan);
            fine.setAmount(java.math.BigDecimal.TEN);
            fine.setReason("Fine préexistante (test rollback DEV-09.6)");
            fine.setIssuedAt(Instant.now());
            fine.setFineStatus(FineStatus.PAID);
            fine.setPaidAt(Instant.now());
            fineRepository.save(fine);

            return loan.getId();
        });

        try {
            authenticateWithLoanManage();
            try {
                assertThatThrownBy(() -> loanService.registerReturn(loanId)).isInstanceOf(IllegalStateException.class);
            } finally {
                SecurityContextHolder.clearContext();
            }

            Loan finalLoan = transactionTemplate.execute(status -> loanRepository.findById(loanId).orElseThrow());
            Copy finalCopy = transactionTemplate.execute(status -> copyRepository.findById(copyId).orElseThrow());
            assertThat(finalLoan.getLoanStatus()).isEqualTo(LoanStatus.ACTIVE);
            assertThat(finalLoan.getReturnDate()).isNull();
            assertThat(finalCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ON_LOAN);
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                fineRepository.findByLoanId(loanId).ifPresent(fineRepository::delete);
                fineRepository.flush();
                loanRepository.deleteById(loanId);
                loanRepository.flush();
                Copy copy = copyRepository.findById(copyId).orElseThrow();
                Long titleId = copy.getTitle().getId();
                copyRepository.delete(copy);
                copyRepository.flush();
                titleRepository.deleteById(titleId);
                appUserRepository.deleteById(borrowerId);
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
