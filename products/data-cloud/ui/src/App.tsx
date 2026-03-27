/**
 * Root Application Component
 *
 * Main application entry point using React Router v7 framework mode.
 * Sets up providers for state management, theming, and routing.
 *
 * @doc.type component
 * @doc.purpose Root application component
 * @doc.layer frontend
 */

import React from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { Provider } from 'jotai';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@ghatana/theme';
import { ToastProvider } from './components/common/Toast';
import { AppErrorBoundary } from './components/common/AppErrorBoundary';
import { LoadingState } from './components/common/LoadingState';
import { routes } from './routes';
import './styles/globals.css';

// ─────────────────────────────────────────────────────────────────────────────
// Query Client Configuration
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Cache duration constants — named so they are self-documenting at usage sites.
 * Per-query overrides should be set in the individual `useQuery` calls.
 */
const CACHE_TIMES = {
  /** Default: data that changes infrequently (collections, schemas, plugins). */
  DEFAULT_STALE_MS: 5 * 60 * 1000,   // 5 minutes
  /** Medium: data that changes every few minutes (cost analysis, learning signals). */
  MEDIUM_STALE_MS: 2 * 60 * 1000,    // 2 minutes
  /** Short-lived: frequently-mutated data (alerts, jobs, workflow status). */
  SHORT_STALE_MS: 30 * 1000,          // 30 seconds
  /** Live: near-real-time data (execution status, health metrics). */
  LIVE_STALE_MS: 5 * 1000,            // 5 seconds
  /** Static: essentially immutable reference data (enums, definitions). */
  STATIC_STALE_MS: 30 * 60 * 1000,   // 30 minutes
} as const;

export { CACHE_TIMES };

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: CACHE_TIMES.DEFAULT_STALE_MS,
      retry: 1,
      refetchOnWindowFocus: false,
      // Garbage-collect unused queries after 10 minutes.
      gcTime: 10 * 60 * 1000,
    },
    mutations: {
      retry: 0,
    },
  },
});

// ─────────────────────────────────────────────────────────────────────────────
// Router
// ─────────────────────────────────────────────────────────────────────────────

const router = createBrowserRouter(routes);

// ─────────────────────────────────────────────────────────────────────────────
// App
// ─────────────────────────────────────────────────────────────────────────────

/**
 * App component
 *
 * Provides the application shell with all necessary providers:
 * - AppErrorBoundary   — global crash guard
 * - QueryClientProvider — TanStack Query
 * - Jotai Provider     — atom-based state management
 * - ThemeProvider      — dark/light mode
 * - RouterProvider     — React Router v7
 *
 * @returns JSX element
 */
export function App(): React.ReactElement {
  return (
    <AppErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <Provider>
          <ThemeProvider>
            <React.Suspense
              fallback={
                <LoadingState
                  message="Loading Data Cloud..."
                  className="w-full h-screen"
                  size="lg"
                />
              }
            >
              <RouterProvider router={router} />
            </React.Suspense>
            <ToastProvider />
          </ThemeProvider>
        </Provider>
      </QueryClientProvider>
    </AppErrorBoundary>
  );
}

export default App;
