package be.primatis.notification.dto;

/**
 * Contrat REST du compteur de Notifications {@code UNREAD} de l'utilisateur
 * authentifié (DEV-10.4, DEV-DEC-0051). DTO dédié plutôt qu'une map
 * anonyme, cohérent avec les conventions backend PRIMATIS (contrats REST
 * explicites, jamais de structure ad hoc).
 */
public record NotificationUnreadCountResponse(long count) {
}
