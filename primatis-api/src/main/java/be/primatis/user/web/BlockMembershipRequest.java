package be.primatis.user.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Contrat REST de blocage/mise à jour de motif d'un adhérent (DEV-05.7,
 * {@code POST /api/v1/users/{id}/membership/block}, {@code USER_MANAGE}).
 * {@code blockedReason} est obligatoire : l'invariant {@code blockedReason
 * != null ⇒ memberStatus == BLOCKED} (déjà FIGÉ) implique la réciproque
 * opérationnelle pour cette action précise — on ne bloque jamais sans motif
 * enregistré. Réutilisé également pour l'appel idempotent {@code BLOCKED →
 * BLOCKED} (mise à jour du motif sans changer le statut).
 */
public record BlockMembershipRequest(@NotBlank String blockedReason) {
}
