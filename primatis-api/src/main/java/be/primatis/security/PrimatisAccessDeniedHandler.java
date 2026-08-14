package be.primatis.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Clock;

/**
 * Rejet 403 pour une Authentication valide mais une autorisation HTTP
 * refusée au niveau de la {@code SecurityFilterChain} (DEV-03.10) —
 * distinct des refus {@code @PreAuthorize} de Method Security (DEV-03.9),
 * qui se produisent après l'entrée dans Spring MVC et sont donc interceptés
 * par le {@code @ExceptionHandler(AccessDeniedException.class)} dédié de
 * {@link be.primatis.exception.GlobalExceptionHandler} — jamais ce handler
 * ici, car {@code ExceptionTranslationFilter} ne voit jamais une exception
 * déjà résolue à l'intérieur du dispatch MVC. Les deux chemins produisent
 * néanmoins le même contrat public {@code 403 / ACCESS_DENIED}.
 */
public class PrimatisAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(PrimatisAccessDeniedHandler.class);

    private final SecurityErrorResponseWriter responseWriter;

    public PrimatisAccessDeniedHandler(ObjectMapper objectMapper, Clock clock) {
        this.responseWriter = new SecurityErrorResponseWriter(objectMapper, clock);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.debug("Autorisation HTTP refusée sur {} {}", request.getMethod(), request.getRequestURI());
        responseWriter.write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Vous n'êtes pas autorisé à effectuer cette action.");
    }
}
