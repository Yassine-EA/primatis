import { PermissionCode } from '../../auth/models/permission';
import { RoleCode } from '../../auth/models/role';

export interface NavigationItem {
  readonly label: string;
  readonly routerLink: string;
  readonly requiredPermissions?: readonly PermissionCode[];
  readonly requiredRoles?: readonly RoleCode[];
}

/**
 * Détermine si un {@link NavigationItem} doit être visible (DEV-04.10) —
 * mécanisme générique, testé indépendamment de tout élément métier réel.
 *
 * Sémantique :
 * - `requiredPermissions` : **ALL** — plusieurs permissions sur une action
 *   sont toutes nécessaires par défaut (cohérent avec `PermissionGuard`,
 *   DEV-04.9).
 * - `requiredRoles` : **ANY** — pour une zone d'application, l'un des
 *   rôles explicitement listés suffit.
 * - aucune contrainte déclarée : toujours visible, y compris pour un
 *   visiteur anonyme.
 *
 * `grantedRoles`/`grantedPermissions` proviennent uniquement des claims
 * JWT déjà exposés par `AuthService` — aucune matrice rôle -> permission
 * n'est construite ici ni ailleurs.
 */
export function isNavigationItemVisible(
  item: NavigationItem,
  authenticated: boolean,
  grantedRoles: readonly string[],
  grantedPermissions: readonly string[],
): boolean {
  const hasPermissionConstraint = (item.requiredPermissions?.length ?? 0) > 0;
  const hasRoleConstraint = (item.requiredRoles?.length ?? 0) > 0;

  if (!hasPermissionConstraint && !hasRoleConstraint) {
    return true;
  }
  if (!authenticated) {
    return false;
  }
  if (
    hasPermissionConstraint &&
    !item.requiredPermissions!.every((permission) => grantedPermissions.includes(permission))
  ) {
    return false;
  }
  if (hasRoleConstraint && !item.requiredRoles!.some((role) => grantedRoles.includes(role))) {
    return false;
  }
  return true;
}
