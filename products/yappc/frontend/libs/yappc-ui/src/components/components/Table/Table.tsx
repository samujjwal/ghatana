import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * Table column alignment
 */
export type TableAlign = 'left' | 'center' | 'right';

/**
 * Table variant types
 */
export type TableVariant = 'default' | 'striped' | 'bordered';

/**
 * Table size density
 */
export type TableSize = 'default' | 'compact';

/**
 * Table column definition
 */
export interface TableColumn<T = unknown> {
  /**
   * Unique column identifier
   */
  id: string;
  /**
   * Column header label
   */
  label: React.ReactNode;
  /**
   * Data accessor (key or function)
   */
  accessor?: keyof T | ((row: T, index: number) => React.ReactNode);
  /**
   * Custom cell renderer
   */
  render?: (value: unknown, row: T, index: number) => React.ReactNode;
  /**
   * Column width (CSS value)
   */
  width?: string | number;
  /**
   * Text alignment
   * @default 'left'
   */
  align?: TableAlign;
  /**
   * Header alignment (defaults to align)
   */
  headerAlign?: TableAlign;
  /**
   * Enable sorting for this column
   * @default false
   */
  sortable?: boolean;
  /**
   * Enable filtering for this column
   * @default false
   */
  filterable?: boolean;
}

/**
 * Table component props
 */
export interface TableProps<T = unknown> {
  /**
   * Table data rows
   */
  data: T[];
  /**
   * Column definitions
   */
  columns: TableColumn<T>[];
  /**
   * Table visual variant
   * @default 'default'
   */
  variant?: TableVariant;
  /**
   * Table density/size
   * @default 'default'
   */
  size?: TableSize;
  /**
   * Enable hover effect on rows
   * @default false
   */
  hover?: boolean;
  /**
   * Make header sticky
   * @default false
   */
  stickyHeader?: boolean;
  /**
   * Maximum height for scrollable table
   */
  maxHeight?: string | number;
  /**
   * Row click handler
   */
  onRowClick?: (row: T, index: number) => void;
  /**
   * Sorting configuration
   */
  sortBy?: string;
  /**
   * Sort direction
   */
  sortDirection?: 'asc' | 'desc';
  /**
   * Sort change handler
   */
  onSort?: (columnId: string, direction: 'asc' | 'desc') => void;
  /**
   * Enable row selection
   * @default false
   */
  selectable?: boolean;
  /**
   * Selected row IDs or indices
   */
  selected?: (string | number)[];
  /**
   * Selection change handler
   */
  onSelectionChange?: (selected: (string | number)[]) => void;
  /**
   * Row ID accessor (for selection tracking)
   */
  rowId?: keyof T | ((row: T, index: number) => string | number);
  /**
   * Enable row expansion
   * @default false
   */
  expandable?: boolean;
  /**
   * Expanded row IDs or indices
   */
  expanded?: (string | number)[];
  /**
   * Expansion change handler
   */
  onExpansionChange?: (expanded: (string | number)[]) => void;
  /**
   * Render function for expanded row content
   */
  renderExpanded?: (row: T, index: number) => React.ReactNode;
  /**
   * Enable pagination
   * @default false
   */
  paginated?: boolean;
  /**
   * Current page (0-indexed)
   */
  page?: number;
  /**
   * Rows per page
   */
  pageSize?: number;
  /**
   * Total number of rows (for server-side pagination)
   */
  totalRows?: number;
  /**
   * Page change handler
   */
  onPageChange?: (page: number) => void;
  /**
   * Page size change handler
   */
  onPageSizeChange?: (pageSize: number) => void;
  /**
   * Available page size options
   */
  pageSizeOptions?: number[];
  /**
   * Custom row className
   */
  rowClassName?: string | ((row: T, index: number) => string);
  /**
   * Empty state message
   */
  emptyMessage?: string;
  /**
   * Loading state
   */
  loading?: boolean;
  /**
   * Custom className
   */
  className?: string;
}

/**
 * Table - Data display component
 *
 * Displays structured data in rows and columns with sorting,
 * hover states, and customizable styling.
 *
 * @example Basic usage
 * ```tsx
 * const columns = [
 *   { id: 'name', label: 'Name', accessor: 'name' },
 *   { id: 'email', label: 'Email', accessor: 'email' },
 *   { id: 'role', label: 'Role', accessor: 'role' }
 * ];
 *
 * <Table data={users} columns={columns} />
 * ```
 *
 * @example With sorting
 * ```tsx
 * <Table
 *   data={users}
 *   columns={columns}
 *   sortBy="name"
 *   sortDirection="asc"
 *   onSort={(column, direction) => handleSort(column, direction)}
 * />
 * ```
 */
export const Table = React.forwardRef<HTMLTableElement, TableProps>(
  (
    {
      data,
      columns,
      variant = 'default',
      size = 'default',
      hover = false,
      stickyHeader = false,
      maxHeight,
      onRowClick,
      sortBy,
      sortDirection,
      onSort,
      selectable = false,
      selected = [],
      onSelectionChange,
      rowId = (_, index) => index,
      expandable = false,
      expanded = [],
      onExpansionChange,
      renderExpanded,
      paginated = false,
      page = 0,
      pageSize = 10,
      totalRows,
      onPageChange,
      onPageSizeChange,
      pageSizeOptions = [5, 10, 25, 50, 100],
      rowClassName,
      emptyMessage = 'No data available',
      loading = false,
      className,
    },
    ref
  ) => {
    // Handle sort click
    const handleSortClick = (column: TableColumn) => {
      if (!column.sortable || !onSort) return;

      const newDirection =
        sortBy === column.id && sortDirection === 'asc' ? 'desc' : 'asc';
      onSort(column.id, newDirection);
    };

    // Get row ID
    const getRowId = (row: unknown, index: number): string | number => {
      if (typeof rowId === 'function') {
        return rowId(row, index);
      }
      return row[rowId];
    };

    // Check if row is selected
    const isRowSelected = (row: unknown, index: number): boolean => {
      const id = getRowId(row, index);
      return selected.includes(id);
    };

    // Check if row is expanded
    const isRowExpanded = (row: unknown, index: number): boolean => {
      const id = getRowId(row, index);
      return expanded.includes(id);
    };

    // Handle row selection
    const handleRowSelect = (row: unknown, index: number) => {
      if (!selectable || !onSelectionChange) return;
      
      const id = getRowId(row, index);
      const newSelected = isRowSelected(row, index)
        ? selected.filter((s) => s !== id)
        : [...selected, id];
      
      onSelectionChange(newSelected);
    };

    // Handle select all
    const handleSelectAll = () => {
      if (!selectable || !onSelectionChange) return;
      
      const displayData = getDisplayData();
      if (selected.length === displayData.length) {
        // Deselect all
        onSelectionChange([]);
      } else {
        // Select all visible rows
        const allIds = displayData.map((row, index) => getRowId(row, index));
        onSelectionChange(allIds);
      }
    };

    // Handle row expansion
    const handleRowExpand = (row: unknown, index: number) => {
      if (!expandable || !onExpansionChange) return;
      
      const id = getRowId(row, index);
      const newExpanded = isRowExpanded(row, index)
        ? expanded.filter((e) => e !== id)
        : [...expanded, id];
      
      onExpansionChange(newExpanded);
    };

    // Get paginated data
    const getDisplayData = () => {
      if (!paginated) return data;
      const start = page * pageSize;
      return data.slice(start, start + pageSize);
    };

    // Calculate pagination info
    const total = totalRows ?? data.length;
    const totalPages = Math.ceil(total / pageSize);
    const displayData = getDisplayData();
    const allSelected = selectable && selected.length === displayData.length && displayData.length > 0;
    const someSelected = selectable && selected.length > 0 && selected.length < displayData.length;

    // Get cell value
    const getCellValue = (column: TableColumn, row: unknown, index: number) => {
      if (column.render) {
        const value = typeof column.accessor === 'function'
          ? column.accessor(row, index)
          : column.accessor
          ? row[column.accessor]
          : undefined;
        return column.render(value, row, index);
      }

      if (typeof column.accessor === 'function') {
        return column.accessor(row, index);
      }

      if (column.accessor) {
        return row[column.accessor];
      }

      return null;
    };

    // Get row className
    const getRowClassName = (row: unknown, index: number) => {
      if (typeof rowClassName === 'function') {
        return rowClassName(row, index);
      }
      return rowClassName;
    };

    // Alignment classes
    const alignClasses: Record<TableAlign, string> = {
      left: 'text-left',
      center: 'text-center',
      right: 'text-right',
    };

    // Size-based padding
    const cellPadding = size === 'compact' ? 'px-3 py-2' : 'px-4 py-3';

    // Variant-specific classes
    const variantClasses = {
      default: '',
      striped: '[&_tbody_tr:nth-child(even)]:bg-grey-50 dark:[&_tbody_tr:nth-child(even)]:bg-grey-900/20',
      bordered: 'border border-grey-200 dark:border-grey-700 [&_td]:border-b [&_td]:border-grey-200 dark:[&_td]:border-grey-700',
    };

    // Container wrapper for scrollable table
    const containerClasses = cn(
      'w-full overflow-auto',
      maxHeight && 'max-h-[var(--table-max-height)]'
    );

    const tableClasses = cn(
      'min-w-full',
      'border-collapse',
      variantClasses[variant],
      className
    );

    if (loading) {
      return (
        <div className="flex items-center justify-center p-8 bg-white dark:bg-grey-900 rounded-lg">
          <div className="text-center">
            <div className="mb-4 text-4xl">⏳</div>
            <div className="text-sm text-grey-600 dark:text-grey-400">Loading...</div>
          </div>
        </div>
      );
    }

    if (data.length === 0) {
      return (
        <div className="flex items-center justify-center p-12 bg-white dark:bg-grey-900 rounded-lg">
          <div className="text-center text-grey-500 dark:text-grey-400">
            <div className="mb-4 text-5xl opacity-50">📋</div>
            <div className="text-sm">{emptyMessage}</div>
          </div>
        </div>
      );
    }

    const tableContent = (
      <table
        ref={ref}
        className={tableClasses}
        style={maxHeight ? { '--table-max-height': typeof maxHeight === 'number' ? `${maxHeight}px` : maxHeight } as React.CSSProperties : undefined}
      >
        <thead
          className={cn(
            'bg-grey-50 dark:bg-grey-800',
            'border-b-2 border-grey-200 dark:border-grey-700',
            stickyHeader && 'sticky top-0 z-10'
          )}
        >
          <tr>
            {selectable && (
              <th className={cn(cellPadding, 'w-12')}>
                <input
                  type="checkbox"
                  checked={allSelected}
                  ref={(el) => {
                    if (el) el.indeterminate = someSelected;
                  }}
                  onChange={handleSelectAll}
                  className="w-4 h-4 rounded border-grey-300 text-primary-600 focus:ring-2 focus:ring-primary-500"
                />
              </th>
            )}
            {expandable && (
              <th className={cn(cellPadding, 'w-12')} />
            )}
            {columns.map((column) => {
              const align = column.headerAlign || column.align || 'left';
              const isSorted = sortBy === column.id;

              return (
                <th
                  key={column.id}
                  className={cn(
                    cellPadding,
                    'font-semibold text-sm text-grey-700 dark:text-grey-300',
                    alignClasses[align],
                    column.sortable && 'cursor-pointer select-none hover:bg-grey-100 dark:hover:bg-grey-700'
                  )}
                  style={{ width: column.width }}
                  onClick={() => handleSortClick(column)}
                >
                  <div className={cn('flex items-center gap-1', align === 'center' && 'justify-center', align === 'right' && 'justify-end')}>
                    <span>{column.label}</span>
                    {column.sortable && (
                      <span className="inline-flex flex-col text-xs opacity-50">
                        {isSorted && sortDirection === 'asc' && '▲'}
                        {isSorted && sortDirection === 'desc' && '▼'}
                        {!isSorted && '⇅'}
                      </span>
                    )}
                  </div>
                </th>
              );
            })}
          </tr>
        </thead>

        <tbody>
          {displayData.map((row, rowIndex) => {
            const isSelected = isRowSelected(row, rowIndex);
            const isExpanded = isRowExpanded(row, rowIndex);
            
            return (
              <React.Fragment key={rowIndex}>
                <tr
                  className={cn(
                    'border-b border-grey-200 dark:border-grey-700',
                    'bg-white dark:bg-grey-900',
                    hover && 'hover:bg-grey-50 dark:hover:bg-grey-800 transition-colors',
                    onRowClick && 'cursor-pointer',
                    isSelected && 'bg-primary-50 dark:bg-primary-900/20',
                    getRowClassName(row, rowIndex)
                  )}
                  onClick={() => onRowClick?.(row, rowIndex)}
                >
                  {selectable && (
                    <td className={cn(cellPadding)} onClick={(e) => e.stopPropagation()}>
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => handleRowSelect(row, rowIndex)}
                        className="w-4 h-4 rounded border-grey-300 text-primary-600 focus:ring-2 focus:ring-primary-500"
                      />
                    </td>
                  )}
                  {expandable && (
                    <td className={cn(cellPadding)} onClick={(e) => e.stopPropagation()}>
                      <button
                        type="button"
                        onClick={() => handleRowExpand(row, rowIndex)}
                        className="text-grey-500 hover:text-grey-700 transition-transform"
                        style={{ transform: isExpanded ? 'rotate(90deg)' : '' }}
                      >
                        ▶
                      </button>
                    </td>
                  )}
                  {columns.map((column) => {
                const align = column.align || 'left';
                const value = getCellValue(column, row, rowIndex);

                return (
                  <td
                    key={column.id}
                    className={cn(
                      cellPadding,
                      'text-sm text-grey-900 dark:text-grey-100',
                      alignClasses[align]
                    )}
                  >
                    {value}
                  </td>
                );
              })}
            </tr>
            
            {/* Expanded row content */}
            {expandable && isExpanded && renderExpanded && (
              <tr className="bg-grey-50 dark:bg-grey-800/50">
                <td
                  colSpan={columns.length + (selectable ? 1 : 0) + (expandable ? 1 : 0)}
                  className={cn(cellPadding, 'border-b border-grey-200 dark:border-grey-700')}
                >
                  {renderExpanded(row, rowIndex)}
                </td>
              </tr>
            )}
          </React.Fragment>
          );
          })}
        </tbody>
      </table>
    );

    const tableWithPagination = (
      <div>
        {tableContent}
        {/* Pagination */}
        {paginated && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-grey-200 dark:border-grey-700 bg-white dark:bg-grey-900">
            <div className="flex items-center gap-2">
              <span className="text-sm text-grey-700 dark:text-grey-300">Rows per page:</span>
              <select
                value={pageSize}
                onChange={(e) => onPageSizeChange?.(Number(e.target.value))}
                className="px-2 py-1 text-sm border border-grey-300 dark:border-grey-600 rounded bg-white dark:bg-grey-800 text-grey-900 dark:text-grey-100"
              >
                {pageSizeOptions.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex items-center gap-4">
              <span className="text-sm text-grey-700 dark:text-grey-300">
                {page * pageSize + 1}-{Math.min((page + 1) * pageSize, total)} of {total}
              </span>

              <div className="flex gap-1">
                <button
                  type="button"
                  onClick={() => onPageChange?.(0)}
                  disabled={page === 0}
                  className={cn(
                    'px-2 py-1 text-sm rounded',
                    page === 0
                      ? 'text-grey-400 cursor-not-allowed'
                      : 'text-grey-700 dark:text-grey-300 hover:bg-grey-100 dark:hover:bg-grey-800'
                  )}
                >
                  ⟪
                </button>
                <button
                  type="button"
                  onClick={() => onPageChange?.(page - 1)}
                  disabled={page === 0}
                  className={cn(
                    'px-2 py-1 text-sm rounded',
                    page === 0
                      ? 'text-grey-400 cursor-not-allowed'
                      : 'text-grey-700 dark:text-grey-300 hover:bg-grey-100 dark:hover:bg-grey-800'
                  )}
                >
                  ‹
                </button>
                <button
                  type="button"
                  onClick={() => onPageChange?.(page + 1)}
                  disabled={page >= totalPages - 1}
                  className={cn(
                    'px-2 py-1 text-sm rounded',
                    page >= totalPages - 1
                      ? 'text-grey-400 cursor-not-allowed'
                      : 'text-grey-700 dark:text-grey-300 hover:bg-grey-100 dark:hover:bg-grey-800'
                  )}
                >
                  ›
                </button>
                <button
                  type="button"
                  onClick={() => onPageChange?.(totalPages - 1)}
                  disabled={page >= totalPages - 1}
                  className={cn(
                    'px-2 py-1 text-sm rounded',
                    page >= totalPages - 1
                      ? 'text-grey-400 cursor-not-allowed'
                      : 'text-grey-700 dark:text-grey-300 hover:bg-grey-100 dark:hover:bg-grey-800'
                  )}
                >
                  ⟫
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );

    if (maxHeight) {
      return <div className={containerClasses}>{paginated ? tableWithPagination : tableContent}</div>;
    }

    return paginated ? tableWithPagination : tableContent;
  }
);

Table.displayName = 'Table';
