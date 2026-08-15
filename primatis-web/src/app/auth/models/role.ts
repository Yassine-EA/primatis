/**
 * Codes de rôle backend stables (PRIMATIS_CONTEXT_DEV_v1.0, DEV-03.9/03.11)
 * — purement un type UX pour la visibilité de navigation (DEV-04.10).
 *
 * Aucune relation rôle -> permission n'est codée ici ni ailleurs côté
 * frontend : le backend (`@PreAuthorize`) reste l'unique autorité.
 */
export type RoleCode = 'ROLE_MEMBER' | 'ROLE_LIBRARIAN' | 'ROLE_ADMIN';
