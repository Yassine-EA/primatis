/**
 * Voir `be.primatis.setting.ApplicationSetting.valueType` côté backend
 * (colonne `value_type`, contrainte `ck_application_setting_value_type`).
 * Le schéma autorise aussi `BOOLEAN`/`STRING`, mais aucune des six clés V1
 * réelles n'utilise ces types (DEV-12.2 mandat §8) : aucun comportement
 * frontend mort n'est créé pour eux ici.
 */
export type SettingValueType = 'INTEGER' | 'DECIMAL';

/**
 * Garde d'exécution : `SettingResponse.valueType` est typé {@link
 * SettingValueType} pour le cas nominal, mais un JSON backend reste
 * non validé à l'exécution (comme tout DTO de ce projet). Si une clé
 * existante portait un jour un `value_type` différent (BOOLEAN/STRING,
 * schéma le permet), ce garde permet de le détecter explicitement plutôt
 * que de masquer silencieusement l'incohérence (DEV-12.2 mandat §8).
 */
export function isSupportedSettingValueType(value: string): value is SettingValueType {
  return value === 'INTEGER' || value === 'DECIMAL';
}
