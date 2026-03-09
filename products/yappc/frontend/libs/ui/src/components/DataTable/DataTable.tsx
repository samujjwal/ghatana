import { useState, useMemo } from 'react';

/**
 *
 */
export interface Column<T = unknown> {
  id: string;
  header: string;
  accessor: keyof T | ((row: T) => any);
  sortable?: boolean;
  filterable?: boolean;
  width?: string;
  align?: 'left' | 'center' | 'right';
  render?: (value: unknown, row: T) => React.ReactNode;
}

/**
 *
 */
export interface DataTableProps<T = unknown> {
  data: T[];
  columns: Column<T>[];
  sortable?: boolean;
  filterable?: boolean;
  paginated?: boolean;
  pageSize?: number;
  selectable?: boolean;
  onRowClick?: (row: T) => void;
  onSelectionChange?: (selected: T[]) => void;
  loading?: boolean;
  emptyMessage?: string;
}

/**
 * DataTable component with sorting, filtering, and pagination
 * 
 * @example
 * ```tsx
 * <DataTable
 *   data={users}
 *   columns={[
 *     { id: 'name', header: 'Name', accessor: 'name', sortable: true },
 *     { id: 'email', header: 'Email', accessor: 'email' },
 *   ]}
 *   paginated
 *   pageSize={10}
 * />
 * ```
 */
export function DataTable<T extends Record<string, unknown>>({
  data,
  columns,
  sortable = false,
  filterable = false,
  paginated = false,
  pageSize = 10,
  selectable = false,
  onRowClick,
  onSelectionChange,
  loading = false,
  emptyMessage = 'No data available',
}: DataTableProps<T>) {
  const [sortColumn, setSortColumn] = useState<string | null>(null);
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
  const [filterText, setFilterText] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [selectedRows, setSelectedRows] = useState<Set<number>>(new Set());

  // Filtering
  const filteredData = useMemo(() => {
    if (!filterable || !filterText) return data;
    
    return data.filter(row =>
      columns.some(col => {
        const value = typeof col.accessor === 'function'
          ? col.accessor(row)
          : row[col.accessor];
        return String(value).toLowerCase().includes(filterText.toLowerCase());
      })
    );
  }, [data, columns, filterText, filterable]);

  // Sorting
  const sortedData = useMemo(() => {
    if (!sortColumn) return filteredData;
    
    return [...filteredData].sort((a, b) => {
      const column = columns.find(col => col.id === sortColumn);
      if (!column) return 0;
      
      const aValue = typeof column.accessor === 'function'
        ? column.accessor(a)
        : a[column.accessor];
      const bValue = typeof column.accessor === 'function'
        ? column.accessor(b)
        : b[column.accessor];
      
      if (aValue < bValue) return sortDirection === 'asc' ? -1 : 1;
      if (aValue > bValue) return sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
  }, [filteredData, sortColumn, sortDirection, columns]);

  // Pagination
  const paginatedData = useMemo(() => {
    if (!paginated) return sortedData;
    
    const start = (currentPage - 1) * pageSize;
    const end = start + pageSize;
    return sortedData.slice(start, end);
  }, [sortedData, paginated, currentPage, pageSize]);

  const totalPages = Math.ceil(sortedData.length / pageSize);

  const handleSort = (columnId: string) => {
    if (sortColumn === columnId) {
      setSortDirection(prev => prev === 'asc' ? 'desc' : 'asc');
    } else {
      setSortColumn(columnId);
      setSortDirection('asc');
    }
  };

  const handleSelectRow = (index: number) => {
    const newSelected = new Set(selectedRows);
    if (newSelected.has(index)) {
      newSelected.delete(index);
    } else {
      newSelected.add(index);
    }
    setSelectedRows(newSelected);
    
    const selectedData = Array.from(newSelected).map(i => sortedData[i]);
    onSelectionChange?.(selectedData);
  };

  const handleSelectAll = () => {
    if (selectedRows.size === paginatedData.length) {
      setSelectedRows(new Set());
      onSelectionChange?.([]);
    } else {
      const allIndices = paginatedData.map((_, i) => (currentPage - 1) * pageSize + i);
      setSelectedRows(new Set(allIndices));
      onSelectionChange?.(paginatedData);
    }
  };

  const tableStyle: React.CSSProperties = {
    width: '100%',
    borderCollapse: 'collapse',
    border: '1px solid var(--color-grey-300, #e0e0e0)',
  };

  const thStyle: React.CSSProperties = {
    padding: '12px',
    textAlign: 'left',
    backgroundColor: 'var(--color-grey-100, #f5f5f5)',
    borderBottom: '2px solid var(--color-grey-300, #e0e0e0)',
    fontWeight: 600,
    fontSize: '0.875rem',
    color: 'var(--color-text-primary, #424242)',
  };

  const tdStyle: React.CSSProperties = {
    padding: '12px',
    borderBottom: '1px solid var(--color-grey-300, #e0e0e0)',
    fontSize: '0.875rem',
    color: 'var(--color-text-primary, #212121)',
  };

  const rowStyle: React.CSSProperties = {
    cursor: onRowClick ? 'pointer' : 'default',
    transition: 'background-color 0.2s',
  };

  if (loading) {
    return <div style={{ padding: '2rem', textAlign: 'center' }}>Loading...</div>;
  }

  if (paginatedData.length === 0) {
    return <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--color-text-secondary, #757575)' }}>{emptyMessage}</div>;
  }

  return (
    <div>
      {filterable && (
        <div style={{ marginBottom: '1rem' }}>
          <input
            type="text"
            placeholder="Search..."
            value={filterText}
            onChange={(e) => setFilterText(e.target.value)}
            style={{
              padding: '0.5rem 1rem',
              border: '1px solid var(--color-grey-300, #e0e0e0)',
              borderRadius: '0.375rem',
              width: '300px',
            }}
          />
        </div>
      )}

      <table style={tableStyle}>
        <thead>
          <tr>
            {selectable && (
              <th style={{ ...thStyle, width: '50px' }}>
                <input
                  type="checkbox"
                  checked={selectedRows.size === paginatedData.length && paginatedData.length > 0}
                  onChange={handleSelectAll}
                />
              </th>
            )}
            {columns.map(column => (
              <th
                key={column.id}
                style={{
                  ...thStyle,
                  width: column.width,
                  textAlign: column.align || 'left',
                  cursor: sortable && column.sortable !== false ? 'pointer' : 'default',
                }}
                onClick={() => sortable && column.sortable !== false && handleSort(column.id)}
              >
                {column.header}
                {sortable && column.sortable !== false && sortColumn === column.id && (
                  <span style={{ marginLeft: '0.5rem' }}>
                    {sortDirection === 'asc' ? '↑' : '↓'}
                  </span>
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {paginatedData.map((row, rowIndex) => {
            const actualIndex = (currentPage - 1) * pageSize + rowIndex;
            return (
              <tr
                key={rowIndex}
                style={{
                  ...rowStyle,
                  backgroundColor: selectedRows.has(actualIndex) ? 'var(--color-primary-light, #e3f2fd)' : 'transparent',
                }}
                onClick={() => onRowClick?.(row)}
                onMouseEnter={(e) => {
                  if (onRowClick) {
                    e.currentTarget.style.backgroundColor = selectedRows.has(actualIndex) ? 'var(--color-primary-lighter, #bbdefb)' : 'var(--color-grey-100, #f5f5f5)';
                  }
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = selectedRows.has(actualIndex) ? 'var(--color-primary-light, #e3f2fd)' : 'transparent';
                }}
              >
                {selectable && (
                  <td style={tdStyle}>
                    <input
                      type="checkbox"
                      checked={selectedRows.has(actualIndex)}
                      onChange={() => handleSelectRow(actualIndex)}
                      onClick={(e) => e.stopPropagation()}
                    />
                  </td>
                )}
                {columns.map(column => {
                  const value = typeof column.accessor === 'function'
                    ? column.accessor(row)
                    : row[column.accessor];
                  
                  return (
                    <td
                      key={column.id}
                      style={{ ...tdStyle, textAlign: column.align || 'left' }}
                    >
                      {column.render ? column.render(value, row) : value}
                    </td>
                  );
                })}
              </tr>
            );
          })}
        </tbody>
      </table>

      {paginated && totalPages > 1 && (
        <div style={{ marginTop: '1rem', display: 'flex', justifyContent: 'center', gap: '0.5rem' }}>
          <button
            onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
            disabled={currentPage === 1}
            style={{
              padding: '0.5rem 1rem',
              border: '1px solid var(--color-grey-300, #e0e0e0)',
              borderRadius: '0.375rem',
              cursor: currentPage === 1 ? 'not-allowed' : 'pointer',
              opacity: currentPage === 1 ? 0.5 : 1,
            }}
          >
            Previous
          </button>
          <span style={{ padding: '0.5rem 1rem', display: 'flex', alignItems: 'center' }}>
            Page {currentPage} of {totalPages}
          </span>
          <button
            onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
            disabled={currentPage === totalPages}
            style={{
              padding: '0.5rem 1rem',
              border: '1px solid var(--color-grey-300, #e0e0e0)',
              borderRadius: '0.375rem',
              cursor: currentPage === totalPages ? 'not-allowed' : 'pointer',
              opacity: currentPage === totalPages ? 0.5 : 1,
            }}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
