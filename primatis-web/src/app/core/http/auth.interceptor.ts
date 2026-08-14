import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthService } from '../../auth/services/auth.service';
import { API_BASE_URL } from '../api/api-base-url.token';
import { ApiErrorResponse } from '../models/api-error-response';
import { isPrimatisApiUrl } from './is-primatis-api-url';

/**
 * Codes 401 signifiant réellement "session invalide côté backend"
 * (`PrimatisAuthenticationEntryPoint`/`PrimatisInvalidTokenAuthenticationEntryPoint`,
 * DEV-03.10) — eux seuls invalident la session locale. `INVALID_CREDENTIALS`
 * et `ACCOUNT_TEMPORARILY_LOCKED` sont des échecs du workflow de login
 * (DEV-03.6) : ils n'ont aucun rapport avec un JWT et ne doivent jamais
 * provoquer de déconnexion locale.
 */
const SESSION_INVALIDATING_CODES = new Set(['AUTHENTICATION_REQUIRED', 'INVALID_TOKEN']);

/**
 * Intercepteur JWT PRIMATIS (DEV-04.8).
 *
 * - Ajoute `Authorization: Bearer <token>` uniquement aux requêtes
 *   destinées à l'API PRIMATIS ({@link isPrimatisApiUrl}), et uniquement
 *   si un token non expiré est disponible — jamais à une URL externe,
 *   jamais un token expiré.
 * - Si l'expiration est détectée localement avant même l'envoi (aucun
 *   aller-retour serveur nécessaire), nettoie immédiatement la session
 *   locale via {@link AuthService.logout}.
 * - Sur un 401 dont le `code` `ApiErrorResponse` signale une session
 *   réellement invalide, invalide la session locale puis repropage
 *   l'erreur telle quelle — jamais transformée en succès, jamais masquée.
 * - Un 401 `INVALID_CREDENTIALS`/`ACCOUNT_TEMPORARILY_LOCKED` (workflow de
 *   login) ne déclenche jamais cette déconnexion.
 * - Un 403 ne touche jamais à la session (ni token, ni `sessionStorage`) :
 *   repropagé tel quel, traitement UX reporté à une étape future.
 *
 * Volontairement sans navigation : aucun `Router` injecté ici. Rediriger
 * vers `/login` à partir d'un état non authentifié appartient aux guards
 * (DEV-04.9), qui peuvent observer réactivement
 * `AuthService.authenticated()` sans dupliquer cette logique ici — évite
 * un intercepteur qui mélangerait transport HTTP et navigation applicative.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const apiBaseUrl = inject(API_BASE_URL);

  const targetsPrimatisApi = isPrimatisApiUrl(req.url, apiBaseUrl);

  if (targetsPrimatisApi && authService.token() !== null && authService.isSessionExpired()) {
    authService.logout();
  }

  const token = authService.token();
  const authorizedReq =
    targetsPrimatisApi && token !== null
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      if (targetsPrimatisApi && error instanceof HttpErrorResponse && error.status === 401) {
        invalidateSessionIfBackendSaysSo(authService, error);
      }
      return throwError(() => error);
    }),
  );
};

function invalidateSessionIfBackendSaysSo(authService: AuthService, error: HttpErrorResponse): void {
  const body = error.error as ApiErrorResponse | null;
  const code = body?.code;
  if (code && SESSION_INVALIDATING_CODES.has(code)) {
    authService.logout();
  }
}
