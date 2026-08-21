package be.primatis.loan;

import be.primatis.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Vérifie {@link LoanDeadlineScheduler} (DEV-10.8) de manière strictement
 * <b>structurelle</b> — jamais temporelle (même précédent exact que {@code
 * ReservationExpirationSchedulerTests}, mission §16 : ni {@code
 * Thread.sleep}, ni attente réelle). {@link LoanDeadlineService} mocké
 * ({@code @MockitoBean}) pour isoler la seule responsabilité du scheduler :
 * obtenir la date courante via {@link Clock} et déléguer intégralement,
 * sans aucune logique métier propre.
 *
 * <p><b>Pool Hikari réduit à 2</b> (DEV-10.8, diagnostic gate complet) :
 * cette classe déclenche son propre contexte Spring dédié (distinct du
 * contexte partagé, à cause de {@code @MockitoBean}), donc son propre
 * {@code HikariDataSource} — jamais fermé avant la fin de la JVM tant que
 * le cache de contexte de test reste chaud. Aucun trafic DB réel n'est
 * exercé ici ({@link LoanDeadlineService} entièrement mocké, les deux
 * tests de délégation ne font que vérifier un appel Mockito, les autres ne
 * touchent qu'à la réflexion) : un pool de taille 10 par défaut n'apporte
 * aucune valeur et ajoutait une pression résiduelle marginale sur le
 * budget de connexions PostgreSQL partagé par l'ensemble de la suite
 * (~7 contextes distincts au total) — pression qui, cumulée aux pics réels
 * d'autres classes (notamment {@code LoanServiceConcurrencyTests}, pool
 * élevé à 25 depuis DEV-07.6), pouvait faire dépasser transitoirement le
 * plafond effectif du rôle applicatif et provoquer l'échec par timeout
 * Hikari (~30 s) du contexte {@code FlywaySchemaRebuildTests}/{@code
 * RbacBootstrapTests} lors d'une exécution complète de la suite. Correction
 * strictement locale à cette classe, sans impact sur la production ni sur
 * les autres classes de test.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class LoanDeadlineSchedulerTests {

    @Autowired
    private LoanDeadlineScheduler scheduler;

    @Autowired
    private Clock clock;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private LoanDeadlineService loanDeadlineService;

    @Test
    void processDueSoonLoansDelegatesToTheServiceWithTheClockDate() {
        doNothing().when(loanDeadlineService).processDueSoonLoans(any());
        LocalDate expected = LocalDate.now(clock);

        scheduler.processDueSoonLoans();

        var captor = forClass(LocalDate.class);
        verify(loanDeadlineService).processDueSoonLoans(captor.capture());
        assertThat(captor.getValue()).isEqualTo(expected);
        verifyNoMoreInteractions(loanDeadlineService);
    }

    @Test
    void processOverdueLoansDelegatesToTheServiceWithTheClockDate() {
        doNothing().when(loanDeadlineService).processOverdueLoans(any());
        LocalDate expected = LocalDate.now(clock);

        scheduler.processOverdueLoans();

        var captor = forClass(LocalDate.class);
        verify(loanDeadlineService).processOverdueLoans(captor.capture());
        assertThat(captor.getValue()).isEqualTo(expected);
        verifyNoMoreInteractions(loanDeadlineService);
    }

    /**
     * {@code fixedDelay}/{@code initialDelay} sont externalisés en
     * propriétés Spring (défaut 60 000 ms chacun) uniquement pour permettre
     * à {@code application-test.yml} de neutraliser le déclenchement
     * automatique réel pendant les tests — la cadence métier par défaut
     * (une minute) reste donc vérifiée ici via le défaut embarqué dans le
     * placeholder lui-même, jamais résolue dynamiquement (le profil de test
     * la surcharge délibérément), même précédent exact que {@code
     * ReservationExpirationSchedulerTests}.
     */
    @Test
    void processDueSoonLoansMethodIsScheduledWithAOneMinuteDefaultDelay() throws NoSuchMethodException {
        Method method = LoanDeadlineScheduler.class.getDeclaredMethod("processDueSoonLoans");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${primatis.loan.due-soon.fixed-delay-ms:60000}");
        assertThat(scheduled.initialDelayString())
                .isEqualTo("${primatis.loan.due-soon.initial-delay-ms:60000}");
    }

    @Test
    void processOverdueLoansMethodIsScheduledWithAOneMinuteDefaultDelay() throws NoSuchMethodException {
        Method method = LoanDeadlineScheduler.class.getDeclaredMethod("processOverdueLoans");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${primatis.loan.overdue.fixed-delay-ms:60000}");
        assertThat(scheduled.initialDelayString())
                .isEqualTo("${primatis.loan.overdue.initial-delay-ms:60000}");
    }

    /**
     * Confirmation structurelle par réflexion (même principe que {@code
     * ReservationExpirationSchedulerTests.schedulerDeclaresNoInstanceFieldOtherThanTheExpirationServiceAndClock})
     * que le scheduler ne déclare aucun champ d'instance vers une
     * dépendance métier — seuls {@link LoanDeadlineService} et {@link
     * Clock} sont déclarés (les constantes {@code static} des placeholders
     * de propriété sont délibérément exclues de ce contrôle).
     */
    @Test
    void schedulerDeclaresNoInstanceFieldOtherThanTheDeadlineServiceAndClock() {
        Set<Class<?>> instanceFieldTypes = Arrays.stream(LoanDeadlineScheduler.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .collect(Collectors.toSet());

        assertThat(instanceFieldTypes).containsExactlyInAnyOrder(LoanDeadlineService.class, Clock.class);
    }

    @Test
    void schedulingIsEnabledByADedicatedConfigurationClassNotAnArbitraryBusinessClass() {
        Object schedulingConfigBean = applicationContext.getBean(SchedulingConfig.class);

        assertThat(schedulingConfigBean).isNotNull();
        assertThat(SchedulingConfig.class.getAnnotation(EnableScheduling.class)).isNotNull();
    }
}
