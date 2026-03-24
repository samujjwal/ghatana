/**
 * DataTable Component Exports
 *
 * @module DevSecOps/DataTable
 * @doc.type module
 * @doc.purpose DataTable component exports with AI enhancements
 * @doc.layer product
 * @doc.pattern Barrel
 */

// Core DataTable
export { DataTable } from './DataTable';
export type {
  DataTableProps,
  DataTableColumn,
  SortConfig,
  SortDirection,
  FilterConfig,
  FilterValue,
  PaginationConfig,
} from './types';

// AI-enhanced DataTable
export { SmartDataTable } from './SmartDataTable';
export type { SmartDataTableProps } from './SmartDataTable';

// AI Hook for external use
export { useDataTableAI } from './useDataTableAI';
export type {
  QueryIntent,
  FieldMapping,
  UseDataTableAIOptions,
} from './useDataTableAI';

// Utilities
export { DataTableUtils } from './utils';
export { DataTableExport } from './export';

// Column controls
export { ColumnVisibility } from './ColumnVisibility';
export type { ColumnVisibilityProps } from './ColumnVisibility';

// Export controls
export { ExportToolbar } from './ExportToolbar';
export type { ExportToolbarProps } from './ExportToolbar';
