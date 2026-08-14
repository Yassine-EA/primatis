package be.primatis.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.Clock;

/**
 * Rejet 401 pour une requête vers une ressource protégée sans aucune
 * Authentication présente (DEV-03.10) — branché au niveau général
 * {@code exceptionHandling().authenticationEntryPoint(...)}, invoqué par
 * {@code ExceptionTranslationFilter} lorsque le contexte de sécurité est
 * anonyme (aucun Bearer token fourni). À distinguer de
 * {@link PrimatisInvalidTokenAuthenticationEntryPoint} (token présent mais
 * rejeté).
 *
 * {@code WWW-Authenticate: Bearer} (RFC 6750 §3.1, challenge initial sans
 * paramètre {@code error}) est conservé plutôt que supprimé.
 */
public class PrimatisAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(PrimatisAuthenticationEntryPoint.class);

    private final SecurityErrorResponseWriter responseWriter;

    public PrimatisAuthenticationEntryPoint(ObjectMapper objectMapper, Clock clock) {
        this.responseWriter = new SecurityErrorResponseWriter(objectMapper, clock);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        log.debug("Requête non authentifiée refusée sur {} {}", request.getMethod(), request.getRequestURI());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        responseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Une authentification est requise pour accéder à cette ressource.");
    }
}
