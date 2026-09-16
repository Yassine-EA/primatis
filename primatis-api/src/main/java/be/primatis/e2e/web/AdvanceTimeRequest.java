package be.primatis.e2e.web;

import jakarta.validation.constraints.NotNull;

/**
 * Contrat REST minimal d'avance du Clock E2E (DEV-DEC-0070,
 * {@code POST /api/v1/e2e/time/advance}). {@code hours} exprime un
 * décalage relatif (jamais un instant absolu) — validation structurelle
 * uniquement ({@code @NotNull}), aucune borne métier arbitraire : un
 * scénario E2E calcule dynamiquement la durée nécessaire à partir des
 * {@code ApplicationSetting} réels (ex. {@code LOAN_DURATION_DAYS}),
 * jamais une valeur fixe côté backend.
 */
public record AdvanceTimeRequest(@NotNull Long hours) {
}
