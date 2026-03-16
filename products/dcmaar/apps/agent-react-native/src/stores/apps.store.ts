/**
 * Apps Management Store - Jotai Atoms
 *
 * Manages app list state including:
 * - List of monitored applications
 * - App search, filtering, and sorting
 * - Multi-select app management
 * - App status and metadata
 * - App detail selection
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose App list and filtering state management
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';
import { guardianApi } from '../services/guardianApi';
import { authAtom } from './auth.store';

/**
 * Application object representing a monitored application.
 *
 * @interface App
 * @property {string} id - Unique application identifier
 * @property {string} name - Application display name
 * @property {string} packageName - Android package name (e.g., "com.android.chrome")
 * @property {boolean} isActive - Whether app is currently active/allowed
 * @property {string} [category] - App category (Browser, Communication, etc.)
 * @property {number} [usageTime] - Total usage time in minutes
 * @property {string} [lastUsed] - ISO timestamp of last usage
 * @property {number} [permissionCount] - Number of permissions granted
 * @property {string} [iconUrl] - URL to app icon (if available)
 * @property {Date} [installedDate] - Installation date
 */
export interface App {
  id: string;
  name: string;
  packageName: string;
  isActive: boolean;
  category?: string;
  usageTime?: number;
  lastUsed?: string;
  permissionCount?: number;
  iconUrl?: string;
  installedDate?: Date;
}

/**
 * App list state.
 *
 * @interface AppListState
 * @property {App[]} apps - Complete list of all monitored apps
 * @property {string[]} selectedAppIds - IDs of currently selected apps (multi-select mode)
 * @property {string} searchQuery - Current search query
 * @property {'all' | 'active' | 'inactive'} filterStatus - Active filter status
 * @property {'name' | 'usage' | 'recent'} sortBy - Current sort criterion
 * @property {boolean} multiSelectMode - Whether multi-select mode is enabled
 * @property {'idle' | 'loading' | 'loaded' | 'error'} status - Loading status
 * @property {string | null} error - Error message if load failed
 */
export interface AppListState {
  apps: App[];
  selectedAppIds: string[];
  searchQuery: string;
  filterStatus: 'all' | 'active' | 'inactive';
  sortBy: 'name' | 'usage' | 'recent';
  multiSelectMode: boolean;
  status: 'idle' | 'loading' | 'loaded' | 'error';
  error: string | null;
}

/**
 * Initial app list state.
 *
 * GIVEN: App initialization
 * WHEN: appsAtom is first accessed
 * THEN: App list starts empty with default filters and sorting
 */
const initialAppListState: AppListState = {
  apps: [],
  selectedAppIds: [],
  searchQuery: '',
  filterStatus: 'all',
  sortBy: 'name',
  multiSelectMode: false,
  status: 'idle',
  error: null,
};

/**
 * Core apps atom.
 *
 * Holds complete app list state including:
 * - All monitored applications
 * - Selection state
 * - Search/filter/sort preferences
 * - Loading and error state
 *
 * Usage (in components):
 * ```typescript
 * const [appListState, setAppListState] = useAtom(appsAtom);
 * ```
 */
export const appsAtom = atom<AppListState>(initialAppListState);

/**
 * Derived atom: Filtered and sorted app list.
 *
 * GIVEN: appsAtom with apps, search query, filter status, and sort preference
 * WHEN: filteredAppsAtom is read
 * THEN: Returns filtered, sorted list of apps matching all criteria
 *
 * GIVEN: searchQuery = "chrome", filterStatus = "all", sortBy = "name"
 * WHEN: filteredAppsAtom is read
 * THEN: Returns apps containing "chrome" in name/package, sorted by name
 *
 * Usage (in components):
 * ```typescript
 * const [filteredApps] = useAtom(filteredAppsAtom);
 * // Render filteredApps in list
 * ```
 */
export const filteredAppsAtom = atom<App[]>((get) => {
  const state = get(appsAtom);
  let result = [...state.apps];

  // Apply search filter
  if (state.searchQuery.trim()) {
    const query = state.searchQuery.toLowerCase();
    result = result.filter(
      (app) =>
        app.name.toLowerCase().includes(query) ||
        app.packageName.toLowerCase().includes(query)
    );
  }

  // Apply status filter
  if (state.filterStatus !== 'all') {
    result = result.filter(
      (app) =>
        (state.filterStatus === 'active' && app.isActive) ||
        (state.filterStatus === 'inactive' && !app.isActive)
    );
  }

  // Apply sorting
  if (state.sortBy === 'name') {
    result.sort((a, b) => a.name.localeCompare(b.name));
  } else if (state.sortBy === 'usage') {
    result.sort((a, b) => (b.usageTime || 0) - (a.usageTime || 0));
  } else if (state.sortBy === 'recent') {
    result.sort(
      (a, b) =>
        new Date(b.lastUsed || 0).getTime() -
        new Date(a.lastUsed || 0).getTime()
    );
  }

  return result;
});

/**
 * Derived atom: Total number of apps.
 *
 * GIVEN: appsAtom with app list
 * WHEN: appCountAtom is read
 * THEN: Returns total count of all monitored apps
 *
 * Usage (in components):
 * ```typescript
 * const [totalCount] = useAtom(appCountAtom);
 * // Display "Monitoring X apps"
 * ```
 */
export const appCountAtom = atom<number>((get) => {
  return get(appsAtom).apps.length;
});

/**
 * Derived atom: Currently selected apps.
 *
 * GIVEN: appsAtom with selectedAppIds and apps
 * WHEN: selectedAppsAtom is read
 * THEN: Returns array of app objects corresponding to selectedAppIds
 *
 * Usage (in components):
 * ```typescript
 * const [selectedApps] = useAtom(selectedAppsAtom);
 * // Show bulk action buttons if selectedApps.length > 0
 * ```
 */
export const selectedAppsAtom = atom<App[]>((get) => {
  const state = get(appsAtom);
  return state.apps.filter((app) => state.selectedAppIds.includes(app.id));
});

/**
 * Derived atom: Count of active apps.
 *
 * GIVEN: appsAtom with app list
 * WHEN: activeAppCountAtom is read
 * THEN: Returns count of apps where isActive === true
 *
 * Usage (in components):
 * ```typescript
 * const [activeCount] = useAtom(activeAppCountAtom);
 * // Display "X active, Y inactive"
 * ```
 */
export const activeAppCountAtom = atom<number>((get) => {
  return get(appsAtom).apps.filter((app) => app.isActive).length;
});

/**
 * Derived atom: Count of inactive apps.
 *
 * GIVEN: appsAtom with app list
 * WHEN: inactiveAppCountAtom is read
 * THEN: Returns count of apps where isActive === false
 *
 * Usage (in components):
 * ```typescript
 * const [inactiveCount] = useAtom(inactiveAppCountAtom);
 * ```
 */
export const inactiveAppCountAtom = atom<number>((get) => {
  return get(appsAtom).apps.filter((app) => !app.isActive).length;
});

/**
 * Action atom: Update search query.
 *
 * GIVEN: New search query string
 * WHEN: updateSearchAtom action is called
 * THEN: Updates appsAtom searchQuery, triggering filteredAppsAtom recalculation
 *
 * GIVEN: searchQuery = "", filterStatus = "all"
 * WHEN: updateSearchAtom is called with "chrome"
 * THEN: searchQuery updated, filtered list recalculated to show only Chrome-related apps
 *
 * Usage (in components):
 * ```typescript
 * const [, updateSearch] = useAtom(updateSearchAtom);
 * <TextInput onChangeText={(text) => updateSearch(text)} />
 * ```
 *
 * @param {string} query - New search query
 */
export const updateSearchAtom = atom<null, [string], void>(
  null,
  (get, set, query: string) => {
    const state = get(appsAtom);
    set(appsAtom, {
      ...state,
      searchQuery: query,
    });
  }
);

/**
 * Action atom: Update filter status.
 *
 * GIVEN: New filter status (all, active, or inactive)
 * WHEN: updateFilterAtom action is called
 * THEN: Updates filterStatus, triggering filtered list recalculation
 *
 * GIVEN: filterStatus = "all"
 * WHEN: updateFilterAtom is called with "active"
 * THEN: Only active apps displayed, filtered list recalculated
 *
 * Usage (in components):
 * ```typescript
 * const [, updateFilter] = useAtom(updateFilterAtom);
 * <TouchableOpacity onPress={() => updateFilter('active')} />
 * ```
 *
 * @param {'all' | 'active' | 'inactive'} status - New filter status
 */
export const updateFilterAtom = atom<null, ['all' | 'active' | 'inactive'], void>(
  null,
  (get, set, status: 'all' | 'active' | 'inactive') => {
    const state = get(appsAtom);
    set(appsAtom, {
      ...state,
      filterStatus: status,
    });
  }
);

/**
 * Action atom: Update sort criterion.
 *
 * GIVEN: New sort option (name, usage, or recent)
 * WHEN: updateSortAtom action is called
 * THEN: Updates sortBy, triggering filtered list recalculation
 *
 * GIVEN: sortBy = "name"
 * WHEN: updateSortAtom is called with "usage"
 * THEN: Apps sorted by usage time (descending), filtered list recalculated
 *
 * Usage (in components):
 * ```typescript
 * const [, updateSort] = useAtom(updateSortAtom);
 * <Picker selectedValue={sortBy} onValueChange={(value) => updateSort(value)} />
 * ```
 *
 * @param {'name' | 'usage' | 'recent'} sortBy - New sort criterion
 */
export const updateSortAtom = atom<null, ['name' | 'usage' | 'recent'], void>(
  null,
  (get, set, sortBy: 'name' | 'usage' | 'recent') => {
    const state = get(appsAtom);
    set(appsAtom, {
      ...state,
      sortBy,
    });
  }
);

/**
 * Action atom: Toggle app selection in multi-select mode.
 *
 * GIVEN: App ID and multi-select mode enabled
 * WHEN: toggleAppSelectionAtom is called
 * THEN: Adds app to selectedAppIds if not present, removes if already present
 *
 * GIVEN: selectedAppIds = ["1", "2"], toggleAppSelection called with "1"
 * WHEN: Action completes
 * THEN: selectedAppIds = ["2"]
 *
 * Usage (in components):
 * ```typescript
 * const [, toggleSelection] = useAtom(toggleAppSelectionAtom);
 * <TouchableOpacity onPress={() => toggleSelection(app.id)} />
 * ```
 *
 * @param {string} appId - ID of app to toggle
 */
export const toggleAppSelectionAtom = atom<null, [string], void>(
  null,
  (get, set, appId: string) => {
    const state = get(appsAtom);
    const isSelected = state.selectedAppIds.includes(appId);
    set(appsAtom, {
      ...state,
      selectedAppIds: isSelected
        ? state.selectedAppIds.filter((id) => id !== appId)
        : [...state.selectedAppIds, appId],
    });
  }
);

/**
 * Action atom: Toggle multi-select mode.
 *
 * GIVEN: Multi-select mode is currently off
 * WHEN: toggleMultiSelectModeAtom is called
 * THEN: Enables multi-select mode, clears selection
 *
 * GIVEN: Multi-select mode is on, selectedAppIds = ["1", "2"]
 * WHEN: toggleMultiSelectModeAtom is called
 * THEN: Disables multi-select, selectedAppIds cleared
 *
 * Usage (in components):
 * ```typescript
 * const [multiSelectMode, toggleMultiSelect] = useAtom(toggleMultiSelectModeAtom);
 * <TouchableOpacity onPress={() => toggleMultiSelect()} />
 * ```
 */
export const toggleMultiSelectModeAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const state = get(appsAtom);
    set(appsAtom, {
      ...state,
      multiSelectMode: !state.multiSelectMode,
      selectedAppIds: !state.multiSelectMode ? [] : state.selectedAppIds,
    });
  }
);

/**
 * Action atom: Bulk update app status.
 *
 * GIVEN: Array of app IDs and new status
 * WHEN: bulkUpdateStatusAtom action is called
 * THEN: Updates isActive status for all specified apps
 *
 * GIVEN: appIds = ["1", "2"], isActive = false
 * WHEN: bulkUpdateStatusAtom is called
 * THEN: Apps with IDs "1" and "2" marked as inactive
 *
 * Usage (in components):
 * ```typescript
 * const [, bulkUpdate] = useAtom(bulkUpdateStatusAtom);
 * <TouchableOpacity onPress={() => bulkUpdate(selectedAppIds, false)} />
 * ```
 *
 * @param {string[]} appIds - IDs of apps to update
 * @param {boolean} isActive - New active status
 */
export const bulkUpdateStatusAtom = atom<null, [string[], boolean], void>(
  null,
  (get, set, appIds: string[], isActive: boolean) => {
    const state = get(appsAtom);
    const updatedApps = state.apps.map((app) =>
      appIds.includes(app.id) ? { ...app, isActive } : app
    );
    set(appsAtom, {
      ...state,
      apps: updatedApps,
      selectedAppIds: [],
      multiSelectMode: false,
    });
  }
);

/**
 * Action atom: Delete apps.
 *
 * GIVEN: Array of app IDs to delete
 * WHEN: deleteAppsAtom action is called
 * THEN: Removes apps from list and clears selection
 *
 * GIVEN: apps has 10 items, deleteAppsAtom called with ["1", "2"]
 * WHEN: Action completes
 * THEN: apps list has 8 items, selectedAppIds cleared
 *
 * Usage (in components):
 * ```typescript
 * const [, deleteApps] = useAtom(deleteAppsAtom);
 * <TouchableOpacity onPress={() => deleteApps(selectedAppIds)} />
 * ```
 *
 * @param {string[]} appIds - IDs of apps to delete
 */
export const deleteAppsAtom = atom<null, [string[]], void>(
  null,
  (get, set, appIds: string[]) => {
    const state = get(appsAtom);
    set(appsAtom, {
      ...state,
      apps: state.apps.filter((app) => !appIds.includes(app.id)),
      selectedAppIds: [],
      multiSelectMode: false,
    });
  }
);

/**
 * Action atom: Load apps from API.
 *
 * GIVEN: App initialization or refresh requested
 * WHEN: fetchAppsAtom action is called
 * THEN: Sets status to loading, fetches apps from API, updates appsAtom
 *
 * GIVEN: Fetch succeeds with app list
 * WHEN: Promise resolves
 * THEN: appsAtom.apps updated, status set to 'loaded', error cleared
 *
 * GIVEN: Fetch fails (network error, API error)
 * WHEN: Promise rejects
 * THEN: status set to 'error', error message stored, apps unchanged
 *
 * Usage (in components):
 * ```typescript
 * const [, fetchApps] = useAtom(fetchAppsAtom);
 * useEffect(() => { fetchApps(); }, []);
 * ```
 *
 * @async
 * @returns {Promise<App[]>} Fetched app list
 * @throws {Error} If fetch fails
 */
export const fetchAppsAtom = atom<null, [], Promise<App[]>>(
  null,
  async (get, set) => {
    const state = get(appsAtom);
    set(appsAtom, {
      ...state,
      status: 'loading',
      error: null,
    });

    try {
      const { user } = get(authAtom);
      const tenantId = user?.tenantId ?? user?.id ?? '';

      const { data: appDataList } = await guardianApi.getApps(tenantId);

      // Map AppData → App
      const apps: App[] = appDataList.map((a) => ({
        id: a.id,
        name: a.name,
        packageName: a.packageName,
        isActive: a.isMonitored,
        category: a.category,
        usageTime: Math.round(a.usageTime / 60000), // ms → minutes
        lastUsed: a.lastSeen ? new Date(a.lastSeen).toISOString() : undefined,
        permissionCount: a.permissions.length,
      }));

      set(appsAtom, {
        ...state,
        apps,
        status: 'loaded',
        error: null,
      });

      return apps;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to fetch apps';
      set(appsAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });
      throw error;
    }
  }
);

/**
 * Action atom: Clear app error.
 *
 * GIVEN: Error message is displayed
 * WHEN: User dismisses error or tries again
 * THEN: clearAppErrorAtom clears the error message
 *
 * Usage (in components):
 * ```typescript
 * const [, clearError] = useAtom(clearAppErrorAtom);
 * <TouchableOpacity onPress={() => clearError()} />
 * ```
 */
export const clearAppErrorAtom = atom<null, [], void>(null, (get, set) => {
  const state = get(appsAtom);
  set(appsAtom, {
    ...state,
    error: null,
  });
});

/**
 * Action atom: Clear all selections and filters.
 *
 * GIVEN: Multi-select mode on, search active, filter applied
 * WHEN: clearAllSelectionsAtom is called
 * THEN: Clears selectedAppIds, searchQuery, resets filterStatus to 'all'
 *
 * Usage (in components):
 * ```typescript
 * const [, clearAll] = useAtom(clearAllSelectionsAtom);
 * <TouchableOpacity onPress={() => clearAll()} />
 * ```
 */
export const clearAllSelectionsAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const state = get(appsAtom);
    set(appsAtom, {
      ...state,
      selectedAppIds: [],
      searchQuery: '',
      filterStatus: 'all',
      multiSelectMode: false,
    });
  }
);
