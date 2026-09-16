package be.primatis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * {@link Clock} injectable pour tout workflow métier dépendant du temps
 * (backend.md « Time ») : évite de disperser {@code Instant.now()} dans les
 * Services et rend les décisions temporelles testables avec un Clock fixe.
 * Introduit par DEV-03.6 (verrouillage temporaire de connexion), réutilisable
 * par les futurs workflows dépendant du temps (expiration de réservation,
 * échéance de prêt...).
 *
 * {@code @Profile("!e2e")} (DEV-DEC-0070) : ce bean reste inchangé pour
 * {@code default}/{@code dev}/{@code test}/{@code production}/
 * {@code preview}. Sous le profil {@code e2e} uniquement, {@link
 * be.primatis.e2e.E2eClockConfig} fournit à la place un {@code Clock}
 * ajustable — les deux beans {@code clock()} sont mutuellement exclusifs
 * par profil, jamais actifs simultanément (aucun {@code @Primary}
 * nécessaire).
 */
@Configuration
public class ClockConfig {

    @Bean
    @Profile("!e2e")
    public Clock clock() {
        return Clock.systemUTC();
    }
}
