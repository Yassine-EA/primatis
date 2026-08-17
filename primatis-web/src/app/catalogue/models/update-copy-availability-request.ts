import { AvailabilityStatus } from './availability-status';

/**
 * Voir `be.primatis.catalogue.dto.UpdateCopyAvailabilityRequest` côté
 * backend, sous `PATCH .../availability`. Champ nommé `status` (jamais
 * renommé en `availabilityStatus`), même convention que
 * `UpdateTitleStatusRequest`. `ON_LOAN`/`RESERVED` sont refusés par le
 * backend comme cibles d'écriture manuelle (409) — non revalidé ici, le
 * backend reste l'autorité.
 */
export interface UpdateCopyAvailabilityRequest {
  status: AvailabilityStatus;
}
