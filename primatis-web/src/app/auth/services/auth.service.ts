import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';

import { JwtClaims } from '../models/jwt-claims';
import { AuthApiService } from './auth-api.service';
import { decodeJwtPayload, isClaimsExpired } from './jwt-decoder';

const STORAGE_KEY = 'primatis.accessToken';

/**
 * État d'authentification frontend (DEV-04.7).
 *
 * Le backend Spring Security reste l'unique autorité de sécurité : ce
 * service ne fait que refléter, pour l'UX, le contenu d'un JWT déjà émis et
 * validé côté serveur — il ne valide aucune signature. `roles`/`permissions`
 * ne doivent jamais être utilisés seuls comme mécanisme de sécurité réel
 * (voir futurs guards DEV-04.9, purement UX eux aussi).
 *
 * Une seule clé `sessionStorage` centralisée (jamais `localStorage`) ;
 * seul le token brut y est stocké, jamais le `LoginResponse` complet ni le
 * mot de passe. Aucun intercepteur HTTP, aucun guard, aucun refresh
 * automatique ici : hors périmètre DEV-04.7 (DEV-04.8/04.9).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authApiService = inject(AuthApiService);

  private readonly tokenSignal = signal<string | null>(null);
  private readonly claimsSignal = signal<JwtClaims | null>(null);

  readonly token = this.tokenSignal.asReadonly();

  /**
   * `authenticated`/`userId`/`roles`/`permissions` sont volontairement de
   * simples fonctions, pas des `computed()` : un `computed()` met en cache
   * son résultat et ne se réévalue que si un Signal dont il dépend change —
   * or l'expiration d'un JWT dépend du temps qui passe, pas d'un Signal.
   * Sans timer (hors périmètre), la seule façon fiable de ne jamais exposer
   * un état "authentifié" périmé est de revérifier `exp` à CHAQUE lecture,
   * via {@link effectiveClaims}. Ces méthodes restent lisibles comme des
   * Signals côté appelant (`authService.authenticated()`), et lisent bien
   * `claimsSignal` en interne : un `effect()`/`computed()` englobant qui les
   * appelle reste correctement notifié des changements de login/logout.
   */
  readonly authenticated = (): boolean => this.effectiveClaims() !== null;
  readonly userId = (): string | null => this.effectiveClaims()?.sub ?? null;
  readonly roles = (): string[] => this.effectiveClaims()?.roles ?? [];
  readonly permissions = (): string[] => this.effectiveClaims()?.permissions ?? [];

  constructor() {
    this.restoreSession();
  }

  /**
   * Authentifie via le backend puis, uniquement en cas de succès, construit
   * l'état local à partir du JWT reçu (jamais avant, jamais depuis un
   * mot de passe conservé localement).
   */
  login(email: string, password: string): Observable<void> {
    return this.authApiService.login({ email, password }).pipe(
      tap((response) => this.applySession(response.token)),
      map(() => undefined),
    );
  }

  logout(): void {
    this.clearSession();
  }

  hasRole(role: string): boolean {
    return this.roles().includes(role);
  }

  hasPermission(permission: string): boolean {
    return this.permissions().includes(permission);
  }

  /**
   * Vérification fraîche de l'expiration (indépendante des Signals, aucun
   * timer) — disponible pour de futurs consommateurs (intercepteur/guards
   * DEV-04.8/04.9) sans dupliquer le décodage.
   */
  isSessionExpired(): boolean {
    return this.effectiveClaims() === null;
  }

  /**
   * Claims du token stocké, uniquement si celui-ci n'est pas expiré à
   * l'instant de l'appel — jamais mis en cache au-delà de la portée de
   * cette lecture.
   */
  private effectiveClaims(): JwtClaims | null {
    const claims = this.claimsSignal();
    if (claims === null || isClaimsExpired(claims)) {
      return null;
    }
    return claims;
  }

  private restoreSession(): void {
    const storedToken = sessionStorage.getItem(STORAGE_KEY);
    if (!storedToken) {
      return;
    }
    if (!this.activateToken(storedToken)) {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  }

  private applySession(token: string): void {
    if (this.activateToken(token)) {
      sessionStorage.setItem(STORAGE_KEY, token);
    }
  }

  private clearSession(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.tokenSignal.set(null);
    this.claimsSignal.set(null);
  }

  /**
   * Décode et valide (forme + expiration) avant de faire vivre le moindre
   * état — un token malformé ou déjà expiré ne doit jamais être conservé,
   * ni en Signals, ni en sessionStorage.
   */
  private activateToken(token: string): boolean {
    const claims = decodeJwtPayload(token);
    if (!claims || isClaimsExpired(claims)) {
      return false;
    }
    this.tokenSignal.set(token);
    this.claimsSignal.set(claims);
    return true;
  }
}
