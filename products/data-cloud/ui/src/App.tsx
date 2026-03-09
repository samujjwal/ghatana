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
import { routes } from './routes';
import './styles/globals.css';

/**
 * TanStack Query client configuration
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

/**
 * Create the router instance
 */
const router = createBrowserRouter(routes);

/**
 * Loading screen component for initial app load
 */
function LoadingScreen(): React.ReactElement {
  return (
    <div className="flex items-center justify-center w-full h-screen bg-gray-50 dark:bg-gray-900">
      <div className="text-center">
        <div className="inline-block">
          <div className="w-12 h-12 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin" />
        </div>
        <p className="mt-4 text-gray-600 dark:text-gray-400">Loading Data Cloud...</p>
      </div>
    </div>
  );
}

/**
 * App component
 *
 * Provides the application shell with all necessary providers:
 * - QueryClientProvider for TanStack Query
 * - Jotai Provider for state management
 * - ThemeProvider for dark/light mode
 * - RouterProvider for React Router v7
 *
 * @returns JSX element
 */
export function App(): React.ReactElement {
  return (
    <QueryClientProvider client={queryClient}>
      <Provider>
        <ThemeProvider>
          <React.Suspense fallback={<LoadingScreen />}>
            <RouterProvider router={router} />
          </React.Suspense>
          <ToastProvider />
        </ThemeProvider>
      </Provider>
    </QueryClientProvider>
  );
}

export default App;
