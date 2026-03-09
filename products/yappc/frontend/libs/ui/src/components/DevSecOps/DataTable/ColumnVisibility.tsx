/**
 * Column Visibility Control Component
 *
 * Allows users to show/hide table columns.
 *
 * @module DevSecOps/DataTable/ColumnVisibility
 */

import { Columns as ViewColumnIcon } from 'lucide-react';
import { IconButton, Menu, MenuItem, Checkbox, FormControlLabel, Typography, Divider, Box } from '@ghatana/ui';
import { useState } from 'react';

import type { DataTableColumn } from './types';

/**
 * Column visibility control props
 */
export interface ColumnVisibilityProps<T = unknown> {
  /**
   * All available columns
   */
  columns: DataTableColumn<T>[];

  /**
   * IDs of visible columns
   */
  visibleColumns: string[];

  /**
   * Callback when visibility changes
   */
  onVisibilityChange: (visibleColumns: string[]) => void;

  /**
   * Button tooltip
   * @default 'Column Visibility'
   */
  tooltip?: string;

  /**
   * Menu title
   * @default 'Show/Hide Columns'
   */
  title?: string;

  /**
   * Show "Select All" option
   * @default true
   */
  showSelectAll?: boolean;
}

/**
 * Column visibility control component
 *
 * Provides a dropdown menu to show/hide table columns.
 *
 * @example
 * ```tsx
 * <ColumnVisibility
 *   columns={columns}
 *   visibleColumns={visibleColumns}
 *   onVisibilityChange={setVisibleColumns}
 * />
 * ```
 */
export function ColumnVisibility<T = unknown>({
  columns,
  visibleColumns,
  onVisibilityChange,
  tooltip = 'Column Visibility',
  title = 'Show/Hide Columns',
  showSelectAll = true,
}: ColumnVisibilityProps<T>) {
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleToggleColumn = (columnId: string) => {
    const newVisibleColumns = visibleColumns.includes(columnId)
      ? visibleColumns.filter((id) => id !== columnId)
      : [...visibleColumns, columnId];

    onVisibilityChange(newVisibleColumns);
  };

  const handleSelectAll = () => {
    if (visibleColumns.length === columns.length) {
      // Deselect all
      onVisibilityChange([]);
    } else {
      // Select all
      onVisibilityChange(columns.map((col) => col.id));
    }
  };

  const allSelected = visibleColumns.length === columns.length;
  const someSelected = visibleColumns.length > 0 && visibleColumns.length < columns.length;

  return (
    <>
      <IconButton
        onClick={handleClick}
        title={tooltip}
        aria-label={tooltip}
        aria-controls={open ? 'column-visibility-menu' : undefined}
        aria-haspopup="true"
        aria-expanded={open ? 'true' : undefined}
      >
        <ViewColumnIcon />
      </IconButton>

      <Menu
        id="column-visibility-menu"
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        MenuListProps={{
          'aria-labelledby': 'column-visibility-button',
          dense: true,
        }}
        PaperProps={{
          sx: {
            minWidth: 200,
            maxHeight: 400,
          },
        }}
      >
        <Box className="px-4 py-2">
          <Typography as="p" className="text-sm font-medium" color="text.secondary">
            {title}
          </Typography>
        </Box>

        {showSelectAll && (
          <>
            <MenuItem onClick={handleSelectAll}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={allSelected}
                    indeterminate={someSelected}
                    onChange={handleSelectAll}
                  />
                }
                label="Select All"
              />
            </MenuItem>
            <Divider />
          </>
        )}

        {columns.map((column) => (
          <MenuItem
            key={column.id}
            onClick={() => handleToggleColumn(column.id)}
          >
            <FormControlLabel
              control={
                <Checkbox
                  checked={visibleColumns.includes(column.id)}
                  onChange={() => handleToggleColumn(column.id)}
                />
              }
              label={column.label}
            />
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}
