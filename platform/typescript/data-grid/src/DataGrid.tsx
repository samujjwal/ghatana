import React, { useState, useCallback, useMemo } from 'react';

export interface SortState {
  column: string;
  direction: 'asc' | 'desc';
}

export interface FilterState {
  column: string;
  value: string;
}

export interface PaginationState {
  page: number;
  pageSize: number;
  total: number;
}

export interface ColumnDef<T> {
  key: keyof T & string;
  header: string;
  sortable?: boolean;
  filterable?: boolean;
  render?: (value: T[keyof T], row: T) => React.ReactNode;
}

export interface DataGridProps<T extends Record<string, unknown>> {
  data: T[];
  columns: ColumnDef<T>[];
  pageSize?: number;
  onSortChange?: (sort: SortState | undefined) => void;
  onFilterChange?: (filters: FilterState[]) => void;
  onPageChange?: (page: number) => void;
  isLoading?: boolean;
  emptyMessage?: string;
  'aria-label'?: string;
}

/**
 * @doc.type component
 * @doc.purpose Accessible data grid with sorting, filtering, and pagination.
 * @doc.layer platform
 * @doc.pattern UI Component
 */
export function DataGrid<T extends Record<string, unknown>>({
  data,
  columns,
  pageSize = 25,
  onSortChange,
  onFilterChange,
  onPageChange,
  isLoading = false,
  emptyMessage = 'No data available',
  'aria-label': ariaLabel,
}: DataGridProps<T>) {
  const [sort, setSort] = useState<SortState | undefined>(undefined);
  const [filters, setFilters] = useState<FilterState[]>([]);
  const [page, setPage] = useState(1);

  const handleSort = useCallback(
    (column: string) => {
      const newSort: SortState =
        sort?.column === column
          ? { column, direction: sort.direction === 'asc' ? 'desc' : 'asc' }
          : { column, direction: 'asc' };
      setSort(newSort);
      onSortChange?.(newSort);
    },
    [sort, onSortChange]
  );

  const handleFilterChange = useCallback(
    (column: string, value: string) => {
      const newFilters = value
        ? [...filters.filter((f) => f.column !== column), { column, value }]
        : filters.filter((f) => f.column !== column);
      setFilters(newFilters);
      onFilterChange?.(newFilters);
    },
    [filters, onFilterChange]
  );

  const pagedData = useMemo(() => {
    const start = (page - 1) * pageSize;
    return data.slice(start, start + pageSize);
  }, [data, page, pageSize]);

  const totalPages = Math.ceil(data.length / pageSize);

  return (
    <div>
      <table aria-label={ariaLabel} aria-busy={isLoading}>
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key} scope="col">
                {col.sortable ? (
                  <button
                    type="button"
                    onClick={() => handleSort(col.key)}
                    aria-sort={
                      sort?.column === col.key
                        ? sort.direction === 'asc'
                          ? 'ascending'
                          : 'descending'
                        : 'none'
                    }
                  >
                    {col.header}
                  </button>
                ) : (
                  col.header
                )}
                {col.filterable && (
                  <input
                    type="search"
                    placeholder={`Filter ${col.header}`}
                    aria-label={`Filter by ${col.header}`}
                    onChange={(e) => handleFilterChange(col.key, e.target.value)}
                  />
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={columns.length} aria-live="polite">Loading…</td>
            </tr>
          ) : pagedData.length === 0 ? (
            <tr>
              <td colSpan={columns.length}>{emptyMessage}</td>
            </tr>
          ) : (
            pagedData.map((row, rowIdx) => (
              <tr key={rowIdx}>
                {columns.map((col) => (
                  <td key={col.key}>
                    {col.render
                      ? col.render(row[col.key], row)
                      : String(row[col.key] ?? '')}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
      {totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={(p) => {
            setPage(p);
            onPageChange?.(p);
          }}
        />
      )}
    </div>
  );
}

export interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  return (
    <nav aria-label="Pagination">
      <button
        type="button"
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 1}
        aria-label="Previous page"
      >
        Previous
      </button>
      <span aria-live="polite" aria-atomic>
        Page {page} of {totalPages}
      </span>
      <button
        type="button"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages}
        aria-label="Next page"
      >
        Next
      </button>
    </nav>
  );
}
