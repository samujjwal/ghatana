/**
 * Export Toolbar Component
 *
 * Toolbar with export actions for DataTable.
 *
 * @module DevSecOps/DataTable/ExportToolbar
 */

import { Code as CodeIcon } from 'lucide-react';
import { Copy as ContentCopyIcon } from 'lucide-react';
import { FileText as DescriptionIcon } from 'lucide-react';
import { Download as DownloadIcon } from 'lucide-react';
import { Printer as PrintIcon } from 'lucide-react';
import { IconButton, Menu, MenuItem, Tooltip } from '@ghatana/ui';
import { ListItemIcon, ListItemText } from '@mui/material';
import { useState } from 'react';

import { DataTableExport } from './export';

import type { DataTableColumn } from './types';

/**
 * Export toolbar props
 */
export interface ExportToolbarProps<T = unknown> {
  /**
   * Data to export
   */
  data: T[];

  /**
   * Column configuration
   */
  columns: DataTableColumn<T>[];

  /**
   * Base filename for exports
   * @default 'export'
   */
  filename?: string;

  /**
   * Document title for print
   * @default 'Table Data'
   */
  title?: string;

  /**
   * Show CSV export option
   * @default true
   */
  showCSV?: boolean;

  /**
   * Show JSON export option
   * @default true
   */
  showJSON?: boolean;

  /**
   * Show copy to clipboard option
   * @default true
   */
  showCopy?: boolean;

  /**
   * Show print option
   * @default true
   */
  showPrint?: boolean;

  /**
   * Callback when export succeeds
   */
  onExport?: (format: 'csv' | 'json' | 'copy' | 'print') => void;

  /**
   * Callback when export fails
   */
  onError?: (error: Error) => void;
}

/**
 * Export toolbar component
 *
 * Provides export actions (CSV, JSON, copy, print) for DataTable.
 *
 * @example
 * ```tsx
 * <ExportToolbar
 *   data={items}
 *   columns={columns}
 *   filename="my-data"
 *   onExport={(format) => console.log(`Exported as ${format}`)}
 * />
 * ```
 */
export function ExportToolbar<T = unknown>({
  data,
  columns,
  filename = 'export',
  title = 'Table Data',
  showCSV = true,
  showJSON = true,
  showCopy = true,
  showPrint = true,
  onExport,
  onError,
}: ExportToolbarProps<T>) {
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleExportCSV = () => {
    try {
      DataTableExport.exportCSV(data, columns, `${filename}.csv`);
      onExport?.('csv');
    } catch (error) {
      onError?.(error as Error);
    }
    handleClose();
  };

  const handleExportJSON = () => {
    try {
      DataTableExport.exportJSON(data, columns, `${filename}.json`);
      onExport?.('json');
    } catch (error) {
      onError?.(error as Error);
    }
    handleClose();
  };

  const handleCopy = async () => {
    try {
      await DataTableExport.copyToClipboard(data, columns);
      onExport?.('copy');
    } catch (error) {
      onError?.(error as Error);
    }
    handleClose();
  };

  const handlePrint = () => {
    try {
      DataTableExport.print(data, columns, title);
      onExport?.('print');
    } catch (error) {
      onError?.(error as Error);
    }
    handleClose();
  };

  return (
    <>
      <Tooltip title="Export">
        <IconButton
          onClick={handleClick}
          aria-label="export"
          aria-controls={open ? 'export-menu' : undefined}
          aria-haspopup="true"
          aria-expanded={open ? 'true' : undefined}
        >
          <DownloadIcon />
        </IconButton>
      </Tooltip>

      <Menu
        id="export-menu"
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        MenuListProps={{
          'aria-labelledby': 'export-button',
          dense: true,
        }}
      >
        {showCSV && (
          <MenuItem onClick={handleExportCSV}>
            <ListItemIcon>
              <DescriptionIcon size={16} />
            </ListItemIcon>
            <ListItemText>Export as CSV</ListItemText>
          </MenuItem>
        )}

        {showJSON && (
          <MenuItem onClick={handleExportJSON}>
            <ListItemIcon>
              <CodeIcon size={16} />
            </ListItemIcon>
            <ListItemText>Export as JSON</ListItemText>
          </MenuItem>
        )}

        {showCopy && (
          <MenuItem onClick={handleCopy}>
            <ListItemIcon>
              <ContentCopyIcon size={16} />
            </ListItemIcon>
            <ListItemText>Copy to Clipboard</ListItemText>
          </MenuItem>
        )}

        {showPrint && (
          <MenuItem onClick={handlePrint}>
            <ListItemIcon>
              <PrintIcon size={16} />
            </ListItemIcon>
            <ListItemText>Print</ListItemText>
          </MenuItem>
        )}
      </Menu>
    </>
  );
}
