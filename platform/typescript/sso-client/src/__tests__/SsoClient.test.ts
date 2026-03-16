/**
 * Tests for @ghatana/sso-client
 *
 * These tests run in jsdom (via jest) — sessionStorage and URL manipulation
 * are available via the global environment.
 */

import { SsoClient, decodeJwtPayload, isPlatformToken, tokenTtlSeconds } from '../src/index';

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** Builds a minimal, unsigned JWT with the given payload (dev/test only). */
function buildTestJwt(payload: Record<string, unknown>): string {
  const header  = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).replace(/=/g, '');
  const body    = btoa(JSON.stringify(payload)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${header}.${body}.fake-sig`;
}

function makeValidToken(overrides: Record<string, unknown> = {}): string {
  return buildTestJwt({
    sub:       'user-123',
    email:     'alice@example.com',
    roles:     ['ROLE_USER'],
    tenantId:  'tenant-a',
    tokenType: 'PLATFORM',
    iss:       'ghatana-auth-service',
    sessionId: 'session-xyz',
    iat:       Math.floor(Date.now() / 1000),
    exp:       Math.floor(Date.now() / 1000) + 900,
    ...overrides,
  });
}

function makeExpiredToken(): string {
  return makeValidToken({ exp: Math.floor(Date.now() / 1000) - 1 });
}

const BASE_CONFIG = {
  authServiceBaseUrl:  'https://auth.ghatana.io',
  authGatewayBaseUrl:  'https://gateway.ghatana.io',
  productId:           'tutorputor',
};

// ─── decodeJwtPayload ────────────────────────────────────────────────────────

describe('decodeJwtPayload', () => {
  it('decodes a valid JWT payload', () => {
    const token  = makeValidToken();
    const claims = decodeJwtPayload(token);
    expect(claims?.sub).toBe('user-123');
    expect(claims?.email).toBe('alice@example.com');
    expect(claims?.tokenType).toBe('PLATFORM');
  });

  it('returns null for a malformed token', () => {
    expect(decodeJwtPayload('not.a.jwt.at.all')).toBeNull();
    expect(decodeJwtPayload('')).toBeNull();
    expect(decodeJwtPayload('onlyone')).toBeNull();
  });
});

// ─── isPlatformToken ──────────────────────────────────────────────────────────

describe('isPlatformToken', () => {
  it('returns true for a PLATFORM token', () => {
    expect(isPlatformToken(makeValidToken())).toBe(true);
  });

  it('returns false for a non-PLATFORM token', () => {
    expect(isPlatformToken(makeValidToken({ tokenType: 'PRODUCT' }))).toBe(false);
  });
});

// ─── tokenTtlSeconds ─────────────────────────────────────────────────────────

describe('tokenTtlSeconds', () => {
  it('returns a positive number for a fresh token', () => {
    expect(tokenTtlSeconds(makeValidToken())).toBeGreaterThan(0);
  });

  it('returns 0 for an expired token', () => {
    expect(tokenTtlSeconds(makeExpiredToken())).toBe(0);
  });
});

// ─── SsoClient ───────────────────────────────────────────────────────────────

describe('SsoClient', () => {
  beforeEach(() => {
    sessionStorage.clear();
    // Reset URL
    window.history.replaceState({}, '', 'http://localhost/');
  });

  describe('init()', () => {
    it('reads platform_token from URL and stores it', async () => {
      const token = makeValidToken();
      window.history.replaceState({}, '', `http://localhost/?platform_token=${token}`);

      const client = new SsoClient(BASE_CONFIG);
      await client.init();

      expect(sessionStorage.getItem('ghatana_platform_token')).toBe(token);
      expect(window.location.search).not.toContain('platform_token');
    });

    it('loads token from sessionStorage if present', async () => {
      const token = makeValidToken();
      sessionStorage.setItem('ghatana_platform_token', token);

      const client = new SsoClient(BASE_CONFIG);
      await client.init();

      expect(client.isAuthenticated()).toBe(true);
    });

    it('does not authenticate when sessionStorage is empty', async () => {
      const client = new SsoClient(BASE_CONFIG);
      await client.init();
      expect(client.isAuthenticated()).toBe(false);
    });
  });

  describe('isAuthenticated()', () => {
    it('returns true for a valid non-expired token', async () => {
      const token = makeValidToken();
      sessionStorage.setItem('ghatana_platform_token', token);

      const client = new SsoClient(BASE_CONFIG);
      await client.init();
      expect(client.isAuthenticated()).toBe(true);
    });

    it('returns false for an expired token', async () => {
      const token = makeExpiredToken();
      sessionStorage.setItem('ghatana_platform_token', token);

      const client = new SsoClient(BASE_CONFIG);
      await client.init();
      expect(client.isAuthenticated()).toBe(false);
    });
  });

  describe('getUser()', () => {
    it('returns user data from token claims', async () => {
      const token = makeValidToken();
      sessionStorage.setItem('ghatana_platform_token', token);

      const client = new SsoClient(BASE_CONFIG);
      await client.init();

      const user = client.getUser();
      expect(user).not.toBeNull();
      expect(user?.userId).toBe('user-123');
      expect(user?.email).toBe('alice@example.com');
      expect(user?.roles).toContain('ROLE_USER');
      expect(user?.tenantId).toBe('tenant-a');
    });

    it('returns null when not authenticated', async () => {
      const client = new SsoClient(BASE_CONFIG);
      await client.init();
      expect(client.getUser()).toBeNull();
    });
  });

  describe('getRawToken()', () => {
    it('returns the raw JWT when authenticated', async () => {
      const token = makeValidToken();
      sessionStorage.setItem('ghatana_platform_token', token);

      const client = new SsoClient(BASE_CONFIG);
      await client.init();

      expect(client.getRawToken()).toBe(token);
    });

    it('returns null when not authenticated', async () => {
      const client = new SsoClient(BASE_CONFIG);
      await client.init();
      expect(client.getRawToken()).toBeNull();
    });
  });

  describe('login()', () => {
    it('redirects to auth-service with correct params', () => {
      const assignSpy = jest.spyOn(window.location, 'assign').mockImplementation(() => {});

      const client = new SsoClient({
        ...BASE_CONFIG,
        postLoginRedirectUrl: 'http://localhost/after-login',
      });
      client.login();

      expect(assignSpy).toHaveBeenCalledTimes(1);
      const called = new URL(assignSpy.mock.calls[0]![0] as string);
      expect(called.pathname).toBe('/auth/login');
      expect(called.searchParams.get('product_id')).toBe('tutorputor');
      expect(called.searchParams.get('redirect_uri')).toBe('http://localhost/after-login');

      assignSpy.mockRestore();
    });
  });

  describe('exchangeForPlatformToken()', () => {
    it('returns a platform token on success', async () => {
      const expectedToken = makeValidToken();
      global.fetch = jest.fn().mockResolvedValue({
        ok:   true,
        json: async () => ({ platformToken: expectedToken }),
      }) as jest.Mock;

      const client = new SsoClient(BASE_CONFIG);
      const result  = await client.exchangeForPlatformToken('some-product-jwt');

      expect(result).toBe(expectedToken);
      expect(fetch).toHaveBeenCalledWith(
        'https://gateway.ghatana.io/auth/exchange',
        expect.objectContaining({ method: 'POST' })
      );
    });

    it('returns null on network error', async () => {
      global.fetch = jest.fn().mockRejectedValue(new Error('network')) as jest.Mock;

      const client = new SsoClient(BASE_CONFIG);
      const result  = await client.exchangeForPlatformToken('some-jwt');
      expect(result).toBeNull();
    });

    it('returns null on non-OK response', async () => {
      global.fetch = jest.fn().mockResolvedValue({ ok: false }) as jest.Mock;

      const client = new SsoClient(BASE_CONFIG);
      const result  = await client.exchangeForPlatformToken('some-jwt');
      expect(result).toBeNull();
    });
  });
});
