import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSearchStateWithInitialQuery } from "../useSearch";

vi.mock("../../api/tutorputorClient", () => ({
    apiClient: {
        search: vi.fn().mockResolvedValue({ results: [], total: 0 }),
        getSearchSuggestions: vi.fn().mockResolvedValue({ suggestions: [] }),
    },
}));

import { apiClient } from "../../api/tutorputorClient";

const mockSearch = vi.mocked(apiClient.search);
const mockGetSearchSuggestions = vi.mocked(apiClient.getSearchSuggestions);

function createWrapper() {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: {
                retry: false,
                gcTime: 0,
            },
        },
    });

    return function Wrapper({ children }: { children: ReactNode }) {
        return (
            <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        );
    };
}

describe("useSearchStateWithInitialQuery", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockSearch.mockResolvedValue({ results: [], total: 0 } as any);
        mockGetSearchSuggestions.mockResolvedValue({ suggestions: [] } as any);
    });

    it("debounces search and only queries with the latest value", async () => {
        const { result } = renderHook(() => useSearchStateWithInitialQuery(""), {
            wrapper: createWrapper(),
        });

        act(() => {
            result.current.setQuery("phy");
            result.current.setQuery("physics");
        });

        expect(mockSearch).not.toHaveBeenCalled();

        await act(async () => {
            await new Promise((resolve) => window.setTimeout(resolve, 350));
        });

        await waitFor(() => {
            expect(mockSearch).toHaveBeenCalledWith("physics", {});
        });
    });

    it("requests suggestions immediately for the current query", async () => {
        const { result } = renderHook(() => useSearchStateWithInitialQuery(""), {
            wrapper: createWrapper(),
        });

        act(() => {
            result.current.setQuery("algebra");
        });

        await waitFor(() => {
            expect(mockGetSearchSuggestions).toHaveBeenCalledWith("algebra");
        });
    });
});