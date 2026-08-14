package be.primatis.security.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Contrat REST d'entrée du login PRIMATIS.
 *
 * L'authentification repose exclusivement sur l'adresse e-mail et le mot de
 * passe. Les erreurs d'identification restent volontairement non
 * discriminantes côté backend.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
