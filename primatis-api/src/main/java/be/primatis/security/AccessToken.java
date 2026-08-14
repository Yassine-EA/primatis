package be.primatis.security;

import java.time.Instant;

/**
 * Résultat interne de génération d'un access token JWT (DEV-03.7). Ce n'est
 * pas le contrat REST final {@code LoginResponse} (token/type/expiration) —
 * ce dernier appartient à l'étape qui créera l'endpoint de login.
 */
public record AccessToken(String token, String tokenType, Instant expiresAt, long expiresInSeconds) {
}
