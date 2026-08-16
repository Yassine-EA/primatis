package be.primatis.user.web;

import java.util.List;

/**
 * Réponse de {@code GET /api/v1/users/{id}} (DEV-05.12 Décision 13).
 * Compose {@link UserResponse} sans le modifier — même précédent que
 * {@link CreateUserResponse} — afin d'exposer les rôles actuels d'un
 * utilisateur sans forcer leur chargement sur {@code listUsers} ni sur
 * les cinq réponses d'action d'écriture qui n'en ont pas besoin.
 *
 * {@code roles} ne contient que les codes de rôle ({@code RoleCode}),
 * jamais de permissions, triés pour une sortie déterministe.
 */
public record UserDetailResponse(UserResponse user, List<String> roles) {
}
