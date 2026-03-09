/**
 * Semantic Search Hook
 *
 * React hook for AI-powered semantic search across items, documents, and knowledge base.
 * Supports hybrid search combining vector similarity with keyword matching.
 *
 * @module ai/hooks/useSemanticSearch
 * @doc.type hook
 * @doc.purpose Semantic and hybrid search
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import { AIAgentClientFactory } from '../agents';

/**
 * Search modes
 */
export type SearchMode = 'SEMANTIC' | 'TEXT' | 'HYBRID';

/**
 * Search result item
 */
export interface SearchResult {
  id: string;
  type: 'item' | 'document' | 'comment' | 'workflow' | 'knowledge';
  title: string;
  snippet: string;
  highlightedSnippet?: string;
  score: number;
  metadata: Record<string, unknown>;
  matchedTerms?: string[];
}

/**
 * Search facet
 */
export interface SearchFacet {
  field: string;
  values: Array<{
    value: string;
    count: number;
    selected?: boolean;
  }>;
}

/**
 * Search response
 */
export interface SearchResponse {
  results: SearchResult[];
  total: number;
  facets: SearchFacet[];
  queryTime: number;
  correctedQuery?: string;
  suggestions?: string[];
}

/**
 * Hook options
 */
export interface UseSemanticSearchOptions {
  /**
   * Workspace ID
   */
  workspaceId: string;

  /**
   * Collections to search
   */
  collections?: string[];

  /**
   * Search mode
   * @default 'HYBRID'
   */
  mode?: SearchMode;

  /**
   * Minimum relevance score (0-1)
   * @default 0.5
   */
  minScore?: number;

  /**
   * Results per page
   * @default 20
   */
  limit?: number;

  /**
   * API base URL
   * @default 'http://localhost:8080'
   */
  baseUrl?: string;

  /**
   * Debounce delay in milliseconds
   * @default 300
   */
  debounceMs?: number;
}

/**
 * Hook return type
 */
export interface UseSemanticSearchReturn {
  /**
   * Search query
   */
  query: string;

  /**
   * Set search query
   */
  setQuery: (query: string) => void;

  /**
   * Search results
   */
  results: SearchResult[];

  /**
   * Total number of results
   */
  total: number;

  /**
   * Available facets
   */
  facets: SearchFacet[];

  /**
   * Query suggestions
   */
  suggestions: string[];

  /**
   * Whether search is in progress
   */
  isSearching: boolean;

  /**
   * Error message if any
   */
  error: string | null;

  /**
   * Execute search with current query
   */
  search: (q?: string) => Promise<SearchResponse>;

  /**
   * Clear search results
   */
  clear: () => void;

  /**
   * Apply facet filter
   */
  applyFacet: (field: string, value: string) => void;

  /**
   * Remove facet filter
   */
  removeFacet: (field: string, value: string) => void;

  /**
   * Current active filters
   */
  activeFilters: Map<string, string[]>;

  /**
   * Load more results
   */
  loadMore: () => Promise<void>;

  /**
   * Whether more results are available
   */
  hasMore: boolean;
}

/**
 * Hook for semantic search
 *
 * @example
 * ```tsx
 * function SearchPanel({ workspaceId }: { workspaceId: string }) {
 *   const {
 *     query,
 *     setQuery,
 *     results,
 *     isSearching,
 *     facets,
 *     applyFacet,
 *   } = useSemanticSearch({
 *     workspaceId,
 *     mode: 'HYBRID',
 *   });
 *
 *   return (
 *     <div>
 *       <input
 *         value={query}
 *         onChange={e => setQuery(e.target.value)}
 *         placeholder="Search..."
 *       />
 *       {isSearching ? (
 *         <Spinner />
 *       ) : (
 *         <>
 *           <FacetPanel facets={facets} onSelect={applyFacet} />
 *           <ResultsList results={results} />
 *         </>
 *       )}
 *     </div>
 *   );
 * }
 * ```
 */
export function useSemanticSearch(
  options: UseSemanticSearchOptions
): UseSemanticSearchReturn {
  const {
    workspaceId,
    collections = [],
    mode = 'HYBRID',
    minScore = 0.5,
    limit = 20,
    baseUrl = import.meta.env.DEV
      ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}`
      : '',
    debounceMs = 300,
  } = options;

  const [query, setQueryState] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [total, setTotal] = useState(0);
  const [facets, setFacets] = useState<SearchFacet[]>([]);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeFilters, setActiveFilters] = useState<Map<string, string[]>>(
    new Map()
  );
  const [offset, setOffset] = useState(0);

  const factoryRef = useRef<AIAgentClientFactory | null>(null);
  const debounceRef = useRef<NodeJS.Timeout | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  // Initialize factory
  useEffect(() => {
    factoryRef.current = new AIAgentClientFactory({ baseUrl });
  }, [baseUrl]);

  // Execute search
  const executeSearch = useCallback(
    async (searchQuery: string, searchOffset = 0): Promise<SearchResponse> => {
      if (!factoryRef.current || !searchQuery.trim()) {
        return {
          results: [],
          total: 0,
          facets: [],
          queryTime: 0,
        };
      }

      // Cancel previous request
      if (abortRef.current) {
        abortRef.current.abort();
      }
      abortRef.current = new AbortController();

      setIsSearching(true);
      setError(null);

      try {
        const startTime = performance.now();

        // Build filters from active facets
        const filters: Record<string, unknown> = {};
        activeFilters.forEach((values, field) => {
          filters[field] = values;
        });

        // Call Java backend via HTTP (not using agent client for search to show alternative pattern)
        const response = await fetch(`${baseUrl}/api/v1/agents/search`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            query: searchQuery,
            searchMode: mode,
            workspaceId,
            collections,
            filters,
            limit,
            offset: searchOffset,
            minScore,
            includeFacets: true,
          }),
          signal: abortRef.current.signal,
        });

        if (!response.ok) {
          throw new Error(`Search failed: ${response.statusText}`);
        }

        const data = await response.json();
        const queryTime = performance.now() - startTime;

        const searchResults: SearchResult[] = (data.data?.results || []).map(
          (r: Record<string, unknown>) => ({
            id: r.id as string,
            type: (r.type as string) || 'item',
            title: r.title as string,
            snippet: r.snippet as string,
            highlightedSnippet: r.highlightedSnippet as string | undefined,
            score: r.score as number,
            metadata: (r.metadata as Record<string, unknown>) || {},
            matchedTerms: r.matchedTerms as string[] | undefined,
          })
        );

        const searchFacets: SearchFacet[] = (data.data?.facets || []).map(
          (f: Record<string, unknown>) => ({
            field: f.field as string,
            values: ((f.values as Record<string, unknown>[]) || []).map(
              (v) => ({
                value: v.value as string,
                count: v.count as number,
                selected: activeFilters
                  .get(f.field as string)
                  ?.includes(v.value as string),
              })
            ),
          })
        );

        const searchResponse: SearchResponse = {
          results: searchResults,
          total: data.data?.total || searchResults.length,
          facets: searchFacets,
          queryTime,
          correctedQuery: data.data?.correctedQuery,
          suggestions: data.data?.suggestions || [],
        };

        if (searchOffset === 0) {
          setResults(searchResults);
        } else {
          setResults((prev) => [...prev, ...searchResults]);
        }
        setTotal(searchResponse.total);
        setFacets(searchFacets);
        setSuggestions(searchResponse.suggestions || []);
        setOffset(searchOffset + searchResults.length);

        return searchResponse;
      } catch (err) {
        if ((err as Error).name === 'AbortError') {
          return { results: [], total: 0, facets: [], queryTime: 0 };
        }
        const errorMessage =
          err instanceof Error ? err.message : 'Search failed';
        setError(errorMessage);
        throw err;
      } finally {
        setIsSearching(false);
      }
    },
    [workspaceId, collections, mode, minScore, limit, baseUrl, activeFilters]
  );

  // Debounced query setter
  const setQuery = useCallback(
    (newQuery: string) => {
      setQueryState(newQuery);

      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }

      if (newQuery.trim()) {
        debounceRef.current = setTimeout(() => {
          setOffset(0);
          executeSearch(newQuery, 0);
        }, debounceMs);
      } else {
        setResults([]);
        setTotal(0);
        setFacets([]);
      }
    },
    [executeSearch, debounceMs]
  );

  const search = useCallback(
    async (q?: string): Promise<SearchResponse> => {
      const searchQuery = q ?? query;
      setOffset(0);
      return executeSearch(searchQuery, 0);
    },
    [query, executeSearch]
  );

  const clear = useCallback(() => {
    setQueryState('');
    setResults([]);
    setTotal(0);
    setFacets([]);
    setSuggestions([]);
    setActiveFilters(new Map());
    setOffset(0);
  }, []);

  const applyFacet = useCallback(
    (field: string, value: string) => {
      setActiveFilters((prev) => {
        const next = new Map(prev);
        const existing = next.get(field) || [];
        if (!existing.includes(value)) {
          next.set(field, [...existing, value]);
        }
        return next;
      });
      // Re-run search with new filters
      setOffset(0);
      executeSearch(query, 0);
    },
    [query, executeSearch]
  );

  const removeFacet = useCallback(
    (field: string, value: string) => {
      setActiveFilters((prev) => {
        const next = new Map(prev);
        const existing = next.get(field) || [];
        const updated = existing.filter((v) => v !== value);
        if (updated.length === 0) {
          next.delete(field);
        } else {
          next.set(field, updated);
        }
        return next;
      });
      // Re-run search with new filters
      setOffset(0);
      executeSearch(query, 0);
    },
    [query, executeSearch]
  );

  const loadMore = useCallback(async () => {
    if (!query || offset >= total) return;
    await executeSearch(query, offset);
  }, [query, offset, total, executeSearch]);

  const hasMore = offset < total;

  // Cleanup
  useEffect(() => {
    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
      if (abortRef.current) {
        abortRef.current.abort();
      }
    };
  }, []);

  return {
    query,
    setQuery,
    results,
    total,
    facets,
    suggestions,
    isSearching,
    error,
    search,
    clear,
    applyFacet,
    removeFacet,
    activeFilters,
    loadMore,
    hasMore,
  };
}
