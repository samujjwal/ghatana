import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  AuthService,
  type LoginResponse,
  type RegisterResponse,
  type RefreshTokenResponse,
  type User,
} from '../auth/authService.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeFetchResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 200 ? 'OK' : String(status),
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response;
}

const SAMPLE_USER: User = {
  id: 'u1',
  email: 'alice@example.com',
  name: 'Alice',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const SAMPLE_TOKENS = {
  accessToken: 'access-tok',
  refreshToken: 'refresh-tok',
  expiresIn: 3600,
};

// ---------------------------------------------------------------------------
// Setup / teardown
// ---------------------------------------------------------------------------

beforeEach(() => {
  vi.spyOn(globalThis, 'fetch').mockReset();
  // localStorage stub
  vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(null);
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => void 0);
  vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => void 0);
});

afterEach(() => {
  vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Constructor / defaults
// ---------------------------------------------------------------------------

describe('AuthService constructor', () => {
  it('uses /api as default baseUrl when env var is absent', () => {
    // Just verify construction doesn't throw and the service is usable
    const svc = new AuthService();
    expect(svc).toBeInstanceOf(AuthService);
  });

  it('accepts a custom baseUrl', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse({ ...SAMPLE_USER, ...SAMPLE_TOKENS, user: SAMPLE_USER })
    );
    const svc = new AuthService({ baseUrl: 'https://custom.api' });
    await svc.login({ email: 'x@x.com', password: 'pw' });
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('https://custom.api'),
      expect.any(Object)
    );
  });
});

// ---------------------------------------------------------------------------
// login()
// ---------------------------------------------------------------------------

describe('AuthService.login()', () => {
  it('returns LoginResponse on success', async () => {
    const payload: LoginResponse = { user: SAMPLE_USER, ...SAMPLE_TOKENS };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse(payload));

    const svc = new AuthService({ baseUrl: '/api' });
    const result = await svc.login({ email: 'alice@example.com', password: 'pw' });

    expect(result.user.email).toBe('alice@example.com');
    expect(result.accessToken).toBe('access-tok');
    expect(result.expiresIn).toBe(3600);
  });

  it('sends POST to /auth/login', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse({ user: SAMPLE_USER, ...SAMPLE_TOKENS })
    );
    const svc = new AuthService({ baseUrl: '/api' });
    await svc.login({ email: 'a@b.com', password: 'pass' });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('throws when server returns non-ok (500)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse({ message: 'Internal server error' }, 500)
    );
    const svc = new AuthService({ baseUrl: '/api' });
    await expect(svc.login({ email: 'a@b.com', password: 'bad' })).rejects.toThrow(
      'Internal server error'
    );
  });
});

// ---------------------------------------------------------------------------
// register()
// ---------------------------------------------------------------------------

describe('AuthService.register()', () => {
  it('returns RegisterResponse on success', async () => {
    const payload: RegisterResponse = { user: SAMPLE_USER, ...SAMPLE_TOKENS };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse(payload));

    const svc = new AuthService({ baseUrl: '/api' });
    const result = await svc.register({ name: 'Alice', email: 'alice@example.com', password: 'pw' });

    expect(result.user.id).toBe('u1');
    expect(result.refreshToken).toBe('refresh-tok');
  });

  it('sends POST to /auth/register', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse({ user: SAMPLE_USER, ...SAMPLE_TOKENS })
    );
    const svc = new AuthService({ baseUrl: '/api' });
    await svc.register({ name: 'B', email: 'b@c.com', password: 'pw2' });
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/register', expect.objectContaining({ method: 'POST' }));
  });
});

// ---------------------------------------------------------------------------
// 401 / onUnauthorized callback
// ---------------------------------------------------------------------------

describe('HTTP 401 handling', () => {
  it('calls onUnauthorized callback when response is 401', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse({}, 401));

    const onUnauthorized = vi.fn();
    const svc = new AuthService({ baseUrl: '/api', onUnauthorized });

    await expect(svc.me()).rejects.toThrow('Unauthorized');
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('does not call onUnauthorized when it is not provided', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse({}, 401));
    const svc = new AuthService({ baseUrl: '/api' });
    await expect(svc.me()).rejects.toThrow('Unauthorized');
    // should not throw due to undefined callback
  });
});

// ---------------------------------------------------------------------------
// 403 / onTokenExpired callback
// ---------------------------------------------------------------------------

describe('HTTP 403 handling', () => {
  it('calls onTokenExpired callback when response is 403', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse({}, 403));

    const onTokenExpired = vi.fn();
    const svc = new AuthService({ baseUrl: '/api', onTokenExpired });

    await expect(svc.me()).rejects.toThrow('Token expired');
    expect(onTokenExpired).toHaveBeenCalledTimes(1);
  });

  it('does not call onTokenExpired when not provided', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse({}, 403));
    const svc = new AuthService({ baseUrl: '/api' });
    await expect(svc.me()).rejects.toThrow('Token expired');
  });
});

// ---------------------------------------------------------------------------
// Bearer token injection
// ---------------------------------------------------------------------------

describe('Token injection', () => {
  it('injects Authorization header when auth_token is in localStorage', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue('stored-jwt');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse(SAMPLE_USER)
    );

    const svc = new AuthService({ baseUrl: '/api' });
    await svc.me();

    const calledConfig = fetchMock.mock.calls[0]![1] as RequestInit;
    expect((calledConfig.headers as Record<string, string>)['Authorization']).toBe(
      'Bearer stored-jwt'
    );
  });

  it('omits Authorization header when no token stored', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(null);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      makeFetchResponse(SAMPLE_USER)
    );

    const svc = new AuthService({ baseUrl: '/api' });
    await svc.me();

    const calledConfig = fetchMock.mock.calls[0]![1] as RequestInit;
    const headers = calledConfig.headers as Record<string, string>;
    expect(headers['Authorization']).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// me()
// ---------------------------------------------------------------------------

describe('AuthService.me()', () => {
  it('returns User on success', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse(SAMPLE_USER));

    const svc = new AuthService({ baseUrl: '/api' });
    const user = await svc.me();
    expect(user.id).toBe('u1');
    expect(user.name).toBe('Alice');
  });

  it('sends GET to /auth/me', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse(SAMPLE_USER));
    const svc = new AuthService({ baseUrl: '/api' });
    await svc.me();
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({ method: 'GET' }));
  });
});

// ---------------------------------------------------------------------------
// refreshToken()
// ---------------------------------------------------------------------------

describe('AuthService.refreshToken()', () => {
  it('returns new tokens on success', async () => {
    const payload: RefreshTokenResponse = { ...SAMPLE_TOKENS, accessToken: 'new-access' };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse(payload));

    const svc = new AuthService({ baseUrl: '/api' });
    const result = await svc.refreshToken({ refreshToken: 'refresh-tok' });
    expect(result.accessToken).toBe('new-access');
  });
});

// ---------------------------------------------------------------------------
// logout()
// ---------------------------------------------------------------------------

describe('AuthService.logout()', () => {
  it('calls /auth/logout with POST', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse(null));
    const svc = new AuthService({ baseUrl: '/api' });
    await svc.logout({ refreshToken: 'tok' });
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({ method: 'POST' }));
  });

  it('works without a refreshToken argument', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(makeFetchResponse(null));
    const svc = new AuthService({ baseUrl: '/api' });
    await expect(svc.logout()).resolves.toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Network error fallback
// ---------------------------------------------------------------------------

describe('Network errors', () => {
  it('wraps unknown rejection as "Network error"', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue('something opaque');
    const svc = new AuthService({ baseUrl: '/api' });
    await expect(svc.me()).rejects.toThrow('Network error');
  });

  it('re-throws Error instances directly', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('DNS failure'));
    const svc = new AuthService({ baseUrl: '/api' });
    await expect(svc.me()).rejects.toThrow('DNS failure');
  });
});
