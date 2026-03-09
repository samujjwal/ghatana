import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";
import { useState, useCallback } from "react";

/**
 * Hook for full-text search with filters.
 */
export function useSearch(query: string, filters?: Record<string, string>) {
    return useQuery({
        queryKey: ["search", query, filters],
        queryFn: () => apiClient.search(query, filters),
        enabled: query.length >= 2
    });
}

/**
 * Hook for search autocomplete suggestions.
 */
export function useSearchSuggestions(query: string) {
    return useQuery({
        queryKey: ["searchSuggestions", query],
        queryFn: () => apiClient.getSearchSuggestions(query),
        enabled: query.length >= 2
    });
}

/**
 * Hook that combines search state with debouncing.
 */
export function useSearchState() {
    const [query, setQuery] = useState("");
    const [debouncedQuery, setDebouncedQuery] = useState("");
    const [filters, setFilters] = useState<Record<string, string>>({});

    // Debounce the query
    const updateQuery = useCallback((newQuery: string) => {
        setQuery(newQuery);

        // Simple debounce
        const timeout = setTimeout(() => {
            setDebouncedQuery(newQuery);
        }, 300);

        return () => clearTimeout(timeout);
    }, []);

    const searchResults = useSearch(debouncedQuery, filters);
    const suggestions = useSearchSuggestions(query);

    return {
        query,
        setQuery: updateQuery,
        filters,
        setFilters,
        results: searchResults.data,
        suggestions: suggestions.data?.suggestions ?? [],
        isLoading: searchResults.isLoading,
        isLoadingSuggestions: suggestions.isLoading
    };
}
