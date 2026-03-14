/**
 * Flashit Web App Main Entry Point
 * Sets up React Query, Jotai, and routing
 */

import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';
import { ErrorBoundary } from '@ghatana/design-system';
import App from './App';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <JotaiProvider>
          <App />
        </JotaiProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  </React.StrictMode>,
);
