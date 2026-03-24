/**
 * DataTable Utility Functions
 *
 * Pure functions for sorting, filtering, and pagination.
 *
 * @module DevSecOps/DataTable/utils
 */

import type { SortConfig, FilterConfig, FilterValue } from './types';

/**
 * DataTable utility functions
 */
export class DataTableUtils {
  /**
   * Get nested property value from object using path
   *
   * @param obj - Object to get value from
   * @param path - Property path (e.g., 'owner.name')
   * @returns Property value
   */
  static getNestedValue(obj: unknown, path: string): unknown {
    if (!obj || typeof obj !== 'object') return undefined;

    const keys = path.split('.');
    let value: unknown = obj;

    for (const key of keys) {
      if (value && typeof value === 'object' && key in value) {
        // eslint-disable-next-line security/detect-object-injection
        value = (value as Record<string, unknown>)[key];
      } else {
        return undefined;
      }
    }

    return value;
  }

  /**
   * Compare two values for sorting
   *
   * @param a - First value
   * @param b - Second value
   * @param direction - Sort direction
   * @returns Comparison result
   */
  static compareValues(a: unknown, b: unknown, direction: 'asc' | 'desc'): number {
    // Handle null/undefined
    if (a == null && b == null) return 0;
    if (a == null) return direction === 'asc' ? 1 : -1;
    if (b == null) return direction === 'asc' ? -1 : 1;

    // Convert to comparable types
    let aValue: string | number | boolean = a as string | number | boolean;
    let bValue: string | number | boolean = b as string | number | boolean;

    // Handle dates
    if (a instanceof Date && b instanceof Date) {
      aValue = a.getTime();
      bValue = b.getTime();
    }

    // Handle strings (case-insensitive)
    if (typeof aValue === 'string' && typeof bValue === 'string') {
      aValue = aValue.toLowerCase();
      bValue = bValue.toLowerCase();
    }

    // Compare
    const comparison = aValue < bValue ? -1 : aValue > bValue ? 1 : 0;
    return direction === 'asc' ? comparison : -comparison;
  }

  /**
   * Sort data by column
   *
   * @param data - Data to sort
   * @param sortConfig - Sort configuration
   * @param getFieldValue - Function to get field value from row
   * @returns Sorted data
   */
  static sortData<T>(
    data: T[],
    sortConfig: SortConfig | undefined,
    getFieldValue: (row: T, field: string) => unknown
  ): T[] {
    if (!sortConfig) return data;

    return [...data].sort((a, b) => {
      const aValue = getFieldValue(a, sortConfig.column);
      const bValue = getFieldValue(b, sortConfig.column);
      return this.compareValues(aValue, bValue, sortConfig.direction);
    });
  }

  /**
   * Check if value matches filter
   *
   * @param value - Value to check
   * @param filterValue - Filter value
   * @returns Whether value matches filter
   */
  static matchesFilter(value: unknown, filterValue: FilterValue): boolean {
    if (filterValue == null || filterValue === '') return true;

    // Handle array filters (multi-select)
    if (Array.isArray(filterValue)) {
      if (filterValue.length === 0) return true;
      return filterValue.some((fv) => this.matchesFilter(value, fv));
    }

    // Handle null/undefined values
    if (value == null) return false;

    // String matching (case-insensitive, partial match)
    if (typeof filterValue === 'string') {
      const valueStr = String(value).toLowerCase();
      const filterStr = filterValue.toLowerCase();
      return valueStr.includes(filterStr);
    }

    // Exact match for numbers and booleans
    return value === filterValue;
  }

  /**
   * Filter data by column filters
   *
   * @param data - Data to filter
   * @param filterConfig - Filter configuration
   * @param getFieldValue - Function to get field value from row
   * @returns Filtered data
   */
  static filterData<T>(
    data: T[],
    filterConfig: FilterConfig | undefined,
    getFieldValue: (row: T, field: string) => unknown
  ): T[] {
    if (!filterConfig || Object.keys(filterConfig).length === 0) return data;

    return data.filter((row) => {
      return Object.entries(filterConfig).every(([columnId, filterValue]) => {
        const value = getFieldValue(row, columnId);
        return this.matchesFilter(value, filterValue);
      });
    });
  }

  /**
   * Paginate data
   *
   * @param data - Data to paginate
   * @param page - Current page (0-indexed)
   * @param rowsPerPage - Rows per page
   * @returns Paginated data
   */
  static paginateData<T>(data: T[], page: number, rowsPerPage: number): T[] {
    const startIndex = page * rowsPerPage;
    const endIndex = startIndex + rowsPerPage;
    return data.slice(startIndex, endIndex);
  }

  /**
   * Process data (filter, sort, paginate)
   *
   * @param data - Original data
   * @param sortConfig - Sort configuration
   * @param filterConfig - Filter configuration
   * @param page - Current page
   * @param rowsPerPage - Rows per page
   * @param getFieldValue - Function to get field value from row
   * @returns Processed data and total count
   */
  static processData<T>(
    data: T[],
    sortConfig: SortConfig | undefined,
    filterConfig: FilterConfig | undefined,
    page: number,
    rowsPerPage: number,
    getFieldValue: (row: T, field: string) => unknown
  ): { processedData: T[]; totalRows: number } {
    // Filter first
    let processedData = this.filterData(data, filterConfig, getFieldValue);
    const totalRows = processedData.length;

    // Then sort
    processedData = this.sortData(processedData, sortConfig, getFieldValue);

    // Then paginate
    processedData = this.paginateData(processedData, page, rowsPerPage);

    return { processedData, totalRows };
  }

  /**
   * Calculate total pages
   *
   * @param totalRows - Total number of rows
   * @param rowsPerPage - Rows per page
   * @returns Total pages
   */
  static getTotalPages(totalRows: number, rowsPerPage: number): number {
    return Math.ceil(totalRows / rowsPerPage);
  }

  /**
   * Get page range for pagination display
   *
   * @param currentPage - Current page (0-indexed)
   * @param totalPages - Total pages
   * @param maxPages - Maximum pages to show
   * @returns Array of page numbers to display
   */
  static getPageRange(currentPage: number, totalPages: number, maxPages = 5): number[] {
    if (totalPages <= maxPages) {
      return Array.from({ length: totalPages }, (_, i) => i);
    }

    const half = Math.floor(maxPages / 2);
    let start = Math.max(0, currentPage - half);
    const end = Math.min(totalPages - 1, start + maxPages - 1);

    if (end - start < maxPages - 1) {
      start = Math.max(0, end - maxPages + 1);
    }

    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
  }

  /**
   * Format cell value for display
   *
   * @param value - Value to format
   * @returns Formatted string
   */
  static formatValue(value: unknown): string {
    if (value == null) return '';
    if (value instanceof Date) return value.toLocaleDateString();
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (Array.isArray(value)) return value.join(', ');
    return String(value);
  }

  /**
   * Get row ID from row data
   *
   * @param row - Row data
   * @param idField - ID field name
   * @returns Row ID
   */
  static getRowId<T>(row: T, idField: keyof T | string = 'id'): string {
    const value = typeof idField === 'string' && idField.includes('.')
      ? this.getNestedValue(row, idField)
      : (row as Record<string, unknown>)[idField as string];

    return String(value ?? '');
  }
}
