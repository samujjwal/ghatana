import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { mockAuthApi } = vi.hoisted(() => ({
  mockAuthApi: {
    loginSession: vi.fn(),
    refresh: vi.fn(),
    logout: vi.fn(),
    updateProfile: vi.fn(),
  },
}));

vi.mock('../../../lib/api/client', () => ({
  yappcApi: {
    auth: mockAuthApi,
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
    mockAuthApi.loginSession.mockReset();
    mockAuthApi.refresh.mockReset();
    mockAuthApi.logout.mockReset();
    mockAuthApi.updateProfile.mockReset();
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
    mockAuthApi.loginSession.mockResolvedValue({
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

    const result = await authService.login({ email: 'sam@yappc.local', password: 'secret' });
    const storedSession = JSON.parse(localStorage.getItem('auth-session') ?? '{}') as Record<string, unknown>;

    expect(mockAuthApi.loginSession).toHaveBeenCalledWith({
        email: 'sam@yappc.local',
        password: 'secret',
      });
    expect(result).toMatchObject({
      success: true,
      token: 'access-token-1',
      user: {
        email: 'sam@yappc.local',
        role: 'admin',
      },
    });
    expect(storedSession).toMatchObject({
      token: 'access-token-1',
      refreshToken: 'refresh-token-1',
      permissions: ['*'],
    });
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
    localStorage.setItem('auth-session', JSON.stringify({
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
    }));
    Reflect.set(authService, 'currentSession', JSON.parse(localStorage.getItem('auth-session') ?? '{}'));
    mockAuthApi.refresh.mockResolvedValue({
      accessToken: 'access-token-2',
      refreshToken: 'refresh-token-2',
      expiresIn: 1800,
    });

    const refreshed = await authService.refreshToken();
    const storedSession = JSON.parse(localStorage.getItem('auth-session') ?? '{}') as Record<string, unknown>;

    expect(refreshed).toBe(true);
    expect(mockAuthApi.refresh).toHaveBeenCalledWith({ refreshToken: 'refresh-token-1' });
    expect(storedSession).toMatchObject({
      token: 'access-token-2',
      refreshToken: 'refresh-token-2',
    });
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

    expect(mockAuthApi.logout).toHaveBeenCalledWith({ refreshToken: 'refresh-token-1' });
    expect(localStorage.getItem('auth-session')).toBeNull();
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