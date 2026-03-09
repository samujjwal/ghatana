/**
 * AppListContainer Component - Container for app list with filtering and sorting
 *
 * Features:
 * - Display list of apps with AppListItem components
 * - Search/filter by app name or package
 * - Sort by usage time, name, or status
 * - Multi-select support
 * - Empty state handling
 * - Loading state
 * - Pagination or infinite scroll
 *
 * Used as a primary container organism in dashboard and app management screens.
 *
 * @doc.type component
 * @doc.purpose Container organism for displaying and managing app lists
 * @doc.layer product
 * @doc.pattern Organism (Container Component)
 */

import React, { useMemo, useCallback, useState } from 'react';
import { cn } from '@ghatana/utils';
import { palette, lightColors, darkColors, fontSize, fontWeight, componentRadius } from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';
import { AppListItem, type AppMetadata } from '../molecules/AppListItem';
import { TextField } from '../atoms/TextField';

/**
 * Sort options for app list
 */
export type SortBy = 'name' | 'usage' | 'status' | 'recent';

/**
 * Filter options
 */
export interface AppListFilter {
  searchText?: string;
  statusFilter?: 'all' | 'active' | 'idle' | 'restricted' | 'blocked';
  sortBy?: SortBy;
  sortOrder?: 'asc' | 'desc';
}

/**
 * Props for AppListContainer component
 */
export interface AppListContainerProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Array of apps to display */
  apps: AppMetadata[];
  /** Currently selected app IDs */
  selectedAppIds?: string[];
  /** Whether to allow multi-select */
  multiSelect?: boolean;
  /** Callback when app is selected */
  onAppSelect?: (app: AppMetadata, isSelected: boolean) => void;
  /** Callback when app action is triggered */
  onAppAction?: (action: string, app: AppMetadata) => void;
  /** Show compact view */
  compact?: boolean;
  /** Show metadata (permissions, usage time) */
  showMetadata?: boolean;
  /** Loading state */
  isLoading?: boolean;
  /** Empty state message */
  emptyMessage?: string;
  /** Show search bar */
  showSearch?: boolean;
  /** Show sort/filter controls */
  showControls?: boolean;
  /** Initial filter state */
  initialFilter?: AppListFilter;
}

/**
 * Comparator function for sorting apps
 */
function createComparator(sortBy: SortBy, sortOrder: 'asc' | 'desc') {
  return (a: AppMetadata, b: AppMetadata) => {
    let comparison = 0;

    switch (sortBy) {
      case 'name':
        comparison = a.displayName.localeCompare(b.displayName);
        break;
      case 'usage':
        comparison = (b.usageTimeSeconds || 0) - (a.usageTimeSeconds || 0);
        break;
      case 'status':
        const statusOrder: Record<string, number> = {
          active: 0,
          idle: 1,
          restricted: 2,
          blocked: 3,
        };
        comparison =
          (statusOrder[a.status || 'idle'] || 10) -
          (statusOrder[b.status || 'idle'] || 10);
        break;
      case 'recent':
        comparison = (b.lastAccessedAt || 0) - (a.lastAccessedAt || 0);
        break;
      default:
        comparison = 0;
    }

    return sortOrder === 'asc' ? comparison : -comparison;
  };
}

/**
 * Filter apps based on criteria
 */
function filterApps(apps: AppMetadata[], filter: AppListFilter): AppMetadata[] {
  let filtered = apps;

  // Search filter
  if (filter.searchText) {
    const searchLower = filter.searchText.toLowerCase();
    filtered = filtered.filter(
      (app) =>
        app.displayName.toLowerCase().includes(searchLower) ||
        app.packageName.toLowerCase().includes(searchLower)
    );
  }

  // Status filter
  if (filter.statusFilter && filter.statusFilter !== 'all') {
    filtered = filtered.filter((app) => app.status === filter.statusFilter);
  }

  return filtered;
}

/**
 * AppListContainer - Organism for managing and displaying app lists
 */
export const AppListContainer = React.forwardRef<HTMLDivElement, AppListContainerProps>(
  (
    {
      apps,
      selectedAppIds = [],
      multiSelect = false,
      onAppSelect,
      onAppAction,
      compact = false,
      showMetadata = true,
      isLoading = false,
      emptyMessage = 'No apps found',
      showSearch = true,
      showControls = true,
      initialFilter = {},
      className,
      ...props
    },
    ref
  ) => {
    const { resolvedTheme } = useTheme();
    const isDark = resolvedTheme === 'dark';
    const surface = isDark ? darkColors : lightColors;

    // Filter and sort state
    const [filter, setFilter] = useState<AppListFilter>({
      searchText: initialFilter.searchText,
      statusFilter: initialFilter.statusFilter || 'all',
      sortBy: initialFilter.sortBy || 'name',
      sortOrder: initialFilter.sortOrder || 'asc',
    });

    // Filter and sort apps
    const processedApps = useMemo(() => {
      const filtered = filterApps(apps, filter);
      const sorted = [...filtered].sort(
        createComparator(filter.sortBy || 'name', filter.sortOrder || 'asc')
      );
      return sorted;
    }, [apps, filter]);

    // Handle app selection
    const handleSelectApp = useCallback(
      (app: AppMetadata) => {
        if (!onAppSelect) return;

        if (multiSelect) {
          const isSelected = selectedAppIds.includes(app.packageName);
          onAppSelect(app, !isSelected);
        } else {
          onAppSelect(app, !selectedAppIds.includes(app.packageName));
        }
      },
      [onAppSelect, multiSelect, selectedAppIds]
    );

    // Handle search input change
    const handleSearchChange = useCallback((value: string) => {
      setFilter((prev) => ({
        ...prev,
        searchText: value,
      }));
    }, []);

    // Handle sort change
    const handleSortChange = useCallback((sortBy: SortBy) => {
      setFilter((prev) => ({
        ...prev,
        sortBy,
        sortOrder: prev.sortBy === sortBy && prev.sortOrder === 'asc' ? 'desc' : 'asc',
      }));
    }, []);

    // Handle status filter change
    const handleStatusFilterChange = useCallback(
      (status: 'all' | 'active' | 'idle' | 'restricted' | 'blocked') => {
        setFilter((prev) => ({
          ...prev,
          statusFilter: status,
        }));
      },
      []
    );

    if (isLoading) {
      return (
        <div
          ref={ref}
          className="flex items-center justify-center py-12"
          style={{
            color: surface.text.primary,
          }}
        >
          <div className="animate-pulse flex flex-col items-center gap-2">
            <div
              className="w-8 h-8 rounded-full border-2 border-transparent"
              style={{
                borderTopColor: palette.primary[500],
                animation: 'spin 1s linear infinite',
              }}
            />
            <p style={{ fontSize: '13px', opacity: 0.7 }}>Loading apps...</p>
          </div>
        </div>
      );
    }

    return (
      <div
        ref={ref}
        className={cn('flex flex-col gap-4', className)}
        {...props}
      >
        {/* Search and Filter Controls */}
        {showSearch && (
          <div className="flex flex-col gap-3 pb-4 border-b" style={{ borderColor: surface.border }}>
            {/* Search bar */}
            <TextField
              placeholder="Search apps by name or package..."
              value={filter.searchText || ''}
              onChange={(e) => handleSearchChange(e.target.value)}
              className="w-full"
            />

            {/* Sort and filter buttons */}
            {showControls && (
              <div className="flex flex-wrap items-center gap-2">
                {/* Status filter */}
                <select
                  value={filter.statusFilter || 'all'}
                  onChange={(e) =>
                    handleStatusFilterChange(
                      e.target.value as
                      | 'all'
                      | 'active'
                      | 'idle'
                      | 'restricted'
                      | 'blocked'
                    )
                  }
                  className="px-3 py-1.5 rounded border text-sm"
                  style={{
                    backgroundColor: surface.background.paper,
                    borderColor: surface.border,
                    color: surface.text.primary,
                    fontSize: '12px',
                  }}
                >
                  <option value="all">All Status</option>
                  <option value="active">Active</option>
                  <option value="idle">Idle</option>
                  <option value="restricted">Restricted</option>
                  <option value="blocked">Blocked</option>
                </select>

                {/* Sort options */}
                <select
                  value={filter.sortBy || 'name'}
                  onChange={(e) => handleSortChange(e.target.value as SortBy)}
                  className="px-3 py-1.5 rounded border text-sm"
                  style={{
                    backgroundColor: surface.background.paper,
                    borderColor: surface.border,
                    color: surface.text.primary,
                    fontSize: '12px',
                  }}
                >
                  <option value="name">Sort by Name</option>
                  <option value="usage">Sort by Usage</option>
                  <option value="status">Sort by Status</option>
                  <option value="recent">Sort by Recent</option>
                </select>

                {/* Sort order toggle */}
                <button
                  onClick={() => handleSortChange(filter.sortBy || 'name')}
                  className="px-3 py-1.5 rounded border text-sm transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
                  style={{
                    backgroundColor: surface.background.paper,
                    borderColor: surface.border,
                    color: surface.text.primary,
                    fontSize: '12px',
                    cursor: 'pointer',
                  }}
                  title={filter.sortOrder === 'asc' ? 'Ascending' : 'Descending'}
                >
                  {filter.sortOrder === 'asc' ? '↑' : '↓'}
                </button>

                {/* Results count */}
                <p
                  className="text-xs ml-auto"
                  style={{
                    color: surface.text.primary,
                    opacity: 0.6,
                  }}
                >
                  {processedApps.length} app{processedApps.length !== 1 ? 's' : ''}
                </p>
              </div>
            )}
          </div>
        )}

        {/* App List */}
        {processedApps.length > 0 ? (
          <div className="flex flex-col gap-2">
            {processedApps.map((app) => (
              <AppListItem
                key={app.packageName}
                app={app}
                isSelected={selectedAppIds.includes(app.packageName)}
                onSelect={handleSelectApp}
                onAction={onAppAction}
                compact={compact}
                showMetadata={showMetadata}
                interactive
              />
            ))}
          </div>
        ) : (
          <div
            className="flex flex-col items-center justify-center py-12 px-4 rounded-lg"
            style={{
              borderRadius: `${componentRadius.panel}px`,
              backgroundColor: surface.background.paper,
              opacity: 0.6,
            }}
          >
            <p
              className="text-sm"
              style={{
                color: surface.text.primary,
                textAlign: 'center',
                fontSize: '13px',
              }}
            >
              {emptyMessage}
            </p>
          </div>
        )}

        {/* Results summary */}
        {processedApps.length > 0 && processedApps.length !== apps.length && (
          <p
            className="text-xs text-center"
            style={{
              color: surface.text.primary,
              opacity: 0.6,
              marginTop: '8px',
            }}
          >
            Showing {processedApps.length} of {apps.length} apps
          </p>
        )}
      </div>
    );
  }
);

AppListContainer.displayName = 'AppListContainer';

export default AppListContainer;
