import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import DashboardPage from './DashboardPage';

// Create a new query client instance
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 5 * 60 * 1000, // 5 minutes
    },
  },
});

// This wrapper ensures the DashboardPage has all required providers
export const DashboardPageWrapper = () => (
  <QueryClientProvider client={queryClient}>
    <DashboardPage />
  </QueryClientProvider>
);

export default DashboardPageWrapper;
