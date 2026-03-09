import type { FilterCriteria, SearchResult } from '../../components/Search';

/**
 * A search suggestion item.
 *
 * @property id - Unique identifier for the suggestion
 * @property text - Display text for the suggestion
 * @property type - Category of suggestion (recent, suggested, or command)
 * @property icon - Optional icon identifier or URL
 * @property description - Optional description text
 */
export interface SearchSuggestion {
  id: string;
  text: string;
  type: 'recent' | 'suggestion' | 'command';
  icon?: string;
  description?: string;
}

/**
 * Available filters from the server.
 *
 * @property [key: string] - Filter key with array of available filter values
 */
export interface AvailableFilters {
  [key: string]: Array<string | number | boolean>;
}

/**
 * Configuration options for WebSocket-based search functionality.
 *
 * @property searchEndpoint - WebSocket endpoint for search requests
 * @property filterEndpoint - WebSocket endpoint for filter operations
 * @property suggestionsEndpoint - WebSocket endpoint for search suggestions
 * @property debounceMs - Debounce delay for search queries in milliseconds
 * @property maxResults - Maximum number of results to return per request
 * @property enableRealTimeUpdates - Whether to enable real-time updates from other clients
 * @property persistFilters - Whether to persist filter criteria to localStorage
 */
export interface WebSocketSearchOptions {
  searchEndpoint?: string;
  filterEndpoint?: string;
  suggestionsEndpoint?: string;
  debounceMs?: number;
  maxResults?: number;
  enableRealTimeUpdates?: boolean;
  persistFilters?: boolean;
}

/**
 * Current state of the WebSocket search.
 *
 * @property query - Current search query string
 * @property criteria - Active filter criteria
 * @property results - Array of search results
 * @property suggestions - Array of search suggestions (recent, suggested, or command)
 * @property isLoading - Whether a search is currently in progress
 * @property error - Error message if search failed, null otherwise
 * @property resultsCount - Number of results currently displayed
 * @property hasMore - Whether more results are available to load
 */
export interface WebSocketSearchState {
  query: string;
  criteria: FilterCriteria;
  results: SearchResult[];
  suggestions: SearchSuggestion[];
  isLoading: boolean;
  error: string | null;
  resultsCount: number;
  hasMore: boolean;
}

/**
 * Action methods for the WebSocket search hook.
 *
 * @property setQuery - Update the search query (with debouncing)
 * @property setCriteria - Update filter criteria
 * @property performSearch - Execute a search with optional query and filters
 * @property clearSearch - Clear all search state and notify other clients
 * @property loadMore - Load additional results from the server
 */
export interface WebSocketSearchActions {
  setQuery: (query: string) => void;
  setCriteria: (criteria: FilterCriteria) => void;
  performSearch: (query?: string, filters?: FilterCriteria) => Promise<void>;
  clearSearch: () => void;
  loadMore: () => Promise<void>;
}

/**
 * Complete return type of the useWebSocketSearch hook.
 * Combines state and action methods.
 */
export type UseWebSocketSearchReturn = WebSocketSearchState &
  WebSocketSearchActions;

/**
 * Configuration for a saved search.
 *
 * @property id - Unique identifier for the saved search
 * @property name - Human-readable name for the search
 * @property query - Search query string
 * @property criteria - Filter criteria used in the search
 * @property createdAt - Timestamp when the search was saved
 */
export interface SavedSearch {
  id: string;
  name: string;
  query: string;
  criteria: FilterCriteria;
  createdAt: Date;
}

/**
 * Return type for useSavedSearches hook.
 *
 * @property savedSearches - Array of saved searches
 * @property saveSearch - Function to save a new search
 * @property deleteSearch - Function to delete a saved search by ID
 */
export interface UseSavedSearchesReturn {
  savedSearches: SavedSearch[];
  saveSearch: (name: string, query: string, criteria: FilterCriteria) => void;
  deleteSearch: (id: string) => void;
}

/**
 * Configuration for unified search across multiple data types.
 * Extends WebSocketSearchOptions with type-specific filtering.
 */
export type UnifiedSearchOptions = WebSocketSearchOptions;

/**
 * Return type for useUnifiedSearch hook with type-specific search capability.
 *
 * @property searchByType - Search results filtered by data type
 */
export type UseUnifiedSearchReturn = UseWebSocketSearchReturn & {
  searchByType: (
    type: 'builds' | 'deployments' | 'logs' | 'issues' | 'all',
    query?: string,
    criteria?: FilterCriteria
  ) => Promise<void>;
};
