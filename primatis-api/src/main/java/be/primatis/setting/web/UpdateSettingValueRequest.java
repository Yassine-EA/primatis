package be.primatis.setting.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Corps {@code PATCH /api/v1/settings/{settingKey}} (DEV-12.2, mandat §6) :
 * modifie uniquement {@code settingValue}. {@code settingKey}, {@code
 * valueType} et {@code description} ne font jamais partie de ce contrat —
 * aucune route de ce workflow ne permet de les modifier.
 *
 * <p>{@code @NotBlank} : absence ou chaîne vide/blanche échoue en 400
 * {@code VALIDATION_FAILED} avant d'atteindre le Service (même précédent que
 * {@code user.web.BlockMembershipRequest#blockedReason}). La validation du
 * contenu (entier/décimal, strictement positif, selon le {@code valueType}
 * existant de la clé) reste une règle métier, appliquée par {@code
 * ApplicationSettingService} — jamais dupliquée ici.
 */
public record UpdateSettingValueRequest(@NotBlank String settingValue) {
}
