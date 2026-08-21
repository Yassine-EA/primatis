package be.primatis.loan;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Déclenchement périodique des traitements d'échéance {@code Loan}
 * (DEV-10.8) : {@code LOAN_DUE_SOON} et {@code LOAN_OVERDUE}. Responsabilité
 * strictement limitée à l'obtention de la date courante via {@link Clock}
 * et à la délégation à {@link LoanDeadlineService} — ne connaît ni {@code
 * LoanRepository}, ni {@code NotificationService}, ni {@code
 * ApplicationSettingService} : aucune logique métier ici. Même précédent
 * architectural exact que {@code ReservationExpirationScheduler}
 * (DEV-08.7, DEV-DEC-0039).
 *
 * <p>Deux méthodes {@code @Scheduled} distinctes (due-soon/overdue), même
 * classe : les deux traitements restent des concepts périodiques
 * indépendants (mission §9), mais fusionner leurs deux composants
 * scheduler/service en quatre fichiers séparés n'apporterait aucune valeur
 * — même principe que la cohabitation de plusieurs méthodes publiques dans
 * {@code LoanDeadlineService}.
 *
 * <p>Cadences externalisées en propriétés Spring, défaut 60 000 ms (1
 * minute) chacune — même valeur et même raison exacte que {@code
 * ReservationExpirationScheduler} : permettre à {@code
 * application-test.yml} de neutraliser tout déclenchement automatique réel
 * pendant les tests, la cadence métier par défaut restant inchangée en
 * production. La fréquence exacte est {@code IMPLEMENTATION FREEDOM}
 * (business-rules.md §9) — aucune décision métier figée sur sa valeur.
 */
@Component
public class LoanDeadlineScheduler {

    private static final String DUE_SOON_FIXED_DELAY_PROPERTY =
            "${primatis.loan.due-soon.fixed-delay-ms:60000}";
    private static final String DUE_SOON_INITIAL_DELAY_PROPERTY =
            "${primatis.loan.due-soon.initial-delay-ms:60000}";
    private static final String OVERDUE_FIXED_DELAY_PROPERTY =
            "${primatis.loan.overdue.fixed-delay-ms:60000}";
    private static final String OVERDUE_INITIAL_DELAY_PROPERTY =
            "${primatis.loan.overdue.initial-delay-ms:60000}";

    private final LoanDeadlineService loanDeadlineService;
    private final Clock clock;

    public LoanDeadlineScheduler(LoanDeadlineService loanDeadlineService, Clock clock) {
        this.loanDeadlineService = loanDeadlineService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = DUE_SOON_FIXED_DELAY_PROPERTY, initialDelayString = DUE_SOON_INITIAL_DELAY_PROPERTY)
    public void processDueSoonLoans() {
        loanDeadlineService.processDueSoonLoans(LocalDate.now(clock));
    }

    @Scheduled(fixedDelayString = OVERDUE_FIXED_DELAY_PROPERTY, initialDelayString = OVERDUE_INITIAL_DELAY_PROPERTY)
    public void processOverdueLoans() {
        loanDeadlineService.processOverdueLoans(LocalDate.now(clock));
    }
}
