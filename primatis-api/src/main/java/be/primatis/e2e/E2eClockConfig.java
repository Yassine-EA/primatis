package be.primatis.e2e;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * Fournit le bean {@code Clock} ajustable sous le profil {@code e2e}
 * uniquement (DEV-DEC-0070). Mutuellement exclusif avec {@link
 * be.primatis.config.ClockConfig} ({@code @Profile("!e2e")}) : les deux
 * méthodes {@code @Bean clock()} ne sont jamais actives simultanément
 * (contrainte 1/4.1 de DEV-DEC-0070) — aucun {@code @Primary} nécessaire.
 *
 * Le bean est déclaré avec le type concret {@link E2eAdjustableClock}
 * (pas seulement {@code Clock}) : tout code injectant {@code Clock}
 * continue de fonctionner sans changement (LoanService, ReservationService,
 * FineService, ...), tandis que {@link E2eTimeService} peut injecter
 * spécifiquement {@link E2eAdjustableClock} pour accéder à {@code
 * advance()}/{@code reset()} (package-private, jamais exposés en dehors
 * de {@code be.primatis.e2e}).
 */
@Configuration
public class E2eClockConfig {

    @Bean
    @Profile("e2e")
    public E2eAdjustableClock clock() {
        return new E2eAdjustableClock(Clock.systemUTC());
    }
}
