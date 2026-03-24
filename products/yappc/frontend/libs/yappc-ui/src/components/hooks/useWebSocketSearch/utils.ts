import type { SavedSearch } from './types';
import type { FilterCriteria } from '../../components/Search';

/**
 * Utility class for WebSocket search operations.
 * Provides static helper methods for search state management and transformations.
 */
export class WebSocketSearchUtils {
  /**
   * Load persisted filters from localStorage.
   *
   * @param projectId - The project identifier
   * @returns Parsed filter criteria or empty object if not found
   * @throws Logs warning if stored data is invalid JSON
   *
   * @example
   * const filters = WebSocketSearchUtils.loadPersistedFilters('proj-123');
   */
  static loadPersistedFilters(projectId: string): FilterCriteria {
    const saved = localStorage.getItem(`yappc-search-filters-${projectId}`);
    if (!saved) {
      return {};
    }

    try {
      return JSON.parse(saved);
    } catch (e) {
      console.warn('Failed to parse saved filters:', e);
      return {};
    }
  }

  /**
   * Persist filter criteria to localStorage.
   *
   * @param projectId - The project identifier
   * @param criteria - Filter criteria to persist
   *
   * @example
   * WebSocketSearchUtils.persistFilters('proj-123', { status: ['active'] });
   */
  static persistFilters(projectId: string, criteria: FilterCriteria): void {
    localStorage.setItem(
      `yappc-search-filters-${projectId}`,
      JSON.stringify(criteria)
    );
  }

  /**
   * Check if two filter criteria objects are equal.
   *
   * @param criteria1 - First filter criteria
   * @param criteria2 - Second filter criteria
   * @returns true if criteria are equivalent, false otherwise
   *
   * @example
   * const same = WebSocketSearchUtils.areCriteriaEqual(old, new);
   */
  static areCriteriaEqual(
    criteria1: FilterCriteria,
    criteria2: FilterCriteria
  ): boolean {
    return JSON.stringify(criteria1) === JSON.stringify(criteria2);
  }

  /**
   * Check if two queries are equal (case-insensitive trimmed comparison).
   *
   * @param query1 - First query string
   * @param query2 - Second query string
   * @returns true if queries are equivalent, false otherwise
   *
   * @example
   * const same = WebSocketSearchUtils.areQueriesEqual('  test  ', 'test');
   */
  static areQueriesEqual(query1: string, query2: string): boolean {
    return query1.trim().toLowerCase() === query2.trim().toLowerCase();
  }

  /**
   * Calculate if there are more results available.
   *
   * @param resultsLength - Current number of results
   * @param totalCount - Total number of available results
   * @returns true if more results exist, false otherwise
   *
   * @example
   * const hasMore = WebSocketSearchUtils.hasMoreResults(10, 50);
   */
  static hasMoreResults(resultsLength: number, totalCount: number): boolean {
    return resultsLength < totalCount;
  }

  /**
   * Build WebSocket search request payload.
   *
   * @param projectId - The project identifier
   * @param query - Search query string
   * @param criteria - Filter criteria
   * @param offset - Pagination offset
   * @param maxResults - Maximum results limit
   * @param searchEndpoint - The search endpoint path
   * @returns Formatted search request payload
   *
   * @example
   * const payload = WebSocketSearchUtils.buildSearchPayload('proj-123', 'test', {}, 0, 100, '/search');
   */
  static buildSearchPayload(
    projectId: string,
    query: string,
    criteria: FilterCriteria,
    offset: number,
    maxResults: number,
    searchEndpoint: string
  ): {
    projectId: string;
    query: string;
    criteria: FilterCriteria;
    offset: number;
    limit: number;
    endpoint: string;
  } {
    return {
      projectId,
      query,
      criteria,
      offset,
      limit: maxResults,
      endpoint: searchEndpoint,
    };
  }

  /**
   * Build WebSocket suggestions request payload.
   *
   * @param projectId - The project identifier
   * @param query - Search query string
   * @param suggestionsEndpoint - The suggestions endpoint path
   * @returns Formatted suggestions request payload
   *
   * @example
   * const payload = WebSocketSearchUtils.buildSuggestionsPayload('proj-123', 'test', '/suggestions');
   */
  static buildSuggestionsPayload(
    projectId: string,
    query: string,
    suggestionsEndpoint: string
  ): {
    projectId: string;
    query: string;
    endpoint: string;
  } {
    return {
      projectId,
      query,
      endpoint: suggestionsEndpoint,
    };
  }

  /**
   * Build WebSocket filter update payload.
   *
   * @param projectId - The project identifier
   * @param criteria - Filter criteria to broadcast
   * @param filterEndpoint - The filter endpoint path
   * @returns Formatted filter update payload
   *
   * @example
   * const payload = WebSocketSearchUtils.buildFilterPayload('proj-123', { status: ['active'] }, '/filters');
   */
  static buildFilterPayload(
    projectId: string,
    criteria: FilterCriteria,
    filterEndpoint: string
  ): {
    projectId: string;
    criteria: FilterCriteria;
    endpoint: string;
  } {
    return {
      projectId,
      criteria,
      endpoint: filterEndpoint,
    };
  }

  /**
   * Load saved searches from localStorage.
   *
   * @param projectId - The project identifier
   * @returns Array of saved searches
   * @throws Logs warning if stored data is invalid JSON
   *
   * @example
   * const searches = WebSocketSearchUtils.loadSavedSearches('proj-123');
   */
  static loadSavedSearches(projectId: string): SavedSearch[] {
    const saved = localStorage.getItem(`yappc-saved-searches-${projectId}`);
    if (!saved) {
      return [];
    }

    try {
      return JSON.parse(saved);
    } catch (e) {
      console.warn('Failed to parse saved searches:', e);
      return [];
    }
  }

  /**
   * Persist saved searches to localStorage.
   *
   * @param projectId - The project identifier
   * @param searches - Array of searches to persist
   *
   * @example
   * WebSocketSearchUtils.persistSavedSearches('proj-123', searches);
   */
  static persistSavedSearches(
    projectId: string,
    searches: SavedSearch[]
  ): void {
    localStorage.setItem(
      `yappc-saved-searches-${projectId}`,
      JSON.stringify(searches)
    );
  }

  /**
   * Create a new saved search object.
   *
   * @param name - Human-readable name for the search
   * @param query - Search query string
   * @param criteria - Filter criteria
   * @returns New SavedSearch object
   *
   * @example
   * const search = WebSocketSearchUtils.createSavedSearch('Active Issues', 'status:active', { type: ['issue'] });
   */
  static createSavedSearch(
    name: string,
    query: string,
    criteria: FilterCriteria
  ): SavedSearch {
    return {
      id: `search-${Date.now()}`,
      name,
      query,
      criteria,
      createdAt: new Date(),
    };
  }

  /**
   * Check if a query is empty (trimmed and no content).
   *
   * @param query - Query string to check
   * @returns true if query is empty, false otherwise
   *
   * @example
   * const isEmpty = WebSocketSearchUtils.isQueryEmpty('   ');
   */
  static isQueryEmpty(query: string): boolean {
    return query.trim().length === 0;
  }

  /**
   * Check if there are any active filter criteria.
   *
   * @param criteria - Filter criteria object to check
   * @returns true if criteria has at least one filter, false otherwise
   *
   * @example
   * const hasFilters = WebSocketSearchUtils.hasActiveCriteria({ status: ['active'] });
   */
  static hasActiveCriteria(criteria: FilterCriteria): boolean {
    return Object.keys(criteria).length > 0;
  }

  /**
   * Should perform search check - determines if search should execute.
   *
   * @param query - Current search query
   * @param criteria - Current filter criteria
   * @returns true if either query is non-empty or criteria has filters, false otherwise
   *
   * @example
   * if (WebSocketSearchUtils.shouldPerformSearch(query, criteria)) {
   *   performSearch();
   * }
   */
  static shouldPerformSearch(query: string, criteria: FilterCriteria): boolean {
    return !this.isQueryEmpty(query) || this.hasActiveCriteria(criteria);
  }
}
