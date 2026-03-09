import { ReactNode, useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';

export interface TableColumn<T> {
  /** Column header text */
  header: string;
  /** Key to access data or render function */
  accessor: keyof T | ((row: T) => ReactNode);
  /** Column width class */
  width?: string;
  /** Hide on mobile */
  hideOnMobile?: boolean;
  /** Sortable */
  sortable?: boolean;
  /** Custom header class */
  headerClass?: string;
  /** Custom cell class */
  cellClass?: string;
}

export interface ResponsiveTableProps<T> {
  /** Table data */
  data: T[];
  /** Column definitions */
  columns: TableColumn<T>[];
  /** Function to get unique row key */
  getRowKey: (row: T) => string;
  /** Mobile card renderer (optional - uses expandable rows by default) */
  mobileCardRenderer?: (row: T) => ReactNode;
  /** Empty state message */
  emptyMessage?: string;
  /** Loading state */
  isLoading?: boolean;
  /** Skeleton row count when loading */
  skeletonRowCount?: number;
  /** Row click handler */
  onRowClick?: (row: T) => void;
  /** Custom table class */
  className?: string;
  /** Caption for accessibility */
  caption?: string;
}

/**
 * Responsive Table Component
 * 
 * Displays data as table on desktop and expandable cards on mobile.
 * WCAG 2.1 AA compliant with proper ARIA attributes.
 * 
 * @doc.type component
 * @doc.purpose Responsive data table with mobile support
 * @doc.layer core
 * @doc.pattern Data Display Component
 * 
 * @example
 * ```tsx
 * const columns: TableColumn<User>[] = [
 *   { header: 'Name', accessor: 'name' },
 *   { header: 'Email', accessor: 'email', hideOnMobile: true },
 *   { header: 'Status', accessor: (row) => <Badge>{row.status}</Badge> },
 * ];
 * 
 * <ResponsiveTable
 *   data={users}
 *   columns={columns}
 *   getRowKey={(user) => user.id}
 *   onRowClick={(user) => navigate(`/users/${user.id}`)}
 * />
 * ```
 */
export function ResponsiveTable<T>({
  data,
  columns,
  getRowKey,
  mobileCardRenderer,
  emptyMessage = 'No data available',
  isLoading = false,
  skeletonRowCount = 5,
  onRowClick,
  className = '',
  caption,
}: ResponsiveTableProps<T>) {
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());

  const toggleRowExpand = (key: string) => {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const getCellValue = (row: T, column: TableColumn<T>): ReactNode => {
    if (typeof column.accessor === 'function') {
      return column.accessor(row);
    }
    return row[column.accessor] as ReactNode;
  };

  const visibleColumns = columns.filter((col) => !col.hideOnMobile);
  const hiddenColumns = columns.filter((col) => col.hideOnMobile);

  if (isLoading) {
    return (
      <div className={`overflow-x-auto ${className}`}>
        <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          {caption && <caption className="sr-only">{caption}</caption>}
          <thead className="bg-gray-50 dark:bg-gray-800">
            <tr>
              {columns.map((col, i) => (
                <th
                  key={i}
                  className={`px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider ${col.width || ''} ${col.headerClass || ''}`}
                >
                  <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse w-20" />
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="bg-white dark:bg-gray-900 divide-y divide-gray-200 dark:divide-gray-700">
            {Array.from({ length: skeletonRowCount }).map((_, rowIndex) => (
              <tr key={rowIndex}>
                {columns.map((col, colIndex) => (
                  <td
                    key={colIndex}
                    className={`px-6 py-4 whitespace-nowrap ${col.cellClass || ''}`}
                  >
                    <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse" />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-500 dark:text-gray-400">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <>
      {/* Desktop Table */}
      <div className={`hidden md:block overflow-x-auto ${className}`}>
        <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          {caption && <caption className="sr-only">{caption}</caption>}
          <thead className="bg-gray-50 dark:bg-gray-800">
            <tr>
              {columns.map((col, i) => (
                <th
                  key={i}
                  scope="col"
                  className={`px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider ${col.width || ''} ${col.headerClass || ''}`}
                >
                  {col.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="bg-white dark:bg-gray-900 divide-y divide-gray-200 dark:divide-gray-700">
            {data.map((row) => (
              <tr
                key={getRowKey(row)}
                onClick={() => onRowClick?.(row)}
                className={`${
                  onRowClick
                    ? 'cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors'
                    : ''
                }`}
              >
                {columns.map((col, colIndex) => (
                  <td
                    key={colIndex}
                    className={`px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-white ${col.cellClass || ''}`}
                  >
                    {getCellValue(row, col)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile Cards */}
      <div className={`md:hidden space-y-3 ${className}`}>
        {data.map((row) => {
          const key = getRowKey(row);
          const isExpanded = expandedRows.has(key);

          if (mobileCardRenderer) {
            return (
              <div
                key={key}
                onClick={() => onRowClick?.(row)}
                className={`bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 ${
                  onRowClick ? 'cursor-pointer' : ''
                }`}
              >
                {mobileCardRenderer(row)}
              </div>
            );
          }

          return (
            <div
              key={key}
              className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden"
            >
              {/* Card Header - Always visible columns */}
              <button
                onClick={() => toggleRowExpand(key)}
                className="w-full p-4 text-left flex items-center justify-between hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                aria-expanded={isExpanded}
                aria-controls={`row-details-${key}`}
              >
                <div className="flex-1 grid grid-cols-2 gap-2">
                  {visibleColumns.slice(0, 2).map((col, i) => (
                    <div key={i}>
                      <div className="text-xs text-gray-500 dark:text-gray-400 uppercase">
                        {col.header}
                      </div>
                      <div className="text-sm font-medium text-gray-900 dark:text-white mt-0.5">
                        {getCellValue(row, col)}
                      </div>
                    </div>
                  ))}
                </div>
                {hiddenColumns.length > 0 && (
                  isExpanded ? (
                    <ChevronDown className="w-5 h-5 text-gray-400 flex-shrink-0" />
                  ) : (
                    <ChevronRight className="w-5 h-5 text-gray-400 flex-shrink-0" />
                  )
                )}
              </button>

              {/* Expanded Details */}
              {isExpanded && hiddenColumns.length > 0 && (
                <div
                  id={`row-details-${key}`}
                  className="px-4 pb-4 pt-2 border-t border-gray-200 dark:border-gray-700 space-y-3"
                >
                  {/* Show remaining visible columns first */}
                  {visibleColumns.slice(2).map((col, i) => (
                    <div key={`visible-${i}`}>
                      <div className="text-xs text-gray-500 dark:text-gray-400 uppercase">
                        {col.header}
                      </div>
                      <div className="text-sm text-gray-900 dark:text-white mt-0.5">
                        {getCellValue(row, col)}
                      </div>
                    </div>
                  ))}
                  {/* Then hidden columns */}
                  {hiddenColumns.map((col, i) => (
                    <div key={`hidden-${i}`}>
                      <div className="text-xs text-gray-500 dark:text-gray-400 uppercase">
                        {col.header}
                      </div>
                      <div className="text-sm text-gray-900 dark:text-white mt-0.5">
                        {getCellValue(row, col)}
                      </div>
                    </div>
                  ))}
                  {/* Row action button */}
                  {onRowClick && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onRowClick(row);
                      }}
                      className="w-full mt-2 px-4 py-2 text-sm font-medium text-primary-600 bg-primary-50 dark:bg-primary-900/20 rounded-lg hover:bg-primary-100 dark:hover:bg-primary-900/30 transition-colors"
                    >
                      View Details
                    </button>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </>
  );
}

export default ResponsiveTable;
