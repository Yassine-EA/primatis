import { RoleCode } from '../../auth/models/role';

/**
 * Voir `be.primatis.user.web.UpdateUserRequest` côté backend (PATCH sparse
 * à trois états par champ : absent = ne pas modifier, présent+valeur =
 * remplacer, présent+`null` = effacer — uniquement pour les champs où le
 * Service backend accepte `null` comme action distincte).
 *
 * Audit champ par champ (`UserService.updateUser`/`applyMembershipUpdate`) :
 * - `firstName`/`lastName` : `null` explicite est rejeté par le backend
 *   (`FIRST_NAME_MUST_NOT_BE_BLANK`/`LAST_NAME_MUST_NOT_BE_BLANK`) — jamais
 *   une action valide, donc pas de `| null` ici.
 * - `registrationDate` : `null` explicite est rejeté pour un adhérent
 *   existant (`REGISTRATION_DATE_REQUIRED`) — même raison, pas de `| null`.
 * - `phoneNumber`/`memberExpirationDate`/`blockedReason` : `null` est
 *   accepté et efface la valeur — `| null` légitime.
 * - `roles` : absent et `null` produisent le même état côté backend (pas de
 *   sémantique de clear), et un ensemble vide est toujours refusé
 *   séparément — pas de `| null` ici (n'apporterait aucune action distincte).
 *
 * `undefined` (propriété absente) n'est jamais sérialisé par
 * `JSON.stringify` : aucune transformation manuelle n'est nécessaire pour
 * obtenir la sémantique "absent = ne pas modifier".
 */
export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  phoneNumber?: string | null;
  roles?: RoleCode[];
  registrationDate?: string;
  memberExpirationDate?: string | null;
  blockedReason?: string | null;
}
