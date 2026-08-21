package be.primatis.loan;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.fine.FineRepository;
import be.primatis.notification.Notification;
import be.primatis.notification.NotificationStatus;
import be.primatis.notification.NotificationType;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Vérifie {@link LoanDeadlineService} (DEV-10.8) contre PostgreSQL réel :
 * fenêtre {@code LOAN_DUE_SOON} (lecture réelle de {@code
 * LOAN_DUE_SOON_DAYS} via {@link be.primatis.setting.ApplicationSettingService}),
 * transition {@code ACTIVE → OVERDUE} et création {@code LOAN_OVERDUE},
 * idempotence des deux traitements sur re-jeu, revalidation post-lock et
 * absence stricte de toute création de {@code Fine} depuis la détection
 * OVERDUE (business-rules.md, mission DEV-10.8 §19 — critère de contrôle
 * C).
 *
 * <p>Appelle {@link LoanDeadlineService#createDueSoonIfStillApplicable}/
 * {@link LoanDeadlineService#markOverdueIfStillDue} directement (candidate
 * déjà identifiée) plutôt que de repasser par {@link
 * LoanDeadlineService#processDueSoonLoans}/{@link
 * LoanDeadlineService#processOverdueLoans} pour chaque scénario métier —
 * même principe exact que {@code ReservationExpirationServiceTests} : les
 * deux points d'entrée par lot ne sont testés qu'une fois pour leur seule
 * responsabilité propre (délégation à partir de la requête bornée).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanDeadlineServiceTests {

    private static final String LOAN_DUE_SOON_DAYS_KEY = "LOAN_DUE_SOON_DAYS";

    @Autowired
    private LoanDeadlineService loanDeadlineService;

    @Autowired
    private Clock clock;

    @Autowired
    private FineRepository fineRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // createDueSoonIfStillApplicable — fenêtre LOAN_DUE_SOON
    // ---------------------------------------------------------------

    @Test
    void createDueSoonIfStillApplicableCreatesNotificationForALoanWithinTheWindow() {
        AppUser borrower = persistUser("due-soon-window@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "DUE-SOON-WINDOW-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.plusDays(2));
        entityManager.flush();

        loanDeadlineService.createDueSoonIfStillApplicable(loan.getId(), referenceDate, referenceDate.plusDays(3));

        List<Notification> notifications = findNotificationsByLoanId(loan.getId());
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.get(0);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.LOAN_DUE_SOON);
        assertThat(notification.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.getReadAt()).isNull();
        assertThat(notification.getRecipientUser().getId()).isEqualTo(borrower.getId());
    }

    @Test
    void createDueSoonIfStillApplicableDoesNothingWhenTheLoanIsTooFarInTheFuture() {
        AppUser borrower = persistUser("due-soon-too-far@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "DUE-SOON-TOO-FAR-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.plusDays(10));
        entityManager.flush();

        loanDeadlineService.createDueSoonIfStillApplicable(loan.getId(), referenceDate, referenceDate.plusDays(3));

        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    /**
     * {@code dueDate == referenceDate} reste exclu de la fenêtre due-soon
     * (même précédent exact que l'exclusion overdue, mission §8) — un Loan
     * dû aujourd'hui n'est ni {@code OVERDUE} ni candidat {@code
     * LOAN_DUE_SOON} au sens de ce traitement.
     */
    @Test
    void createDueSoonIfStillApplicableDoesNothingWhenDueDateEqualsReferenceDate() {
        AppUser borrower = persistUser("due-soon-today@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "DUE-SOON-TODAY-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate);
        entityManager.flush();

        loanDeadlineService.createDueSoonIfStillApplicable(loan.getId(), referenceDate, referenceDate.plusDays(3));

        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void createDueSoonIfStillApplicableDoesNothingWhenTheDueDateIsAlreadyPast() {
        AppUser borrower = persistUser("due-soon-past@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "DUE-SOON-PAST-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.minusDays(1));
        entityManager.flush();

        loanDeadlineService.createDueSoonIfStillApplicable(loan.getId(), referenceDate, referenceDate.plusDays(3));

        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void createDueSoonIfStillApplicableDoesNothingForAReturnedLoan() {
        AppUser borrower = persistUser("due-soon-returned@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "DUE-SOON-RETURNED-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.RETURNED, referenceDate.plusDays(2));
        entityManager.flush();

        loanDeadlineService.createDueSoonIfStillApplicable(loan.getId(), referenceDate, referenceDate.plusDays(3));

        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void createDueSoonIfStillApplicableDoesNothingForAnAlreadyOverdueLoan() {
        AppUser borrower = persistUser("due-soon-overdue-status@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "DUE-SOON-OVERDUE-STATUS-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.OVERDUE, referenceDate.plusDays(2));
        entityManager.flush();

        loanDeadlineService.createDueSoonIfStillApplicable(loan.getId(), referenceDate, referenceDate.plusDays(3));

        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void createDueSoonIfStillApplicableOnUnknownIdDoesNothing() {
        loanDeadlineService.createDueSoonIfStillApplicable(
                999999999L, LocalDate.now(clock), LocalDate.now(clock).plusDays(3));
    }

    /**
     * Re-jeu : au maximum une {@code LOAN_DUE_SOON} par Loan, même après
     * plusieurs exécutions du traitement (database-model.md §13.7 — critère
     * de contrôle D).
     */
    @Test
    void createDueSoonIfStillApplicableIsIdempotentOnReplay() {
        AppUser borrower = persistUser("due-soon-replay@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "DUE-SOON-REPLAY-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.plusDays(2));
        entityManager.flush();

        loanDeadlineService.createDueSoonIfStillApplicable(loan.getId(), referenceDate, referenceDate.plusDays(3));
        loanDeadlineService.createDueSoonIfStillApplicable(loan.getId(), referenceDate, referenceDate.plusDays(3));

        assertThat(findNotificationsByLoanId(loan.getId())).hasSize(1);
    }

    // ---------------------------------------------------------------
    // processDueSoonLoans — orchestration par lot, lecture du setting
    // ---------------------------------------------------------------

    @Test
    void processDueSoonLoansCreatesNotificationsForAllCandidatesAcrossDifferentLoans() {
        AppUser borrowerOne = persistUser("due-soon-batch-1@primatis.test");
        AppUser borrowerTwo = persistUser("due-soon-batch-2@primatis.test");
        Title title = persistTitle();
        Copy copyOne = persistCopy(title, "DUE-SOON-BATCH-1");
        Copy copyTwo = persistCopy(title, "DUE-SOON-BATCH-2");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loanOne = persistLoanWithDueDate(borrowerOne, copyOne, LoanStatus.ACTIVE, referenceDate.plusDays(1));
        Loan loanTwo = persistLoanWithDueDate(borrowerTwo, copyTwo, LoanStatus.ACTIVE, referenceDate.plusDays(3));
        entityManager.flush();

        loanDeadlineService.processDueSoonLoans(referenceDate);

        assertThat(findNotificationsByLoanId(loanOne.getId())).hasSize(1);
        assertThat(findNotificationsByLoanId(loanTwo.getId())).hasSize(1);
    }

    @Test
    void processDueSoonLoansNeverTouchesALoanOutsideTheConfiguredWindow() {
        AppUser borrower = persistUser("due-soon-batch-outside@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "DUE-SOON-BATCH-OUTSIDE-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.plusDays(10));
        entityManager.flush();

        loanDeadlineService.processDueSoonLoans(referenceDate);

        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    /**
     * Preuve que le seuil est réellement lu via {@code
     * ApplicationSettingService} (jamais codé en dur, mission §5) : un Loan
     * échéant dans 5 jours reste hors fenêtre avec le seuil par défaut (3
     * jours, database.md — valeur initiale figée).
     */
    @Test
    void processDueSoonLoansExcludesALoanOutsideTheDefaultThresholdWindow() {
        AppUser borrower = persistUser("due-soon-setting-default@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "DUE-SOON-SETTING-DEFAULT-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.plusDays(5));
        entityManager.flush();

        loanDeadlineService.processDueSoonLoans(referenceDate);

        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    /**
     * Même Loan que {@link #processDueSoonLoansExcludesALoanOutsideTheDefaultThresholdWindow}
     * mais avec {@code LOAN_DUE_SOON_DAYS} reconfiguré à 5 <em>avant</em> le
     * seul appel à {@code ApplicationSettingService.getInteger} de cette
     * transaction (même précédent exact que {@code
     * ReservationExpirationServiceTests#expireReservationIfStillDueReadsHoldHoursDynamicallyWhenPromotingNextReservation}
     * : reconfigurer puis lire une seule fois, jamais relire une seconde
     * fois dans la même transaction — un bulk JPQL UPDATE ne rafraîchit pas
     * une entité {@code ApplicationSetting} déjà chargée dans le contexte
     * de persistance, cf. DEV-10.8 log §"Anti-doublon DUE_SOON"). Preuve
     * que le seuil est réellement lu dynamiquement, jamais codé en dur.
     */
    @Test
    void processDueSoonLoansReadsTheThresholdDynamicallyFromApplicationSetting() {
        AppUser borrower = persistUser("due-soon-setting-reconfigured@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "DUE-SOON-SETTING-RECONFIGURED-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.plusDays(5));
        updateLoanDueSoonDaysSetting(5);
        entityManager.flush();

        loanDeadlineService.processDueSoonLoans(referenceDate);

        assertThat(findNotificationsByLoanId(loan.getId())).hasSize(1);
    }

    // ---------------------------------------------------------------
    // markOverdueIfStillDue — transition ACTIVE → OVERDUE
    // ---------------------------------------------------------------

    @Test
    void markOverdueIfStillDueTransitionsAnActiveLoanPastItsDueDateToOverdueAndCreatesNotification() {
        AppUser borrower = persistUser("overdue-past@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "OVERDUE-PAST-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.minusDays(1));
        entityManager.flush();

        loanDeadlineService.markOverdueIfStillDue(loan.getId(), referenceDate);

        Loan reloaded = entityManager.find(Loan.class, loan.getId());
        assertThat(reloaded.getLoanStatus()).isEqualTo(LoanStatus.OVERDUE);

        List<Notification> notifications = findNotificationsByLoanId(loan.getId());
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.get(0);
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.LOAN_OVERDUE);
        assertThat(notification.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.getRecipientUser().getId()).isEqualTo(borrower.getId());
    }

    /**
     * Critère de contrôle C (mission §19/§36) : la détection périodique
     * OVERDUE ne crée jamais de {@code Fine} — seul {@code
     * FineService#createForLateReturnIfApplicable}, appelé exclusivement
     * depuis {@code LoanService#registerReturn}, en crée une.
     */
    @Test
    void markOverdueIfStillDueNeverCreatesAFineEvenWhenTheLoanBecomesOverdue() {
        AppUser borrower = persistUser("overdue-no-fine@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "OVERDUE-NO-FINE-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.minusDays(5));
        entityManager.flush();

        loanDeadlineService.markOverdueIfStillDue(loan.getId(), referenceDate);

        assertThat(fineRepository.findByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void markOverdueIfStillDueLeavesAnActiveLoanUnchangedWhenDueDateEqualsReferenceDate() {
        AppUser borrower = persistUser("overdue-today@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "OVERDUE-TODAY-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate);
        entityManager.flush();

        loanDeadlineService.markOverdueIfStillDue(loan.getId(), referenceDate);

        Loan reloaded = entityManager.find(Loan.class, loan.getId());
        assertThat(reloaded.getLoanStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void markOverdueIfStillDueLeavesAnActiveLoanUnchangedWhenDueDateIsInTheFuture() {
        AppUser borrower = persistUser("overdue-future@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "OVERDUE-FUTURE-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.plusDays(1));
        entityManager.flush();

        loanDeadlineService.markOverdueIfStillDue(loan.getId(), referenceDate);

        Loan reloaded = entityManager.find(Loan.class, loan.getId());
        assertThat(reloaded.getLoanStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void markOverdueIfStillDueIsANoOpOnAnAlreadyOverdueLoan() {
        AppUser borrower = persistUser("overdue-already@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "OVERDUE-ALREADY-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.OVERDUE, referenceDate.minusDays(1));
        entityManager.flush();

        loanDeadlineService.markOverdueIfStillDue(loan.getId(), referenceDate);

        Loan reloaded = entityManager.find(Loan.class, loan.getId());
        assertThat(reloaded.getLoanStatus()).isEqualTo(LoanStatus.OVERDUE);
        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void markOverdueIfStillDueIsANoOpOnAReturnedLoan() {
        AppUser borrower = persistUser("overdue-returned@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "OVERDUE-RETURNED-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.RETURNED, referenceDate.minusDays(1));
        entityManager.flush();

        loanDeadlineService.markOverdueIfStillDue(loan.getId(), referenceDate);

        Loan reloaded = entityManager.find(Loan.class, loan.getId());
        assertThat(reloaded.getLoanStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(findNotificationsByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void markOverdueIfStillDueUpdatesUpdatedAtUsingTheInjectedClock() {
        AppUser borrower = persistUser("overdue-updated-at@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "OVERDUE-UPDATED-AT-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.minusDays(1));
        loan.setUpdatedAt(clock.instant().minusSeconds(7200));
        entityManager.flush();
        Instant before = clock.instant();

        loanDeadlineService.markOverdueIfStillDue(loan.getId(), referenceDate);

        Instant after = clock.instant();
        Loan reloaded = entityManager.find(Loan.class, loan.getId());
        assertThat(reloaded.getUpdatedAt()).isBetween(before, after);
    }

    /**
     * Structurellement impossible (un Loan ne dispose d'aucun workflow de
     * suppression physique, database.md « Data deletion ») — signalé, même
     * précédent exact que {@code ReservationRepository.findByIdForUpdate}
     * combiné à un identifiant absent.
     */
    @Test
    void markOverdueIfStillDueOnUnknownIdThrowsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> loanDeadlineService.markOverdueIfStillDue(999999999L, LocalDate.now(clock)));
    }

    /**
     * Re-jeu : une seule {@code LOAN_OVERDUE} par Loan, la seconde
     * exécution ne trouvant plus {@code loanStatus == ACTIVE} (critère de
     * contrôle D, transposé à OVERDUE).
     */
    @Test
    void markOverdueIfStillDueReplayCreatesNoNewNotificationAfterTheFirstTransition() {
        AppUser borrower = persistUser("overdue-replay@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "OVERDUE-REPLAY-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, referenceDate.minusDays(1));
        entityManager.flush();

        loanDeadlineService.markOverdueIfStillDue(loan.getId(), referenceDate);
        loanDeadlineService.markOverdueIfStillDue(loan.getId(), referenceDate);

        assertThat(findNotificationsByLoanId(loan.getId())).hasSize(1);
    }

    // ---------------------------------------------------------------
    // processOverdueLoans — orchestration par lot
    // ---------------------------------------------------------------

    @Test
    void processOverdueLoansTransitionsAllOverdueCandidatesAcrossDifferentLoans() {
        AppUser borrowerOne = persistUser("overdue-batch-1@primatis.test");
        AppUser borrowerTwo = persistUser("overdue-batch-2@primatis.test");
        Title title = persistTitle();
        Copy copyOne = persistCopy(title, "OVERDUE-BATCH-1");
        Copy copyTwo = persistCopy(title, "OVERDUE-BATCH-2");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan loanOne = persistLoanWithDueDate(borrowerOne, copyOne, LoanStatus.ACTIVE, referenceDate.minusDays(1));
        Loan loanTwo = persistLoanWithDueDate(borrowerTwo, copyTwo, LoanStatus.ACTIVE, referenceDate.minusDays(4));
        entityManager.flush();

        loanDeadlineService.processOverdueLoans(referenceDate);

        Loan reloadedOne = entityManager.find(Loan.class, loanOne.getId());
        Loan reloadedTwo = entityManager.find(Loan.class, loanTwo.getId());
        assertThat(reloadedOne.getLoanStatus()).isEqualTo(LoanStatus.OVERDUE);
        assertThat(reloadedTwo.getLoanStatus()).isEqualTo(LoanStatus.OVERDUE);
    }

    @Test
    void processOverdueLoansNeverTouchesALoanNotYetDueOrAlreadyClosed() {
        AppUser futureOwner = persistUser("overdue-batch-future@primatis.test");
        AppUser returnedOwner = persistUser("overdue-batch-returned@primatis.test");
        Title title = persistTitle();
        Copy futureCopy = persistCopy(title, "OVERDUE-BATCH-FUTURE-1");
        Copy returnedCopy = persistCopy(title, "OVERDUE-BATCH-RETURNED-1");
        LocalDate referenceDate = LocalDate.now(clock);
        Loan futureLoan = persistLoanWithDueDate(futureOwner, futureCopy, LoanStatus.ACTIVE, referenceDate.plusDays(1));
        Loan returnedLoan = persistLoanWithDueDate(returnedOwner, returnedCopy, LoanStatus.RETURNED, referenceDate.minusDays(1));
        entityManager.flush();

        loanDeadlineService.processOverdueLoans(referenceDate);

        Loan reloadedFuture = entityManager.find(Loan.class, futureLoan.getId());
        Loan reloadedReturned = entityManager.find(Loan.class, returnedLoan.getId());
        assertThat(reloadedFuture.getLoanStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(reloadedReturned.getLoanStatus()).isEqualTo(LoanStatus.RETURNED);
    }

    // ---------------------------------------------------------------
    // Fixtures
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
        title.setTitle("Titre de test — échéances");
        title.setLanguage(Language.FR);
        title.setTitleStatus(TitleStatus.ACTIVE);
        title.setCreatedAt(Instant.now());
        title.setUpdatedAt(Instant.now());
        entityManager.persist(title);
        return title;
    }

    private Copy persistCopy(Title title, String inventoryCode) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(inventoryCode);
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }

    private Loan persistLoanWithDueDate(AppUser user, Copy copy, LoanStatus status, LocalDate dueDate) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(dueDate.minusDays(21).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        loan.setDueDate(dueDate);
        loan.setLoanStatus(status);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        return loan;
    }

    private List<Notification> findNotificationsByLoanId(Long loanId) {
        return entityManager.createQuery("SELECT n FROM Notification n WHERE n.loan.id = :loanId", Notification.class)
                .setParameter("loanId", loanId)
                .getResultList();
    }

    private void updateLoanDueSoonDaysSetting(int value) {
        entityManager.createQuery("UPDATE ApplicationSetting s SET s.settingValue = :value WHERE s.settingKey = :key")
                .setParameter("value", String.valueOf(value))
                .setParameter("key", LOAN_DUE_SOON_DAYS_KEY)
                .executeUpdate();
    }
}
