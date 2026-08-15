import { HttpErrorResponse } from '@angular/common/http';

import { ApiErrorResponse } from '../models/api-error-response';
import { AppError } from './api-error';
import { getFieldError, hasFieldError, isApiErrorResponse, toAppError } from './api-error.util';

function apiErrorResponse(overrides: Partial<ApiErrorResponse> = {}): ApiErrorResponse {
  return {
    timestamp: '2026-08-15T12:00:00Z',
    status: 400,
    error: 'Bad Request',
    code: 'VALIDATION_FAILED',
    message: 'Un ou plusieurs champs sont invalides.',
    path: '/api/v1/titles',
    fieldErrors: [],
    ...overrides,
  };
}

describe('isApiErrorResponse', () => {
  it('should recognize a valid ApiErrorResponse', () => {
    expect(isApiErrorResponse(apiErrorResponse())).toBe(true);
  });

  it('should recognize a valid ApiErrorResponse with fieldErrors', () => {
    const body = apiErrorResponse({ fieldErrors: [{ field: 'email', message: 'Adresse e-mail requise.' }] });
    expect(isApiErrorResponse(body)).toBe(true);
  });

  it('should reject null and non-object values', () => {
    expect(isApiErrorResponse(null)).toBe(false);
    expect(isApiErrorResponse(undefined)).toBe(false);
    expect(isApiErrorResponse('error')).toBe(false);
    expect(isApiErrorResponse(42)).toBe(false);
  });

  it('should reject an object missing required fields', () => {
    expect(isApiErrorResponse({ status: 400 })).toBe(false);
    expect(isApiErrorResponse({ code: 'X', message: 'Y' })).toBe(false);
  });

  it('should reject an object with wrong field types', () => {
    expect(isApiErrorResponse(apiErrorResponse({ status: '400' as unknown as number }))).toBe(false);
    expect(isApiErrorResponse(apiErrorResponse({ code: 42 as unknown as string }))).toBe(false);
    expect(isApiErrorResponse(apiErrorResponse({ fieldErrors: 'none' as unknown as [] }))).toBe(false);
  });

  it('should reject fieldErrors entries that are not well-formed FieldError objects', () => {
    const malformed = apiErrorResponse({ fieldErrors: [{ field: 'email' } as never] });
    expect(isApiErrorResponse(malformed)).toBe(false);
  });
});

describe('toAppError', () => {
  it('should preserve code/status/message/fieldErrors for a valid ApiErrorResponse', () => {
    const body = apiErrorResponse({
      code: 'INVALID_CREDENTIALS',
      status: 401,
      message: 'Identifiants invalides.',
      fieldErrors: [{ field: 'email', message: 'Requis.' }],
    });
    const httpError = new HttpErrorResponse({ error: body, status: 401, statusText: 'Unauthorized' });

    const result = toAppError(httpError);

    expect(result).toEqual<AppError>({
      code: 'INVALID_CREDENTIALS',
      status: 401,
      message: 'Identifiants invalides.',
      fieldErrors: [{ field: 'email', message: 'Requis.' }],
    });
  });

  it('should fall back to a generic message when the backend message is empty', () => {
    const body = apiErrorResponse({ message: '   ' });
    const httpError = new HttpErrorResponse({ error: body, status: 400, statusText: 'Bad Request' });

    expect(toAppError(httpError).message).toBe('Une erreur est survenue. Veuillez réessayer.');
  });

  it('should return a generic network message for status 0', () => {
    const httpError = new HttpErrorResponse({ status: 0 });

    const result = toAppError(httpError);

    expect(result.message).toBe('Impossible de contacter le serveur. Veuillez réessayer.');
    expect(result.status).toBe(0);
    expect(result.code).toBeUndefined();
  });

  it('should return a generic technical message for a 5xx response without an exploitable body', () => {
    const httpError = new HttpErrorResponse({
      status: 500,
      statusText: 'Internal Server Error',
      error: '<html>Internal Server Error</html>',
    });

    const result = toAppError(httpError);

    expect(result.message).toBe('Une erreur technique est survenue. Veuillez réessayer.');
    expect(result.status).toBe(500);
  });

  it('should return a sober generic message for another HTTP error without an exploitable body', () => {
    const httpError = new HttpErrorResponse({ status: 404, statusText: 'Not Found', error: null });

    const result = toAppError(httpError);

    expect(result.message).toBe('Une erreur est survenue. Veuillez réessayer.');
    expect(result.status).toBe(404);
  });

  it('should return a safe generic fallback for a plain JavaScript Error', () => {
    const result = toAppError(new Error('Cannot read property "x" of undefined at Foo.bar (main.js:42)'));

    expect(result.message).toBe('Une erreur est survenue. Veuillez réessayer.');
    expect(result.code).toBeUndefined();
    expect(result.status).toBeUndefined();
  });

  it('should return a safe generic fallback for a completely unknown value', () => {
    expect(toAppError(undefined).message).toBe('Une erreur est survenue. Veuillez réessayer.');
    expect(toAppError('some string').message).toBe('Une erreur est survenue. Veuillez réessayer.');
    expect(toAppError({ random: 'shape' }).message).toBe('Une erreur est survenue. Veuillez réessayer.');
  });

  it('should never leak a stack trace or technical exception details', () => {
    const jsError = new Error('boom');
    const httpError = new HttpErrorResponse({
      status: 500,
      error: {
        exception: 'org.springframework.dao.DataIntegrityViolationException',
        sqlState: '23505',
        trace: 'at be.primatis.SomeClass.method(SomeClass.java:42)',
      },
    });

    expect(toAppError(jsError).message).not.toContain('at Foo.bar');
    const httpResult = toAppError(httpError);
    expect(httpResult.message).not.toContain('org.springframework');
    expect(httpResult.message).not.toContain('SomeClass.java');
    expect(JSON.stringify(httpResult)).not.toContain('DataIntegrityViolationException');
  });
});

describe('getFieldError / hasFieldError', () => {
  const error: AppError = {
    message: 'Un ou plusieurs champs sont invalides.',
    fieldErrors: [
      { field: 'email', message: 'Adresse e-mail requise.' },
      { field: 'password', message: 'Mot de passe requis.' },
    ],
  };

  it('should return the backend message associated with a known field', () => {
    expect(getFieldError(error, 'email')).toBe('Adresse e-mail requise.');
  });

  it('should return undefined for a field without an error', () => {
    expect(getFieldError(error, 'phoneNumber')).toBeUndefined();
  });

  it('should report presence/absence of a field error consistently with getFieldError', () => {
    expect(hasFieldError(error, 'password')).toBe(true);
    expect(hasFieldError(error, 'phoneNumber')).toBe(false);
  });
});
