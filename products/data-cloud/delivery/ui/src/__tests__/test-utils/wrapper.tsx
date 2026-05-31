/**
 * Shared test wrapper providing all required React context providers.
 *
 * Wraps components under test with:
 * - Jotai Provider (atom state)
 * - React Query QueryClientProvider (server state, retry disabled)
 * - ThemeProvider (design-system theme context)
 * - BrowserRouter (routing)
 *
 * @doc.type test
 * @doc.purpose Shared provider wrapper for React component tests
 * @doc.layer frontend
 */
import { ThemeProvider } from "@ghatana/theme";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Provider } from "jotai";
import React from "react";
import { BrowserRouter } from "react-router";
import { OperationsProvider } from "../../contexts/OperationsContext";

/**
 * Creates a fresh QueryClient per test render.
 * Disables retries so failed queries surface immediately in tests.
 */
function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

export function TestWrapper({
  children,
}: {
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <Provider>
      <QueryClientProvider client={makeQueryClient()}>
        <ThemeProvider>
          <BrowserRouter>
            <OperationsProvider>
              <main>{children}</main>
            </OperationsProvider>
          </BrowserRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>
  );
}
