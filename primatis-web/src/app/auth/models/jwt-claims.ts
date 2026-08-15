/**
 * Claims JWT PRIMATIS lues côté frontend pour l'état UX uniquement.
 *
 * Le décodage local N'EST PAS une validation cryptographique : le backend
 * Spring Security (signature RS256, issuer, audience, expiration) reste
 * l'unique autorité de sécurité. `roles`/`permissions` ne servent ici qu'à
 * adapter l'affichage, jamais à décider seuls d'une autorisation réelle.
 */
export interface JwtClaims {
  sub: string;
  roles: string[];
  permissions: string[];
  exp: number;
  iss?: string;
  aud?: string | string[];
  iat?: number;
}
