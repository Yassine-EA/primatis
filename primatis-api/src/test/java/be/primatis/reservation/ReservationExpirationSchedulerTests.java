package be.primatis.reservation;

import be.primatis.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

/**
 * Vérifie {@link ReservationExpirationScheduler} (DEV-08.7, DEV-DEC-0039)
 * de manière strictement <b>structurelle</b> — jamais temporelle (mission
 * §16 : ni {@code Thread.sleep}, ni attente réelle d'une minute).
 * {@link ReservationExpirationService} mocké ({@code @MockitoBean}, même
 * précédent que {@code AuthControllerTests}) pour isoler la seule
 * responsabilité du scheduler : obtenir l'instant courant via {@link
 * Clock} et déléguer intégralement, sans aucune logique métier propre.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationExpirationSchedulerTests {

    @Autowired
    private ReservationExpirationScheduler scheduler;

    @Autowired
    private Clock clock;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private ReservationExpirationService reservationExpirationService;

    @Test
    void expireReadyReservationsDelegatesToTheServiceWithTheClockInstant() {
        doNothing().when(reservationExpirationService).processExpiredReadyReservations(any());
        Instant before = clock.instant();

        scheduler.expireReadyReservations();

        Instant after = clock.instant();
        var captor = forClass(Instant.class);
        verify(reservationExpirationService).processExpiredReadyReservations(captor.capture());
        assertThat(captor.getValue()).isBetween(before, after);
    }

    /**
     * {@code fixedDelay}/{@code initialDelay} sont externalisés en
     * propriétés Spring (défaut 60 000 ms chacun) uniquement pour permettre
     * à {@code application-test.yml} de neutraliser le déclenchement
     * automatique réel pendant les tests (cf. Javadoc de la classe) — la
     * cadence métier décidée (une minute, DEV-DEC-0039) reste donc vérifiée
     * ici via le défaut embarqué dans le placeholder lui-même, jamais
     * résolue dynamiquement (le profil de test la surcharge
     * délibérément).
     */
    @Test
    void expireReadyReservationsMethodIsScheduledWithAOneMinuteDefaultDelay() throws NoSuchMethodException {
        Method method = ReservationExpirationScheduler.class.getDeclaredMethod("expireReadyReservations");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${primatis.reservation.expiration.fixed-delay-ms:60000}");
        assertThat(scheduled.initialDelayString())
                .isEqualTo("${primatis.reservation.expiration.initial-delay-ms:60000}");
    }

    /**
     * Confirmation structurelle par réflexion (même principe que {@code
     * UserServiceTests.listUsersMethodBodyNeverReferencesUserRoleRepository})
     * que le scheduler ne déclare aucun champ d'instance vers une
     * dépendance métier (mission §11 : ni {@code CopyRepository}, ni
     * {@code ReservationRepository}, ni {@code ReservationAssignmentService},
     * ni {@code MemberExpirationPolicy}) — seuls {@link
     * ReservationExpirationService} et {@link Clock} sont déclarés (les
     * constantes {@code static} des placeholders de propriété sont
     * délibérément exclues de ce contrôle, elles ne représentent aucune
     * dépendance injectée).
     */
    @Test
    void schedulerDeclaresNoInstanceFieldOtherThanTheExpirationServiceAndClock() {
        Set<Class<?>> instanceFieldTypes = Arrays.stream(ReservationExpirationScheduler.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .collect(Collectors.toSet());

        assertThat(instanceFieldTypes).containsExactlyInAnyOrder(ReservationExpirationService.class, Clock.class);
    }

    @Test
    void schedulingIsEnabledByADedicatedConfigurationClassNotAnArbitraryBusinessClass() {
        Object schedulingConfigBean = applicationContext.getBean(SchedulingConfig.class);

        assertThat(schedulingConfigBean).isNotNull();
        assertThat(SchedulingConfig.class.getAnnotation(EnableScheduling.class)).isNotNull();
    }
}
