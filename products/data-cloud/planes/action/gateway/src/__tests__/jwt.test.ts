import { describe, it, expect } from 'vitest';
import { createHmac } from 'node:crypto';
import { verifyJwt, extractBearerToken } from '../jwt.js';

const TEST_SECRET = 'test-secret-key-for-unit-tests';

/** Build a valid HS256 JWT for testing. */
function makeJwt(payload: Record<string, unknown>, secret = TEST_SECRET): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${sig}`;
}

describe('extractBearerToken', () => {
  it('extracts token from valid Bearer header', () => {
    expect(extractBearerToken('Bearer abc123')).toBe('abc123');
  });

  it('returns null for undefined header', () => {
    expect(extractBearerToken(undefined)).toBeNull();
  });

  it('returns null for non-Bearer scheme', () => {
    expect(extractBearerToken('Basic abc123')).toBeNull();
  });

  it('returns null for empty token after Bearer', () => {
    expect(extractBearerToken('Bearer    ')).toBeNull();
  });
});

describe('verifyJwt', () => {
  it('verifies a valid HS256 JWT', () => {
    const now = Math.floor(Date.now() / 1000);
    const token = makeJwt({ sub: 'user-1', exp: now + 3600, iat: now });
    const payload = verifyJwt(token, TEST_SECRET);
    expect(payload.sub).toBe('user-1');
  });

  it('rejects a JWT with invalid structure', () => {
    expect(() => verifyJwt('not.a.jwt.at.all', TEST_SECRET)).toThrow('Invalid JWT');
  });

  it('rejects a JWT with wrong signature', () => {
    const token = makeJwt({ sub: 'user-1' }, 'correct-secret');
    expect(() => verifyJwt(token, 'wrong-secret')).toThrow('Invalid JWT signature');
  });

  it('rejects an expired JWT', () => {
    const expired = Math.floor(Date.now() / 1000) - 100;
    const token = makeJwt({ sub: 'user-1', exp: expired });
    expect(() => verifyJwt(token, TEST_SECRET)).toThrow('JWT has expired');
  });

  it('rejects a JWT with unsupported algorithm', () => {
    const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
    const body = Buffer.from(JSON.stringify({ sub: 'user-1' })).toString('base64url');
    const sig = createHmac('sha256', TEST_SECRET).update(`${header}.${body}`).digest('base64url');
    expect(() => verifyJwt(`${header}.${body}.${sig}`, TEST_SECRET)).toThrow('Unsupported JWT algorithm');
  });

  it('accepts a JWT without exp claim', () => {
    const token = makeJwt({ sub: 'user-1' });
    const payload = verifyJwt(token, TEST_SECRET);
    expect(payload.sub).toBe('user-1');
    expect(payload.exp).toBeUndefined();
  });
});
