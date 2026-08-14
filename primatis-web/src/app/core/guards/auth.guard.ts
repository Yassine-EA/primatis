import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../../auth/services/auth.service';

/**
 * Guard UX (DEV-04.9) protégeant les zones structurantes (`/member`,
 * `/staff`, `/admin`) contre un visiteur anonyme.
 *
 * Ce N'EST PAS l'autorité de sécurité : le backend Spring Security reste
 * seul responsable de toute décision d'autorisation réelle (401/403,
 * DEV-03.10). N'appelle jamais le backend, ne manipule jamais
 * `sessionStorage` directement et n'appelle jamais `logout()` — `AuthService`
 * revérifie déjà l'expiration du JWT à chaque lecture de `authenticated()`
 * (DEV-04.7), ce guard n'a donc rien à nettoyer lui-même.
 *
 * Retourne un `UrlTree` (jamais de navigation impérative) vers `/login`,
 * en préservant l'URL demandée dans `returnUrl` — toujours une URL
 * Angular interne (`state.url` du Router, jamais une chaîne externe),
 * donc sans risque d'open redirect.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.authenticated()) {
    return true;
  }

  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
