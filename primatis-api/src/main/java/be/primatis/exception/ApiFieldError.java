package be.primatis.exception;

/**
 * Erreur de validation associée à un champ précis du contrat REST.
 *
 * {@code field} désigne le nom du champ tel qu'exposé côté client (par
 * exemple le nom de propriété JSON d'un DTO), pas le chemin interne complet
 * de la validation.
 */
public record ApiFieldError(String field, String message) {
}
