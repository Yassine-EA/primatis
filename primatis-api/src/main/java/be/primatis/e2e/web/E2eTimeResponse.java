package be.primatis.e2e.web;

import java.time.Instant;

/**
 * Contrat REST de lecture de l'état du Clock E2E (DEV-DEC-0070) —
 * {@code currentTime} : temps perçu actuellement par l'application
 * (identique au temps UTC réel tant qu'{@code offsetSeconds == 0}) ;
 * {@code offsetSeconds} : décalage relatif appliqué (peut être négatif),
 * jamais un instant absolu figé. Surface observable minimale permettant
 * de prouver, depuis un test Playwright, qu'{@code advance}/{@code reset}
 * ont réellement pris effet côté backend.
 */
public record E2eTimeResponse(Instant currentTime, long offsetSeconds) {
}
