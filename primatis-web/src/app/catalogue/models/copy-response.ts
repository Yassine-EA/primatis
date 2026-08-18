import { AvailabilityStatus } from './availability-status';
import { CopyCondition } from './copy-condition';

/**
 * Voir `be.primatis.catalogue.dto.CopyResponse` côté backend. `titleId`
 * seul (jamais un `Title`/`TitleResponse` imbriqué) — pas de graphe DTO
 * récursif.
 */
export interface CopyResponse {
  id: number;
  titleId: number;
  inventoryCode: string;
  location: string | null;
  copyCondition: CopyCondition;
  availabilityStatus: AvailabilityStatus;
}
