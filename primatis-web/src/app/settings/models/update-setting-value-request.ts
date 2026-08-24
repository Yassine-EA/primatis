/**
 * Voir `be.primatis.setting.web.UpdateSettingValueRequest` côté backend.
 * Modifie uniquement `settingValue` — `settingKey`/`valueType`/
 * `description` ne font jamais partie de ce contrat.
 */
export interface UpdateSettingValueRequest {
  readonly settingValue: string;
}
