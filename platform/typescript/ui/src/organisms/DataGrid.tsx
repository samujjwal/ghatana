import React, { useState, useMemo, useCallback } from 'react';

/**
 * Configuration for statistics cards displayed above data grid.
 *
 * @typeParam T - Data item type
 * @doc.type interface
 * @doc.purpose Statistics card configuration for data grids
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface DataGridStatConfig<T> {
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
 * Configuration for displaying items in grid/list mode.
 *
 * @typeParam T - Data item type
 * @doc.type interface
 * @doc.purpose Item rendering configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface ItemRendererConfig<T> {
  /** Custom renderer for grid/list item */
  render: (item: T) => React.ReactNode;

  /** CSS classes for item container */
  itemClassName?: string;
}

/**
 * Configuration for table columns.
 *
 * @typeParam T - Data item type
 * @doc.type interface
 * @doc.purpose Table column configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface DataGridColumnConfig<T> {
  /** Column header (preferred). */
  header?: string;

  /** Legacy/MUI-like header alias. */
  headerName?: string;

  /** Field key for simple value access (legacy/MUI-like). */
  field?: string;

  /** Function to render cell content (preferred). */
  render?: (item: T) => React.ReactNode;

  /** Legacy/MUI-like cell renderer. */
  renderCell?: (params: { value: unknown; row: T; field: string }) => React.ReactNode;

  /** Column width CSS class (preferred) or numeric width (legacy). */
  width?: string | number;
}

/**
 * Configuration for filter inputs.
 *
 * @doc.type interface
 * @doc.purpose Filter configuration for data grids
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface DataGridFilterConfig {
  /** Filter field name */
  name: string;

  /** Placeholder text */
  placeholder: string;

  /** Input type */
  type?: 'text' | 'select';

  /** Options for select inputs */
  options?: Array<{ label: string; value: string }>;

  /** Label for the filter */
  label?: string;
}

/**
 * Configuration for CRUD actions.
 *
 * @typeParam T - Data item type
 * @doc.type interface
 * @doc.purpose CRUD action configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface CrudConfig<T> {
  /** Enable create action */
  onCreate?: () => void;

  /** Enable edit action */
  onEdit?: (item: T) => void;

  /** Enable delete action */
  onDelete?: (item: T) => void;

  /** Custom create button text */
  createButtonText?: string;

  /** Custom create button icon */
  createButtonIcon?: React.ReactNode;

  /** Show edit button per item */
  showEditButton?: boolean;

  /** Show delete button per item */
  showDeleteButton?: boolean;

  /** Edit button label */
  editButtonLabel?: string;

  /** Delete button label */
  deleteButtonLabel?: string;
}

/**
 * Props for DataGrid component.
 *
 * @typeParam T - Data item type
 * @doc.type interface
 * @doc.purpose DataGrid props
 * @doc.layer platform
 * @doc.pattern Configuration
 */
export interface DataGridProps<T> {
  /** Data items to display */
  items?: T[];

  /** Legacy alias for `items` (MUI-style). */
  rows?: T[];

  /** Display mode */
  displayMode?: 'table' | 'grid' | 'list';

  /** Statistics card configurations */
  statsCards?: DataGridStatConfig<T>[];

  /** Table column configurations (for table mode) */
  columns?: DataGridColumnConfig<T>[];

  /** Legacy pagination prop (accepted for compatibility; not fully implemented). */
  pageSize?: number;

  /** Item renderer (for grid/list mode) */
  itemRenderer?: ItemRendererConfig<T>;

  /** Filter configurations */
  filters?: DataGridFilterConfig[];

  /** Function to filter items based on filter values */
  onFilter?: (items: T[], filterValues: Record<string, string>) => T[];

  /** CRUD action configurations */
  crudConfig?: CrudConfig<T>;

  /** Empty state message */
  emptyMessage?: string;

  /** Loading state */
  loading?: boolean;

  /** Loading message */
  loadingMessage?: string;

  /** Grid title */
  title?: string;

  /** Custom CSS classes */
  className?: string;

  /** Item key extractor */
  keyExtractor?: (item: T, index: number) => string | number;

  /** Grid layout columns (for grid mode) */
  gridCols?: {
    base?: number;
    md?: number;
    lg?: number;
    xl?: number;
  };
}

/**
 * Generic data grid with multiple display modes and CRUD operations.
 *
 * <p><b>Purpose</b><br>
 * Flexible data display component supporting table, grid, and list views
 * with built-in statistics, filtering, and CRUD operations. Ideal for
 * management dashboards, resource listings, and data tables.
 *
 * <p><b>Features</b><br>
 * - Three display modes: table, grid, list
 * - Statistics cards with custom calculations
 * - Filterable data (text and select filters)
 * - CRUD operations (create, edit, delete)
 * - Responsive design with Tailwind
 * - Memoized filtering for performance
 * - Loading states
 * - Empty states
 * - Customizable item rendering
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Device management with grid display
 * <DataGrid
 *   items={devices}
 *   displayMode="grid"
 *   statsCards={[
 *     {
 *       title: 'Total Devices',
 *       calculate: (items) => items.length,
 *       variant: 'blue'
 *     }
 *   ]}
 *   itemRenderer={{
 *     render: (device) => (
 *       <DeviceCard device={device} />
 *     )
 *   }}
 *   filters={[
 *     { name: 'search', placeholder: 'Search devices...' },
 *     { 
 *       name: 'status', 
 *       type: 'select',
 *       options: [
 *         { label: 'All', value: 'all' },
 *         { label: 'Online', value: 'online' }
 *       ]
 *     }
 *   ]}
 *   crudConfig={{
 *     onCreate: () => setShowCreateForm(true),
 *     onEdit: (device) => setEditingDevice(device),
 *     onDelete: (device) => deleteDevice(device.id)
 *   }}
 * />
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Generic organism component for data display and management. Combines
 * statistics, filtering, multiple view modes, and CRUD operations into
 * unified interface. Designed for maximum reusability across products.
 *
 * <p><b>Thread Safety</b><br>
 * React component - not thread-safe. Use in React context only.
 *
 * @typeParam T - Data item type
 * @param props - Component props
 *
 * @see DataGridStatConfig
 * @see DataGridColumnConfig
 * @see ItemRendererConfig
 * @see CrudConfig
 * @doc.type class
 * @doc.purpose Multi-mode data grid with CRUD operations
 * @doc.layer platform
 * @doc.pattern Component
 */
export function DataGrid<T>({
  items = [],
  rows,
  displayMode = 'table',
  statsCards = [],
  columns = [],
  itemRenderer,
  filters = [],
  onFilter,
  crudConfig,
  emptyMessage = 'No items found.',
  loading = false,
  loadingMessage = 'Loading...',
  title,
  className = '',
  keyExtractor = (_, index) => index,
  gridCols = { base: 1, md: 2, lg: 3, xl: 3 },
}: DataGridProps<T>): React.JSX.Element {
  const allItems = items.length ? items : rows ?? [];
  const [filterValues, setFilterValues] = useState<Record<string, string>>(() =>
    filters.reduce((acc, f) => ({ ...acc, [f.name]: '' }), {})
  );

  /**
   * Filters items based on filter values.
   */
  const filteredItems = useMemo(() => {
    if (onFilter) {
      return onFilter(allItems, filterValues);
    }
    return allItems;
  }, [allItems, filterValues, onFilter]);

  const normalizedColumns = useMemo(() => {
    if (!columns || columns.length === 0) return [];

    return columns.map((column) => {
      const header = column.header ?? column.headerName ?? (column.field ? String(column.field) : '');
      const widthClass = typeof column.width === 'string' ? column.width : undefined;

      const render =
        column.render ??
        ((item: T) => {
          const field = column.field ? String(column.field) : '';
          const record = item as unknown as Record<string, unknown>;
          const value = field ? record[field] : undefined;

          if (column.renderCell) {
            return column.renderCell({ value, row: item, field });
          }

          return value as React.ReactNode;
        });

      return { header, width: widthClass, render };
    });
  }, [columns]);

  /**
   * Handles filter input change.
   */
  const handleFilterChange = useCallback((name: string, value: string) => {
    setFilterValues((prev) => ({ ...prev, [name]: value }));
  }, []);

  /**
   * Clears all filters.
   */
  const handleClearFilters = useCallback(() => {
    setFilterValues(filters.reduce((acc, f) => ({ ...acc, [f.name]: '' }), {}));
  }, [filters]);

  /**
   * Checks if any filters are active.
   */
  const hasActiveFilters = useMemo(() => {
    return Object.values(filterValues).some((value) => value !== '');
  }, [filterValues]);

  /**
   * Gets variant-specific colors for stat cards.
   */
  const getVariantClasses = (variant: DataGridStatConfig<T>['variant'] = 'gray') => {
    const variants = {
      red: 'bg-red-50 text-red-600 border-red-200',
      orange: 'bg-orange-50 text-orange-600 border-orange-200',
      yellow: 'bg-yellow-50 text-yellow-600 border-yellow-200',
      green: 'bg-green-50 text-green-600 border-green-200',
      blue: 'bg-blue-50 text-blue-600 border-blue-200',
      purple: 'bg-purple-50 text-purple-600 border-purple-200',
      gray: 'bg-gray-50 text-gray-600 border-gray-200',
    };
    return variants[variant];
  };

  /**
   * Gets grid column classes based on configuration.
   */
  const getGridColsClass = () => {
    const { base = 1, md = 2, lg = 3, xl = 3 } = gridCols;
    return `grid-cols-${base} md:grid-cols-${md} lg:grid-cols-${lg} xl:grid-cols-${xl}`;
  };

  /**
   * Renders table view.
   */
  const renderTable = () => {
    if (!normalizedColumns || normalizedColumns.length === 0) {
      return (
        <div className="text-center py-8 text-gray-500">
          No columns configured for table view.
        </div>
      );
    }

    return (
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              {normalizedColumns.map((column, index) => (
                <th
                  key={index}
                  className={`px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider ${column.width || ''}`}
                >
                  {column.header}
                </th>
              ))}
              {(crudConfig?.showEditButton || crudConfig?.showDeleteButton) && (
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              )}
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {filteredItems.map((item, index) => (
              <tr key={keyExtractor(item, index)} className="hover:bg-gray-50">
                {normalizedColumns.map((column, colIndex) => (
                  <td key={colIndex} className="px-6 py-4 whitespace-nowrap">
                    {column.render(item)}
                  </td>
                ))}
                {(crudConfig?.showEditButton || crudConfig?.showDeleteButton) && (
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <div className="flex justify-end gap-2">
                      {crudConfig?.showEditButton && crudConfig?.onEdit && (
                        <button
                          onClick={() => crudConfig.onEdit?.(item)}
                          className="text-blue-600 hover:text-blue-900"
                        >
                          {crudConfig.editButtonLabel || 'Edit'}
                        </button>
                      )}
                      {crudConfig?.showDeleteButton && crudConfig?.onDelete && (
                        <button
                          onClick={() => crudConfig.onDelete?.(item)}
                          className="text-red-600 hover:text-red-900"
                        >
                          {crudConfig.deleteButtonLabel || 'Delete'}
                        </button>
                      )}
                    </div>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  /**
   * Renders grid view.
   */
  const renderGrid = () => {
    if (!itemRenderer) {
      return (
        <div className="text-center py-8 text-gray-500">
          No item renderer configured for grid view.
        </div>
      );
    }

    return (
      <div className={`grid ${getGridColsClass()} gap-4`}>
        {filteredItems.map((item, index) => (
          <div
            key={keyExtractor(item, index)}
            className={itemRenderer.itemClassName || ''}
          >
            {itemRenderer.render(item)}
          </div>
        ))}
      </div>
    );
  };

  /**
   * Renders list view.
   */
  const renderList = () => {
    if (!itemRenderer) {
      return (
        <div className="text-center py-8 text-gray-500">
          No item renderer configured for list view.
        </div>
      );
    }

    return (
      <div className="space-y-3">
        {filteredItems.map((item, index) => (
          <div
            key={keyExtractor(item, index)}
            className={itemRenderer.itemClassName || ''}
          >
            {itemRenderer.render(item)}
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className={`space-y-6 ${className}`}>
      <div className="bg-white shadow rounded-lg p-6">
        {/* Header with title and create button */}
        {(title || crudConfig?.onCreate) && (
          <div className="flex items-center justify-between mb-6">
            {title && <h2 className="text-2xl font-bold text-gray-900">{title}</h2>}
            {crudConfig?.onCreate && (
              <button
                onClick={crudConfig.onCreate}
                className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 flex items-center gap-2"
              >
                {crudConfig.createButtonIcon}
                {crudConfig.createButtonText || 'Create'}
              </button>
            )}
          </div>
        )}

        {/* Statistics Cards */}
        {statsCards.length > 0 && (
          <div className={`grid grid-cols-1 md:grid-cols-${Math.min(statsCards.length, 4)} gap-4 mb-6`}>
            {statsCards.map((card, index) => {
              const value = card.calculate(filteredItems);
              const variantClasses = getVariantClasses(card.variant);

              return (
                <div
                  key={index}
                  className={`rounded-lg p-4 border ${variantClasses}`}
                >
                  <h3 className="text-sm font-medium">{card.title}</h3>
                  <p className="text-3xl font-bold mt-1">{value}</p>
                  {card.subtitle && (
                    <p className="text-xs mt-1 opacity-75">{card.subtitle}</p>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* Filters */}
        {filters.length > 0 && (
          <div className="space-y-4 mb-6">
            <div className={`grid grid-cols-1 md:grid-cols-${Math.min(filters.length, 3)} gap-4`}>
              {filters.map((filter) => {
                if (filter.type === 'select' && filter.options) {
                  return (
                    <div key={filter.name}>
                      {filter.label && (
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                          {filter.label}
                        </label>
                      )}
                      <select
                        data-testid={`filter-${filter.name}`}
                        value={filterValues[filter.name] || ''}
                        onChange={(e) => handleFilterChange(filter.name, e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                      >
                        <option value="">{filter.placeholder}</option>
                        {filter.options.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    </div>
                  );
                }

                return (
                  <div key={filter.name}>
                    {filter.label && (
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        {filter.label}
                      </label>
                    )}
                    <input
                      type="text"
                      data-testid={`filter-${filter.name}`}
                      value={filterValues[filter.name] || ''}
                      onChange={(e) => handleFilterChange(filter.name, e.target.value)}
                      placeholder={filter.placeholder}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                );
              })}
            </div>

            {/* Clear Filters */}
            {hasActiveFilters && (
              <button
                onClick={handleClearFilters}
                className="text-sm text-blue-600 hover:text-blue-800"
              >
                Clear all filters
              </button>
            )}
          </div>
        )}

        {/* Loading State */}
        {loading && (
          <div className="text-center py-8 text-gray-500">{loadingMessage}</div>
        )}

        {/* Empty State */}
        {!loading && filteredItems.length === 0 && (
          <div className="text-center py-8 text-gray-500">{emptyMessage}</div>
        )}

        {/* Data Display */}
        {!loading && filteredItems.length > 0 && (
          <>
            {displayMode === 'table' && renderTable()}
            {displayMode === 'grid' && renderGrid()}
            {displayMode === 'list' && renderList()}
          </>
        )}
      </div>
    </div>
  );
}
