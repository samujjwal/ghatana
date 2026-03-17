/**
 * DataTable Component
 *
 * Sortable and filterable data table with pagination.
 *
 * @module DevSecOps/DataTable
 */

import { Box, Surface as Paper, Typography } from '@ghatana/ui';
import {
  Checkbox,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TableSortLabel,
} from '@mui/material';
import { useMemo, useState } from 'react';

import { DataTableUtils } from './utils';

import type { DataTableProps, DataTableColumn, SortConfig } from './types';
import type { Item } from '@ghatana/yappc-types/devsecops';

/**
 * DataTable component for displaying tabular data with sorting, filtering, and pagination
 *
 * @example
 * ```tsx
 * <DataTable
 *   data={items}
 *   columns={columns}
 *   sortConfig={sortConfig}
 *   onSortChange={setSortConfig}
 *   onRowClick={handleRowClick}
 * />
 * ```
 */
export function DataTable<T = Item>({
  data,
  columns,
  sortConfig,
  onSortChange,
  filterConfig,
  paginationConfig,
  onPaginationChange,
  onRowClick,
  selectionMode = 'none',
  selectedRows = [],
  onSelectionChange,
  loading = false,
  showHeader = true,
  showPagination = true,
  dense = false,
  striped = true,
  hoverable = true,
  emptyMessage = 'No data available',
  className,
  visibleColumns,
}: DataTableProps<T>) {
  // Local pagination state if not controlled
  const [localPage, setLocalPage] = useState(0);
  const [localRowsPerPage, setLocalRowsPerPage] = useState(10);

  const page = paginationConfig?.page ?? localPage;
  const rowsPerPage = paginationConfig?.rowsPerPage ?? localRowsPerPage;

  // Filter columns by visibility
  const displayColumns = useMemo(() => {
    if (!visibleColumns) return columns;
    return columns.filter((col) => visibleColumns.includes(col.id));
  }, [columns, visibleColumns]);

  // Process data (filter, sort, paginate)
  const { processedData, totalRows } = useMemo(() => {
    const getFieldValue = (row: T, field: string): unknown => {
      const column = columns.find((c) => c.id === field);
      if (!column?.field) return '';

      return DataTableUtils.getNestedValue(row, column.field as string);
    };

    return DataTableUtils.processData(
      data,
      sortConfig,
      filterConfig,
      page,
      rowsPerPage,
      getFieldValue
    );
  }, [data, sortConfig, filterConfig, page, rowsPerPage, columns]);

  // Handle sort
  const handleSort = (columnId: string) => {
    const column = columns.find((c) => c.id === columnId);
    if (!column?.sortable) return;

    const newConfig: SortConfig = {
      column: columnId,
      direction:
        sortConfig?.column === columnId && sortConfig.direction === 'asc' ? 'desc' : 'asc',
    };

    onSortChange?.(newConfig);
  };

  // Handle pagination
  const handleChangePage = (_: unknown, newPage: number) => {
    if (onPaginationChange && paginationConfig) {
      onPaginationChange({ ...paginationConfig, page: newPage });
    } else {
      setLocalPage(newPage);
    }
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    const newRowsPerPage = parseInt(event.target.value, 10);
    if (onPaginationChange && paginationConfig) {
      onPaginationChange({
        ...paginationConfig,
        rowsPerPage: newRowsPerPage,
        page: 0,
      });
    } else {
      setLocalRowsPerPage(newRowsPerPage);
      setLocalPage(0);
    }
  };

  // Handle row selection
  const handleSelectAll = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.checked) {
      const allIds = processedData.map((row) => DataTableUtils.getRowId(row));
      onSelectionChange?.(allIds);
    } else {
      onSelectionChange?.([]);
    }
  };

  const handleSelectRow = (rowId: string) => {
    if (selectionMode === 'single') {
      onSelectionChange?.([rowId]);
    } else if (selectionMode === 'multiple') {
      const newSelected = selectedRows.includes(rowId)
        ? selectedRows.filter((id) => id !== rowId)
        : [...selectedRows, rowId];
      onSelectionChange?.(newSelected);
    }
  };

  // Render cell content
  const renderCell = (column: DataTableColumn<T>, row: T) => {
    const value = column.field
      ? DataTableUtils.getNestedValue(row, column.field as string)
      : undefined;

    if (column.render) {
      return column.render(value, row);
    }

    if (column.format) {
      return column.format(value);
    }

    return DataTableUtils.formatValue(value);
  };

  // Loading state
  if (loading) {
    return (
      <TableContainer component={Paper} className={className}>
        <Table size={dense ? 'small' : 'medium'}>
          {showHeader && (
            <TableHead>
              <TableRow>
                {selectionMode !== 'none' && <TableCell padding="checkbox" />}
                {displayColumns.map((column) => (
                  <TableCell key={column.id} align={column.align || 'left'}>
                    <Skeleton width="80%" />
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
          )}
          <TableBody>
            {[...Array(5)].map((_, index) => (
              <TableRow key={index}>
                {selectionMode !== 'none' && <TableCell padding="checkbox" />}
                {displayColumns.map((column) => (
                  <TableCell key={column.id}>
                    <Skeleton />
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    );
  }

  // Empty state
  if (processedData.length === 0) {
    return (
      <TableContainer component={Paper} className={className}>
        <Box className="p-8 text-center">
          <Typography as="p" color="text.secondary">
            {emptyMessage}
          </Typography>
        </Box>
      </TableContainer>
    );
  }

  const isAllSelected =
    selectionMode === 'multiple' &&
    processedData.length > 0 &&
    processedData.every((row) => selectedRows.includes(DataTableUtils.getRowId(row)));

  return (
    <Paper className={className}>
      <TableContainer>
        <Table size={dense ? 'small' : 'medium'}>
          {showHeader && (
            <TableHead>
              <TableRow>
                {selectionMode !== 'none' && (
                  <TableCell padding="checkbox">
                    {selectionMode === 'multiple' && (
                      <Checkbox
                        indeterminate={
                          selectedRows.length > 0 &&
                          selectedRows.length < processedData.length
                        }
                        checked={isAllSelected}
                        onChange={handleSelectAll}
                      />
                    )}
                  </TableCell>
                )}
                {displayColumns.map((column) => (
                  <TableCell
                    key={column.id}
                    align={column.align || 'left'}
                    style={{ width: column.width }}
                  >
                    {column.sortable !== false ? (
                      <TableSortLabel
                        active={sortConfig?.column === column.id}
                        direction={
                          sortConfig?.column === column.id
                            ? sortConfig.direction
                            : 'asc'
                        }
                        onClick={() => handleSort(column.id)}
                      >
                        {column.label}
                      </TableSortLabel>
                    ) : (
                      column.label
                    )}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
          )}
          <TableBody>
            {processedData.map((row, index) => {
              const rowId = DataTableUtils.getRowId(row);
              const isSelected = selectedRows.includes(rowId);

              return (
                <TableRow
                  key={rowId}
                  hover={hoverable}
                  selected={isSelected}
                  onClick={() => onRowClick?.(row)}
                  className={`${onRowClick ? 'cursor-pointer' : 'cursor-default'} ${striped && index % 2 === 1 ? 'bg-gray-50 dark:bg-gray-800' : ''}`}
                >
                  {selectionMode !== 'none' && (
                    <TableCell padding="checkbox">
                      <Checkbox
                        checked={isSelected}
                        onChange={() => handleSelectRow(rowId)}
                        onClick={(e) => e.stopPropagation()}
                      />
                    </TableCell>
                  )}
                  {displayColumns.map((column) => (
                    <TableCell key={column.id} align={column.align || 'left'}>
                      {renderCell(column, row)}
                    </TableCell>
                  ))}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>

      {showPagination && (
        <TablePagination
          component="div"
          count={totalRows}
          page={page}
          onPageChange={handleChangePage}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={handleChangeRowsPerPage}
          rowsPerPageOptions={[5, 10, 25, 50, 100]}
        />
      )}
    </Paper>
  );
}
