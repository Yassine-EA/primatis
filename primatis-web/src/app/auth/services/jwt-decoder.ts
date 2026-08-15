import { JwtClaims } from '../models/jwt-claims';

/**
 * Décodage local du payload JWT (Base64URL -> JSON), sans bibliothèque
 * externe. Ce N'EST PAS une vérification de signature : uniquement une
 * lecture des claims pour l'état UX (voir `JwtClaims`).
 *
 * Retourne `null` pour tout token malformé (mauvais nombre de segments,
 * Base64URL invalide, JSON invalide, ou forme de claims inattendue) plutôt
 * que de lever une exception — l'appelant (`AuthService`) traite l'absence
 * de claims comme "aucune session".
 */
export function decodeJwtPayload(token: string): JwtClaims | null {
  const segments = token.split('.');
  if (segments.length !== 3) {
    return null;
  }

  try {
    const json = base64UrlDecode(segments[1]);
    const parsed: unknown = JSON.parse(json);
    return isJwtClaims(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

/** `true` si `claims.exp` (secondes epoch) est déjà dépassé. */
export function isClaimsExpired(claims: JwtClaims): boolean {
  return claims.exp * 1000 <= Date.now();
}

function base64UrlDecode(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
  const paddingLength = (4 - (base64.length % 4)) % 4;
  const padded = base64 + '='.repeat(paddingLength);

  const binary = atob(padded);
  const percentEncoded = Array.from(binary)
    .map((char) => '%' + char.charCodeAt(0).toString(16).padStart(2, '0'))
    .join('');

  return decodeURIComponent(percentEncoded);
}

function isJwtClaims(value: unknown): value is JwtClaims {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate['sub'] === 'string' &&
    Array.isArray(candidate['roles']) &&
    candidate['roles'].every((role) => typeof role === 'string') &&
    Array.isArray(candidate['permissions']) &&
    candidate['permissions'].every((permission) => typeof permission === 'string') &&
    typeof candidate['exp'] === 'number'
  );
}
