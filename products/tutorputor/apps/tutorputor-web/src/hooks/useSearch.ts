import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";
import { useEffect, useState } from "react";

/**
 * Hook for full-text search with filters.
 */
export function useSearch(query: string, filters?: Record<string, string>) {
  return useQuery({
    queryKey: ["search", query, filters],
    queryFn: () => apiClient.search(query, filters),
    enabled: query.length >= 2,
  });
}

/**
 * Hook for search autocomplete suggestions.
 */
export function useSearchSuggestions(query: string) {
  return useQuery({
    queryKey: ["searchSuggestions", query],
    queryFn: () => apiClient.getSearchSuggestions(query),
    enabled: query.length >= 2,
  });
}

export function useAssetRecommendations(assetId?: string, limit: number = 4) {
  return useQuery({
    queryKey: ["assetRecommendations", assetId, limit],
    queryFn: () => apiClient.getAssetRecommendations(assetId!, limit),
    enabled: Boolean(assetId),
  });
}

export function useAssetNextSteps(assetId?: string, limit: number = 4) {
  return useQuery({
    queryKey: ["assetNextSteps", assetId, limit],
    queryFn: () => apiClient.getNextSteps(assetId!, limit),
    enabled: Boolean(assetId),
  });
}

/**
 * Hook that combines search state with debouncing.
 */
export function useSearchState() {
  const [query, setQuery] = useState("");
  const [filters, setFilters] = useState<Record<string, string>>({});
  const debouncedQuery = useDebouncedValue(query, 300);

  const searchResults = useSearch(debouncedQuery, filters);
  const suggestions = useSearchSuggestions(query);

  return {
    query,
    setQuery,
    filters,
    setFilters,
    results: searchResults.data,
    suggestions: suggestions.data?.suggestions ?? [],
    isLoading: searchResults.isLoading,
    isLoadingSuggestions: suggestions.isLoading,
  };
}

export function useSearchStateWithInitialQuery(initialQuery: string) {
  const [query, setQuery] = useState(initialQuery);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const debouncedQuery = useDebouncedValue(query, 300);

  const searchResults = useSearch(debouncedQuery, filters);
  const suggestions = useSearchSuggestions(query);

  return {
    query,
    setQuery,
    filters,
    setFilters,
    results: searchResults.data,
    suggestions: suggestions.data?.suggestions ?? [],
    isLoading: searchResults.isLoading,
    isLoadingSuggestions: suggestions.isLoading,
  };
}

function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      setDebouncedValue(value);
    }, delayMs);

    return () => window.clearTimeout(timeout);
  }, [delayMs, value]);

  return debouncedValue;
}
