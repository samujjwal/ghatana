/**
 * DataTable Component Types
 *
 * @module DevSecOps/DataTable/types
 */

import type { Item } from '@ghatana/yappc-types/devsecops';

/**
 * Sort direction
 */
export type SortDirection = 'asc' | 'desc';

/**
 * Column configuration for table
 */
export interface DataTableColumn<T = Item> {
  /**
   * Unique identifier for the column
   */
  id: string;

  /**
   * Column header label
   */
  label: string;

  /**
   * Property path to access value (supports nested paths like 'owner.name')
   */
  field?: keyof T | string;

  /**
   * Whether this column is sortable
   * @default true
   */
  sortable?: boolean;

  /**
   * Whether this column is filterable
   * @default true
   */
  filterable?: boolean;

  /**
   * Column width (CSS value: px, %, auto)
   * @default 'auto'
   */
  width?: string;

  /**
   * Text alignment
   * @default 'left'
   */
  align?: 'left' | 'center' | 'right';

  /**
   * Custom render function for cell content
   */
  render?: (value: unknown, row: T) => React.ReactNode;

  /**
   * Custom format function for cell value
   */
  format?: (value: unknown) => string;
}

/**
 * Sort configuration
 */
export interface SortConfig {
  /**
   * Column ID to sort by
   */
  column: string;

  /**
   * Sort direction
   */
  direction: SortDirection;
}

/**
 * Filter value for a column
 */
export type FilterValue = string | number | boolean | string[] | null;

/**
 * Filter configuration (column ID to filter value map)
 */
export type FilterConfig = Record<string, FilterValue>;

/**
 * Pagination configuration
 */
export interface PaginationConfig {
  /**
   * Current page (0-indexed)
   */
  page: number;

  /**
   * Rows per page
   */
  rowsPerPage: number;

  /**
   * Total number of rows
   */
  totalRows: number;
}

/**
 * DataTable component props
 */
export interface DataTableProps<T = Item> {
  /**
   * Array of data rows to display
   */
  data: T[];

  /**
   * Column configuration
   */
  columns: DataTableColumn<T>[];

  /**
   * Current sort configuration
   */
  sortConfig?: SortConfig;

  /**
   * Callback when sort changes
   */
  onSortChange?: (config: SortConfig) => void;

  /**
   * Current filter configuration
   */
  filterConfig?: FilterConfig;

  /**
   * Callback when filter changes
   */
  onFilterChange?: (config: FilterConfig) => void;

  /**
   * Pagination configuration
   */
  paginationConfig?: PaginationConfig;

  /**
   * Callback when pagination changes
   */
  onPaginationChange?: (config: PaginationConfig) => void;

  /**
   * Callback when row is clicked
   */
  onRowClick?: (row: T) => void;

  /**
   * Row selection mode
   * @default 'none'
   */
  selectionMode?: 'none' | 'single' | 'multiple';

  /**
   * Selected row IDs (when using selection)
   */
  selectedRows?: string[];

  /**
   * Callback when selection changes
   */
  onSelectionChange?: (selectedIds: string[]) => void;

  /**
   * Whether to show loading state
   * @default false
   */
  loading?: boolean;

  /**
   * Whether to show table header
   * @default true
   */
  showHeader?: boolean;

  /**
   * Whether to show pagination controls
   * @default true
   */
  showPagination?: boolean;

  /**
   * Whether table is dense (compact)
   * @default false
   */
  dense?: boolean;

  /**
   * Whether to stripe rows
   * @default true
   */
  striped?: boolean;

  /**
   * Whether to highlight row on hover
   * @default true
   */
  hoverable?: boolean;

  /**
   * Empty state message
   * @default 'No data available'
   */
  emptyMessage?: string;

  /**
   * Additional CSS class
   */
  className?: string;

  /**
   * IDs of visible columns (for column visibility control)
   * If provided, only these columns will be shown
   */
  visibleColumns?: string[];

  /**
   * Callback when visible columns change
   */
  onVisibleColumnsChange?: (visibleColumns: string[]) => void;
}
