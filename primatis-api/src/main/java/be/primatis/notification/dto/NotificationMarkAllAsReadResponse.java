package be.primatis.notification.dto;

/**
 * Contrat REST du résultat de l'opération « tout marquer comme lu »
 * (DEV-10.4, DEV-DEC-0052). Aucun précédent {@code 204 No Content} dans
 * PRIMATIS (tous les endpoints de mutation existants retournent la
 * ressource affectée ou un DTO explicite) : {@code updatedCount} confirme
 * au frontend le nombre de Notifications réellement passées {@code UNREAD
 * → READ} par cet appel, utile pour un message de confirmation, sans
 * nécessiter de re-fetch immédiat de la liste complète. Idempotent :
 * {@code updatedCount = 0} est un succès normal, jamais une erreur.
 */
public record NotificationMarkAllAsReadResponse(int updatedCount) {
}
