package be.primatis.security.web;

import be.primatis.security.AccessToken;

import java.time.Instant;

/**
 * Contrat REST retourné après une authentification réussie.
 *
 * Les rôles et permissions ne sont pas dupliqués dans cette réponse :
 * ils sont portés par les claims du JWT signé par le backend.
 */
public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        long expiresInSeconds) {

    public static LoginResponse from(AccessToken accessToken) {
        return new LoginResponse(
                accessToken.token(),
                accessToken.tokenType(),
                accessToken.expiresAt(),
                accessToken.expiresInSeconds());
    }
}
