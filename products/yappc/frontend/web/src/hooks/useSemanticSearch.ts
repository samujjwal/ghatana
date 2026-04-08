/**
 * Semantic Search Hook
 *
 * React hook for AI-powered semantic search across all content types.
 * Provides intelligent search results with fuzzy and semantic matching.
 *
 * @doc.type hook
 * @doc.purpose Semantic search hook
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  semanticSearch,
  indexDocuments,
  getHighlights,
  type SearchDocument,
  type SearchResult,
  type SemanticSearchRequest,
} from '../services/search/SearchService';

// ============================================================================
// Types
// ============================================================================

export interface UseSemanticSearchOptions {
  documents: SearchDocument[];
  enabled?: boolean;
  debounceMs?: number;
  limit?: number;
  threshold?: number;
}

export interface UseSemanticSearchResult {
  query: string;
  setQuery: (query: string) => void;
  results: SearchResult[];
  isLoading: boolean;
  error: Error | null;
  searchTime: number;
  totalResults: number;
  highlightQuery: (content: string) => string[];
  clearQuery: () => void;
  refresh: () => void;
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useSemanticSearch({
  documents,
  enabled = true,
  debounceMs = 300,
  limit = 10,
  threshold = 0.3,
}: UseSemanticSearchOptions): UseSemanticSearchResult {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');

  // Index documents on mount
  const { mutate: indexDocs } = useMutation({
    mutationFn: indexDocuments,
  });

  // Perform semantic search
  const {
    data: searchResponse,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['semantic-search', query, documents],
    queryFn: () =>
      semanticSearch({
        query,
        documents,
        limit,
        threshold,
      } as SemanticSearchRequest),
    enabled: enabled && query.length > 0,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  const results = searchResponse?.results || [];
  const searchTime = searchResponse?.metadata.searchTime || 0;
  const totalResults = searchResponse?.metadata.totalResults || 0;

  // Highlight query in content
  const highlightQuery = useCallback(
    (content: string): string[] => {
      return getHighlights(query, content);
    },
    [query]
  );

  // Clear query
  const clearQuery = useCallback(() => {
    setQuery('');
  }, []);

  // Index documents on mount
  useMemo(() => {
    if (documents.length > 0) {
      indexDocs(documents);
    }
  }, [documents, indexDocs]);

  return {
    query,
    setQuery,
    results,
    isLoading,
    error,
    searchTime,
    totalResults,
    highlightQuery,
    clearQuery,
    refresh: refetch,
  };
}
