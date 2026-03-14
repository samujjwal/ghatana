import React from 'react';
import { tokens } from '@ghatana/tokens';
import { sxToStyle, type SxProps } from '../utils/sx';

export interface TableColumn<T = unknown> {
  key: string;
  header: string;
  accessor?: (row: T) => React.ReactNode;
  sortable?: boolean;
  width?: string;
  align?: 'left' | 'center' | 'right';
}

export interface TableDataProps<T = unknown> {
  /** Table columns configuration */
  columns: TableColumn<T>[];
  /** Table data */
  data: T[];
  /** Enable row selection */
  selectable?: boolean;
  /** Selected row keys */
  selectedRows?: Set<string | number>;
  /** Row selection handler */
  onRowSelect?: (rowKey: string | number) => void;
  /** Row key accessor */
  rowKey?: keyof T | ((row: T) => string | number);
  /** Enable sorting */
  sortable?: boolean;
  /** Current sort column */
  sortColumn?: string;
  /** Current sort direction */
  sortDirection?: 'asc' | 'desc';
  /** Sort handler */
  onSort?: (column: string, direction: 'asc' | 'desc') => void;
  /** Enable striped rows */
  striped?: boolean;
  /** Enable hover effect */
  hoverable?: boolean;
  /** Table size */
  size?: 'sm' | 'md' | 'lg' | 'small' | 'medium' | 'large';
  /** Loading state */
  loading?: boolean;
  /** Empty state message */
  emptyMessage?: string;
  /** Additional class name */
  className?: string;
}

export interface TableMarkupProps extends React.TableHTMLAttributes<HTMLTableElement> {
  /** Render raw table markup (e.g., <TableHead/>, <TableRow/>). */
  children: React.ReactNode;
  /** Table size (compatibility with data-driven API + MUI aliases). */
  size?: 'sm' | 'md' | 'lg' | 'small' | 'medium' | 'large';
  columns?: never;
  data?: never;
}

export type TableProps<T = unknown> = TableDataProps<T> | TableMarkupProps;

export const TableHead: React.FC<React.HTMLAttributes<HTMLTableSectionElement>> = (props) => (
  <thead {...props} />
);
TableHead.displayName = 'TableHead';

export const TableBody: React.FC<React.HTMLAttributes<HTMLTableSectionElement>> = (props) => (
  <tbody {...props} />
);
TableBody.displayName = 'TableBody';

export interface TableRowProps extends React.HTMLAttributes<HTMLTableRowElement> {
  /** MUI-like prop (accepted for compatibility; hover styling is handled by the table). */
  hover?: boolean;
  /** MUI-like prop (accepted for compatibility). */
  selected?: boolean;
}

export const TableRow: React.FC<TableRowProps> = (props) => {
  const { hover: _hover, selected: _selected, ...rest } = props;
  return <tr {...rest} />;
};
TableRow.displayName = 'TableRow';

export const TableCell: React.FC<
  React.TdHTMLAttributes<HTMLTableCellElement> & { component?: 'td' | 'th'; sx?: SxProps }
> = (props) => {
  const { component = 'td', sx, style, ...rest } = props;
  const Cell = component;
  return <Cell {...rest} style={{ ...sxToStyle(sx), ...(style ?? {}) }} />;
};
TableCell.displayName = 'TableCell';

export const TableContainer: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({ style, ...props }) => (
  <div style={{ overflowX: 'auto', ...style }} {...props} />
);
TableContainer.displayName = 'TableContainer';

export function Table<T = unknown>(props: TableDataProps<T>): React.ReactElement;
export function Table(props: TableMarkupProps): React.ReactElement;

export function Table<T = unknown>(props: TableProps<T>) {
  const resolveSize = (value: TableDataProps<T>['size'] | TableMarkupProps['size']): 'sm' | 'md' | 'lg' => {
    if (value === 'small') return 'sm';
    if (value === 'medium') return 'md';
    if (value === 'large') return 'lg';
    return value ?? 'md';
  };

  const tableStyles: React.CSSProperties = {
    width: '100%',
    borderCollapse: 'collapse',
    fontFamily: tokens.typography.fontFamily.sans,
    fontSize: tokens.typography.fontSize.sm,
    backgroundColor: tokens.colors.white,
    border: `${tokens.borderWidth[1]} solid ${tokens.colors.neutral[200]}`,
    borderRadius: tokens.borderRadius.lg,
    overflow: 'hidden',
  };

  const isDataProps = (value: TableProps<T>): value is TableDataProps<T> => {
    const v = value as unknown as { columns?: unknown; data?: unknown };
    return Array.isArray(v.columns) && Array.isArray(v.data);
  };

  if (!isDataProps(props)) {
    const { className, children, ...rest } = props;
    return (
      <div style={{ overflowX: 'auto' }} className={className}>
        <table style={tableStyles} {...rest}>
          {children}
        </table>
      </div>
    );
  }

  const {
    columns,
    data,
    selectable = false,
    selectedRows = new Set(),
    onRowSelect,
    rowKey = (row: unknown) => (row && (row as Record<string, unknown>).id ? (row as Record<string, unknown>).id : '') as string | number,
    sortable = false,
    sortColumn,
    sortDirection = 'asc',
    onSort,
    striped = false,
    hoverable = true,
    size = 'md',
    loading = false,
    emptyMessage = 'No data available',
    className,
  } = props;
  const getRowKey = (row: T, index: number): string | number => {
    if (typeof rowKey === 'function') {
      return rowKey(row);
    }
    return (row[rowKey] as string | number) ?? index;
  };

  const handleSort = (column: TableColumn<T>) => {
    if (!sortable || !column.sortable || !onSort) return;
    const newDirection = sortColumn === column.key && sortDirection === 'asc' ? 'desc' : 'asc';
    onSort(column.key, newDirection);
  };

  const sizeConfig = {
    sm: { padding: tokens.spacing[2] },
    md: { padding: tokens.spacing[3] },
    lg: { padding: tokens.spacing[4] },
  };

  const resolvedSize = resolveSize(size);
  const cellPadding = sizeConfig[resolvedSize].padding;

  const theadStyles: React.CSSProperties = {
    backgroundColor: tokens.colors.neutral[50],
    borderBottom: `${tokens.borderWidth[2]} solid ${tokens.colors.neutral[200]}`,
  };

  const thStyles: React.CSSProperties = {
    padding: cellPadding,
    textAlign: 'left',
    fontWeight: tokens.typography.fontWeight.semibold,
    color: tokens.colors.neutral[700],
    whiteSpace: 'nowrap',
  };

  const tbodyStyles: React.CSSProperties = {};

  const getCellValue = (row: T, column: TableColumn<T>) => {
    if (column.accessor) {
      return column.accessor(row);
    }
    return (row as Record<string, React.ReactNode>)[column.key];
  };

  if (loading) {
    return (
      <div style={{ padding: tokens.spacing[8], textAlign: 'center', color: tokens.colors.neutral[500] }}>
        Loading...
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div style={{ padding: tokens.spacing[8], textAlign: 'center', color: tokens.colors.neutral[500] }}>
        {emptyMessage}
      </div>
    );
  }

  return (
    <div style={{ overflowX: 'auto' }} className={className}>
      <table style={tableStyles}>
        <thead style={theadStyles}>
          <tr>
            {selectable && (
              <th style={{ ...thStyles, width: '40px' }}>
                <input type="checkbox" aria-label="Select all rows" />
              </th>
            )}
            {columns.map((column) => (
              <th
                key={column.key}
                style={{
                  ...thStyles,
                  width: column.width,
                  textAlign: column.align || 'left',
                  cursor: sortable && column.sortable ? 'pointer' : 'default',
                }}
                onClick={() => handleSort(column)}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: tokens.spacing[1] }}>
                  {column.header}
                  {sortable && column.sortable && sortColumn === column.key && (
                    <span style={{ fontSize: '0.75em' }}>
                      {sortDirection === 'asc' ? '▲' : '▼'}
                    </span>
                  )}
                </div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody style={tbodyStyles}>
          {data.map((row, rowIndex) => {
            const key = getRowKey(row, rowIndex);
            const isSelected = selectedRows.has(key);
            const isStriped = striped && rowIndex % 2 === 1;

            const rowStyles: React.CSSProperties = {
              backgroundColor: isSelected
                ? tokens.colors.primary[50]
                : isStriped
                  ? tokens.colors.neutral[25]
                  : tokens.colors.white,
              borderBottom: `${tokens.borderWidth[1]} solid ${tokens.colors.neutral[200]}`,
              transition: `background-color ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
            };

            return (
              <tr
                key={key}
                style={rowStyles}
                onMouseEnter={(e) => {
                  if (hoverable) {
                    e.currentTarget.style.backgroundColor = isSelected
                      ? tokens.colors.primary[100]
                      : tokens.colors.neutral[50];
                  }
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = isSelected
                    ? tokens.colors.primary[50]
                    : isStriped
                      ? tokens.colors.neutral[25]
                      : tokens.colors.white;
                }}
              >
                {selectable && (
                  <td style={{ padding: cellPadding }}>
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => onRowSelect?.(key)}
                      aria-label={`Select row ${key}`}
                    />
                  </td>
                )}
                {columns.map((column) => (
                  <td
                    key={column.key}
                    style={{
                      padding: cellPadding,
                      textAlign: column.align || 'left',
                      color: tokens.colors.neutral[900],
                    }}
                  >
                    {getCellValue(row, column)}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

Table.displayName = 'Table';
