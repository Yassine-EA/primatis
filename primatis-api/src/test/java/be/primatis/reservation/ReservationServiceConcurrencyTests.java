package be.primatis.reservation;

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
import be.primatis.loan.LoanService;
import be.primatis.loan.LoanStatus;
import be.primatis.loan.dto.CreateLoanRequest;
import be.primatis.reservation.dto.CreateOwnReservationRequest;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

/**
 * Preuve de concurrence réelle pour DEV-08.5 : deux threads tentent
 * simultanément de créer une Reservation pour le même membre et le même
 * Title (aucun des deux ne peut détecter le doublon à l'avance — les deux
 * lectures de {@code findByUserIdAndTitleIdAndReservationStatusIn} se font
 * avant que l'un ou l'autre n'ait écrit). Seule la contrainte PostgreSQL
 * {@code ux_reservation_active_user_title} peut donc arbitrer. Ce test
 * prouve que la course concurrente ne produit ni doublon actif ni HTTP 500
 * exposé : exactement un succès et un rejet propre
 * {@code RESERVATION_ALREADY_ACTIVE} (traduction ciblée de {@link
 * org.springframework.dao.DataIntegrityViolationException}, même précédent
 * exact que {@code ResidenceServiceConcurrencyTests}/DEV-05.8 §2 — aucun
 * verrou pessimiste introduit, cf. Javadoc de
 * {@code ReservationService#createReservationForUser}).
 *
 * Volontairement SANS {@code @Transactional} de classe : chaque thread doit
 * ouvrir sa propre transaction/connexion réelle pour que la contrainte
 * PostgreSQL arbitre effectivement entre deux transactions concurrentes
 * distinctes (même principe que {@code AuthServiceConcurrencyTests}/
 * {@code ResidenceServiceConcurrencyTests}). Nettoyage manuel explicite en
 * fin de test.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceConcurrencyTests {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TitleRepository titleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private CopyRepository copyRepository;

    @Autowired
    private ReservationExpirationService reservationExpirationService;

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private static void authenticateWithLoanManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("LOAN_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void concurrentReservationCreationForSameMemberAndTitleYieldsExactlyOneSuccessAndOneCleanConflict()
            throws InterruptedException, ExecutionException {
        String email = "reservation-concurrency@primatis.test";
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
            user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
            user.setMemberStatus(MemberStatus.ACTIVE);
            user.setRegistrationDate(LocalDate.now().minusYears(1));
            user.setMemberExpirationDate(LocalDate.now().plusYears(1));
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            return user.getId();
        });

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Reservation Concurrency Title");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
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
                        reservationService.createOwnReservation(userId, new CreateOwnReservationRequest(titleId));
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
            assertThat(outcomeCounts.getOrDefault("RESERVATION_ALREADY_ACTIVE", 0L)).isEqualTo(1L);

            List<Reservation> finalReservations = transactionTemplate.execute(status ->
                    reservationRepository.findByUserIdAndTitleIdAndReservationStatusIn(
                            userId, titleId, List.of(ReservationStatus.WAITING, ReservationStatus.READY)));
            assertThat(finalReservations).hasSize(1);
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                // DEV-10.6 : la création réussie crée désormais RESERVATION_CREATED
                // (fk_notification_reservation_id, ON DELETE RESTRICT) — supprimée avant
                // la Reservation.
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.user.id = :userId")
                        .setParameter("userId", userId).executeUpdate();
                reservationRepository.findByUserIdAndTitleIdAndReservationStatusIn(
                                userId, titleId, List.of(
                                        ReservationStatus.WAITING, ReservationStatus.READY,
                                        ReservationStatus.FULFILLED, ReservationStatus.CANCELLED,
                                        ReservationStatus.EXPIRED))
                        .forEach(reservationRepository::delete);
                reservationRepository.flush();
                appUserRepository.deleteById(userId);
                titleRepository.deleteById(titleId);
            });
        }
    }

    /**
     * Deux threads annulent simultanément la même Reservation {@code READY}
     * (DEV-08.6, ordre de lock {@code Copy → Reservation} strict,
     * DEV-DEC-0033). Le premier verrouille {@code Copy} puis
     * {@code Reservation}, trouve {@code READY}, annule. Le second, débloqué
     * ensuite, verrouille à son tour {@code Copy} puis {@code Reservation},
     * revalide et trouve désormais {@code CANCELLED} (statut terminal) — donc
     * {@code RESERVATION_NOT_CANCELLABLE} (409), jamais une exception brute.
     * Exactement une transition effective, aucun état incohérent.
     */
    @Test
    void concurrentCancellationOfTheSameReadyReservationYieldsExactlyOneSuccessAndOneCleanConflict()
            throws InterruptedException, ExecutionException {
        String email = "reservation-cancel-concurrency@primatis.test";
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
            user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
            user.setMemberStatus(MemberStatus.ACTIVE);
            user.setRegistrationDate(LocalDate.now().minusYears(1));
            user.setMemberExpirationDate(LocalDate.now().plusYears(1));
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            return user.getId();
        });

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Reservation Cancel Concurrency Title");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyId = transactionTemplate.execute(status -> {
            Copy copy = new Copy();
            copy.setTitle(titleRepository.getReferenceById(titleId));
            copy.setInventoryCode("CANCEL-CONCURRENCY-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        Long reservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.getReferenceById(userId));
            reservation.setTitle(titleRepository.getReferenceById(titleId));
            reservation.setReservationDate(Instant.now());
            reservation.setReservationStatus(ReservationStatus.READY);
            reservation.setAssignedCopy(copyRepository.getReferenceById(copyId));
            reservation.setExpirationDate(Instant.now().plusSeconds(3600));
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
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
                        reservationService.cancelOwnReservation(userId, reservationId);
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
            assertThat(outcomeCounts.getOrDefault("RESERVATION_NOT_CANCELLABLE", 0L)).isEqualTo(1L);

            Reservation finalReservation = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationId).orElseThrow());
            assertThat(finalReservation.getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        } finally {
            Long finalCopyId = copyId;
            transactionTemplate.executeWithoutResult(status -> {
                // DEV-10.6 : annulation/expiration READY crée désormais
                // RESERVATION_CANCELLED/RESERVATION_EXPIRED (fk_notification_reservation_id,
                // ON DELETE RESTRICT) — supprimée avant la Reservation.
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.id = :id")
                        .setParameter("id", reservationId).executeUpdate();
                reservationRepository.deleteById(reservationId);
                reservationRepository.flush();
                copyRepository.deleteById(finalCopyId);
                appUserRepository.deleteById(userId);
                titleRepository.deleteById(titleId);
            });
        }
    }

    /**
     * Preuve de concurrence réelle pour DEV-08.7 (mission §13) : une
     * Reservation {@code READY} déjà expirée est simultanément visée par
     * le scheduler d'expiration et par une annulation self-service. Les
     * deux workflows verrouillent {@code Copy} puis {@code Reservation}
     * dans le même ordre (DEV-DEC-0033) — le second thread se bloque
     * réellement sur le verrou {@code Copy} jusqu'au commit du premier,
     * puis découvre un statut qui n'est plus {@code READY} :
     * l'expiration se retire silencieusement (candidate stale, jamais une
     * erreur), l'annulation rejette proprement ({@code
     * RESERVATION_NOT_CANCELLABLE}). Une seule transition terminale gagne
     * jamais les deux, jamais de violation SQL brute, jamais un double
     * traitement FIFO.
     */
    @Test
    void concurrentExpirationAndCancellationOfTheSameReadyReservationYieldsExactlyOneTerminalTransition()
            throws InterruptedException, ExecutionException {
        String email = "reservation-expire-cancel-concurrency@primatis.test";
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long userId = transactionTemplate.execute(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash("hash");
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
            user.setMemberStatus(MemberStatus.ACTIVE);
            user.setRegistrationDate(LocalDate.now().minusYears(1));
            user.setMemberExpirationDate(LocalDate.now().plusYears(1));
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            return user.getId();
        });

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Reservation Expire/Cancel Concurrency Title");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyId = transactionTemplate.execute(status -> {
            Copy copy = new Copy();
            copy.setTitle(titleRepository.getReferenceById(titleId));
            copy.setInventoryCode("EXPIRE-CANCEL-CONCURRENCY-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        Instant expiredExpirationDate = Instant.now().minusSeconds(3600);
        Long reservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.getReferenceById(userId));
            reservation.setTitle(titleRepository.getReferenceById(titleId));
            reservation.setReservationDate(Instant.now().minusSeconds(7200));
            reservation.setReservationStatus(ReservationStatus.READY);
            reservation.setAssignedCopy(copyRepository.getReferenceById(copyId));
            reservation.setExpirationDate(expiredExpirationDate);
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
        });

        try {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            Future<String> expireOutcome = executor.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    reservationExpirationService.expireReservationIfStillDue(reservationId, Instant.now());
                    return "COMPLETED";
                } catch (RuntimeException ex) {
                    return "ERROR:" + ex.getClass().getSimpleName();
                }
            });
            Future<String> cancelOutcome = executor.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    reservationService.cancelOwnReservation(userId, reservationId);
                    return "SUCCESS";
                } catch (BusinessRuleException ex) {
                    return ex.getCode();
                }
            });

            ready.await();
            go.countDown();
            String expireResult = expireOutcome.get();
            String cancelResult = cancelOutcome.get();
            executor.shutdown();

            assertThat(expireResult).isEqualTo("COMPLETED");

            Reservation finalReservation = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationId).orElseThrow());

            if (finalReservation.getReservationStatus() == ReservationStatus.CANCELLED) {
                assertThat(cancelResult).isEqualTo("SUCCESS");
            } else if (finalReservation.getReservationStatus() == ReservationStatus.EXPIRED) {
                assertThat(cancelResult).isEqualTo("RESERVATION_NOT_CANCELLABLE");
            } else {
                throw new AssertionError("Statut final inattendu : " + finalReservation.getReservationStatus());
            }

            Copy finalCopy = transactionTemplate.execute(status -> copyRepository.findById(copyId).orElseThrow());
            assertThat(finalCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        } finally {
            Long finalCopyId = copyId;
            transactionTemplate.executeWithoutResult(status -> {
                // DEV-10.6 : annulation/expiration READY crée désormais
                // RESERVATION_CANCELLED/RESERVATION_EXPIRED (fk_notification_reservation_id,
                // ON DELETE RESTRICT) — supprimée avant la Reservation.
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.id = :id")
                        .setParameter("id", reservationId).executeUpdate();
                reservationRepository.deleteById(reservationId);
                reservationRepository.flush();
                copyRepository.deleteById(finalCopyId);
                appUserRepository.deleteById(userId);
                titleRepository.deleteById(titleId);
            });
        }
    }

    /**
     * Preuve de concurrence réelle pour DEV-08.7 (mission §14) : une
     * Reservation {@code READY} déjà expirée, assignée à un {@code Copy},
     * est simultanément visée par le scheduler d'expiration et par un
     * {@code registerLoan} tentant de la fulfill pour son bénéficiaire
     * exact. Un candidat {@code WAITING} tiers existe pour le même Title,
     * afin d'exercer le cas le plus exigeant : si l'expiration gagne la
     * course, le Copy est réaffecté {@code READY} à ce tiers avant que le
     * thread {@code registerLoan} ne l'examine.
     *
     * <p>Les deux workflows verrouillent {@code Copy} en premier
     * (DEV-DEC-0033 respectée par les deux : {@code
     * CopyRepository.findByIdForUpdate} pour {@code registerLoan},
     * {@code ReservationExpirationService} pour l'expiration) — la
     * contention réelle PostgreSQL sur ce verrou arbitre donc laquelle des
     * deux opérations s'exécute en premier. Invariant vérifié
     * explicitement dans les deux issues possibles : la Reservation
     * d'origine n'est <b>jamais</b> à la fois {@code FULFILLED} et
     * {@code EXPIRED} ; le {@code Copy} n'est jamais simultanément
     * {@code ON_LOAN} et réaffecté {@code READY} à une autre Reservation.
     */
    @Test
    void concurrentExpirationAndFulfillmentOfTheSameReadyReservationNeverProducesBothTerminalStates()
            throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long beneficiaryId = transactionTemplate.execute(status -> persistMember("expire-fulfill-beneficiary").getId());
        Long waitingCandidateId = transactionTemplate.execute(status -> persistMember("expire-fulfill-candidate").getId());

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Reservation Expire/Fulfill Concurrency Title");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyId = transactionTemplate.execute(status -> {
            Copy copy = new Copy();
            copy.setTitle(titleRepository.getReferenceById(titleId));
            copy.setInventoryCode("EXPIRE-FULFILL-CONCURRENCY-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        Long reservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.getReferenceById(beneficiaryId));
            reservation.setTitle(titleRepository.getReferenceById(titleId));
            reservation.setReservationDate(Instant.now().minusSeconds(7200));
            reservation.setReservationStatus(ReservationStatus.READY);
            reservation.setAssignedCopy(copyRepository.getReferenceById(copyId));
            reservation.setExpirationDate(Instant.now().minusSeconds(3600));
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
        });

        Long waitingReservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.getReferenceById(waitingCandidateId));
            reservation.setTitle(titleRepository.getReferenceById(titleId));
            reservation.setReservationDate(Instant.now().minusSeconds(60));
            reservation.setReservationStatus(ReservationStatus.WAITING);
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
        });

        try {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            Future<String> expireOutcome = executor.submit(() -> {
                ready.countDown();
                go.await();
                reservationExpirationService.expireReservationIfStillDue(reservationId, Instant.now());
                return "COMPLETED";
            });
            Future<String> loanOutcome = executor.submit(() -> {
                authenticateWithLoanManage();
                ready.countDown();
                go.await();
                try {
                    loanService.registerLoan(new CreateLoanRequest(beneficiaryId, copyId));
                    return "SUCCESS";
                } catch (BusinessRuleException ex) {
                    return ex.getCode();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            ready.await();
            go.countDown();
            String expireResult = expireOutcome.get();
            String loanResult = loanOutcome.get();
            executor.shutdown();

            assertThat(expireResult).isEqualTo("COMPLETED");

            Reservation finalReservation = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationId).orElseThrow());
            Reservation finalWaitingReservation = transactionTemplate.execute(status ->
                    reservationRepository.findById(waitingReservationId).orElseThrow());
            Copy finalCopy = transactionTemplate.execute(status -> copyRepository.findById(copyId).orElseThrow());

            if (finalReservation.getReservationStatus() == ReservationStatus.FULFILLED) {
                assertThat(loanResult).isEqualTo("SUCCESS");
                assertThat(finalReservation.getFulfilledByLoan()).isNotNull();
                assertThat(finalCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ON_LOAN);
                assertThat(finalWaitingReservation.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
            } else if (finalReservation.getReservationStatus() == ReservationStatus.EXPIRED) {
                assertThat(loanResult).isEqualTo("COPY_RESERVED_FOR_ANOTHER_MEMBER");
                assertThat(finalCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
                assertThat(finalWaitingReservation.getReservationStatus()).isEqualTo(ReservationStatus.READY);
                assertThat(finalWaitingReservation.getAssignedCopy().getId()).isEqualTo(copyId);
            } else {
                throw new AssertionError("Statut final inattendu, invariant violé : "
                        + finalReservation.getReservationStatus());
            }
        } finally {
            Long finalCopyId = copyId;
            transactionTemplate.executeWithoutResult(status -> {
                // DEV-10.6 : expiration/promotion crée désormais RESERVATION_EXPIRED/
                // RESERVATION_READY (fk_notification_reservation_id, ON DELETE RESTRICT) —
                // supprimées avant Loan/Reservation.
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.id IN :ids")
                        .setParameter("ids", List.of(waitingReservationId, reservationId)).executeUpdate();
                loanRepository.findByCopyIdAndLoanStatusIn(
                                finalCopyId, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.RETURNED))
                        .forEach(loanRepository::delete);
                loanRepository.flush();
                reservationRepository.deleteById(waitingReservationId);
                reservationRepository.deleteById(reservationId);
                reservationRepository.flush();
                copyRepository.deleteById(finalCopyId);
                appUserRepository.deleteById(beneficiaryId);
                appUserRepository.deleteById(waitingCandidateId);
                titleRepository.deleteById(titleId);
            });
        }
    }

    /**
     * Preuve de concurrence réelle pour le finding B de l'audit
     * contradictoire (`documentation-interne//DEV-08.6-08.7 — AUDIT CONTRADICTOIRE.md`
     * §19) : la branche « snapshot périmé » de {@code
     * ReservationService.cancelReservationForUser} (le snapshot initial lit
     * {@code WAITING}/{@code assignedCopyId == null}, mais le verrouillage
     * ultérieur de la seule {@code Reservation} découvre qu'elle est
     * devenue {@code READY} entre-temps) n'était exercée par aucun test —
     * ni unitaire, ni de concurrence. Ce test la force réellement, via un
     * vrai workflow de promotion FIFO (retour de Loan,
     * {@code LoanService.registerReturn}), sans mock ni H2 ni
     * instrumentation de production.
     *
     * <p><b>Choréographie déterministe</b> (même technique que {@code
     * LoanServiceConcurrencyTests.concurrentReturnThrowsContentionAndRollsBackWhenAllAttemptsAreRaced},
     * DEV-07.6 : un « grabber » verrouille la ressource cible via le
     * Repository réel, dans une transaction ouverte et maintenue par des
     * {@code CountDownLatch}, pour garantir — et pas seulement espérer — un
     * ordre de mise en file d'attente précis) :
     *
     * <ol>
     * <li>un thread « blocker » verrouille la {@code Reservation} {@code R}
     * (WAITING) via {@code reservationRepository.findByIdForUpdate} et
     * retient le verrou (transaction ouverte, non committée) ;</li>
     * <li>le thread {@code registerReturn} démarre en premier : il
     * verrouille son {@code Loan} puis son {@code Copy} (même Title que
     * {@code R}), identifie {@code R} comme unique candidate FIFO
     * {@code WAITING}, puis tente de la verrouiller à son tour — il se
     * bloque réellement sur le verrou détenu par le blocker (vérifié par un
     * {@code await} borné qui doit retourner {@code false}, jamais supposé) ;</li>
     * <li>le thread d'annulation ({@code cancelOwnReservation}) démarre
     * ensuite seulement : sa lecture non verrouillée du snapshot
     * ({@code findCancellationSnapshotById}) s'exécute à cet instant précis
     * — {@code R} est encore {@code WAITING}/{@code assignedCopyId == null}
     * en base (le blocker n'a encore rien muté), donc le snapshot lu est
     * authentiquement {@code WAITING}. Le thread d'annulation tente alors à
     * son tour de verrouiller {@code R} — il se met en file <b>après</b>
     * {@code registerReturn} (qui a demandé le verrou en premier, à l'étape
     * précédente) et se bloque également (vérifié par un second
     * {@code await} borné) ;</li>
     * <li>le blocker relâche son verrou : PostgreSQL sert la file d'attente
     * dans l'ordre de demande (comportement documenté du gestionnaire de
     * verrous de lignes PostgreSQL pour des demandes mutuellement
     * exclusives) — {@code registerReturn}, premier arrivé, obtient le
     * verrou en premier, relit {@code R} (toujours {@code WAITING},
     * inchangée), la promeut {@code READY} avec {@code assignedCopy}/{@code
     * expirationDate}, puis committe (relâchant son propre verrou) ;</li>
     * <li>le thread d'annulation, seul restant en file, obtient alors le
     * verrou et relit {@code R} : elle est désormais {@code READY} — la
     * branche « snapshot périmé » (ligne `continue`) est ainsi réellement
     * empruntée. L'annulation abandonne cette tentative sans mutation,
     * réévalue depuis un nouveau snapshot (qui reflète cette fois
     * {@code assignedCopyId != null}), verrouille {@code Copy} <b>puis</b>
     * {@code Reservation} (jamais l'inverse, DEV-DEC-0033), revalide, et
     * annule la {@code Reservation} désormais {@code READY}.</li>
     * </ol>
     *
     * <p><b>Preuve comportementale</b> que la branche a bien été empruntée
     * (§6 de la mission) : seule cette branche peut laisser
     * {@code assignedCopy}/{@code expirationDate} renseignés sur une
     * {@code Reservation} finalement {@code CANCELLED} (DEV-DEC-0038,
     * conservation historique du passage par {@code READY}) — la branche
     * {@code WAITING} directe ({@code cancelReservationForUser}, lignes
     * 380-384) ne les renseigne jamais. Un état final {@code CANCELLED}
     * avec {@code assignedCopy == null} indiquerait que la course a été
     * gagnée dans le mauvais ordre (annulation directe avant toute
     * promotion) et ferait donc échouer ce test — la démonstration n'est
     * pas seulement l'absence d'exception, mais la forme précise de l'état
     * final.
     */
    @Test
    void cancelOfAWaitingReservationDiscoversItWasPromotedToReadyViaTheStaleSnapshotBranchAndCancelsIt()
            throws InterruptedException, ExecutionException, TimeoutException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long userId = transactionTemplate.execute(status -> persistMember("stale-snapshot-canceller").getId());
        Long borrowerId = transactionTemplate.execute(status -> persistMember("stale-snapshot-borrower").getId());

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Reservation Stale Snapshot Concurrency Title");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyId = transactionTemplate.execute(status -> {
            Copy copy = new Copy();
            copy.setTitle(titleRepository.getReferenceById(titleId));
            copy.setInventoryCode("STALE-SNAPSHOT-CONCURRENCY-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        Long loanId = transactionTemplate.execute(status -> {
            Loan loan = new Loan();
            loan.setUser(appUserRepository.getReferenceById(borrowerId));
            loan.setCopy(copyRepository.getReferenceById(copyId));
            loan.setLoanDate(Instant.now().minusSeconds(3600));
            loan.setDueDate(LocalDate.now().plusDays(21));
            loan.setLoanStatus(LoanStatus.ACTIVE);
            loan.setCreatedAt(Instant.now());
            loan.setUpdatedAt(Instant.now());
            loanRepository.save(loan);
            return loan.getId();
        });

        Long reservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.getReferenceById(userId));
            reservation.setTitle(titleRepository.getReferenceById(titleId));
            reservation.setReservationDate(Instant.now().minusSeconds(60));
            reservation.setReservationStatus(ReservationStatus.WAITING);
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
        });

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch blockerLocked = new CountDownLatch(1);
            CountDownLatch blockerRelease = new CountDownLatch(1);
            Future<?> blockerFuture = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                reservationRepository.findByIdForUpdate(reservationId).orElseThrow();
                blockerLocked.countDown();
                try {
                    blockerRelease.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(blockerLocked.await(10, TimeUnit.SECONDS))
                    .as("le blocker doit réellement détenir le verrou Reservation avant la suite")
                    .isTrue();

            CountDownLatch returnDone = new CountDownLatch(1);
            List<String> returnOutcome = Collections.synchronizedList(new ArrayList<>());
            executor.submit(() -> {
                authenticateWithLoanManage();
                try {
                    loanService.registerReturn(loanId);
                    returnOutcome.add("SUCCESS");
                } catch (RuntimeException ex) {
                    returnOutcome.add(ex.getClass().getSimpleName() + ":" + ex.getMessage());
                } finally {
                    SecurityContextHolder.clearContext();
                    returnDone.countDown();
                }
            });
            assertThat(returnDone.await(500, TimeUnit.MILLISECONDS))
                    .as("registerReturn doit être réellement bloqué sur le verrou Reservation "
                            + "(déjà en file d'attente, après Loan+Copy+FIFO) avant le démarrage de l'annulation")
                    .isFalse();

            CountDownLatch cancelDone = new CountDownLatch(1);
            List<String> cancelOutcome = Collections.synchronizedList(new ArrayList<>());
            executor.submit(() -> {
                try {
                    reservationService.cancelOwnReservation(userId, reservationId);
                    cancelOutcome.add("SUCCESS");
                } catch (RuntimeException ex) {
                    cancelOutcome.add(ex.getClass().getSimpleName() + ":" + ex.getMessage());
                } finally {
                    cancelDone.countDown();
                }
            });
            assertThat(cancelDone.await(500, TimeUnit.MILLISECONDS))
                    .as("cancelOwnReservation doit lui aussi être réellement bloqué sur le verrou Reservation, "
                            + "en file derrière registerReturn — preuve que son snapshot non verrouillé a bien "
                            + "été lu avant toute promotion")
                    .isFalse();

            blockerRelease.countDown();
            blockerFuture.get(10, TimeUnit.SECONDS);

            assertThat(returnDone.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(cancelDone.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();

            assertThat(returnOutcome).containsExactly("SUCCESS");
            assertThat(cancelOutcome).containsExactly("SUCCESS");

            Reservation finalReservation = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationId).orElseThrow());
            assertThat(finalReservation.getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(finalReservation.getAssignedCopy())
                    .as("assignedCopy renseigné prouve que la Reservation est bien passée par READY "
                            + "avant d'être annulée — la branche WAITING directe ne le renseigne jamais")
                    .isNotNull();
            assertThat(finalReservation.getAssignedCopy().getId()).isEqualTo(copyId);
            assertThat(finalReservation.getExpirationDate()).isNotNull();

            Loan finalLoan = transactionTemplate.execute(status -> loanRepository.findById(loanId).orElseThrow());
            assertThat(finalLoan.getLoanStatus()).isEqualTo(LoanStatus.RETURNED);

            Copy finalCopy = transactionTemplate.execute(status -> copyRepository.findById(copyId).orElseThrow());
            assertThat(finalCopy.getAvailabilityStatus())
                    .as("aucun autre candidat WAITING pour ce Title : le Copy doit redevenir AVAILABLE "
                            + "après l'annulation de la Reservation READY qui l'occupait")
                    .isEqualTo(AvailabilityStatus.AVAILABLE);
        } finally {
            Long finalCopyId = copyId;
            transactionTemplate.executeWithoutResult(status -> {
                // DEV-10.5/10.6 : registerReturn crée désormais LOAN_RETURNED et
                // l'annulation/promotion crée RESERVATION_CANCELLED/RESERVATION_READY —
                // supprimées avant Loan/Reservation (fk_notification_loan_id/
                // fk_notification_reservation_id, ON DELETE RESTRICT).
                entityManager.createQuery("DELETE FROM Notification n WHERE n.loan.copy.id = :copyId")
                        .setParameter("copyId", finalCopyId).executeUpdate();
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.id = :id")
                        .setParameter("id", reservationId).executeUpdate();
                loanRepository.findByCopyIdAndLoanStatusIn(
                                finalCopyId, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.RETURNED))
                        .forEach(loanRepository::delete);
                loanRepository.flush();
                reservationRepository.deleteById(reservationId);
                reservationRepository.flush();
                copyRepository.deleteById(finalCopyId);
                titleRepository.deleteById(titleId);
                appUserRepository.deleteById(userId);
                appUserRepository.deleteById(borrowerId);
            });
        }
    }

    private AppUser persistMember(String emailPrefix) {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "-" + System.nanoTime() + "@primatis.test");
        user.setPasswordHash("hash");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setRegistrationDate(LocalDate.now().minusYears(1));
        user.setMemberExpirationDate(LocalDate.now().plusYears(1));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        appUserRepository.save(user);
        return user;
    }
}
