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
 * Rejet 401 pour un Bearer token présent mais refusé par le Resource Server
 * (DEV-03.10) — branché au niveau {@code oauth2ResourceServer().authenticationEntryPoint(...)},
 * invoqué directement par {@code BearerTokenAuthenticationFilter} lorsque
 * {@link org.springframework.security.oauth2.jwt.JwtDecoder} (DEV-03.7)
 * rejette le token : expiré, signature invalide, issuer/audience incorrects,
 * token malformé — toutes ces causes sont volontairement regroupées sous un
 * unique code public {@code INVALID_TOKEN}, sans jamais exposer la cause
 * cryptographique exacte au client. À distinguer de
 * {@link PrimatisAuthenticationEntryPoint} (aucune Authentication du tout).
 *
 * {@code WWW-Authenticate: Bearer error="invalid_token"} (RFC 6750 §3.1) est
 * conservé — vocabulaire OAuth2 standard, ne révèle aucun détail
 * d'implémentation PRIMATIS.
 */
public class PrimatisInvalidTokenAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(PrimatisInvalidTokenAuthenticationEntryPoint.class);

    private final SecurityErrorResponseWriter responseWriter;

    public PrimatisInvalidTokenAuthenticationEntryPoint(ObjectMapper objectMapper, Clock clock) {
        this.responseWriter = new SecurityErrorResponseWriter(objectMapper, clock);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        // DEBUG, pas ERROR : un token expiré/invalide est un rejet attendu du
        // workflow, pas une erreur serveur. Jamais le JWT complet journalisé.
        log.debug("Bearer token rejeté sur {} {} : {}", request.getMethod(), request.getRequestURI(),
                authException.getClass().getSimpleName());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
        responseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                "Le jeton d'authentification fourni est invalide ou a expiré.");
    }
}
