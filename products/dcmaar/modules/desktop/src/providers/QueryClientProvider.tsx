import { QueryClient, QueryClientProvider as ReactQueryProvider } from '@tanstack/react-query';
import React, { useState } from 'react';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { isDev } from '../utils/env';

/**
 * Create a new QueryClient instance
 */
const createQueryClient = () => {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes
        refetchOnWindowFocus: false,
        retry: (failureCount, error: any) => {
          // Don't retry on 4xx errors, except for 408 (Request Timeout) and 429 (Too Many Requests)
          if (error?.response?.status >= 400 && error?.response?.status < 500) {
            if ([408, 429].includes(error.response.status)) {
              return failureCount < 3; // Retry on 408 and 429
            }
            return false; // Don't retry on other 4xx errors
          }

          // Retry on 5xx errors and network errors
          return failureCount < 3;
        },
      },
      mutations: {
        retry: (failureCount, error: any) => {
          // Don't retry mutations on 4xx errors
          if (error?.response?.status >= 400 && error?.response?.status < 500) {
            return false;
          }
          return failureCount < 3;
        },
      },
    },
  });
};

interface QueryClientProviderProps {
  children: React.ReactNode;
}

/**
 * QueryClientProvider with React Query Devtools in development
 */
export const QueryClientProvider = ({ children }: QueryClientProviderProps) => {
  const [queryClient] = useState(createQueryClient);

  return (
    <ReactQueryProvider client={queryClient}>
      {children as any}
      {isDev && <ReactQueryDevtools initialIsOpen={false} position="bottom" />}
    </ReactQueryProvider>
  );
};

export default QueryClientProvider;
