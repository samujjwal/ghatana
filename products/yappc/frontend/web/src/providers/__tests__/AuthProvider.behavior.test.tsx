import { Provider, useAtomValue } from 'jotai';
import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '../AuthProvider';
import { currentUserAtom } from '../../stores/user.store';

function CurrentUserProbe(): JSX.Element {
  const currentUser = useAtomValue(currentUserAtom);

  return <div data-testid="current-user">{currentUser?.email ?? 'guest'}</div>;
}

describe('AuthProvider', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('keeps guest state when there is no stored auth token', async () => {
    render(
      <Provider>
        <AuthProvider>
          <CurrentUserProbe />
        </AuthProvider>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('current-user')).toHaveTextContent('guest');
    });

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('hydrates currentUserAtom from /api/auth/me when a valid token exists', async () => {
    localStorage.setItem('auth-session', JSON.stringify({ token: 'access-token-1' }));
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'user-1',
          firstName: 'Sam',
          lastName: 'User',
          email: 'sam@yappc.local',
          role: 'ADMIN',
          tenantId: 'tenant-1',
          workspaceIds: ['ws-1'],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );

    render(
      <Provider>
        <AuthProvider>
          <CurrentUserProbe />
        </AuthProvider>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('current-user')).toHaveTextContent('sam@yappc.local');
    });

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', {
      headers: {
        Accept: 'application/json',
        Authorization: 'Bearer access-token-1',
      },
    });
  });

  it('fails closed when /api/auth/me returns unauthorized', async () => {
    localStorage.setItem('auth-session', JSON.stringify({ token: 'expired-token' }));
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));

    render(
      <Provider>
        <AuthProvider>
          <CurrentUserProbe />
        </AuthProvider>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('current-user')).toHaveTextContent('guest');
    });
  });

  it('refreshes the stored session and retries /api/auth/me once when the access token is expired', async () => {
    localStorage.setItem(
      'auth-session',
      JSON.stringify({
        token: 'expired-token',
        refreshToken: 'refresh-token-1',
      })
    );
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            accessToken: 'access-token-2',
            refreshToken: 'refresh-token-2',
            expiresIn: 1800,
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            id: 'user-1',
            firstName: 'Sam',
            lastName: 'User',
            email: 'sam@yappc.local',
            role: 'ADMIN',
            tenantId: 'tenant-1',
            workspaceIds: ['ws-1'],
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }
        )
      );

    render(
      <Provider>
        <AuthProvider>
          <CurrentUserProbe />
        </AuthProvider>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('current-user')).toHaveTextContent('sam@yappc.local');
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/me', {
      headers: {
        Accept: 'application/json',
        Authorization: 'Bearer expired-token',
      },
    });
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/refresh', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refreshToken: 'refresh-token-1' }),
    });
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/auth/me', {
      headers: {
        Accept: 'application/json',
        Authorization: 'Bearer access-token-2',
      },
    });
    expect(JSON.parse(localStorage.getItem('auth-session') ?? '{}')).toMatchObject({
      token: 'access-token-2',
      refreshToken: 'refresh-token-2',
    });
  });

  it('clears the stale stored session when refresh also fails', async () => {
    localStorage.setItem(
      'auth-session',
      JSON.stringify({
        token: 'expired-token',
        refreshToken: 'refresh-token-1',
      })
    );
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }));

    render(
      <Provider>
        <AuthProvider>
          <CurrentUserProbe />
        </AuthProvider>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('current-user')).toHaveTextContent('guest');
    });

    expect(localStorage.getItem('auth-session')).toBeNull();
  });
});