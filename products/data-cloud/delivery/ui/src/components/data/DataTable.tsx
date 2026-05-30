import type { ReactNode } from 'react';

/**
 * DataTable - Standardized table component for consistent data display
 * 
 * Provides consistent table structure with:
 * - Column headers
 * - Row actions
 * - Selection support
 * - Empty state
 * 
 * @doc.type component
 * @doc.purpose Standardized data table
 * @doc.layer frontend
 * @doc.pattern Data Display
 */
interface Column<T> {
  key: string;
  header: string;
  render: (item: T) => ReactNode;
  sortable?: boolean;
}

interface DataTableProps<T> {
  data: T[];
  columns: Column<T>[];
  keyExtractor: (item: T) => string;
  rowActions?: (item: T) => ReactNode;
  onRowClick?: (item: T) => void;
  selectable?: boolean;
  selectedKeys?: Set<string>;
  onSelectionChange?: (selectedKeys: Set<string>) => void;
  emptyState?: ReactNode;
  isLoading?: boolean;
}

export function DataTable<T>({
  data,
  columns,
  keyExtractor,
  rowActions,
  onRowClick,
  selectable = false,
  selectedKeys = new Set(),
  onSelectionChange,
  emptyState,
  isLoading = false,
}: DataTableProps<T>) {
  const handleSelectAll = (checked: boolean) => {
    if (onSelectionChange) {
      const newSelection = checked
        ? new Set(data.map(keyExtractor))
        : new Set<string>();
      onSelectionChange(newSelection);
    }
  };

  const handleSelectRow = (key: string, checked: boolean) => {
    if (onSelectionChange) {
      const newSelection = new Set(selectedKeys);
      if (checked) {
        newSelection.add(key);
      } else {
        newSelection.delete(key);
      }
      onSelectionChange(newSelection);
    }
  };

  if (isLoading) {
    return (
      <div className="p-8 text-center text-muted-foreground">
        Loading...
      </div>
    );
  }

  if (data.length === 0 && emptyState) {
    return <div className="p-8">{emptyState}</div>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr className="border-b">
            {selectable && (
              <th className="p-3 text-left">
                <input
                  type="checkbox"
                  checked={data.length > 0 && selectedKeys.size === data.length}
                  onChange={(e) => handleSelectAll(e.target.checked)}
                  className="rounded"
                />
              </th>
            )}
            {columns.map((column) => (
              <th key={column.key} className="p-3 text-left font-medium">
                {column.header}
              </th>
            ))}
            {rowActions && <th className="p-3 text-right">Actions</th>}
          </tr>
        </thead>
        <tbody>
          {data.map((item) => {
            const key = keyExtractor(item);
            const isSelected = selectedKeys.has(key);
            return (
              <tr
                key={key}
                className={`border-b hover:bg-muted/50 cursor-pointer ${
                  isSelected ? 'bg-muted' : ''
                }`}
                onClick={() => onRowClick?.(item)}
              >
                {selectable && (
                  <td className="p-3">
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={(e) => {
                        e.stopPropagation();
                        handleSelectRow(key, e.target.checked);
                      }}
                      className="rounded"
                    />
                  </td>
                )}
                {columns.map((column) => (
                  <td key={column.key} className="p-3">
                    {column.render(item)}
                  </td>
                ))}
                {rowActions && (
                  <td className="p-3 text-right" onClick={(e) => e.stopPropagation()}>
                    {rowActions(item)}
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
