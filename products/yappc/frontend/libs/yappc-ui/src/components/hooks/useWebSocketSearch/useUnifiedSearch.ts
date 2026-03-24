import { useCallback } from 'react';

import { useWebSocketSearch } from './index';

import type { UseUnifiedSearchReturn, UnifiedSearchOptions } from './types';
import type { FilterCriteria } from '../../components/Search';

/**
 * Hook for unified search across multiple data types.
 *
 * Extends useWebSocketSearch with type-specific filtering capabilities.
 * Allows searching for specific resource types (builds, deployments, logs, issues) or all types.
 *
 * @param projectId - The project identifier for scoped searches
 * @param options - Configuration options for search behavior
 * @returns Extended search state with searchByType method
 *
 * @example
 * const search = useUnifiedSearch('proj-123');
 * await search.searchByType('builds', 'test-build');
 */
export function useUnifiedSearch(
  projectId: string,
  options: UnifiedSearchOptions = {}
): UseUnifiedSearchReturn {
  const search = useWebSocketSearch(projectId, options);

  // Enhanced search that can handle different data types
  const searchByType = useCallback(
    async (
      type: 'builds' | 'deployments' | 'logs' | 'issues' | 'all',
      query?: string,
      criteria?: FilterCriteria
    ) => {
      const enhancedCriteria: FilterCriteria = {
        ...criteria,
      };

      if (type !== 'all') {
        enhancedCriteria.type = [type];
      }

      await search.performSearch(query, enhancedCriteria);
    },
    [search]
  );

  return {
    ...search,
    searchByType,
  };
}
