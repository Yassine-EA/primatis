import { FieldError } from '../models/field-error';

/**
 * Modèle d'erreur frontend normalisé (DEV-04.11).
 *
 * Ne remplace jamais `ApiErrorResponse` (contrat backend, DEV-04.6) : le
 * complète pour offrir un modèle UI uniforme quelle que soit la source
 * réelle de l'erreur (HTTP avec corps `ApiErrorResponse` conforme, HTTP
 * sans corps exploitable, erreur réseau, `Error` JavaScript, valeur
 * inconnue). `code`/`status` restent absents lorsque l'origine ne permet
 * pas de les connaître avec certitude — jamais devinés.
 */
export interface AppError {
  readonly code?: string;
  readonly message: string;
  readonly status?: number;
  readonly fieldErrors: readonly FieldError[];
}
