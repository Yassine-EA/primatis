import { CopyCondition } from './copy-condition';

/**
 * Voir `be.primatis.catalogue.dto.UpdateCopyRequest` côté backend. Même
 * mécanisme de PATCH sparse à trois états que `UpdateTitleRequest`.
 *
 * Volontairement absents : `availabilityStatus` (action dédiée
 * `UpdateCopyAvailabilityRequest`, `PATCH .../availability`) et `titleId`
 * (immuable — un Copy ne change pas de Title).
 *
 * - `inventoryCode` : `null`/vide explicite est rejeté par le backend
 *   (colonne `NOT NULL`/`UNIQUE`) — pas de `| null` ici.
 * - `location` : `null` est accepté et efface la valeur — `| null`
 *   légitime.
 * - `copyCondition` : `null` explicite est rejeté par le backend (colonne
 *   `NOT NULL`) — PAS de `| null` ici (à ne pas confondre avec le type
 *   suggéré `CopyCondition | null` : le backend refuse `copyCondition`
 *   présent+`null` avec `COPY_CONDITION_MUST_NOT_BE_NULL`, vérifié dans
 *   `UpdateCopyRequest.java`). Si la nouvelle valeur est `LOST` ou
 *   `OUT_OF_SERVICE`, le backend impose `UNAVAILABLE` dans la même
 *   transaction — non dupliqué ici.
 */
export interface UpdateCopyRequest {
  inventoryCode?: string;
  location?: string | null;
  copyCondition?: CopyCondition;
}
