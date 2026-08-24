package be.primatis.setting.web;

import be.primatis.setting.ApplicationSetting;
import be.primatis.user.AppUser;

import java.time.Instant;
import java.util.Objects;

/**
 * Contrat REST de lecture d'un {@code application_setting} (DEV-12.2,
 * {@code GET}/{@code PATCH /api/v1/settings}). Expose exactement les champs
 * attendus par le mandat DEV-12.2 §6 (DTO de lecture) : {@code settingKey},
 * {@code settingValue}, {@code valueType}, {@code description}, {@code
 * updatedAt}, {@code updatedByUser}. Ne reproduit jamais l'Entity {@code
 * ApplicationSetting} telle quelle.
 *
 * <p>{@code updatedByUser} est {@code null} tant qu'aucune modification
 * n'a encore eu lieu depuis le bootstrap Flyway (V001/V006, {@code
 * updated_by_user_id} absent de l'INSERT initial) — reflète strictement
 * l'état persisté, jamais une valeur de repli substituée.
 */
public record SettingResponse(
        String settingKey,
        String settingValue,
        String valueType,
        String description,
        Instant updatedAt,
        SettingUpdatedByResponse updatedByUser) {

    public static SettingResponse from(ApplicationSetting setting) {
        Objects.requireNonNull(setting, "setting");
        AppUser updatedByUser = setting.getUpdatedByUser();
        return new SettingResponse(
                setting.getSettingKey(),
                setting.getSettingValue(),
                setting.getValueType(),
                setting.getDescription(),
                setting.getUpdatedAt(),
                updatedByUser == null ? null : SettingUpdatedByResponse.from(updatedByUser));
    }
}
