/**
 * DataTable Export Utilities
 *
 * Functions for exporting table data to various formats.
 *
 * @module DevSecOps/DataTable/export
 */

import { DataTableUtils } from './utils';

import type { DataTableColumn } from './types';

/**
 * Export utilities for DataTable
 */
export class DataTableExport {
  /**
   * Convert data to CSV format
   *
   * @param data - Data to export
   * @param columns - Column configuration
   * @param includeHeaders - Include header row
   * @returns CSV string
   */
  static toCSV<T>(
    data: T[],
    columns: DataTableColumn<T>[],
    includeHeaders = true
  ): string {
    const rows: string[] = [];

    // Add headers
    if (includeHeaders) {
      const headers = columns.map((col) => this.escapeCSV(col.label));
      rows.push(headers.join(','));
    }

    // Add data rows
    for (const row of data) {
      const values = columns.map((col) => {
        if (!col.field) return '';

        const value = DataTableUtils.getNestedValue(row, col.field as string);

        // Use format function if provided
        if (col.format) {
          return this.escapeCSV(col.format(value));
        }

        // Default formatting
        return this.escapeCSV(DataTableUtils.formatValue(value));
      });

      rows.push(values.join(','));
    }

    return rows.join('\n');
  }

  /**
   * Escape CSV value (handle commas, quotes, newlines)
   *
   * @param value - Value to escape
   * @returns Escaped value
   */
  static escapeCSV(value: string): string {
    if (value == null) return '';

    const str = String(value);

    // If contains comma, quote, or newline, wrap in quotes and escape quotes
    if (str.includes(',') || str.includes('"') || str.includes('\n')) {
      return `"${str.replace(/"/g, '""')}"`;
    }

    return str;
  }

  /**
   * Convert data to JSON format
   *
   * @param data - Data to export
   * @param columns - Column configuration (optional, for filtering fields)
   * @param pretty - Pretty print JSON
   * @returns JSON string
   */
  static toJSON<T>(
    data: T[],
    columns?: DataTableColumn<T>[],
    pretty = false
  ): string {
    if (!columns) {
      // Export all fields
      return JSON.stringify(data, null, pretty ? 2 : 0);
    }

    // Export only specified columns
    const filtered = data.map((row) => {
      const obj: Record<string, unknown> = {};

      for (const col of columns) {
        if (!col.field) continue;

        const value = DataTableUtils.getNestedValue(row, col.field as string);
         
        obj[col.id] = value;
      }

      return obj;
    });

    return JSON.stringify(filtered, null, pretty ? 2 : 0);
  }

  /**
   * Download data as file
   *
   * @param content - File content
   * @param filename - File name
   * @param mimeType - MIME type
   */
  static downloadFile(content: string, filename: string, mimeType: string): void {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');

    link.href = url;
    link.download = filename;
    link.click();

    // Cleanup
    URL.revokeObjectURL(url);
  }

  /**
   * Export table data to CSV file
   *
   * @param data - Data to export
   * @param columns - Column configuration
   * @param filename - File name
   */
  static exportCSV<T>(
    data: T[],
    columns: DataTableColumn<T>[],
    filename = 'export.csv'
  ): void {
    const csv = this.toCSV(data, columns);
    this.downloadFile(csv, filename, 'text/csv;charset=utf-8;');
  }

  /**
   * Export table data to JSON file
   *
   * @param data - Data to export
   * @param columns - Column configuration (optional)
   * @param filename - File name
   * @param pretty - Pretty print JSON
   */
  static exportJSON<T>(
    data: T[],
    columns?: DataTableColumn<T>[],
    filename = 'export.json',
    pretty = true
  ): void {
    const json = this.toJSON(data, columns, pretty);
    this.downloadFile(json, filename, 'application/json;charset=utf-8;');
  }

  /**
   * Copy data to clipboard as CSV
   *
   * @param data - Data to copy
   * @param columns - Column configuration
   * @returns Promise that resolves when copied
   */
  static async copyToClipboard<T>(
    data: T[],
    columns: DataTableColumn<T>[]
  ): Promise<void> {
    const csv = this.toCSV(data, columns);
    await navigator.clipboard.writeText(csv);
  }

  /**
   * Print table data
   *
   * @param data - Data to print
   * @param columns - Column configuration
   * @param title - Document title
   */
  static print<T>(
    data: T[],
    columns: DataTableColumn<T>[],
    title = 'Table Data'
  ): void {
    const printWindow = window.open('', '_blank');
    if (!printWindow) return;

    const html = this.toHTML(data, columns, title);
    printWindow.document.write(html);
    printWindow.document.close();
    printWindow.print();
  }

  /**
   * Convert data to HTML table
   *
   * @param data - Data to convert
   * @param columns - Column configuration
   * @param title - Document title
   * @returns HTML string
   */
  static toHTML<T>(
    data: T[],
    columns: DataTableColumn<T>[],
    title = 'Table Data'
  ): string {
    const headers = columns.map((col) => `<th>${col.label}</th>`).join('');
    const rows = data
      .map((row) => {
        const cells = columns
          .map((col) => {
            if (!col.field) return '<td></td>';

            const value = DataTableUtils.getNestedValue(row, col.field as string);
            const formatted = col.format
              ? col.format(value)
              : DataTableUtils.formatValue(value);

            return `<td>${formatted}</td>`;
          })
          .join('');

        return `<tr>${cells}</tr>`;
      })
      .join('');

    return `
<!DOCTYPE html>
<html>
<head>
  <title>${title}</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 20px; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
    th { background-color: #f5f5f5; font-weight: bold; }
    tr:nth-child(even) { background-color: #fafafa; }
  </style>
</head>
<body>
  <h1>${title}</h1>
  <table>
    <thead>
      <tr>${headers}</tr>
    </thead>
    <tbody>
      ${rows}
    </tbody>
  </table>
</body>
</html>
    `.trim();
  }
}
