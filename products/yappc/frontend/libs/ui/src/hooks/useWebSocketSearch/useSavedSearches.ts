import { useState, useCallback, useEffect } from 'react';

import { WebSocketSearchUtils } from './utils';

import type { SavedSearch, UseSavedSearchesReturn } from './types';
import type { FilterCriteria } from '../../components/Search';

/**
 * Hook for managing saved searches with localStorage persistence.
 *
 * Provides functionality to save, retrieve, and delete search queries and filter configurations.
 * All saved searches are persisted to localStorage scoped by project.
 *
 * @param projectId - The project identifier for scoped saved searches
 * @returns Object with saved searches and save/delete methods
 *
 * @example
 * const { savedSearches, saveSearch, deleteSearch } = useSavedSearches('proj-123');
 *
 * const handleSave = () => {
 *   saveSearch('Active Builds', 'status:active', { type: ['builds'] });
 * };
 *
 * return (
 *   <>
 *     {savedSearches.map(search => (
 *       <button key={search.id} onClick={() => deleteSearch(search.id)}>
 *         {search.name}
 *       </button>
 *     ))}
 *   </>
 * );
 */
export function useSavedSearches(projectId: string): UseSavedSearchesReturn {
  const [savedSearches, setSavedSearches] = useState<SavedSearch[]>([]);

  // Load saved searches on mount
  useEffect(() => {
    const searches = WebSocketSearchUtils.loadSavedSearches(projectId);
    setSavedSearches(searches);
  }, [projectId]);

  /**
   * Save a new search configuration.
   * Adds to existing saved searches and persists to localStorage.
   */
  const saveSearch = useCallback(
    (name: string, query: string, criteria: FilterCriteria) => {
      const newSearch = WebSocketSearchUtils.createSavedSearch(
        name,
        query,
        criteria
      );

      const updated = [...savedSearches, newSearch];
      setSavedSearches(updated);
      WebSocketSearchUtils.persistSavedSearches(projectId, updated);
    },
    [savedSearches, projectId]
  );

  /**
   * Delete a saved search by ID.
   * Removes from saved searches and updates localStorage.
   */
  const deleteSearch = useCallback(
    (id: string) => {
      const updated = savedSearches.filter((s) => s.id !== id);
      setSavedSearches(updated);
      WebSocketSearchUtils.persistSavedSearches(projectId, updated);
    },
    [savedSearches, projectId]
  );

  return {
    savedSearches,
    saveSearch,
    deleteSearch,
  };
}
