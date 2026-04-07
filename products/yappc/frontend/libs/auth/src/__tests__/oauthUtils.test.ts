import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { OAuthUtils } from '../oauth/utils.js';
import type {
  OAuthProvider,
  OAuthToken,
  TokenResponse,
  UserInfoResponse,
} from '../oauth/types.js';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const PROVIDER: OAuthProvider = {
  name: 'test',
  clientId: 'client-id-123',
  clientSecret: 'secret-abc',
  authorizationUrl: 'https://auth.example.com/authorize',
  tokenUrl: 'https://auth.example.com/token',
  userInfoUrl: 'https://auth.example.com/userinfo',
  redirectUri: 'https://app.example.com/callback/test',
  scopes: ['openid', 'email', 'profile'],
};

const BASE_TOKEN: OAuthToken = {
  accessToken: 'access-123',
  tokenType: 'Bearer',
  expiresIn: 3600,
  scope: 'openid email profile',
  issuedAt: Date.now() - 1000, // issued 1 second ago
};

function makeFetchResponse(
  body: unknown,
  ok = true,
  statusText = 'OK'
): Response {
  return {
    ok,
    status: ok ? 200 : 400,
    statusText,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('OAuthUtils.generateAuthorizationUrl()', () => {
  it('builds a URL with all required query params', () => {
    const url = OAuthUtils.generateAuthorizationUrl(PROVIDER, 'csrf-state-xyz');
    expect(url).toContain(PROVIDER.authorizationUrl);
    expect(url).toContain('client_id=client-id-123');
    expect(url).toContain('response_type=code');
    expect(url).toContain('state=csrf-state-xyz');
    expect(url).toContain('redirect_uri=');
    expect(url).toContain('scope=');
  });

  it('includes space-separated scopes in the URL', () => {
    const url = OAuthUtils.generateAuthorizationUrl(PROVIDER, 'state');
    const searchPart = url.split('?')[1]!;
    const params = new URLSearchParams(searchPart);
    expect(params.get('scope')).toBe('openid email profile');
  });
});

describe('OAuthUtils.generateState()', () => {
  it('returns a hex string of 64 characters (32 bytes)', () => {
    const state = OAuthUtils.generateState();
    expect(state).toMatch(/^[0-9a-f]{64}$/);
  });

  it('returns a unique value each call', () => {
    const s1 = OAuthUtils.generateState();
    const s2 = OAuthUtils.generateState();
    expect(s1).not.toBe(s2);
  });
});

describe('OAuthUtils.parseAuthorizationResponse()', () => {
  it('parses code and state from callback URL', () => {
    const url =
      'https://app.example.com/callback?code=auth-code&state=csrf-state';
    const result = OAuthUtils.parseAuthorizationResponse(url);
    expect(result.code).toBe('auth-code');
    expect(result.state).toBe('csrf-state');
    expect(result.error).toBeUndefined();
    expect(result.errorDescription).toBeUndefined();
  });

  it('parses error and errorDescription when present', () => {
    const url =
      'https://app.example.com/callback?error=access_denied&error_description=User+denied&state=s';
    const result = OAuthUtils.parseAuthorizationResponse(url);
    expect(result.error).toBe('access_denied');
    expect(result.errorDescription).toBe('User denied');
  });

  it('returns empty code/state when absent', () => {
    const url = 'https://app.example.com/callback';
    const result = OAuthUtils.parseAuthorizationResponse(url);
    expect(result.code).toBe('');
    expect(result.state).toBe('');
  });
});

describe('OAuthUtils.exchangeCodeForToken()', () => {
  afterEach(() => vi.restoreAllMocks());

  it('returns an OAuthToken on success', async () => {
    const tokenResponse: TokenResponse = {
      access_token: 'new-access',
      token_type: 'Bearer',
      expires_in: 3600,
      refresh_token: 'new-refresh',
      scope: 'openid email',
    };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse(tokenResponse)
    );

    const token = await OAuthUtils.exchangeCodeForToken(PROVIDER, 'auth-code');
    expect(token.accessToken).toBe('new-access');
    expect(token.tokenType).toBe('Bearer');
    expect(token.refreshToken).toBe('new-refresh');
    expect(token.issuedAt).toBeGreaterThan(0);
  });

  it('throws when the token endpoint returns non-ok', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse({}, false, 'Bad Request')
    );
    await expect(
      OAuthUtils.exchangeCodeForToken(PROVIDER, 'bad-code')
    ).rejects.toThrow('Token exchange failed');
  });
});

describe('OAuthUtils.refreshAccessToken()', () => {
  afterEach(() => vi.restoreAllMocks());

  it('returns a new OAuthToken on success', async () => {
    const tokenResponse: TokenResponse = {
      access_token: 'refreshed-access',
      token_type: 'Bearer',
      expires_in: 3600,
      scope: 'openid email',
    };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse(tokenResponse)
    );

    const token = await OAuthUtils.refreshAccessToken(PROVIDER, 'old-refresh');
    expect(token.accessToken).toBe('refreshed-access');
    // Falls back to the supplied refresh token when not in response
    expect(token.refreshToken).toBe('old-refresh');
  });

  it('uses new refresh token when response includes one', async () => {
    const tokenResponse: TokenResponse = {
      access_token: 'acc',
      token_type: 'Bearer',
      expires_in: 3600,
      refresh_token: 'brand-new-refresh',
      scope: 'openid',
    };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse(tokenResponse)
    );

    const token = await OAuthUtils.refreshAccessToken(PROVIDER, 'old-refresh');
    expect(token.refreshToken).toBe('brand-new-refresh');
  });

  it('throws when token endpoint returns non-ok', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse({}, false, 'Unauthorized')
    );
    await expect(
      OAuthUtils.refreshAccessToken(PROVIDER, 'tok')
    ).rejects.toThrow('Token refresh failed');
  });
});

describe('OAuthUtils.fetchUserInfo()', () => {
  afterEach(() => vi.restoreAllMocks());

  it('returns OAuthUser mapped from provider response', async () => {
    const userInfoResponse: UserInfoResponse = {
      id: 'user-1',
      email: 'bob@example.com',
      name: 'Bob',
      picture: 'https://example.com/avatar.png',
    };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse(userInfoResponse)
    );

    const user = await OAuthUtils.fetchUserInfo(PROVIDER, BASE_TOKEN);
    expect(user.id).toBe('user-1');
    expect(user.email).toBe('bob@example.com');
    expect(user.name).toBe('Bob');
    expect(user.avatar).toBe('https://example.com/avatar.png');
    expect(user.provider).toBe('test');
  });

  it('sends Authorization header with token type and access token', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(
        makeFetchResponse({ id: 'u', email: 'e@e.com', name: 'E' })
      );
    await OAuthUtils.fetchUserInfo(PROVIDER, BASE_TOKEN);
    expect(fetchMock).toHaveBeenCalledWith(
      PROVIDER.userInfoUrl,
      expect.objectContaining({
        headers: { Authorization: 'Bearer access-123' },
      })
    );
  });

  it('throws when user info endpoint returns non-ok', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse({}, false, 'Forbidden')
    );
    await expect(
      OAuthUtils.fetchUserInfo(PROVIDER, BASE_TOKEN)
    ).rejects.toThrow('User info fetch failed');
  });
});

describe('OAuthUtils.isTokenExpired()', () => {
  it('returns false for a fresh token', () => {
    const token: OAuthToken = {
      ...BASE_TOKEN,
      issuedAt: Date.now(),
      expiresIn: 3600,
    };
    expect(OAuthUtils.isTokenExpired(token)).toBe(false);
  });

  it('returns true for an expired token', () => {
    const token: OAuthToken = {
      ...BASE_TOKEN,
      issuedAt: Date.now() - 4000 * 1000, // issued 4000 seconds ago
      expiresIn: 3600,
    };
    expect(OAuthUtils.isTokenExpired(token)).toBe(true);
  });
});

describe('OAuthUtils.isTokenExpiringSoon()', () => {
  it('returns false for a token with plenty of time left', () => {
    const token: OAuthToken = {
      ...BASE_TOKEN,
      issuedAt: Date.now(),
      expiresIn: 3600,
    };
    expect(OAuthUtils.isTokenExpiringSoon(token)).toBe(false);
  });

  it('returns true for a token expiring within 5 minutes', () => {
    const token: OAuthToken = {
      ...BASE_TOKEN,
      issuedAt: Date.now() - (3600 - 200) * 1000, // 200 seconds left
      expiresIn: 3600,
    };
    expect(OAuthUtils.isTokenExpiringSoon(token)).toBe(true);
  });
});

describe('OAuthUtils token storage (localStorage)', () => {
  beforeEach(() => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => void 0);
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(null);
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => void 0);
  });
  afterEach(() => vi.restoreAllMocks());

  it('storeToken calls localStorage.setItem with JSON', () => {
    const setMock = vi.spyOn(Storage.prototype, 'setItem');
    OAuthUtils.storeToken(BASE_TOKEN, 'my_token');
    expect(setMock).toHaveBeenCalledWith(
      'my_token',
      JSON.stringify(BASE_TOKEN)
    );
  });

  it('storeToken uses default key "oauth_token"', () => {
    const setMock = vi.spyOn(Storage.prototype, 'setItem');
    OAuthUtils.storeToken(BASE_TOKEN);
    expect(setMock).toHaveBeenCalledWith('oauth_token', expect.any(String));
  });

  it('retrieveToken returns null when key absent', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(null);
    expect(OAuthUtils.retrieveToken()).toBeNull();
  });

  it('retrieveToken returns parsed token', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(
      JSON.stringify(BASE_TOKEN)
    );
    const retrieved = OAuthUtils.retrieveToken('my_token');
    expect(retrieved?.accessToken).toBe('access-123');
  });

  it('clearToken calls localStorage.removeItem', () => {
    const removeMock = vi.spyOn(Storage.prototype, 'removeItem');
    OAuthUtils.clearToken('my_token');
    expect(removeMock).toHaveBeenCalledWith('my_token');
  });
});
