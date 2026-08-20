package be.primatis.reservation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Déclenchement périodique de l'expiration des {@code Reservation READY}
 * (DEV-08.7, DEV-DEC-0039). Responsabilité strictement limitée à
 * l'obtention de l'instant courant et à la délégation à {@link
 * ReservationExpirationService} — ne connaît ni {@code CopyRepository},
 * ni {@code ReservationRepository}, ni {@code ReservationAssignmentService},
 * ni {@code MemberExpirationPolicy} : aucune logique métier ici.
 *
 * <p>Cadence fixée à une minute ({@code fixedDelay} : la prochaine
 * exécution est planifiée une minute après la <em>fin</em> de la
 * précédente, jamais un chevauchement d'exécutions ; {@code initialDelay}
 * également fixé à une minute — la première exécution n'a donc pas lieu
 * immédiatement au démarrage). Valeurs externalisées en propriétés Spring
 * avec un défaut de 60 000 ms (DEV-DEC-0039) uniquement pour permettre à
 * {@code application-test.yml} de neutraliser tout déclenchement
 * automatique réel pendant l'exécution des tests (fixé à une valeur très
 * supérieure à la durée de toute suite de tests) — la cadence métier
 * décidée reste inchangée en production.
 */
@Component
public class ReservationExpirationScheduler {

    /**
     * Externalisés en propriété Spring (défaut 60 000 ms = 1 minute,
     * DEV-DEC-0039) uniquement pour permettre de neutraliser le
     * déclenchement automatique réel pendant les tests
     * ({@code application-test.yml}, cf. Javadoc de la classe) — la
     * cadence métier décidée reste une minute en production, valeur par
     * défaut inchangée.
     */
    private static final String FIXED_DELAY_PROPERTY =
            "${primatis.reservation.expiration.fixed-delay-ms:60000}";
    private static final String INITIAL_DELAY_PROPERTY =
            "${primatis.reservation.expiration.initial-delay-ms:60000}";

    private final ReservationExpirationService reservationExpirationService;
    private final Clock clock;

    public ReservationExpirationScheduler(
            ReservationExpirationService reservationExpirationService, Clock clock) {
        this.reservationExpirationService = reservationExpirationService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = FIXED_DELAY_PROPERTY, initialDelayString = INITIAL_DELAY_PROPERTY)
    public void expireReadyReservations() {
        reservationExpirationService.processExpiredReadyReservations(clock.instant());
    }
}
