package be.primatis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active l'infrastructure {@code @Scheduled} de Spring (DEV-08.7,
 * DEV-DEC-0039). Composant technique isolé, au même titre que
 * {@link ClockConfig}/{@link SecurityConfig} — aucune classe métier
 * n'est modifiée pour porter cette annotation.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
