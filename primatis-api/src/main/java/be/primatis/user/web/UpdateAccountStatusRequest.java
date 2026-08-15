package be.primatis.user.web;

import be.primatis.user.AccountStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Contrat REST de changement d'{@code AccountStatus} (DEV-05.7, {@code
 * PATCH /api/v1/users/{id}/account-status}, {@code USER_MANAGE}).
 * Idempotent : appliquer le statut déjà courant est un succès sans effet
 * de bord (200, aucune mutation), pas une erreur.
 */
public record UpdateAccountStatusRequest(@NotNull AccountStatus status) {
}
