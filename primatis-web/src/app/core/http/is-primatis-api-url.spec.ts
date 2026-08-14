import { isPrimatisApiUrl } from './is-primatis-api-url';

describe('isPrimatisApiUrl', () => {
  const apiBaseUrl = '/api/v1';

  it('should match a same-origin relative request under the API base path', () => {
    expect(isPrimatisApiUrl('/api/v1/titles', apiBaseUrl)).toBe(true);
  });

  it('should match the API base path exactly, with no trailing segment', () => {
    expect(isPrimatisApiUrl('/api/v1', apiBaseUrl)).toBe(true);
  });

  it('should reject a path that only shares a string prefix with the base', () => {
    expect(isPrimatisApiUrl('/api/v1xyz/evil', apiBaseUrl)).toBe(false);
  });

  it('should reject a sibling API version path', () => {
    expect(isPrimatisApiUrl('/api/v2/titles', apiBaseUrl)).toBe(false);
  });

  it('should reject an absolute URL pointing at an external origin', () => {
    expect(isPrimatisApiUrl('https://attacker.example/api/v1/titles', apiBaseUrl)).toBe(false);
  });

  it('should match an absolute URL that resolves to the same origin and path', () => {
    expect(isPrimatisApiUrl(`${location.origin}/api/v1/titles`, apiBaseUrl)).toBe(true);
  });

  it('should reject an unrelated same-origin path', () => {
    expect(isPrimatisApiUrl('/assets/logo.png', apiBaseUrl)).toBe(false);
  });
});
