/**
 * Détermine si une URL de requête cible réellement l'API PRIMATIS
 * (`API_BASE_URL`).
 *
 * Résout les deux URLs en absolu contre l'origine courante avant de les
 * comparer — jamais une comparaison naïve de préfixe de chaîne, qui
 * pourrait être trompée par :
 * - une URL externe absolue (`https://attacker.example/api/v1/...`) ;
 * - un chevauchement de préfixe sans rapport
 *   (`/api/v1xyz/evil` face à `/api/v1`).
 */
export function isPrimatisApiUrl(requestUrl: string, apiBaseUrl: string): boolean {
  const base = new URL(apiBaseUrl, location.origin);
  const target = new URL(requestUrl, location.origin);

  if (target.origin !== base.origin) {
    return false;
  }

  const basePath = base.pathname.replace(/\/+$/, '');
  const targetPath = target.pathname;

  return targetPath === basePath || targetPath.startsWith(`${basePath}/`);
}
