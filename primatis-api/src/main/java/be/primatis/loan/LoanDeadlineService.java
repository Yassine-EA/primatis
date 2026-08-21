package be.primatis.loan;

import be.primatis.notification.NotificationService;
import be.primatis.notification.NotificationType;
import be.primatis.setting.ApplicationSettingService;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Traitement périodique des échéances {@code Loan} (DEV-10.8) : détection
 * des Loans arrivant bientôt à échéance ({@code LOAN_DUE_SOON}) et des
 * Loans réellement échus, avec matérialisation de la transition {@code
 * ACTIVE → OVERDUE} ({@code LOAN_OVERDUE}). Composant dédié, distinct de
 * {@link LoanService} — même principe de séparation que {@code
 * ReservationExpirationService}/{@code ReservationAssignmentService}
 * (DEV-08.6/08.7) : ne pas alourdir la responsabilité déjà large de
 * {@code LoanService} (enregistrement/retour) avec un traitement
 * périodique orthogonal.
 *
 * <p><b>Deux traitements indépendants, deux méthodes d'entrée</b> ({@link
 * #processDueSoonLoans}/{@link #processOverdueLoans}) : due-soon ne mute
 * jamais le Loan (seulement une Notification auxiliaire), overdue mute le
 * {@code loanStatus} — aucune raison de les coupler dans une même
 * transaction ou un même batch.
 *
 * <p><b>Résilience par candidate</b> (même précédent exact que {@code
 * ReservationExpirationService}, DEV-08.7 §17) : chaque traitement
 * identifie un batch borné de candidats (projection scalaire {@code id},
 * jamais l'Entity), puis délègue chaque candidat à sa propre méthode
 * {@code @Transactional} via {@code self} (bean injecté paresseusement
 * pour router l'appel à travers le proxy Spring — l'auto-invocation directe
 * rendrait {@code @Transactional} inopérant, même contournement documenté
 * que {@code ReservationExpirationService}). Une erreur sur une candidate
 * est journalisée puis n'empêche jamais le traitement des autres.
 */
@Service
public class LoanDeadlineService {

    private static final Logger log = LoggerFactory.getLogger(LoanDeadlineService.class);

    /**
     * Garde-fou technique, jamais une règle métier ni une {@code
     * ApplicationSetting} (même précédent exact que {@code
     * ReservationExpirationService.EXPIRATION_BATCH_SIZE}, DEV-DEC-0039) :
     * borne le nombre de candidats traités par exécution. Les candidats
     * excédentaires seront traités lors d'une exécution ultérieure.
     */
    static final int OVERDUE_BATCH_SIZE = 100;
    static final int DUE_SOON_BATCH_SIZE = 100;

    private static final String LOAN_DUE_SOON_DAYS_KEY = "LOAN_DUE_SOON_DAYS";

    /**
     * Nom de la contrainte PostgreSQL (V007, DEV-10.3) dont la violation
     * traduit spécifiquement une course concurrente sur l'anti-doublon
     * {@code LOAN_DUE_SOON} — même précédent exact que {@code
     * ReservationService.RESERVATION_DUPLICATE_CONSTRAINTS} (DEV-08.5) :
     * dernier rempart contre une vraie course entre deux exécutions
     * périodiques, sans introduire de lock pessimiste sur {@code Loan}
     * pour cette seule création auxiliaire (mission DEV-10.8 §17).
     */
    private static final Set<String> DUE_SOON_DUPLICATE_CONSTRAINTS = Set.of("ux_notification_loan_due_soon");

    private final LoanRepository loanRepository;
    private final ApplicationSettingService applicationSettingService;
    private final NotificationService notificationService;
    private final Clock clock;
    private final LoanDeadlineService self;

    public LoanDeadlineService(
            LoanRepository loanRepository,
            ApplicationSettingService applicationSettingService,
            NotificationService notificationService,
            Clock clock,
            @Lazy LoanDeadlineService self) {
        this.loanRepository = loanRepository;
        this.applicationSettingService = applicationSettingService;
        this.notificationService = notificationService;
        this.clock = clock;
        this.self = self;
    }

    // ---------------------------------------------------------------
    // LOAN_DUE_SOON
    // ---------------------------------------------------------------

    /**
     * Point d'entrée appelé par {@link LoanDeadlineScheduler}. Fenêtre
     * due-soon : {@code referenceDate < dueDate <= referenceDate +
     * LOAN_DUE_SOON_DAYS} — {@code LOAN_DUE_SOON_DAYS} lu exclusivement via
     * {@link ApplicationSettingService#getInteger(String)} (jamais codé en
     * dur, business-rules.md §10.4). Un Loan déjà {@code OVERDUE} (donc
     * {@code dueDate < referenceDate}) ou {@code RETURNED} n'est jamais
     * candidat (filtré par {@link LoanRepository#findDueSoonLoanIds}).
     */
    public void processDueSoonLoans(LocalDate referenceDate) {
        int dueSoonDays = applicationSettingService.getInteger(LOAN_DUE_SOON_DAYS_KEY);
        LocalDate upperBound = referenceDate.plusDays(dueSoonDays);

        List<Long> candidateIds = loanRepository.findDueSoonLoanIds(
                LoanStatus.ACTIVE, referenceDate, upperBound, PageRequest.of(0, DUE_SOON_BATCH_SIZE));

        for (Long candidateId : candidateIds) {
            try {
                self.createDueSoonIfStillApplicable(candidateId, referenceDate, upperBound);
            } catch (DataIntegrityViolationException ex) {
                if (isDueSoonDuplicateConstraint(ex)) {
                    log.debug("LOAN_DUE_SOON déjà matérialisée pour le Loan {} "
                            + "(course concurrente absorbée localement).", candidateId);
                } else {
                    log.error("Échec du traitement due-soon pour le Loan {} : {}",
                            candidateId, ex.getMessage(), ex);
                }
            } catch (RuntimeException ex) {
                log.error("Échec du traitement due-soon pour le Loan {} : {}", candidateId, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Traite une candidate unique, dans sa propre transaction. Revalidation
     * complète après chargement (aucun verrou pessimiste — cette méthode ne
     * mute jamais le Loan, seule une Notification auxiliaire est créée ;
     * l'anti-doublon reste entièrement porté par {@link
     * NotificationService#createLoanDueSoonIfAbsent}, {@code
     * existsByLoanIdAndNotificationType} + {@code
     * ux_notification_loan_due_soon} en filet de sécurité ultime, DEV-10.3)
     * : un Loan retourné ou déjà repassé {@code OVERDUE} par une exécution
     * concurrente entre l'identification et cet appel est silencieusement
     * ignoré — jamais une erreur métier.
     */
    @Transactional
    public void createDueSoonIfStillApplicable(Long loanId, LocalDate referenceDate, LocalDate upperBound) {
        Loan loan = loanRepository.findById(loanId).orElse(null);
        if (loan == null || loan.getLoanStatus() != LoanStatus.ACTIVE) {
            return;
        }

        LocalDate dueDate = loan.getDueDate();
        if (!referenceDate.isBefore(dueDate) || dueDate.isAfter(upperBound)) {
            return;
        }

        notificationService.createLoanDueSoonIfAbsent(loan,
                "Échéance de prêt proche", "La date de retour prévue de votre prêt approche.");
    }

    private boolean isDueSoonDuplicateConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException hibernateException) {
            return DUE_SOON_DUPLICATE_CONSTRAINTS.contains(hibernateException.getConstraintName());
        }
        return false;
    }

    // ---------------------------------------------------------------
    // LOAN_OVERDUE
    // ---------------------------------------------------------------

    /**
     * Point d'entrée appelé par {@link LoanDeadlineScheduler}. Un Loan
     * {@code ACTIVE} dont {@code dueDate < referenceDate} passe {@code
     * OVERDUE} — {@code dueDate == referenceDate} reste {@code ACTIVE}
     * (mission §8, même précédent que la fenêtre due-soon). Ne crée jamais
     * de {@code Fine} (business-rules.md §10.2 : « Do not create a Fine
     * from the simple periodic detection of OVERDUE ») — la Fine reste
     * exclusivement créée par {@code FineService.createForLateReturnIfApplicable},
     * appelée uniquement depuis {@code LoanService.registerReturn}, jamais
     * depuis ce traitement.
     */
    public void processOverdueLoans(LocalDate referenceDate) {
        List<Long> candidateIds = loanRepository.findOverdueLoanIds(
                LoanStatus.ACTIVE, referenceDate, PageRequest.of(0, OVERDUE_BATCH_SIZE));

        for (Long candidateId : candidateIds) {
            try {
                self.markOverdueIfStillDue(candidateId, referenceDate);
            } catch (RuntimeException ex) {
                log.error("Échec du traitement overdue pour le Loan {} : {}", candidateId, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Traite une candidate unique, dans sa propre transaction. Verrouillage
     * explicite via {@link LoanRepository#findByIdForUpdate(Long)} (même
     * primitive exacte que {@code LoanService.registerReturn}, DEV-07.6) —
     * élimine toute course avec un retour concurrent : si {@code
     * registerReturn} committe {@code RETURNED} avant que ce traitement
     * n'acquière le verrou, la revalidation post-lock découvre {@code
     * loanStatus != ACTIVE} et abandonne silencieusement (aucune mutation,
     * aucune Notification) ; si ce traitement acquiert le verrou en
     * premier, {@code registerReturn} attend réellement au niveau
     * PostgreSQL jusqu'au commit (charge et verrouille via la même
     * primitive), puis constate {@code OVERDUE} — transition déjà
     * compatible avec {@code registerReturn} (statuts ouverts {@code
     * ACTIVE}/{@code OVERDUE} tous deux acceptés, {@code
     * OPEN_LOAN_STATUSES}). Aucun verrou {@code Copy}/{@code Reservation}/
     * {@code Fine} acquis ici — workflow minimal (mission §13).
     *
     * <p>{@code updatedAt = clock.instant()} (même {@link Clock} injecté
     * que {@code LoanService}), jamais {@code Instant.now()} direct —
     * déterministe en test.
     */
    @Transactional
    public void markOverdueIfStillDue(Long loanId, LocalDate referenceDate) {
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new IllegalStateException(
                        "Incohérence de données : Loan " + loanId
                                + " disparu entre son identification et son verrouillage."));

        if (loan.getLoanStatus() != LoanStatus.ACTIVE) {
            return;
        }
        if (!loan.getDueDate().isBefore(referenceDate)) {
            return;
        }

        loan.setLoanStatus(LoanStatus.OVERDUE);
        loan.setUpdatedAt(clock.instant());

        notificationService.createForLoan(loan, NotificationType.LOAN_OVERDUE,
                "Prêt en retard", "La date de retour prévue de votre prêt est dépassée.");
    }
}
