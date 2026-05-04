/**
 * TableWidget Component
 *
 * Feature-rich data table with sorting, pagination, filtering, row selection, and export.
 *
 * @doc.type component
 * @doc.purpose Display tabular data with interactive features
 * @doc.layer product
 * @doc.pattern Component
 */
import React, { useState, useMemo } from 'react';
import { Button, Input, Checkbox } from '@ghatana/design-system';

interface ColumnDef<T = Record<string, unknown>> {
  key: string;
  label: string;
  sortable?: boolean;
  render?: (row: T) => React.ReactNode;
}

interface TableWidgetProps<T extends Record<string, unknown> = Record<string, unknown>> {
  data: T[];
  columns: ColumnDef<T>[];
  pageSize?: number;
  selectable?: boolean;
  filterable?: boolean;
  exportable?: boolean;
  onSelectionChange?: (selectedIds: unknown[]) => void;
  onExport?: (data: T[]) => void;
}

/**
 * TableWidget renders a sortable, filterable, paginated data table.
 */
export function TableWidget<T extends Record<string, unknown>>({
  data,
  columns,
  pageSize,
  selectable = false,
  filterable = false,
  exportable = false,
  onSelectionChange,
  onExport,
}: TableWidgetProps<T>): React.ReactElement {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortAsc, setSortAsc] = useState<boolean>(true);
  const [page, setPage] = useState<number>(0);
  const [filter, setFilter] = useState<string>('');
  const [selectedIds, setSelectedIds] = useState<Set<unknown>>(new Set());

  const filtered = useMemo(() => {
    if (!filter) return data;
    const lower = filter.toLowerCase();
    return data.filter((row) =>
      columns.some((col) => String(row[col.key] ?? '').toLowerCase().includes(lower))
    );
  }, [data, columns, filter]);

  const sorted = useMemo(() => {
    if (!sortKey) return filtered;
    return [...filtered].sort((a, b) => {
      const av = a[sortKey] ?? '';
      const bv = b[sortKey] ?? '';
      const cmp = String(av).localeCompare(String(bv));
      return sortAsc ? cmp : -cmp;
    });
  }, [filtered, sortKey, sortAsc]);

  const paged = useMemo(() => {
    if (!pageSize) return sorted;
    const start = page * pageSize;
    return sorted.slice(start, start + pageSize);
  }, [sorted, pageSize, page]);

  const totalPages = pageSize ? Math.ceil(sorted.length / pageSize) : 1;

  function handleSort(key: string) {
    if (sortKey === key) {
      setSortAsc((prev) => !prev);
    } else {
      setSortKey(key);
      setSortAsc(true);
    }
  }

  function handleSelect(id: unknown) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      onSelectionChange?.(Array.from(next));
      return next;
    });
  }

  if (data.length === 0) {
    return <div>No data available</div>;
  }

  return (
    <div>
      {filterable && (
        <Input
          placeholder="Filter..."
          value={filter}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
            setFilter(e.target.value);
            setPage(0);
          }}
          className="mb-2"
        />
      )}
      {exportable && (
        <Button
          aria-label="Export"
          onClick={() => onExport?.(data)}
          variant="outline"
          size="sm"
          className="mb-2 mr-2"
        >
          Export
        </Button>
      )}
      <table>
        <thead>
          <tr>
            {selectable && <th />}
            {columns.map((col) => (
              <th key={col.key}>
                {col.sortable ? (
                  <Button aria-label={col.label} variant="ghost" size="sm" onClick={() => handleSort(col.key)}>
                    {col.label}
                    {sortKey === col.key ? (sortAsc ? ' ↑' : ' ↓') : ''}
                  </Button>
                ) : (
                  col.label
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {paged.map((row, rowIdx) => {
            const id = row['id'] ?? rowIdx;
            return (
              <tr key={String(id)}>
                {selectable && (
                  <td>
                    <Checkbox
                      checked={selectedIds.has(id)}
                      onChange={() => handleSelect(id)}
                    />
                  </td>
                )}
                {columns.map((col) => (
                  <td key={col.key}>
                    {col.render ? col.render(row) : String(row[col.key] ?? '')}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
      {pageSize && totalPages > 1 && (
        <div>
          <Button
            aria-label="Previous"
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
          >
            Previous
          </Button>
          <span>
            {page + 1} / {totalPages}
          </span>
          <Button
            aria-label="Next"
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}

export default TableWidget;
