/**
 * @doc.type test-suite
 * @doc.purpose Real router integration tests validating actual App.tsx route resolution
 * @doc.layer application
 * @doc.pattern Integration Test
 */

import { describe, it, expect, vi, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactNode } from 'react';
import { router } from '../router/routes';
import { App } from '../App';
import ErrorBoundary from '../components/ErrorBoundary';
import { MinimalThemeProvider } from '../providers/MinimalThemeProvider';

/**
 * Test fixture for mocking API client context
 */
interface TestContextFixture {
  queryClient: QueryClient;
  mockFetch: ReturnType<typeof vi.fn>;
}

const dashboardPayload = {
  profile: {
    displayName: 'Test Learner',
  },
  recommendedPathways: [],
  inProgressModules: [],
  suggestedModules: [],
  upcomingAssessments: [],
  recentActivity: [],
};

/**
 * Creates test context with mocked dependencies
 */
function createTestContext(): TestContextFixture {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        refetchOnWindowFocus: false,
      },
    },
  });

  const mockFetch = vi.fn();
  mockFetch.mockResolvedValue({
    ok: true,
    json: async () => dashboardPayload,
  });
  global.fetch = mockFetch;

  return { queryClient, mockFetch };
}

function renderWithProviders(
  ui: ReactNode,
  queryClient: QueryClient = new QueryClient()
): { container: HTMLElement; queryClient: QueryClient } {
  const { container } = render(
    <MinimalThemeProvider storageKey="tutorputor-theme-test">
      <QueryClientProvider client={queryClient}>
        <ErrorBoundary>
          {ui}
        </ErrorBoundary>
      </QueryClientProvider>
    </MinimalThemeProvider>
  );

  return { container, queryClient };
}

/**
 * Renders App with test context and returns utilities
 */
function renderAppWithRouter(): {
  container: HTMLElement;
  mockFetch: ReturnType<typeof vi.fn>;
  queryClient: QueryClient;
} {
  const { queryClient } = createTestContext();

  const { container } = renderWithProviders(<App />, queryClient);

  return { container, mockFetch: global.fetch as ReturnType<typeof vi.fn>, queryClient };
}

/**
 * Renders a specific route using memory router (for navigation testing)
 */
function renderRoute(
  initialPath: string
): {
  container: HTMLElement;
  queryClient: QueryClient;
  routerInstance: ReturnType<typeof createMemoryRouter>;
} {
  const { queryClient } = createTestContext();
  const routerInstance = createMemoryRouter(router.routes, {
    initialEntries: [initialPath],
  });

  const { container } = renderWithProviders(
    <RouterProvider router={routerInstance} />,
    queryClient
  );

  return { container, queryClient, routerInstance };
}

describe('Tutorputor App Router (Real Integration)', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders the learner shell on the root route', async () => {
    renderAppWithRouter();

    await waitFor(() => {
      expect(screen.getByRole('main')).toBeTruthy();
      expect(screen.getAllByRole('link', { name: /^dashboard$/i }).length).toBeGreaterThan(0);
      expect(screen.getAllByRole('link', { name: /learning paths/i }).length).toBeGreaterThan(0);
      expect(screen.getAllByRole('link', { name: /browse content/i }).length).toBeGreaterThan(0);
    });
  });

  it('redirects the retired /ai-tutor route into the canonical learner dashboard', async () => {
    const { routerInstance } = renderRoute('/ai-tutor');

    await waitFor(() => {
      expect(routerInstance.state.location.pathname).toBe('/dashboard');
      expect(screen.getByText(/floating AI tutor button/i)).toBeTruthy();
    });
  });

  it('redirects the retired /modules index to canonical search', async () => {
    const { routerInstance } = renderRoute('/modules');

    await waitFor(() => {
      expect(routerInstance.state.location.pathname).toBe('/search');
      expect(screen.getAllByRole('link', { name: /browse content/i }).length).toBeGreaterThan(0);
    });
  });

  it('renders the assessment detail start state for a dynamic assessment route', async () => {
    renderRoute('/assessments/assessment-123');

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /start assessment/i })).toBeTruthy();
    });
  });

  it('loads the simulation studio route through the lazy route boundary', async () => {
    renderRoute('/simulations/studio');

    await waitFor(
      () => {
        expect(screen.getByRole('heading', { name: /simulation studio/i })).toBeTruthy();
        expect(screen.getByRole('button', { name: /^save$/i })).toBeTruthy();
      },
      { timeout: 3000 }
    );
  });
});
