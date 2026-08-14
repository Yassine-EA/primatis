/**
 * Codes de permission backend stables (PRIMATIS_CONTEXT_DEV_v1.0 §6.3,
 * DEV-03.9/03.11) — purement un type UX pour éviter les fautes de frappe
 * lors de la configuration de `route.data.permissions` (DEV-04.9).
 *
 * N'est jamais une matrice rôle → permission frontend : aucune logique
 * n'associe un rôle à ces codes ici. Le backend (`@PreAuthorize`) reste
 * l'unique autorité qui décide réellement une autorisation.
 */
export type PermissionCode =
  | 'CATALOGUE_READ'
  | 'CATALOGUE_MANAGE'
  | 'COPY_READ'
  | 'COPY_MANAGE'
  | 'LOAN_READ'
  | 'LOAN_MANAGE'
  | 'RESERVATION_READ'
  | 'RESERVATION_MANAGE'
  | 'FINE_READ'
  | 'FINE_MANAGE'
  | 'ARTICLE_READ'
  | 'ARTICLE_MANAGE'
  | 'ARTICLE_PUBLISH'
  | 'USER_READ'
  | 'USER_MANAGE'
  | 'ROLE_READ'
  | 'ROLE_MANAGE'
  | 'SETTING_READ'
  | 'SETTING_MANAGE';
