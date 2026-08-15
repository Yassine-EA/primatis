import { JwtClaims } from '../models/jwt-claims';
import { decodeJwtPayload, isClaimsExpired } from './jwt-decoder';

function base64UrlEncode(value: string): string {
  const base64 = btoa(value);
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function buildJwt(payload: unknown, header: unknown = { alg: 'RS256', typ: 'JWT' }): string {
  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedPayload = base64UrlEncode(JSON.stringify(payload));
  return `${encodedHeader}.${encodedPayload}.signature-not-verified-by-frontend`;
}

describe('decodeJwtPayload', () => {
  const validClaims: JwtClaims = {
    sub: '42',
    roles: ['ROLE_LIBRARIAN'],
    permissions: ['LOAN_MANAGE', 'CATALOGUE_READ'],
    exp: Math.floor(Date.now() / 1000) + 3600,
    iss: 'primatis-api',
    aud: 'primatis-api',
    iat: Math.floor(Date.now() / 1000),
  };

  it('should decode a well-formed token into its claims', () => {
    const token = buildJwt(validClaims);

    expect(decodeJwtPayload(token)).toEqual(validClaims);
  });

  it('should return null for a token without three segments', () => {
    expect(decodeJwtPayload('only-one-segment')).toBeNull();
    expect(decodeJwtPayload('two.segments')).toBeNull();
  });

  it('should return null for a token whose payload is not valid Base64URL', () => {
    expect(decodeJwtPayload('header.not-valid-base64!!!.signature')).toBeNull();
  });

  it('should return null for a token whose payload is not valid JSON', () => {
    const encodedHeader = base64UrlEncode(JSON.stringify({ alg: 'RS256' }));
    const encodedPayload = base64UrlEncode('not-json');
    expect(decodeJwtPayload(`${encodedHeader}.${encodedPayload}.sig`)).toBeNull();
  });

  it('should return null when required claims are missing', () => {
    const token = buildJwt({ sub: '42' });

    expect(decodeJwtPayload(token)).toBeNull();
  });

  it('should return null when roles/permissions are not arrays of strings', () => {
    const token = buildJwt({ sub: '42', roles: 'ROLE_MEMBER', permissions: [], exp: 9999999999 });

    expect(decodeJwtPayload(token)).toBeNull();
  });
});

describe('isClaimsExpired', () => {
  it('should return false for a claim expiring in the future', () => {
    const claims: JwtClaims = {
      sub: '1',
      roles: [],
      permissions: [],
      exp: Math.floor(Date.now() / 1000) + 60,
    };

    expect(isClaimsExpired(claims)).toBe(false);
  });

  it('should return true for a claim already expired', () => {
    const claims: JwtClaims = {
      sub: '1',
      roles: [],
      permissions: [],
      exp: Math.floor(Date.now() / 1000) - 60,
    };

    expect(isClaimsExpired(claims)).toBe(true);
  });
});
