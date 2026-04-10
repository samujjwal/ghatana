/**
 * @ghatana/data-grid — platform data grid library.
 *
 * Provides an accessible, sortable, filterable, paginated data grid
 * for Ghatana products.
 *
 * @doc.type module
 * @doc.purpose Shared data grid for Ghatana platform
 * @doc.layer platform
 * @doc.pattern Library
 */

export {
  DataGrid,
  Pagination,
} from './DataGrid';
export type {
  ColumnDef,
  DataGridProps,
  PaginationProps,
  SortState,
  FilterState,
  PaginationState,
} from './DataGrid';
