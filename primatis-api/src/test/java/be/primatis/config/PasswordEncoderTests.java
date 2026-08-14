package be.primatis.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie le bean {@link PasswordEncoder} de {@link SecurityConfig}
 * (DEV-03.4) : DelegatingPasswordEncoder + BCrypt.
 *
 * Instanciation directe de {@link SecurityConfig#passwordEncoder()} : cette
 * méthode de fabrique ne dépend d'aucune autre bean (contrairement à
 * {@code securityFilterChain}, qui exige un contexte web servlet réel), donc
 * aucun contexte Spring n'est nécessaire ici — test rapide et déterministe,
 * sans PostgreSQL ni rapport avec la persistance. {@link SecurityConfig}
 * dans son ensemble est déjà prouvée chargeable par le contexte Spring réel
 * via {@code GlobalExceptionHandlerTests} (DEV-03.3).
 */
class PasswordEncoderTests {

    private final SecurityConfig securityConfig = new SecurityConfig();

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger().addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        rootLogger().detachAppender(logAppender);
    }

    @Test
    void passwordEncoderBeanIsAvailable() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        assertThat(encoder).isNotNull();
    }

    @Test
    void encodedPasswordUsesBcryptFormat() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String encoded = encoder.encode("Un-Mot-De-Passe-Suffisamment-Long");
        assertThat(encoded).startsWith("{bcrypt}");
    }

    @Test
    void correctPasswordMatches() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String rawPassword = "Un-Mot-De-Passe-Suffisamment-Long";
        String encoded = encoder.encode(rawPassword);
        assertThat(encoder.matches(rawPassword, encoded)).isTrue();
    }

    @Test
    void incorrectPasswordDoesNotMatch() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String encoded = encoder.encode("Un-Mot-De-Passe-Suffisamment-Long");
        assertThat(encoder.matches("Un-Autre-Mot-De-Passe-Different", encoded)).isFalse();
    }

    @Test
    void sameRawPasswordProducesDifferentButValidHashes() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String rawPassword = "Un-Mot-De-Passe-Suffisamment-Long";
        String firstEncoded = encoder.encode(rawPassword);
        String secondEncoded = encoder.encode(rawPassword);

        assertThat(firstEncoded).isNotEqualTo(secondEncoded);
        assertThat(encoder.matches(rawPassword, firstEncoded)).isTrue();
        assertThat(encoder.matches(rawPassword, secondEncoded)).isTrue();
    }

    @Test
    void encodingNeverLogsRawPasswordOrHash() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String rawPassword = "Un-Mot-De-Passe-Qui-Ne-Doit-Jamais-Etre-Journalise";
        String encoded = encoder.encode(rawPassword);
        encoder.matches(rawPassword, encoded);

        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(rawPassword).doesNotContain(encoded);
        }
    }

    private Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
