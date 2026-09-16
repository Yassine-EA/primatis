import { APIRequestContext } from '@playwright/test';

import { e2eAccounts } from './e2e-accounts';

/**
 * Contrôle du Clock E2E (DEV-DEC-0070) — `GET/POST /api/v1/e2e/time*`,
 * réservé au profil Spring "e2e" et à ROLE_ADMIN. Chaque appel obtient un
 * JWT ADMIN fraîchement émis : un `advance()` significatif (jours/heures)
 * invalide tout JWT déjà émis (son `exp`, calculé au moment de l'émission
 * via le même Clock partagé, se retrouve dans le passé une fois le Clock
 * avancé) — un token capturé avant l'avance ne doit donc jamais être
 * réutilisé après. Voir `.claude/logs/DEV-14.7 — FINES E2E.md` (section
 * reprise) pour la découverte et la documentation complète de cet effet.
 */

export interface E2eTimeState {
  currentTime: string;
  offsetSeconds: number;
}

async function freshAdminToken(request: APIRequestContext): Promise<string> {
  const response = await request.post('http://localhost:8080/api/v1/auth/login', {
    data: { email: e2eAccounts.admin.email, password: e2eAccounts.admin.password },
  });
  const { token } = await response.json();
  return token;
}

export async function getE2eTime(request: APIRequestContext): Promise<E2eTimeState> {
  const token = await freshAdminToken(request);
  const response = await request.get('http://localhost:8080/api/v1/e2e/time', {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.json();
}

/** `hours` : décalage relatif (jamais un instant absolu, DEV-DEC-0070 contrainte 4). */
export async function advanceE2eTime(request: APIRequestContext, hours: number): Promise<E2eTimeState> {
  const token = await freshAdminToken(request);
  const response = await request.post('http://localhost:8080/api/v1/e2e/time/advance', {
    headers: { Authorization: `Bearer ${token}` },
    data: { hours },
  });
  return response.json();
}

export async function resetE2eTime(request: APIRequestContext): Promise<E2eTimeState> {
  const token = await freshAdminToken(request);
  const response = await request.post('http://localhost:8080/api/v1/e2e/time/reset', {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.json();
}
