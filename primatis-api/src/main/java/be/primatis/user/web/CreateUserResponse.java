package be.primatis.user.web;

/**
 * Réponse de {@code POST /api/v1/users} (DEV-05.5). {@code initialPassword}
 * n'est retourné qu'ici, une seule fois : aucun {@code GET} ni {@link
 * UserResponse} ultérieur ne l'expose, et il n'est jamais persisté en clair
 * (seul {@code passwordHash} est stocké sur {@code AppUser}).
 */
public record CreateUserResponse(UserResponse user, String initialPassword) {
}
