import { AvailabilityStatus } from '../../catalogue/models/availability-status';
import { CopyCondition } from '../../catalogue/models/copy-condition';

export interface CopyConditionOption {
  label: string;
  value: CopyCondition;
}

export const COPY_CONDITION_OPTIONS: CopyConditionOption[] = [
  { label: 'Bon état', value: 'GOOD' },
  { label: 'Endommagé', value: 'DAMAGED' },
  { label: 'Perdu', value: 'LOST' },
  { label: 'Hors service', value: 'OUT_OF_SERVICE' },
];

export interface AvailabilityOption {
  label: string;
  value: AvailabilityStatus;
}

/**
 * Options d'écriture manuelle de `availabilityStatus` — `AVAILABLE`/
 * `UNAVAILABLE` uniquement. `ON_LOAN`/`RESERVED` ne figurent JAMAIS ici :
 * le backend les refuse comme cibles d'écriture manuelle (409
 * `COPY_AVAILABILITY_WORKFLOW_MANAGED`), ce ne sont que des valeurs de
 * lecture possibles de `CopyResponse.availabilityStatus`.
 */
export const CREATE_AVAILABILITY_OPTIONS: AvailabilityOption[] = [
  { label: 'Disponible', value: 'AVAILABLE' },
  { label: 'Indisponible', value: 'UNAVAILABLE' },
];
