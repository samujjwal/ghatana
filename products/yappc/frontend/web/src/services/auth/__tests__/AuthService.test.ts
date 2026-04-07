import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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
    fetchMock.mockResolvedValue(
      jsonResponse({
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
      })
    );

    const result = await authService.login({ email: 'sam@yappc.local', password: 'secret' });
    const storedSession = JSON.parse(localStorage.getItem('auth-session') ?? '{}') as Record<string, unknown>;

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        email: 'sam@yappc.local',
        password: 'secret',
        rememberMe: false,
      }),
    }));
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

  it('maps registration into the canonical backend payload before logging in', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ user: { id: 'user-1' } }, 201))
      .mockResolvedValueOnce(
        jsonResponse({
          user: {
            id: 'user-1',
            email: 'sam@yappc.local',
            name: 'Sam User',
            role: 'EDITOR',
          },
          tokens: {
            accessToken: 'access-token-2',
            refreshToken: 'refresh-token-2',
            expiresIn: 900,
          },
        })
      );

    const result = await authService.register({
      firstName: 'Sam',
      lastName: 'User',
      username: 'samuser',
      email: 'sam@yappc.local',
      password: 'secret',
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/register', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        email: 'sam@yappc.local',
        password: 'secret',
        name: 'Sam User',
      }),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/login', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        email: 'sam@yappc.local',
        password: 'secret',
        rememberMe: false,
      }),
    }));
    expect(result).toMatchObject({
      success: true,
      token: 'access-token-2',
      user: {
        role: 'user',
      },
    });
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
    fetchMock.mockResolvedValue(
      jsonResponse({
        accessToken: 'access-token-2',
        refreshToken: 'refresh-token-2',
        expiresIn: 1800,
      })
    );

    const refreshed = await authService.refreshToken();
    const storedSession = JSON.parse(localStorage.getItem('auth-session') ?? '{}') as Record<string, unknown>;

    expect(refreshed).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/refresh', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ refreshToken: 'refresh-token-1' }),
    }));
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
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await authService.logout();

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ refreshToken: 'refresh-token-1' }),
    }));
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