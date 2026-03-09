import { type ReactNode } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "@/state/queryClient";

/**
 * TanStack Query provider wrapper
 *
 * <p><b>Purpose</b><br>
 * Wraps application with TanStack Query client for server state management.
 * Provides cache, retry logic, and synchronization across components.
 *
 * <p><b>Configuration</b><br>
 * Uses queryClient from src/state/queryClient.ts with:
 * - staleTime: 5 minutes
 * - gcTime: 10 minutes
 * - retry: 1 with exponential backoff
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <QueryProvider>
 *   <App />
 * </QueryProvider>
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose TanStack Query provider
 * @doc.layer product
 * @doc.pattern Provider
 */
export function QueryProvider({ children }: { children: ReactNode }) {
    return (
        <QueryClientProvider client={queryClient}>
            {children}
        </QueryClientProvider>
    );
}

export default QueryProvider;
