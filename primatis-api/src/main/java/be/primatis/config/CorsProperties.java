package be.primatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origines CORS autorisées, configuration technique PRIMATIS
 * ({@code primatis.security.cors.allowed-origins}) — n'appartient pas à
 * {@code application_setting}. Centralisée ici uniquement : ne pas
 * disperser la configuration CORS dans plusieurs classes.
 */
@ConfigurationProperties(prefix = "primatis.security.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
