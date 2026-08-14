package be.primatis.exception;

import java.time.Instant;
import java.util.List;

/**
 * Contrat d'erreur REST commun de PRIMATIS (architecture.md §4.5).
 *
 * {@code timestamp} est un instant non ambigu (UTC), sérialisé en ISO-8601
 * grâce au module Jackson JSR-310 embarqué par Spring Boot.
 *
 * {@code fieldErrors} est toujours présent, y compris vide, pour que le
 * frontend puisse s'appuyer sur sa présence sans vérification défensive.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<ApiFieldError> fieldErrors) {
}
