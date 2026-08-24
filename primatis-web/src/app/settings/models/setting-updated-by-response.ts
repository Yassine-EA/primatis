/**
 * Voir `be.primatis.setting.web.SettingUpdatedByResponse` côté backend.
 * Représentation compacte de l'`AppUser` ayant réalisé la dernière
 * modification — n'expose ni `email`, ni `phoneNumber`, ni aucune donnée
 * RBAC (DEV-12.2 mandat §8).
 */
export interface SettingUpdatedByResponse {
  readonly id: number;
  readonly firstName: string;
  readonly lastName: string;
}
