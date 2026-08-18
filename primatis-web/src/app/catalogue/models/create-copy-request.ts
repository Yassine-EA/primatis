import { AvailabilityStatus } from './availability-status';
import { CopyCondition } from './copy-condition';

/**
 * Voir `be.primatis.catalogue.dto.CreateCopyRequest` côté backend, sous
 * `POST /api/v1/staff/titles/{titleId}/copies`. `titleId` vient
 * exclusivement du path — volontairement absent du body. Aucun défaut
 * métier côté frontend : `copyCondition`/`availabilityStatus` sont tous
 * deux obligatoires, le backend reste l'autorité de leur combinaison
 * (LOST/OUT_OF_SERVICE → UNAVAILABLE, ON_LOAN/RESERVED refusés à la
 * création).
 */
export interface CreateCopyRequest {
  inventoryCode: string;
  location?: string;
  copyCondition: CopyCondition;
  availabilityStatus: AvailabilityStatus;
}
