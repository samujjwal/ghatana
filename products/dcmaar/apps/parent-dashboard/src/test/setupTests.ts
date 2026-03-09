import '@testing-library/jest-dom';
import { QueryClient } from '@tanstack/react-query';
import React from 'react';

// Ensure RNTL flags if needed (keeps parity with RN tests, harmless in web)
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

// Create a global QueryClient for tests with no-op caching
const testQueryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      gcTime: 0, // React Query v5 uses gcTime instead of cacheTime
    },
    mutations: {
      retry: false,
    },
  },
});

// Ensure React is available globally for test code that might expect it
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore
globalThis.React = React;

// Export QueryClient for use in test wrappers
export { testQueryClient };
