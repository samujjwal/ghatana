import React from 'react';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';

export interface Column<T> {
  id: string;
  header: string;
  width?: number | string;
  align?: 'left' | 'right' | 'center';
  render?: (row: T) => React.ReactNode;
  accessor?: (row: T) => React.ReactNode;
}

export interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  getRowId?: (row: T, index: number) => string | number;
  emptyState?: React.ReactNode;
  dense?: boolean;
  onRowClick?: (row: T) => void;
}

export function DataTable<T>({
  columns,
  rows,
  getRowId,
  emptyState,
  dense,
  onRowClick,
}: DataTableProps<T>) {
  if (!rows.length) {
    return (
      <Box
        sx={{
          border: '1px dashed',
          borderColor: 'divider',
          borderRadius: 2,
          py: 6,
          textAlign: 'center',
          color: 'text.secondary',
        }}
      >
        {emptyState ?? (
          <>
            <Typography variant="subtitle1" fontWeight={600}>
              No data available
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Adjust filters or check back later.
            </Typography>
          </>
        )}
      </Box>
    );
  }

  return (
    <TableContainer>
      <Table size={dense ? 'small' : 'medium'}>
        <TableHead>
          <TableRow>
            {columns.map((column) => (
              <TableCell
                key={column.id}
                align={column.align ?? 'left'}
                sx={{ width: column.width, color: 'text.secondary', fontWeight: 600 }}
              >
                {column.header}
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row, index) => {
            const handleActivate = () => onRowClick?.(row);
            const handleKeyDown = (event: React.KeyboardEvent<HTMLTableRowElement>) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                handleActivate();
              }
            };

            return (
              <TableRow
                hover
                key={getRowId ? getRowId(row, index) : index}
                sx={{
                  '&:last-child td': { borderBottom: 0 },
                  cursor: onRowClick ? 'pointer' : 'default',
                }}
                onClick={handleActivate}
                onKeyDown={onRowClick ? handleKeyDown : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                role={onRowClick ? 'button' : undefined}
                aria-label={onRowClick ? 'Select row' : undefined}
              >
                {columns.map((column) => (
                  <TableCell key={column.id} align={column.align ?? 'left'}>
                    {column.render
                      ? column.render(row)
                      : column.accessor
                        ? column.accessor(row)
                        : (row as any)[column.id]}
                  </TableCell>
                ))}
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default DataTable;
