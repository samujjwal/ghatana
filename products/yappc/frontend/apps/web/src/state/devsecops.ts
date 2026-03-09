import { atom, useAtomValue } from 'jotai';
import type {
  Item,
  Milestone,
  Phase,
  ActivityLog,
  PersonaDashboardSummary,
} from '@ghatana/yappc-types/devsecops';
import { createDevSecOpsOverview } from '@ghatana/yappc-types/devsecops/fixtures';
import { devsecopsClient } from '@ghatana/yappc-api/devsecops/client';
import {
  // Canvas/store-level hooks for shared UI state (view mode, filters, side panel, current phase)
  useViewManagement as useStoreViewManagement,
  useSearchQuery,
  useFilterConfig,
  useCurrentPhase as useStoreCurrentPhase,
  useSidePanel,
  useSelectedItem,
} from '@ghatana/yappc-store/devsecops';

/**
 * Logging utility for state management debugging.
 *
 * @doc.type utility
 * @doc.purpose State change tracking and debugging
 * @doc.layer state
 */
const createLogger = (name: string) => ({
  log: (message: string, data?: unknown) => {
    console.log(`[DevSecOps:${name}] ${message}`, data ?? '');
  },
  error: (message: string, error?: unknown) => {
    console.error(`[DevSecOps:${name}] ${message}`, error ?? '');
  },
  warn: (message: string, data?: unknown) => {
    console.warn(`[DevSecOps:${name}] ${message}`, data ?? '');
  },
});

// App-level atoms using canonical types
export const itemsAtom = atom<Item[]>([]);
export const phasesAtom = atom<Phase[]>([]);
export const milestonesAtom = atom<Milestone[]>([]);
export const activityAtom = atom<ActivityLog[]>([]);
export const personaDashboardsAtom = atom<PersonaDashboardSummary[]>([]);

// Derived Atoms
export const phasesWithItemsAtom = atom((get) => {
  const items = get(itemsAtom);
  return get(phasesAtom).map(phase => ({
    ...phase,
    items: items.filter(item => item.phaseId === phase.id)
  }));
});

// Hooks for canonical types
export const usePhases = (): Phase[] => useAtomValue(phasesWithItemsAtom);
export const useMilestones = () => useAtomValue(milestonesAtom);
export const useRecentActivity = (): ActivityLog[] => useAtomValue(activityAtom);
export const usePersonaDashboards = (): PersonaDashboardSummary[] => useAtomValue(personaDashboardsAtom);

// Delegate current phase management to shared store hook
export const useCurrentPhase = () => useStoreCurrentPhase();

export const usePhase = (phaseId: string) => {
  const phases = usePhases();
  return phases.find(phase => phase.id === phaseId);
};

export const usePhaseItems = (phaseId: string) => {
  const items = useAtomValue(itemsAtom);
  return items.filter(item => item.phaseId === phaseId);
};

export const useMilestoneItems = (milestoneId: string) => {
  const items = useAtomValue(itemsAtom);
  return items.filter(item => item.childrenIds?.includes(milestoneId)); // Adjust based on your data model
};

// KPI hooks calculated from items
export const useKpiStats = () => {
  const items = useAtomValue(itemsAtom);
  const totalItems = items.length;
  const completedItems = items.filter((item) => item.status === 'completed').length;
  const inProgressItems = items.filter((item) => item.status === 'in-progress').length;
  const blockedItems = items.filter((item) => item.status === 'blocked').length;

  return {
    completionRate:
      totalItems > 0 ? Math.round((completedItems / totalItems) * 100) : 0,
    inProgress: inProgressItems,
    blocked: blockedItems,
    onTrack:
      totalItems > 0 && completedItems / totalItems > 0.7 ? 'on-track' : 'at-risk',
  };
};

interface PhaseKpiStats {
  [key: string]: {
    total: number;
    completed: number;
    inProgress: number;
    blocked: number;
    progress: number;
  };
}

export const usePhaseKpiStats = (phaseId: string | 'all'): PhaseKpiStats => {
  const allItems = useAtomValue(itemsAtom);
  const phases = usePhases();

  if (phaseId === 'all') {
    // Return stats for all phases combined
    const allPhaseStats: PhaseKpiStats = {};

    phases.forEach((phase) => {
      const phaseItems = allItems.filter((item) => item.phaseId === phase.id);
      const total = phaseItems.length;
      const completed = phaseItems.filter((item) => item.status === 'completed').length;
      const inProgress = phaseItems.filter((item) => item.status === 'in-progress').length;
      const blocked = phaseItems.filter((item) => item.status === 'blocked').length;

      allPhaseStats[phase.id] = {
        total,
        completed,
        inProgress,
        blocked,
        progress: total > 0 ? Math.round((completed / total) * 100) : 0,
      };
    });

    return allPhaseStats;
  }

  // Return stats for a specific phase
  const phaseItems = allItems.filter((item) => item.phaseId === phaseId);
  const total = phaseItems.length;
  const completed = phaseItems.filter((item) => item.status === 'completed').length;
  const inProgress = phaseItems.filter((item) => item.status === 'in-progress').length;
  const blocked = phaseItems.filter((item) => item.status === 'blocked').length;

  return {
    [phaseId]: {
      total,
      completed,
      inProgress,
      blocked,
      progress: total > 0 ? Math.round((completed / total) * 100) : 0,
    },
  };
};

// View Management (delegates to shared store hooks)
export type ViewMode = 'kanban' | 'timeline' | 'table';

export const useViewManagement = () => {
  return useStoreViewManagement();
};

// Filter Management (delegates to shared store hooks, preserves logging)
export const useFilterManagement = () => {
  const logger = createLogger('FilterManagement');
  const [searchQuery, setSearchQuery] = useSearchQuery();
  const [filterConfig, setFilterConfig] = useFilterConfig();

  const wrappedSetSearchQuery = (query: string) => {
    logger.log(`Search query updated: "${query}"`);
    setSearchQuery(query);
  };

  const wrappedSetFilterConfig = (config: typeof filterConfig) => {
    logger.log('Filter config updated', config);
    setFilterConfig(config);
  };

  return {
    searchQuery,
    setSearchQuery: wrappedSetSearchQuery,
    filterConfig,
    setFilterConfig: wrappedSetFilterConfig,
  };
};

// Side Panel Management (delegates to shared store hooks, preserves logging API shape)
export const useSidePanelManagement = () => {
  const logger = createLogger('SidePanel');
  const [selectedItem, setSelectedItem] = useSelectedItem();
  const [sidePanelOpen, setSidePanelOpen] = useSidePanel();

  const openPanel = (item: typeof selectedItem) => {
    logger.log('Opening side panel', item);
    setSelectedItem(item);
    setSidePanelOpen(true);
  };

  const closePanel = () => {
    logger.log('Closing side panel');
    setSidePanelOpen(false);
  };

  return {
    selectedItem,
    sidePanelOpen,
    openPanel,
    closePanel,
  };
};

// Data Initialization from API
interface InitializeDataOptions {
  setPhases?: (phases: Phase[]) => void;
  setMilestones?: (milestones: Milestone[]) => void;
  setItems?: (items: Item[]) => void;
  setActivity?: (activity: ActivityLog[]) => void;
  setPersonaDashboards?: (dashboards: PersonaDashboardSummary[]) => void;
}

/**
 * Initialize DevSecOps data from the backend API.
 * Falls back to local fixtures only if API is unavailable.
 */
export const initializeDevSecOpsData = async ({
  setPhases,
  setMilestones,
  setItems,
  setActivity,
  setPersonaDashboards,
}: InitializeDataOptions = {}) => {
  const logger = createLogger('initializeDevSecOpsData');
  try {
    logger.log('Fetching DevSecOps data from API...');
    const { data: canonical } = await devsecopsClient.getOverview();

    if (!canonical) {
      throw new Error('DevSecOps overview response is empty');
    }

    // Use canonical types directly - no mapping needed
    setPhases?.(canonical.phases);
    setMilestones?.(canonical.milestones);
    setItems?.(canonical.items);
    setActivity?.(canonical.activity);
    setPersonaDashboards?.(canonical.personaDashboards || []);

    logger.log('DevSecOps data initialized successfully from API');
    return canonical;
  } catch (error) {
    logger.error('Failed to fetch data from API, falling back to fixtures', error);

    try {
      const fallback = createDevSecOpsOverview();

      // Populate atoms from local fixtures
      setPhases?.(fallback.phases);
      setMilestones?.(fallback.milestones);
      setItems?.(fallback.items);
      setActivity?.(fallback.activity);
      setPersonaDashboards?.(fallback.personaDashboards || []);

      logger.log('DevSecOps data initialized from local fixtures (API unavailable)');
      return fallback;
    } catch (fallbackError) {
      logger.error('Failed to initialize data from fixtures', fallbackError);
      throw fallbackError;
    }
  }
};

// Alias for backwards compatibility
export const initializeMockData = initializeDevSecOpsData;
