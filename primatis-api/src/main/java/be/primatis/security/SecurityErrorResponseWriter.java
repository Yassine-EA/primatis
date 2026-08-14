package be.primatis.security;

import be.primatis.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

/**
 * Écriture factorisée d'{@link ApiErrorResponse} pour les rejets Spring
 * Security (401/403, DEV-03.10) — mêmes composants de sérialisation
 * (Jackson) et le même {@link Clock} applicatif que le reste de PRIMATIS,
 * évitant de dupliquer cette logique entre les entry points et le
 * {@code AccessDeniedHandler}. Ces rejets se produisent avant l'entrée dans
 * Spring MVC (au niveau des filtres servlet), donc {@link GlobalExceptionHandler}
 * (mécanisme {@code @RestControllerAdvice}) ne peut pas les intercepter :
 * la réponse doit être écrite directement sur le {@link HttpServletResponse}.
 */
class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    SecurityErrorResponseWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
            String message) throws IOException {
        ApiErrorResponse body = new ApiErrorResponse(
                clock.instant(), status.value(), status.getReasonPhrase(), code, message,
                request.getRequestURI(), List.of());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
