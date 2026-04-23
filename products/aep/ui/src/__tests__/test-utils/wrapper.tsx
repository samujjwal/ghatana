import React from 'react';
import { BrowserRouter } from 'react-router';
import { Provider, createStore } from 'jotai';

type Store = ReturnType<typeof createStore>;
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@ghatana/theme';

function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

export function createAepTestWrapper(store?: Store): React.FC<{ children: React.ReactNode }> {
  const queryClient = makeQueryClient();
  const jotaiStore = store ?? createStore();

  return function AepTestWrapper({ children }: { children: React.ReactNode }): React.ReactElement {
    return (
      <Provider store={jotaiStore}>
        <QueryClientProvider client={queryClient}>
          <ThemeProvider>
            <BrowserRouter>{children}</BrowserRouter>
          </ThemeProvider>
        </QueryClientProvider>
      </Provider>
    );
  };
}