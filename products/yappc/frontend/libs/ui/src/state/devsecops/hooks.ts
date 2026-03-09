/**
 * DevSecOps Custom Hooks
 *
 * Convenience hooks for accessing DevSecOps state.
 *
 * @module state/devsecops/hooks
 */

import { useAtom, useAtomValue, useSetAtom } from 'jotai';

import {
  itemsAtom,
  phasesAtom,
  milestonesAtom,
  selectedItemAtom,
  viewModeAtom,
  searchQueryAtom,
  filterConfigAtom,
  currentPhaseAtom,
  sidePanelOpenAtom,
  loadingStatesAtom,
  filteredItemsAtom,
  itemsByStatusAtom,
  phaseStatisticsAtom,
  selectedItemIdsAtom,
  createItemAtom,
  updateItemAtom,
  deleteItemAtom,
  bulkUpdateItemsAtom,
  filterPanelOpenAtom,
  activePhaseIdAtom,
  sortConfigAtom,
  activePhaseAtom,
  activePhaseItemsAtom,
} from './atoms';

// ============================================================================ //
// Data Hooks
// ============================================================================ //

/**
 * Hook to access and manage all DevSecOps items.
 */
export function useItems() {
  return useAtom(itemsAtom);
}

/**
 * Hook to access and manage lifecycle phases.
 */
export function usePhases() {
  return useAtom(phasesAtom);
}

/**
 * Hook to access and manage milestones.
 */
export function useMilestones() {
  return useAtom(milestonesAtom);
}

// ============================================================================ //
// View / UI Hooks
// ============================================================================ //

/**
 * Hook for the currently selected item.
 */
export function useSelectedItem() {
  return useAtom(selectedItemAtom);
}

/**
 * Hook for the current view mode.
 */
export function useViewMode() {
  return useAtom(viewModeAtom);
}

/**
 * Hook for the search query.
 */
export function useSearchQuery() {
  return useAtom(searchQueryAtom);
}

/**
 * Hook for filter configuration.
 */
export function useFilterConfig() {
  return useAtom(filterConfigAtom);
}

/**
 * Hook for the active phase ID.
 */
export function useActivePhaseId() {
  return useAtom(activePhaseIdAtom);
}

/**
 * Hook for managing the current phase filter.
 */
export function useCurrentPhase() {
  return useAtom(currentPhaseAtom);
}

/**
 * Hook for managing the filter panel visibility.
 */
export function useFilterPanel() {
  return useAtom(filterPanelOpenAtom);
}

/**
 * Hook for managing selected item IDs (bulk actions).
 */
export function useSelectedItemIds() {
  return useAtom(selectedItemIdsAtom);
}

/**
 * Hook for side panel visibility.
 */
export function useSidePanelState() {
  return useAtom(sidePanelOpenAtom);
}

/**
 * Hook for loading state map.
 */
export function useLoadingStates() {
  return useAtom(loadingStatesAtom);
}

/**
 * Hook for individual loading state setter.
 */
export function useSetLoadingState() {
  const setLoadingStates = useSetAtom(loadingStatesAtom);

  return (key: string, value: boolean) => {
    setLoadingStates((current) => ({
      ...current,
      [key]: value,
    }));
  };
}

/**
 * Hook for the sort configuration.
 */
export function useSortConfig() {
  return useAtom(sortConfigAtom);
}

// ============================================================================ //
// Derived Hooks
// ============================================================================ //

/**
 * Hook returning filtered items (derived).
 */
export function useFilteredItems() {
  return useAtomValue(filteredItemsAtom);
}

/**
 * Hook returning items grouped by status.
 */
export function useItemsByStatus() {
  return useAtomValue(itemsByStatusAtom);
}

/**
 * Hook returning active phase statistics.
 */
export function usePhaseStatistics() {
  return useAtomValue(phaseStatisticsAtom);
}

/**
 * Hook returning the active phase object.
 */
export function useActivePhase() {
  return useAtomValue(activePhaseAtom);
}

/**
 * Hook returning items scoped to the active phase.
 */
export function useActivePhaseItems() {
  return useAtomValue(activePhaseItemsAtom);
}

// ============================================================================ //
// Mutation Hooks
// ============================================================================ //

/**
 * Hook to create new items.
 */
export function useCreateItem() {
  return useSetAtom(createItemAtom);
}

/**
 * Hook to update an item.
 */
export function useUpdateItem() {
  return useSetAtom(updateItemAtom);
}

/**
 * Hook to delete an item.
 */
export function useDeleteItem() {
  return useSetAtom(deleteItemAtom);
}

/**
 * Hook to bulk update items.
 */
export function useBulkUpdateItems() {
  return useSetAtom(bulkUpdateItemsAtom);
}
