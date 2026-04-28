import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { ThemeProvider } from '@ghatana/theme';
import { AuthProvider } from '@/context/AuthContext';
import { ProtectedRoute } from '@/components/security/ProtectedRoute';
import { NavBar } from '@/components/shared/NavBar';
import { LoginPage } from '@/pages/LoginPage';

function makeJwt(expOffsetSeconds = 3600): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({ sub: 'user-1', exp: Math.floor(Date.now() / 1000) + expOffsetSeconds }));
  return `${header}.${payload}.signature`;
}

vi.mock('@/api/sse', () => ({
  subscribeToAepStream: () => ({ close: vi.fn() }),
}));

vi.mock('@/lib/feature-flags', () => ({
  isFeatureEnabled: (flag: string) => flag === 'LEGACY_JWT_PASTE',
  featureFlags: { LEGACY_JWT_PASTE: true },
}));

function renderWithProviders(ui: React.ReactElement, initialEntries: string[] = ['/login']) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('AEP auth flow', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    sessionStorage.clear();
    localStorage.clear();
  });

  it('redirects unauthenticated users to the login page', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/login" element={<div>Login screen</div>} />
        <Route element={<ProtectedRoute />}>
          <Route path="/operate" element={<div>Operate</div>} />
        </Route>
      </Routes>,
      ['/operate'],
    );

    await waitFor(() => expect(screen.getByText('Login screen')).toBeInTheDocument());
  });

  it('stores the verified token and attempts session bootstrap after sign in', async () => {
    const token = makeJwt();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ roles: ['operator'], retrievedAt: new Date().toISOString() }), {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
          },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ session: 'session-123', expiresInSeconds: 3600 }), {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'X-AEP-Session': 'session-123',
          },
        }),
      );
    vi.stubGlobal('fetch', fetchMock);

    renderWithProviders(
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/operate" element={<div>Operate</div>} />
        </Route>
      </Routes>,
    );

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/jwt access token/i), token);
    await user.click(screen.getByRole('button', { name: /sign in with token/i }));

    await waitFor(() => expect(screen.getByText('Operate')).toBeInTheDocument());
    expect(sessionStorage.getItem('aep-token')).toBe(token);
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/auth/roles',
      expect.objectContaining({
        method: 'GET',
      }),
    );
    expect(fetchMock.mock.calls).toContainEqual([
      '/api/v1/session',
      expect.objectContaining({
        method: 'POST',
      }),
    ]);
  });

  it('rejects sign in when the backend cannot verify the JWT', async () => {
    const token = makeJwt();
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: 'Authentication failed' }), {
        status: 401,
        headers: {
          'Content-Type': 'application/json',
        },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    renderWithProviders(
      <Routes>
        <Route path="/login" element={<LoginPage />} />
      </Routes>,
    );

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/jwt access token/i), token);
    await user.click(screen.getByRole('button', { name: /sign in with token/i }));

    await waitFor(() => expect(screen.getByText(/unable to verify jwt access token/i)).toBeInTheDocument());
    expect(sessionStorage.getItem('aep-token')).toBeNull();
    expect(sessionStorage.getItem('aep-session')).toBeNull();
  });

  it('clears auth state when signing out from the nav bar', async () => {
    const token = makeJwt();
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ roles: ['operator'], retrievedAt: new Date().toISOString() }), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
        },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    sessionStorage.setItem('aep-token', token);
    sessionStorage.setItem('aep-session', 'session-123');

    renderWithProviders(<NavBar />);

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /sign out/i }));

    expect(sessionStorage.getItem('aep-token')).toBeNull();
    expect(sessionStorage.getItem('aep-session')).toBeNull();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });
});
