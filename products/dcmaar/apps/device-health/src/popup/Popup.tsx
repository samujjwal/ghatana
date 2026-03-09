import 'webextension-polyfill';
import React from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PopupSummary } from './PopupSummary';
import '../styles/globals.css';

// Create QueryClient for the popup
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 5 * 60 * 1000,
    },
  },
});

// Ensure the root element exists
const container = document.getElementById('root');

// Only attempt to render if we're in a browser environment
if (container) {
  // Delay rendering to ensure React is fully initialized
  setTimeout(() => {
    try {
      const root = createRoot(container);
      root.render(
        <React.StrictMode>
          <QueryClientProvider client={queryClient}>
            <PopupSummary />
          </QueryClientProvider>
        </React.StrictMode>
      );
    } catch (error) {
      console.error('Error rendering popup:', error);
    }
  }, 0);
}

export {};
