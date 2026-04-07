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
});