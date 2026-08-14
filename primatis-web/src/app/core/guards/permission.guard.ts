import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../../auth/services/auth.service';
import { PermissionCode } from '../../auth/models/permission';

/**
 * Guard UX générique (DEV-04.9), piloté par `route.data.permissions`
 * (liste de {@link PermissionCode}) :
 *
 * ```ts
 * { path: 'users', data: { permissions: ['USER_READ'] }, canActivate: [permissionGuard] }
 * ```
 *
 * Sémantique **ALL** : toutes les permissions déclarées doivent être
 * présentes dans les claims JWT courants (`AuthService.permissions()`).
 * Choix explicite (fail-closed, sans ambiguïté) — une sémantique ANY
 * pourra être introduite séparément si un vrai besoin métier apparaît.
 *
 * Ce N'EST PAS l'autorité de sécurité : seul le backend
 * (`@PreAuthorize`, DEV-03.9) décide réellement d'une autorisation. Ne
 * mélange jamais rôles et permissions : `ROLE_*` désigne un rôle backend,
 * jamais une permission — ce guard ne doit être configuré qu'avec de
 * vrais codes de permission (jamais `ROLE_ADMIN`/`ROLE_LIBRARIAN`/etc.).
 * Aucune matrice rôle → permission n'est codée ici.
 *
 * Comportement fail-closed : configuration de route absente, vide ou
 * malformée => refus (`/forbidden`), jamais un accès implicite.
 * N'appelle jamais `logout()` : une permission absente n'est pas une
 * session invalide (backend : 403, jamais 401).
 */
export const permissionGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.authenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  const requiredPermissions = route.data['permissions'];
  if (!isPermissionCodeList(requiredPermissions)) {
    return router.createUrlTree(['/forbidden']);
  }

  const grantedPermissions = authService.permissions();
  const hasAllRequiredPermissions = requiredPermissions.every((permission) =>
    grantedPermissions.includes(permission),
  );

  return hasAllRequiredPermissions ? true : router.createUrlTree(['/forbidden']);
};

function isPermissionCodeList(value: unknown): value is PermissionCode[] {
  return Array.isArray(value) && value.length > 0 && value.every((item) => typeof item === 'string');
}
