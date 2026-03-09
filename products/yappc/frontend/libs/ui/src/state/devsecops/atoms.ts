/**
 * DevSecOps State Management - Jotai Atoms
 *
 * Global state atoms for the DevSecOps Canvas using Jotai.
 * Provides reactive state management with built-in async support.
 *
 * @module state/devsecops/atoms
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

import { devsecopsClient } from '@ghatana/yappc-api/devsecops/client';

import type {
  Item,
  ItemFilter,
  Milestone,
  SortConfig,
  ViewConfig,
  ViewMode,
} from '@ghatana/yappc-types/devsecops';


// ============================================================================
// Configuration Atoms
// ============================================================================

/**
 * Current view mode (canvas, kanban, timeline, table)
 */
export const viewModeAtom = atomWithStorage<ViewMode>('devsecops-view-mode', 'canvas');

/**
 * Active phase ID
 */
export const activePhaseIdAtom = atomWithStorage<string>('devsecops-active-phase', 'phase-3');

/**
 * Internal refresh token used to force item refetches after mutations.
 */
export const itemsRefreshAtom = atom(0);

/**
 * Selected item ID
 */
export const selectedItemIdAtom = atom<string | null>(null);

/**
 * Filter configuration
 */
export const filterConfigAtom = atom<ItemFilter>({});

/**
 * Sort configuration
 */
export const sortConfigAtom = atom<SortConfig>({
  field: 'updatedAt',
  direction: 'desc',
});

/**
 * View configuration (combines mode, filter, sort)
 */
export const viewConfigAtom = atom<ViewConfig>(
  (get) => ({
    mode: get(viewModeAtom),
    filter: get(filterConfigAtom),
    sort: get(sortConfigAtom),
  })
);

// ============================================================================
// Data Atoms (with async loaders)
// ============================================================================

/**
 * All phases - async atom
 */
export const phasesAtom = atom(async () => {
  const response = await devsecopsClient.getPhases();
  return response.data;
});

export const milestonesAtom = atom(async (): Promise<Milestone[]> => {
  const overview = await devsecopsClient.getOverview();
  return overview.data?.milestones ?? [];
});

/**
 * Active phase - derived from phasesAtom and activePhaseIdAtom
 */
export const activePhaseAtom = atom(async (get) => {
  const phases = await get(phasesAtom);
  const activeId = get(activePhaseIdAtom);
  return phases.find((p) => p.id === activeId) || phases[0];
});

/**
 * All items - async atom with filtering
 */
export const itemsAtom = atom(async (get) => {
  // Depend on refresh token so mutations can invalidate this atom.
  get(itemsRefreshAtom);
  const filter = get(filterConfigAtom);
  const response = await devsecopsClient.getItems(filter as ItemFilter);
  return response.data;
});

/**
 * Items for active phase
 */
export const activePhaseItemsAtom = atom(async (get) => {
  const items = await get(itemsAtom);
  const activePhaseId = get(activePhaseIdAtom);
  return items.filter((item) => item.phaseId === activePhaseId);
});

/**
 * Selected item - derived from itemsAtom and selectedItemIdAtom
 */
export const selectedItemAtom = atom(async (get) => {
  const items = await get(itemsAtom);
  const selectedId = get(selectedItemIdAtom);
  return selectedId ? items.find((item) => item.id === selectedId) || null : null;
});

/**
 * All KPIs
 */
export const kpisAtom = atom(async () => {
  const overview = await devsecopsClient.getOverview();
  return overview.data?.kpis ?? [];
});

/**
 * KPIs for active phase
 */
export const activePhaseKPIsAtom = atom(async (get) => {
  const activePhaseId = get(activePhaseIdAtom);
  const overview = await devsecopsClient.getOverview();
  const allKpis = overview.data?.kpis ?? [];
  if (!activePhaseId) return allKpis;
  return allKpis.filter((kpi) => kpi.phaseId === activePhaseId);
});

/**
 * Current user
 */
export const currentUserAtom = atom(async () => {
  const response = await devsecopsClient.getCurrentUser();
  return response.data;
});

/**
 * All users
 */
export const usersAtom = atom(async () => {
  const response = await devsecopsClient.getUsers();
  return response.data;
});

// ============================================================================
// UI State Atoms
// ============================================================================

/**
 * Side panel open state
 */
export const sidePanelOpenAtom = atom(false);

/**
 * Search query
 */
export const searchQueryAtom = atom('');

/**
 * Filter panel open state
 */
export const filterPanelOpenAtom = atom(false);

/**
 * Selected item IDs for bulk operations
 */
export const selectedItemIdsAtom = atom<Set<string>>(new Set<string>());

/**
 * Loading states
 */
export const loadingStatesAtom = atom<Record<string, boolean>>({});

// ============================================================================
// Derived/Computed Atoms
// ============================================================================

/**
 * Filtered and sorted items
 */
export const filteredItemsAtom = atom(async (get) => {
  let items = await get(itemsAtom);
  const sort = get(sortConfigAtom);
  const search = get(searchQueryAtom);

  // Apply search
  if (search) {
    const query = search.toLowerCase();
    items = items.filter(
      (item) =>
        item.title.toLowerCase().includes(query) ||
        item.description?.toLowerCase().includes(query) ||
        item.tags.some((tag) => tag.toLowerCase().includes(query))
    );
  }

  // Apply sort
  items.sort((a, b) => {
    const aVal = a[sort.field as keyof Item];
    const bVal = b[sort.field as keyof Item];

    if (typeof aVal === 'string' && typeof bVal === 'string') {
      return sort.direction === 'asc'
        ? aVal.localeCompare(bVal)
        : bVal.localeCompare(aVal);
    }

    if (typeof aVal === 'number' && typeof bVal === 'number') {
      return sort.direction === 'asc' ? aVal - bVal : bVal - aVal;
    }

    return 0;
  });

  return items;
});

/**
 * Items grouped by status (for Kanban view)
 */
export const itemsByStatusAtom = atom(async (get) => {
  const items = await get(activePhaseItemsAtom);

  return items.reduce(
    (acc, item) => {
      if (!acc[item.status]) {
        acc[item.status] = [];
      }
      acc[item.status].push(item);
      return acc;
    },
    {} as Record<string, Item[]>
  );
});

/**
 * Statistics for active phase
 */
export const phaseStatisticsAtom = atom(async (get) => {
  const items = await get(activePhaseItemsAtom);

  return {
    total: items.length,
    byStatus: {
      'not-started': items.filter((i) => i.status === 'not-started').length,
      'in-progress': items.filter((i) => i.status === 'in-progress').length,
      'in-review': items.filter((i) => i.status === 'in-review').length,
      completed: items.filter((i) => i.status === 'completed').length,
      blocked: items.filter((i) => i.status === 'blocked').length,
      archived: items.filter((i) => i.status === 'archived').length,
    },
    byPriority: {
      low: items.filter((i) => i.priority === 'low').length,
      medium: items.filter((i) => i.priority === 'medium').length,
      high: items.filter((i) => i.priority === 'high').length,
      critical: items.filter((i) => i.priority === 'critical').length,
    },
    averageProgress:
      items.reduce((sum, item) => sum + item.progress, 0) / items.length || 0,
  };
});

// ============================================================================
// Write Atoms (for mutations)
// ============================================================================

/**
 * Create new item atom
 */
export const createItemAtom = atom(
  null,
  async (_get, set, data: Partial<Item>) => {
    set(loadingStatesAtom, (prev) => ({ ...prev, createItem: true }));

    try {
      const response = await devsecopsClient.createItem(data);
      // Invalidate items cache so views refetch from API
      set(itemsRefreshAtom, (prev) => prev + 1);
      return response.data;
    } finally {
      set(loadingStatesAtom, (prev) => ({ ...prev, createItem: false }));
    }
  }
);

/**
 * Update item atom
 */
export const updateItemAtom = atom(
  null,
  async (_get, set, { id, data }: { id: string; data: Partial<Item> }) => {
    set(loadingStatesAtom, (prev) => ({ ...prev, [`updateItem-${id}`]: true }));

    try {
      const response = await devsecopsClient.updateItem(id, data);
      // Invalidate items cache so views refetch from API
      set(itemsRefreshAtom, (prev) => prev + 1);
      return response.data;
    } finally {
      set(loadingStatesAtom, (prev) => ({ ...prev, [`updateItem-${id}`]: false }));
    }
  }
);

/**
 * Delete item atom
 */
export const deleteItemAtom = atom(
  null,
  async (_get, set, itemId: string) => {
    set(loadingStatesAtom, (prev) => ({ ...prev, [`deleteItem-${itemId}`]: true }));

    try {
      await devsecopsClient.deleteItem(itemId);
      // Invalidate items cache so views refetch from API
      set(itemsRefreshAtom, (prev) => prev + 1);
    } finally {
      set(loadingStatesAtom, (prev) => ({ ...prev, [`deleteItem-${itemId}`]: false }));
    }
  }
);

/**
 * Bulk update items atom
 */
export const bulkUpdateItemsAtom = atom(
  null,
  async (_get, set, { itemIds, data }: { itemIds: string[]; data: Partial<Item> }) => {
    set(loadingStatesAtom, (prev) => ({ ...prev, bulkUpdate: true }));

    try {
      await devsecopsClient.bulkUpdateItems(itemIds, data);
      // Invalidate items cache so views refetch from API
      set(itemsRefreshAtom, (prev) => prev + 1);
    } finally {
      set(loadingStatesAtom, (prev) => ({ ...prev, bulkUpdate: false }));
    }
  }
);
