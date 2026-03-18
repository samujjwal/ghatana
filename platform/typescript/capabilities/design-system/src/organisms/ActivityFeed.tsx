import React, { useState, useMemo, useCallback } from 'react';

/**
 * Configuration for statistics cards displayed above activity feed.
 *
 * @typeParam T - Activity item type
 * @doc.type interface
 * @doc.purpose Statistics card configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface StatCardConfig<T> {
  /** Card title */
  title: string;

  /** Function to calculate stat value from items */
  calculate: (items: T[]) => string | number;

  /** Card color variant */
  variant?: 'red' | 'orange' | 'yellow' | 'green' | 'blue' | 'purple' | 'gray';

  /** Optional subtitle */
  subtitle?: string;
}

/**
 * Configuration for table columns in activity feed.
 *
 * @typeParam T - Activity item type
 * @doc.type interface
 * @doc.purpose Table column configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface ColumnConfig<T> {
  /** Column header */
  header: string;

  /** Function to render cell content */
  render: (item: T) => React.ReactNode;

  /** Column width CSS class */
  width?: string;
}

/**
 * Configuration for filter inputs.
 *
 * @doc.type interface
 * @doc.purpose Filter configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface FilterConfig {
  /** Filter field name */
  name: string;

  /** Placeholder text */
  placeholder: string;

  /** Input type */
  type?: 'text' | 'select';

  /** Options for select inputs */
  options?: Array<{ label: string; value: string }>;
}

/**
 * Configuration for toast notifications.
 *
 * @typeParam T - Activity item type
 * @doc.type interface
 * @doc.purpose Toast notification configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface ToastConfig<T> {
  /** Toast title */
  title: string | ((item: T) => string);

  /** Toast message */
  message: (item: T) => string;

  /** Optional subtitle */
  subtitle?: (item: T) => string;

  /** Auto-hide duration in ms (default: 5000) */
  duration?: number;

  /** Icon component */
  icon?: React.ReactNode;

  /** Toast color variant */
  variant?: 'info' | 'success' | 'warning' | 'error';
}

/**
 * Props for ActivityFeed component.
 *
 * @typeParam T - Activity item type
 * @doc.type interface
 * @doc.purpose ActivityFeed props
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface ActivityFeedProps<T> {
  /** Activity items to display */
  items: T[];

  /** Statistics card configurations */
  statsCards?: StatCardConfig<T>[];

  /** Table column configurations */
  columns: ColumnConfig<T>[];

  /** Filter configurations */
  filters?: FilterConfig[];

  /** Function to filter items based on filter values */
  onFilter?: (items: T[], filterValues: Record<string, string>) => T[];

  /** Toast notification config */
  toastConfig?: ToastConfig<T>;

  /** Latest item for toast notification */
  latestItem?: T | null;

  /** Show toast notification */
  showToast?: boolean;

  /** Callback when toast is dismissed */
  onToastDismiss?: () => void;

  /** Empty state message */
  emptyMessage?: string;

  /** Feed title */
  title?: string;

  /** Table section title */
  tableTitle?: string;

  /** Custom CSS classes */
  className?: string;

  /** Enable reverse chronological order (newest first) */
  reverseOrder?: boolean;

  /** Item key extractor */
  keyExtractor?: (item: T, index: number) => string | number;
}

/**
 * Generic activity feed with real-time updates, statistics, and filtering.
 *
 * <p><b>Purpose</b><br>
 * Displays a real-time feed of activities/events with statistics cards,
 * filterable table view, and optional toast notifications for new items.
 * Designed for monitoring dashboards, audit logs, notification feeds, etc.
 *
 * <p><b>Features</b><br>
 * - Real-time toast notifications for new items
 * - Statistics cards with custom calculations
 * - Filterable activity table
 * - Responsive design with Tailwind
 * - Memoized filtering for performance
 * - Accessible with semantic HTML
 * - Customizable columns and stats
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Block event monitoring
 * <ActivityFeed
 *   items={blockEvents}
 *   title="Block Event Notifications"
 *   statsCards={[
 *     {
 *       title: 'Total Blocks',
 *       calculate: (items) => items.length,
 *       variant: 'red'
 *     }
 *   ]}
 *   columns={[
 *     {
 *       header: 'Item Blocked',
 *       render: (event) => event.blockedItem
 *     }
 *   ]}
 *   toastConfig={{
 *     title: 'Content Blocked',
 *     message: (event) => event.blockedItem
 *   }}
 * />
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Generic organism component for activity monitoring. Combines statistics
 * visualization, real-time notifications, and data tables into unified feed.
 *
 * <p><b>Thread Safety</b><br>
 * React component - not thread-safe. Use in React context only.
 *
 * @typeParam T - Activity item type
 * @param props - Component props
 *
 * @see StatCardConfig
 * @see ColumnConfig
 * @see ToastConfig
 * @doc.type class
 * @doc.purpose Real-time activity feed with statistics
 * @doc.layer platform
 * @doc.pattern Component
 */
export function ActivityFeed<T>({
  items,
  statsCards = [],
  columns,
  filters = [],
  onFilter,
  toastConfig,
  latestItem,
  showToast = false,
  onToastDismiss,
  emptyMessage = 'No activities yet. New items will appear here in real-time.',
  title = 'Activity Feed',
  tableTitle = 'Activity History',
  className = '',
  reverseOrder = true,
  keyExtractor = (_, index) => index,
}: ActivityFeedProps<T>): React.JSX.Element {
  const [filterValues, setFilterValues] = useState<Record<string, string>>(() =>
    filters.reduce((acc, f) => ({ ...acc, [f.name]: '' }), {})
  );

  /**
   * Filters items based on filter values.
   */
  const filteredItems = useMemo(() => {
    if (onFilter) {
      return onFilter(items, filterValues);
    }
    return items;
  }, [items, filterValues, onFilter]);

  /**
   * Handles filter input change.
   */
  const handleFilterChange = useCallback((name: string, value: string) => {
    setFilterValues((prev) => ({ ...prev, [name]: value }));
  }, []);

  /**
   * Clears all filters.
   */
  const clearFilters = useCallback(() => {
    setFilterValues(filters.reduce((acc, f) => ({ ...acc, [f.name]: '' }), {}));
  }, [filters]);

  /**
   * Checks if any filter is active.
   */
  const hasActiveFilters = useMemo(() => {
    return Object.values(filterValues).some((v) => v !== '');
  }, [filterValues]);

  /**
   * Gets variant-specific colors for stat cards.
   */
  const getVariantClasses = (variant: StatCardConfig<T>['variant'] = 'gray') => {
    const variants = {
      red: 'bg-red-50 text-red-600 text-red-900',
      orange: 'bg-orange-50 text-orange-600 text-orange-900',
      yellow: 'bg-yellow-50 text-yellow-600 text-yellow-900',
      green: 'bg-green-50 text-green-600 text-green-900',
      blue: 'bg-blue-50 text-blue-600 text-blue-900',
      purple: 'bg-purple-50 text-purple-600 text-purple-900',
      gray: 'bg-gray-50 text-gray-600 text-gray-900',
    };
    return variants[variant].split(' ');
  };

  /**
   * Gets variant-specific colors for toast.
   */
  const getToastVariantClasses = (variant: ToastConfig<T>['variant'] = 'info') => {
    const variants = {
      info: 'bg-blue-500',
      success: 'bg-green-500',
      warning: 'bg-yellow-500',
      error: 'bg-red-500',
    };
    return variants[variant];
  };

  /**
   * Items to display (optionally reversed).
   */
  const displayItems = useMemo(() => {
    return reverseOrder ? [...filteredItems].reverse() : filteredItems;
  }, [filteredItems, reverseOrder]);

  return (
    <div className={`space-y-6 ${className}`}>
      {/* Toast Notification */}
      {showToast && latestItem && toastConfig && (
        <div className="fixed top-4 right-4 z-50 animate-slide-in">
          <div
            className={`${getToastVariantClasses(toastConfig.variant)} text-white px-6 py-4 rounded-lg shadow-lg max-w-md`}
          >
            <div className="flex items-start">
              {toastConfig.icon && (
                <div className="flex-shrink-0">{toastConfig.icon}</div>
              )}
              {!toastConfig.icon && (
                <div className="flex-shrink-0">
                  <svg
                    className="h-6 w-6 text-white"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                    />
                  </svg>
                </div>
              )}
              <div className="ml-3 flex-1">
                <h3 className="text-sm font-medium">
                  {typeof toastConfig.title === 'function'
                    ? toastConfig.title(latestItem)
                    : toastConfig.title}
                </h3>
                <p className="mt-1 text-sm">{toastConfig.message(latestItem)}</p>
                {toastConfig.subtitle && (
                  <p className="mt-1 text-xs opacity-90">
                    {toastConfig.subtitle(latestItem)}
                  </p>
                )}
              </div>
              {onToastDismiss && (
                <button
                  onClick={onToastDismiss}
                  className="ml-4 flex-shrink-0 text-white hover:text-gray-200"
                  aria-label="Dismiss notification"
                >
                  <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">
                    <path
                      fillRule="evenodd"
                      d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                      clipRule="evenodd"
                    />
                  </svg>
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      <div className="bg-white shadow rounded-lg p-6">
        <h2 className="text-2xl font-bold text-gray-900 mb-4">{title}</h2>

        {/* Statistics Cards */}
        {statsCards.length > 0 && (
          <div className={`grid grid-cols-1 md:grid-cols-${Math.min(statsCards.length, 3)} gap-4 mb-6`}>
            {statsCards.map((card, index) => {
              const [bgClass, textClass, valueClass] = getVariantClasses(card.variant);
              const value = card.calculate(filteredItems);

              return (
                <div key={index} className={`${bgClass} rounded-lg p-4`}>
                  <h3 className={`text-sm font-medium ${textClass}`}>{card.title}</h3>
                  <p className={`text-3xl font-bold ${valueClass}`}>{value}</p>
                  {card.subtitle && (
                    <p className={`text-xs ${textClass} mt-1`}>{card.subtitle}</p>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* Filters */}
        {filters.length > 0 && (
          <>
            <div className={`grid grid-cols-1 md:grid-cols-${Math.min(filters.length, 3)} gap-4 mb-6`}>
              {filters.map((filter) => {
                if (filter.type === 'select' && filter.options) {
                  return (
                    <select
                      key={filter.name}
                      value={filterValues[filter.name] || ''}
                      onChange={(e) => handleFilterChange(filter.name, e.target.value)}
                      className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="">{filter.placeholder}</option>
                      {filter.options.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  );
                }

                return (
                  <input
                    key={filter.name}
                    type="text"
                    placeholder={filter.placeholder}
                    value={filterValues[filter.name] || ''}
                    onChange={(e) => handleFilterChange(filter.name, e.target.value)}
                    className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                );
              })}
            </div>

            {/* Clear Filters */}
            {hasActiveFilters && (
              <button
                onClick={clearFilters}
                className="mb-4 text-sm text-blue-600 hover:text-blue-800"
              >
                Clear all filters
              </button>
            )}
          </>
        )}

        {/* Activity Table */}
        <div className="space-y-3">
          <h3 className="text-lg font-semibold text-gray-900">{tableTitle}</h3>
          {filteredItems.length === 0 ? (
            <p className="text-gray-500 text-center py-8">{emptyMessage}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    {columns.map((column, index) => (
                      <th
                        key={index}
                        className={`px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider ${column.width || ''}`}
                      >
                        {column.header}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {displayItems.map((item, index) => (
                    <tr key={keyExtractor(item, index)} className="hover:bg-gray-50">
                      {columns.map((column, colIndex) => (
                        <td key={colIndex} className="px-6 py-4">
                          {column.render(item)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default ActivityFeed;
