import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../../auth/services/auth.service';
import { RoleCode } from '../../auth/models/role';

/**
 * Guard UX générique (DEV-05.13), piloté par `route.data.roles` (liste de
 * {@link RoleCode}) :
 *
 * ```ts
 * { path: 'member', data: { roles: ['ROLE_MEMBER'] }, canActivate: [roleGuard] }
 * ```
 *
 * Sémantique **ANY** : un seul des rôles déclarés doit être présent dans les
 * claims JWT courants (`AuthService.roles()`) — cohérent avec
 * `isNavigationItemVisible`/`NavigationItem.requiredRoles` (DEV-DEC-0009),
 * pas ALL comme `permissionGuard` (dédié aux permissions, jamais aux rôles).
 *
 * Ce N'EST PAS l'autorité de sécurité : seul le backend décide réellement
 * d'une autorisation. `/api/v1/me/**` reste accessible à tout `AppUser`
 * authentifié (ownership structurelle, DEV-05.8/05.9) — ce guard n'introduit
 * aucune permission `*_SELF` ni aucune règle métier backend, il restreint
 * uniquement la navigation/le routing frontend d'une zone.
 *
 * Comportement fail-closed : configuration de route absente, vide ou
 * malformée => refus (`/forbidden`), jamais un accès implicite.
 * N'appelle jamais `logout()` : un rôle absent n'est pas une session
 * invalide (backend : 403, jamais 401).
 */
export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.authenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  const requiredRoles = route.data['roles'];
  if (!isRoleCodeList(requiredRoles)) {
    return router.createUrlTree(['/forbidden']);
  }

  const grantedRoles = authService.roles();
  const hasAnyRequiredRole = requiredRoles.some((role) => grantedRoles.includes(role));

  return hasAnyRequiredRole ? true : router.createUrlTree(['/forbidden']);
};

function isRoleCodeList(value: unknown): value is RoleCode[] {
  return Array.isArray(value) && value.length > 0 && value.every((item) => typeof item === 'string');
}
