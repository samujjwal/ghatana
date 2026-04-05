import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  SsoClient,
  decodeJwtPayload,
  isPlatformToken,
  tokenTtlSeconds,
} from '../index';
import type { SsoClientConfig, PlatformTokenClaims } from '../index';

/**
 * OAuth flow tests — validates JWT decoding, platform token detection,
 * TTL calculation, and SsoClient authentication state management.
 *
 * @doc.type module
 * @doc.purpose Tests for SSO OAuth flow and JWT token lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */

// ── JWT test helpers ─────────────────────────────────────────────────────────

function buildTestJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).replace(/=/g, '');
  const body = btoa(JSON.stringify(payload))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  return `${header}.${body}.fake-sig`;
}

function makeValidToken(overrides: Record<string, unknown> = {}): string {
  return buildTestJwt({
    sub: 'user-123',
    email: 'alice@example.com',
    roles: ['ROLE_USER'],
    tenantId: 'tenant-a',
    tokenType: 'PLATFORM',
    iss: 'ghatana-auth-service',
    sessionId: 'session-xyz',
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 900,
    ...overrides,
  });
}

function makeExpiredToken(): string {
  return makeValidToken({ exp: Math.floor(Date.now() / 1000) - 60 });
}

const BASE_CONFIG: SsoClientConfig = {
  authServiceBaseUrl: 'https://auth.ghatana.io',
  authGatewayBaseUrl: 'https://gateway.ghatana.io',
  productId: 'tutorputor',
};

// ── decodeJwtPayload ──────────────────────────────────────────────────────────

describe('decodeJwtPayload', () => {
  describe('valid JWT', () => {
    it('decodes sub claim', () => {
      const claims = decodeJwtPayload(makeValidToken());
      expect(claims?.sub).toBe('user-123');
    });

    it('decodes email claim', () => {
      const claims = decodeJwtPayload(makeValidToken());
      expect(claims?.email).toBe('alice@example.com');
    });

    it('decodes roles array', () => {
      const claims = decodeJwtPayload(makeValidToken());
      expect(claims?.roles).toContain('ROLE_USER');
    });

    it('decodes tenantId', () => {
      const claims = decodeJwtPayload(makeValidToken());
      expect(claims?.tenantId).toBe('tenant-a');
    });

    it('decodes tokenType', () => {
      const claims = decodeJwtPayload(makeValidToken());
      expect(claims?.tokenType).toBe('PLATFORM');
    });
  });

  describe('malformed JWT', () => {
    it('returns null for a non-JWT string', () => {
      expect(decodeJwtPayload('not-a-token')).toBeNull();
    });

    it('returns null for an empty string', () => {
      expect(decodeJwtPayload('')).toBeNull();
    });

    it('returns null for a two-segment string', () => {
      expect(decodeJwtPayload('header.payload')).toBeNull();
    });
  });
});

// ── isPlatformToken ───────────────────────────────────────────────────────────

describe('isPlatformToken', () => {
  it('returns true for a token with tokenType=PLATFORM', () => {
    expect(isPlatformToken(makeValidToken())).toBe(true);
  });

  it('returns false for a token with a different tokenType', () => {
    const token = makeValidToken({ tokenType: 'PRODUCT' });
    expect(isPlatformToken(token)).toBe(false);
  });

  it('returns false for a malformed token', () => {
    expect(isPlatformToken('garbage')).toBe(false);
  });
});

// ── tokenTtlSeconds ───────────────────────────────────────────────────────────

describe('tokenTtlSeconds', () => {
  it('returns a positive TTL for a valid token expiring in the future', () => {
    const token = makeValidToken({ exp: Math.floor(Date.now() / 1000) + 900 });
    expect(tokenTtlSeconds(token)).toBeGreaterThan(0);
  });

  it('returns 0 for an already-expired token', () => {
    expect(tokenTtlSeconds(makeExpiredToken())).toBe(0);
  });

  it('returns 0 for a malformed token', () => {
    expect(tokenTtlSeconds('not-a-token')).toBe(0);
  });

  it('TTL is approximately 900 seconds for a token issued 0 seconds ago with exp+900', () => {
    const token = makeValidToken({ exp: Math.floor(Date.now() / 1000) + 900 });
    const ttl = tokenTtlSeconds(token);
    expect(ttl).toBeGreaterThanOrEqual(898); // allow ≤2s clock skew
    expect(ttl).toBeLessThanOrEqual(900);
  });
});

// ── SsoClient authentication state ───────────────────────────────────────────

describe('SsoClient authentication state', () => {
  beforeEach(() => {
    // Ensure sessionStorage is clean for each test
    sessionStorage.clear();
  });

  describe('before init()', () => {
    it('isAuthenticated() returns false initially', () => {
      const sso = new SsoClient(BASE_CONFIG);
      expect(sso.isAuthenticated()).toBe(false);
    });

    it('getUser() returns null initially', () => {
      const sso = new SsoClient(BASE_CONFIG);
      expect(sso.getUser()).toBeNull();
    });

    it('getClaims() returns null initially', () => {
      const sso = new SsoClient(BASE_CONFIG);
      expect(sso.getClaims()).toBeNull();
    });
  });

  describe('after init() with a token in sessionStorage', () => {
    it('isAuthenticated() returns true when valid token is in storage', async () => {
      const token = makeValidToken();
      sessionStorage.setItem('ghatana_platform_token', token);

      const sso = new SsoClient(BASE_CONFIG);
      await sso.init();

      expect(sso.isAuthenticated()).toBe(true);
    });

    it('getUser() returns the decoded user when authenticated', async () => {
      const token = makeValidToken();
      sessionStorage.setItem('ghatana_platform_token', token);

      const sso = new SsoClient(BASE_CONFIG);
      await sso.init();

      const user = sso.getUser();
      expect(user).not.toBeNull();
      expect(user?.userId).toBe('user-123');
      expect(user?.email).toBe('alice@example.com');
    });

    it('getUser().roles contains the decoded roles', async () => {
      const token = makeValidToken({ roles: ['ROLE_USER', 'ROLE_ADMIN'] });
      sessionStorage.setItem('ghatana_platform_token', token);

      const sso = new SsoClient(BASE_CONFIG);
      await sso.init();

      expect(sso.getUser()?.roles).toContain('ROLE_ADMIN');
    });
  });

  describe('init() with expired token in sessionStorage', () => {
    it('isAuthenticated() returns false for expired token', async () => {
      sessionStorage.setItem('ghatana_platform_token', makeExpiredToken());

      const sso = new SsoClient(BASE_CONFIG);
      await sso.init();

      expect(sso.isAuthenticated()).toBe(false);
    });
  });

  describe('init() with token in URL query string', () => {
    it('extracts platform_token from URL on init', async () => {
      const token = makeValidToken();

      // Simulate token in URL
      Object.defineProperty(window, 'location', {
        writable: true,
        value: {
          ...window.location,
          search: `?platform_token=${encodeURIComponent(token)}`,
          href: `https://app.ghatana.io/?platform_token=${token}`,
        },
      });

      const sso = new SsoClient(BASE_CONFIG);
      await sso.init();

      expect(sso.isAuthenticated()).toBe(true);
    });
  });
});
