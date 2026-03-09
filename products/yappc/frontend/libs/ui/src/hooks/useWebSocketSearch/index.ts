import { useWebSocketData, useWebSocket } from '@ghatana/yappc-crdt/websocket';
import { useState, useCallback, useEffect, useMemo, useRef } from 'react';

import { WebSocketSearchUtils } from './utils';

import type {
  UseWebSocketSearchReturn,
  WebSocketSearchOptions,
  SearchSuggestion,
  AvailableFilters,
} from './types';
import type { FilterCriteria, SearchResult } from '../../components/Search';

/**
 * WebSocket-integrated search hook for real-time search capabilities.
 *
 * Provides advanced search functionality with:
 * - Real-time search results via WebSocket connection
 * - Filter persistence to localStorage
 * - Cross-tab search state synchronization
 * - Debounced query handling
 * - Pagination support with loadMore capability
 *
 * @param projectId - The project identifier for scoped searches
 * @param options - Configuration options for search behavior
 * @returns Search state and action methods
 *
 * @example
 * const search = useWebSocketSearch('proj-123', {
 *   debounceMs: 300,
 *   maxResults: 50,
 *   persistFilters: true,
 * });
 *
 * return (
 *   <input
 *     value={search.query}
 *     onChange={(e) => search.setQuery(e.target.value)}
 *   />
 * );
 */
// eslint-disable-next-line max-lines-per-function
export function useWebSocketSearch(
  projectId: string,
  options: WebSocketSearchOptions = {}
): UseWebSocketSearchReturn {
  const {
    searchEndpoint = `/projects/${projectId}/search`,
    filterEndpoint = `/projects/${projectId}/filters`,
    suggestionsEndpoint = `/projects/${projectId}/suggestions`,
    debounceMs = 300,
    maxResults = 100,
    enableRealTimeUpdates = true,
    persistFilters = true,
  } = options;

  // WebSocket connection
  const { isConnected, send } = useWebSocket();

  // Search state
  const [query, setQuery] = useState('');
  const [criteria, setCriteria] = useState<FilterCriteria>(
    persistFilters ? WebSocketSearchUtils.loadPersistedFilters(projectId) : {}
  );
  const [results, setResults] = useState<SearchResult[]>([]);
  const [suggestions, setSuggestions] = useState<SearchSuggestion[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [currentOffset, setCurrentOffset] = useState(0);
  const [totalCount, setTotalCount] = useState(0);

  // Debounce timeout ref
  const debounceTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Real-time search results from WebSocket
  const searchData = useWebSocketData<{
    results: SearchResult[];
    totalCount: number;
    query: string;
    criteria: FilterCriteria;
    suggestions?: SearchSuggestion[];
  }>(`search:${projectId}`);

  // Real-time filter updates
  const filterData = useWebSocketData<{
    availableFilters: AvailableFilters;
    activeFilters: FilterCriteria;
  }>(`filters:${projectId}`);

  // Load persisted filters on mount
  useEffect(() => {
    if (persistFilters) {
      const saved = WebSocketSearchUtils.loadPersistedFilters(projectId);
      if (Object.keys(saved).length > 0) {
        setCriteria(saved);
      }
    }
  }, [projectId, persistFilters]);

  // Persist filters when they change
  useEffect(() => {
    if (persistFilters) {
      WebSocketSearchUtils.persistFilters(projectId, criteria);
    }
  }, [criteria, projectId, persistFilters]);

  // Update results from WebSocket data
  useEffect(() => {
    if (searchData?.data?.results) {
      setResults(searchData.data.results);
      setTotalCount(
        searchData.data.totalCount || searchData.data.results.length
      );

      if (searchData.data.suggestions) {
        setSuggestions(searchData.data.suggestions);
      }

      // Sync query and criteria if they came from other clients
      if (
        searchData.data.query !== undefined &&
        !WebSocketSearchUtils.areQueriesEqual(searchData.data.query, query)
      ) {
        setQuery(searchData.data.query);
      }
      if (
        searchData.data.criteria &&
        !WebSocketSearchUtils.areCriteriaEqual(
          searchData.data.criteria,
          criteria
        )
      ) {
        setCriteria(searchData.data.criteria);
      }
    }
  }, [searchData, query, criteria]);

  // Update filters from WebSocket data
  useEffect(() => {
    if (
      filterData?.data?.activeFilters &&
      !WebSocketSearchUtils.areCriteriaEqual(
        filterData.data.activeFilters,
        criteria
      )
    ) {
      setCriteria(filterData.data.activeFilters);
    }
  }, [filterData, criteria]);

  // Perform search with proper error handling
  const performSearch = useCallback(
    async (
      searchQuery?: string,
      searchCriteria?: FilterCriteria,
      offset = 0
    ) => {
      const finalQuery = searchQuery ?? query;
      const finalCriteria = searchCriteria ?? criteria;

      if (!isConnected) {
        setError('Not connected to server. Please check your connection.');
        return;
      }

      setIsLoading(true);
      setError(null);

      try {
        // Send search request via WebSocket
        send({
          type: 'search:request',
          payload: WebSocketSearchUtils.buildSearchPayload(
            projectId,
            finalQuery,
            finalCriteria,
            offset,
            maxResults,
            searchEndpoint
          ),
        });

        // Request suggestions if query is provided
        if (!WebSocketSearchUtils.isQueryEmpty(finalQuery)) {
          send({
            type: 'suggestions:request',
            payload: WebSocketSearchUtils.buildSuggestionsPayload(
              projectId,
              finalQuery,
              suggestionsEndpoint
            ),
          });
        }

        // Reset offset if this is a new search
        if (offset === 0) {
          setCurrentOffset(0);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Search failed');
        console.error('WebSocket search error:', err);
      } finally {
        setIsLoading(false);
      }
    },
    [
      query,
      criteria,
      isConnected,
      send,
      projectId,
      maxResults,
      searchEndpoint,
      suggestionsEndpoint,
    ]
  );

  // Load more results
  const loadMore = useCallback(async () => {
    if (isLoading || !hasMore) return;

    const newOffset = currentOffset + maxResults;
    await performSearch(query, criteria, newOffset);
    setCurrentOffset(newOffset);
  }, [isLoading, currentOffset, maxResults, performSearch, query, criteria]);

  // Clear search
  const clearSearch = useCallback(() => {
    setQuery('');
    setCriteria({});
    setResults([]);
    setSuggestions([]);
    setError(null);
    setCurrentOffset(0);
    setTotalCount(0);

    // Notify other clients via WebSocket
    if (isConnected) {
      send({
        type: 'search:clear',
        payload: { projectId },
      });
    }
  }, [isConnected, send, projectId]);

  // Handle query changes with debouncing
  const handleQueryChange = useCallback(
    (newQuery: string) => {
      setQuery(newQuery);

      // Clear previous debounce timeout
      if (debounceTimeoutRef.current) {
        clearTimeout(debounceTimeoutRef.current);
      }

      // Debounce search execution
      debounceTimeoutRef.current = setTimeout(() => {
        if (WebSocketSearchUtils.shouldPerformSearch(newQuery, criteria)) {
          performSearch(newQuery, criteria);
        }
      }, debounceMs);
    },
    [criteria, performSearch, debounceMs]
  );

  // Cleanup debounce timeout on unmount
  useEffect(() => {
    return () => {
      if (debounceTimeoutRef.current) {
        clearTimeout(debounceTimeoutRef.current);
      }
    };
  }, []);

  // Handle criteria changes
  const handleCriteriaChange = useCallback(
    (newCriteria: FilterCriteria) => {
      setCriteria(newCriteria);

      // Immediately search when filters change
      if (WebSocketSearchUtils.shouldPerformSearch(query, newCriteria)) {
        performSearch(query, newCriteria);
      }

      // Broadcast filter change to other clients
      if (isConnected) {
        send({
          type: 'filters:update',
          payload: WebSocketSearchUtils.buildFilterPayload(
            projectId,
            newCriteria,
            filterEndpoint
          ),
        });
      }
    },
    [query, performSearch, isConnected, send, projectId, filterEndpoint]
  );

  // Computed values
  const hasMore = useMemo(() => {
    return WebSocketSearchUtils.hasMoreResults(results.length, totalCount);
  }, [results.length, totalCount]);

  // Set up real-time search updates listener
  useEffect(() => {
    if (!enableRealTimeUpdates || !isConnected) return;

    // Listen for search result updates from other users
    const handleSearchUpdate = (event: Event): void => {
      const customEvent = event as CustomEvent<{
        projectId: string;
        results?: SearchResult[];
        totalCount?: number;
        query?: string;
        criteria?: FilterCriteria;
      }>;

      const data = customEvent.detail;

      if (data.projectId === projectId) {
        setResults(data.results || []);
        setTotalCount(data.totalCount || 0);

        if (data.query !== undefined) {
          setQuery(data.query);
        }
        if (data.criteria) {
          setCriteria(data.criteria);
        }
      }
    };

    // Listen for filter updates from other users
    const handleFilterUpdate = (event: Event): void => {
      const customEvent = event as CustomEvent<{
        projectId: string;
        criteria?: FilterCriteria;
      }>;

      const data = customEvent.detail;

      if (data.projectId === projectId && data.criteria) {
        setCriteria(data.criteria);
      }
    };

    // Set up event listeners
    window.addEventListener('websocket:search:updated', handleSearchUpdate);
    window.addEventListener('websocket:filters:updated', handleFilterUpdate);

    return () => {
      window.removeEventListener(
        'websocket:search:updated',
        handleSearchUpdate
      );
      window.removeEventListener(
        'websocket:filters:updated',
        handleFilterUpdate
      );
    };
  }, [enableRealTimeUpdates, isConnected, projectId]);

  return {
    // State
    query,
    criteria,
    results,
    suggestions,
    isLoading,
    error,
    resultsCount: results.length,
    hasMore,

    // Actions
    setQuery: handleQueryChange,
    setCriteria: handleCriteriaChange,
    performSearch,
    clearSearch,
    loadMore,
  };
}
