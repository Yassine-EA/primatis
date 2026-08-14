package be.primatis.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Fournit un {@link MutableClock} de test, prioritaire sur le
 * {@code Clock.systemUTC()} de production ({@code ClockConfig}), partagé par
 * les tests DEV-03.6 nécessitant un instant figé et manipulable (aucun
 * Thread.sleep()) : {@code AuthServiceTests} et
 * {@code AuthServiceConcurrencyTests}.
 */
@TestConfiguration
class ClockTestConfig {

    static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(FIXED_INSTANT, ZoneOffset.UTC);
    }
}
