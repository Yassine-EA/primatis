package be.primatis.security;

import java.time.Instant;

/**
 * Résultat interne de génération d'un access token JWT.
 *
 * Ce record reste distinct du contrat REST {@code LoginResponse} afin de ne
 * pas exposer directement un objet interne du domaine de sécurité.
 */
public record AccessToken(String token, String tokenType, Instant expiresAt, long expiresInSeconds) {
}
