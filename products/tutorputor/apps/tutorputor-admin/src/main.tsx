import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { ErrorBoundary } from '@ghatana/design-system';
import { MinimalThemeProvider } from './providers/MinimalThemeProvider';
import { router } from './App';
import './index.css';

// Suppress expected 404 errors in development
if (import.meta.env.DEV) {
  const originalError = console.error;
  console.error = (...args: any[]) => {
    // Suppress fetch failed messages for expected 404s
    if (
      args[0]?.toString?.().includes('Failed') ||
      args[0]?.toString?.().includes('404')
    ) {
      return;
    }
    originalError(...args);
  };
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      retry: 1,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <MinimalThemeProvider storageKey="tutorputor-theme">
          <RouterProvider router={router} />
        </MinimalThemeProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  </React.StrictMode>
);
