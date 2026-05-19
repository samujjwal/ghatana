import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { mockAuthApi } = vi.hoisted(() => ({
  mockAuthApi: {
    login: vi.fn(),
    currentUser: vi.fn(),
    logout: vi.fn(),
  },
}));

vi.mock('@/clients/generated/api', () => ({
  AuthService: mockAuthApi,
  ApiError: class MockApiError extends Error {
    status: number;
    constructor(message: string, status = 500) {
      super(message);
      this.status = status;
    }
  },
}));

import { authService } from '../AuthService';

function resetAuthServiceState(): void {
  const timer = Reflect.get(authService, 'sessionTimeout') as ReturnType<typeof setTimeout> | null;
  if (timer) {
    clearTimeout(timer);
  }
  Reflect.set(authService, 'currentSession', null);
  Reflect.set(authService, 'sessionTimeout', null);
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

describe('AuthService', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
    mockAuthApi.login.mockReset();
    mockAuthApi.currentUser.mockReset();
    mockAuthApi.logout.mockReset();
    localStorage.clear();
    resetAuthServiceState();
  });

  afterEach(() => {
    resetAuthServiceState();
    localStorage.clear();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('maps the BFF login response into the stored auth-session shape', async () => {
    mockAuthApi.login.mockResolvedValue({
      user: {
        id: 'user-1',
        email: 'sam@yappc.local',
        name: 'Sam User',
        role: 'ADMIN',
      },
      tokens: {
        accessToken: 'access-token-1',
        refreshToken: 'refresh-token-1',
        expiresIn: 900,
      },
    });
    mockAuthApi.currentUser.mockResolvedValue({
      id: 'user-1',
      email: 'sam@yappc.local',
      name: 'Sam User',
      role: 'ADMIN',
    });

    const result = await authService.login({ email: 'sam@yappc.local', password: 'secret' });
    const storedMetadata = JSON.parse(localStorage.getItem('auth-session-meta') ?? '{}') as Record<string, unknown>;

    expect(mockAuthApi.login).toHaveBeenCalledWith({
        email: 'sam@yappc.local',
        password: 'secret',
      });
    expect(mockAuthApi.currentUser).toHaveBeenCalled();
    expect(result).toMatchObject({
      success: true,
      token: 'access-token-1',
      user: {
        email: 'sam@yappc.local',
        role: 'admin',
      },
    });
    expect(storedMetadata).toMatchObject({
      userId: 'user-1',
    });
    expect(localStorage.getItem('auth-session')).toBeNull();
  });

  it('returns an error when registration endpoint is unavailable', async () => {
    const result = await authService.register({
      firstName: 'Sam',
      lastName: 'User',
      username: 'samuser',
      email: 'sam@yappc.local',
      password: 'secret',
    });

    expect(result.success).toBe(false);
    expect(result.error).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refreshes tokens with the canonical refresh payload', async () => {
    const session = {
      user: {
        id: 'user-1',
        username: 'sam@yappc.local',
        email: 'sam@yappc.local',
        firstName: 'Sam',
        lastName: 'User',
        role: 'admin',
        permissions: ['*'],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
      token: 'access-token-1',
      refreshToken: 'refresh-token-1',
      expiresAt: new Date(Date.now() + 30000).toISOString(),
      permissions: ['*'],
    };
    Reflect.set(authService, 'currentSession', session);
    fetchMock.mockResolvedValue(jsonResponse({
      expiresAt: new Date(Date.now() + 1800_000).toISOString(),
      authMode: 'cookie',
    }));

    const refreshed = await authService.refreshToken();
    const storedMetadata = JSON.parse(localStorage.getItem('auth-session-meta') ?? '{}') as Record<string, unknown>;

    expect(refreshed).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/refresh', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({}),
    }));
    expect(storedMetadata).toMatchObject({
      userId: 'user-1',
    });
    expect(localStorage.getItem('auth-session')).toBeNull();
  });

  it('posts the refresh token on logout and clears the browser session', async () => {
    const currentSession = {
      user: {
        id: 'user-1',
        username: 'sam@yappc.local',
        email: 'sam@yappc.local',
        firstName: 'Sam',
        lastName: 'User',
        role: 'admin',
        permissions: ['*'],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
      token: 'access-token-1',
      refreshToken: 'refresh-token-1',
      expiresAt: new Date(Date.now() + 30000).toISOString(),
      permissions: ['*'],
    };
    localStorage.setItem('auth-session', JSON.stringify(currentSession));
    Reflect.set(authService, 'currentSession', currentSession);
    mockAuthApi.logout.mockResolvedValue(undefined);

    await authService.logout();

    expect(mockAuthApi.logout).toHaveBeenCalledWith();
    expect(localStorage.getItem('auth-session')).toBeNull();
    expect(localStorage.getItem('auth-session-meta')).toBeNull();
    expect(authService.getCurrentUser()).toBeNull();
  });

  it('fails closed when demo login is not explicitly enabled', async () => {
    const result = await authService.demoLogin();

    expect(result).toEqual({
      success: false,
      error: 'Demo login is unavailable in this environment',
    });
    expect(localStorage.getItem('auth-session')).toBeNull();
  });
});
