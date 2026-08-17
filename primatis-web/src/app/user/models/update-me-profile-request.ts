/**
 * Voir `be.primatis.user.web.UpdateMeProfileRequest` côté backend. Absent
 * et `null` explicite produisent la même sémantique (aucune modification) :
 * contrairement à `UpdateUserRequest`, `null` n'apporte ici aucune action
 * distincte d'`undefined` — le type reste fidèle au contrat backend.
 */
export interface UpdateMeProfileRequest {
  phoneNumber?: string | null;
}
