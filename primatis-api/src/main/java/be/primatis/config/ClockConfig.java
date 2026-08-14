package be.primatis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * {@link Clock} injectable pour tout workflow métier dépendant du temps
 * (backend.md « Time ») : évite de disperser {@code Instant.now()} dans les
 * Services et rend les décisions temporelles testables avec un Clock fixe.
 * Introduit par DEV-03.6 (verrouillage temporaire de connexion), réutilisable
 * par les futurs workflows dépendant du temps (expiration de réservation,
 * échéance de prêt...).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
