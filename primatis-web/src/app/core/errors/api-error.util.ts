import { HttpErrorResponse } from '@angular/common/http';

import { ApiErrorResponse } from '../models/api-error-response';
import { FieldError } from '../models/field-error';
import { AppError } from './api-error';

const NETWORK_ERROR_MESSAGE = 'Impossible de contacter le serveur. Veuillez réessayer.';
const TECHNICAL_ERROR_MESSAGE = 'Une erreur technique est survenue. Veuillez réessayer.';
const GENERIC_ERROR_MESSAGE = 'Une erreur est survenue. Veuillez réessayer.';

/**
 * Contrôle runtime de la forme minimale d'`ApiErrorResponse` (DEV-04.11) —
 * jamais un simple cast TypeScript, qui ne protège en rien à l'exécution.
 */
export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate['status'] === 'number' &&
    typeof candidate['code'] === 'string' &&
    typeof candidate['message'] === 'string' &&
    Array.isArray(candidate['fieldErrors']) &&
    candidate['fieldErrors'].every(isFieldError)
  );
}

function isFieldError(value: unknown): value is FieldError {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate['field'] === 'string' && typeof candidate['message'] === 'string';
}

/**
 * Normalise n'importe quelle erreur (HTTP avec/sans corps
 * `ApiErrorResponse`, `Error` JavaScript, valeur inconnue) vers
 * {@link AppError} — jamais de stack trace, nom de classe, SQL ou détail
 * technique backend exposé, uniquement des messages génériques sobres en
 * l'absence de contrat exploitable.
 */
export function toAppError(error: unknown): AppError {
  if (error instanceof HttpErrorResponse) {
    return normalizeHttpErrorResponse(error);
  }
  // Error JS ou valeur totalement inconnue : `Error.message` n'est jamais
  // affiché tel quel (imprévisible, potentiellement technique) — toujours
  // le message générique.
  return { message: GENERIC_ERROR_MESSAGE, fieldErrors: [] };
}

function normalizeHttpErrorResponse(error: HttpErrorResponse): AppError {
  if (isApiErrorResponse(error.error)) {
    const body = error.error;
    return {
      code: body.code,
      status: body.status,
      message: body.message.trim().length > 0 ? body.message : GENERIC_ERROR_MESSAGE,
      fieldErrors: body.fieldErrors,
    };
  }

  if (error.status === 0) {
    return { status: 0, message: NETWORK_ERROR_MESSAGE, fieldErrors: [] };
  }

  if (error.status >= 500) {
    return { status: error.status, message: TECHNICAL_ERROR_MESSAGE, fieldErrors: [] };
  }

  return { status: error.status, message: GENERIC_ERROR_MESSAGE, fieldErrors: [] };
}

/** Message backend associé à `field`, ou `undefined` si absent. */
export function getFieldError(error: AppError, field: string): string | undefined {
  return error.fieldErrors.find((fieldError) => fieldError.field === field)?.message;
}

export function hasFieldError(error: AppError, field: string): boolean {
  return getFieldError(error, field) !== undefined;
}
