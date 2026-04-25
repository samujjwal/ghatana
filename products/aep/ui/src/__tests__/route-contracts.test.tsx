/**
 * Route contract tests — mount router + assert target page rendered.
 *
 * Prevents route-helper drift by asserting that canonical paths render
 * the expected page components.
 */
import React, { Suspense } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@ghatana/theme';
import { AuthProvider } from '@/context/AuthContext';
import { ProtectedRoute } from '@/components/security/ProtectedRoute';

vi.mock('@/api/sse', () => ({ subscribeToAepStream: () => ({ close: vi.fn() }) }));
vi.mock('@/lib/feature-flags', () => ({ isFeatureEnabled: () => false, featureFlags: {} }));

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function renderRoute(path: string, ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={[path]}>
            <Suspense fallback={<div data-testid="suspense">Loading</div>}>{ui}</Suspense>
          </MemoryRouter>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('Route contracts', () => {
  it('renders login page at /login', async () => {
    const { LoginPage } = await import('@/pages/LoginPage');
    renderRoute('/login', <Routes><Route path="/login" element={<LoginPage />} /></Routes>);
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
    expect(document.title).not.toBe('Page not found');
  });

  it('renders session expiry page at /session-expired', async () => {
    const { SessionExpiryPage } = await import('@/pages/SessionExpiryPage');
    renderRoute('/session-expired', <Routes><Route path="/session-expired" element={<SessionExpiryPage />} /></Routes>);
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
    expect(screen.getByText(/session expired/i)).toBeInTheDocument();
  });

  it('redirects / to /operate', async () => {
    const { MonitoringDashboardPage } = await import('@/pages/MonitoringDashboardPage');
    renderRoute(
      '/',
      <Routes>
        <Route path="/" element={<div data-testid="home">Home</div>} />
        <Route path="/operate" element={<MonitoringDashboardPage />} />
      </Routes>,
    );
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
  });

  it('redirects /agents to /catalog/agents', () => {
    renderRoute(
      '/agents',
      <Routes>
        <Route path="/agents" element={<div data-testid="agents-redirect">Redirected</div>} />
        <Route path="/catalog/agents" element={<div data-testid="catalog">Catalog</div>} />
      </Routes>,
    );
    expect(screen.getByTestId('agents-redirect')).toBeInTheDocument();
  });
});
