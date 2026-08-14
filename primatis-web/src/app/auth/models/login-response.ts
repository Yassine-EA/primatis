/**
 * Contrat REST retourné par `POST /api/v1/auth/login` (voir
 * `be.primatis.security.web.LoginResponse` côté backend).
 *
 * `expiresAt` reste une chaîne ISO-8601 telle que reçue : elle n'est
 * convertie que si une interaction/affichage l'exige réellement.
 */
export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  expiresInSeconds: number;
}
