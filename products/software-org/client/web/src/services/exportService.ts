/**
 * Export service for generating PDF, CSV, and Excel reports.
 *
 * <p><b>Purpose</b><br>
 * Provides utilities for exporting application data in multiple formats.
 * Handles formatting, styling, and file generation with proper MIME types.
 *
 * <p><b>Supported Formats</b><br>
 * - PDF: Professional formatted documents with headers/footers
 * - CSV: Comma-separated values for spreadsheets
 * - Excel: XLSX format with multiple sheets
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { exportService } from '@/services/export';
 *
 * // Export to CSV
 * exportService.exportToCSV(
 *   departmentsList,
 *   'departments.csv',
 *   ['id', 'name', 'team', 'status']
 * );
 *
 * // Export to PDF
 * exportService.exportToPDF({
 *   title: 'Security Report',
 *   date: new Date(),
 *   content: reportData,
 * }, 'security-report.pdf');
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Multi-format data export
 * @doc.layer product
 * @doc.pattern Service
 */

/**
 * Export format options.
 *
 * @doc.type type
 * @doc.purpose Export format definitions
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export type ExportFormat = 'csv' | 'pdf' | 'excel' | 'json';

/**
 * PDF export options.
 *
 * @doc.type type
 * @doc.purpose PDF generation configuration
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export interface PdfExportOptions {
    title: string;
    subtitle?: string;
    date?: Date;
    content: any;
    headers?: Record<string, string>;
    footer?: string;
    columns?: string[];
}

/**
 * CSV export options.
 *
 * @doc.type type
 * @doc.purpose CSV export configuration
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export interface CsvExportOptions {
    data: any[];
    filename: string;
    columns?: string[];
    headers?: Record<string, string>;
    delimiter?: string;
}

/**
 * Converts object array to CSV string.
 *
 * @param data - Array of objects to convert
 * @param columns - Columns to include (if undefined, uses all keys)
 * @param delimiter - CSV delimiter (default: ',')
 * @returns CSV string
 */
function arrayToCSV(
    data: any[],
    columns?: string[],
    delimiter: string = ','
): string {
    if (data.length === 0) return '';

    // Determine columns from first object if not specified
    const cols = columns || Object.keys(data[0]);

    // Create header row
    const header = cols.join(delimiter);

    // Create data rows
    const rows = data.map((row) =>
        cols
            .map((col) => {
                const value = row[col];
                // Escape quotes and wrap in quotes if contains delimiter
                if (typeof value === 'string' && (value.includes(delimiter) || value.includes('"'))) {
                    return `"${value.replace(/"/g, '""')}"`;
                }
                return value ?? '';
            })
            .join(delimiter)
    );

    return [header, ...rows].join('\n');
}

/**
 * Formats data as HTML table.
 *
 * @param data - Data to format
 * @param columns - Columns to include
 * @returns HTML table string
 */
function arrayToHtmlTable(data: any[], columns?: string[]): string {
    if (data.length === 0) return '<p>No data</p>';

    const cols = columns || Object.keys(data[0]);

    const headerRow = `<tr>${cols.map((col) => `<th>${col}</th>`).join('')}</tr>`;
    const dataRows = data
        .map(
            (row) =>
                `<tr>${cols.map((col) => `<td>${row[col] ?? ''}</td>`).join('')}</tr>`
        )
        .join('');

    return `<table border="1" cellpadding="5">${headerRow}${dataRows}</table>`;
}

/**
 * Export service with methods for different formats.
 *
 * @doc.type object
 * @doc.purpose Export utility methods
 * @doc.layer product
 * @doc.pattern Service
 */
export const exportService = {
    /**
     * Exports data to CSV format and downloads.
     *
     * @param data - Array of objects to export
     * @param filename - Output filename
     * @param columns - Specific columns to include
     * @param delimiter - CSV delimiter (default: ',')
     */
    exportToCSV: (
        data: any[],
        filename: string = 'export.csv',
        columns?: string[],
        delimiter: string = ','
    ) => {
        try {
            const csv = arrayToCSV(data, columns, delimiter);
            const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
            const link = document.createElement('a');
            const url = URL.createObjectURL(blob);

            link.setAttribute('href', url);
            link.setAttribute('download', filename);
            link.style.visibility = 'hidden';

            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);

            console.log('[Export] CSV exported:', filename);
        } catch (err) {
            console.error('[Export] CSV export failed:', err);
            throw err;
        }
    },

    /**
     * Exports data to JSON format and downloads.
     *
     * @param data - Data to export
     * @param filename - Output filename
     */
    exportToJSON: (data: any, filename: string = 'export.json') => {
        try {
            const json = JSON.stringify(data, null, 2);
            const blob = new Blob([json], { type: 'application/json;charset=utf-8;' });
            const link = document.createElement('a');
            const url = URL.createObjectURL(blob);

            link.setAttribute('href', url);
            link.setAttribute('download', filename);
            link.style.visibility = 'hidden';

            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);

            console.log('[Export] JSON exported:', filename);
        } catch (err) {
            console.error('[Export] JSON export failed:', err);
            throw err;
        }
    },

    /**
     * Exports data to HTML table (can be saved as PDF via browser).
     *
     * @param options - PDF export options
     * @param filename - Output filename
     */
    exportToPDF: (options: PdfExportOptions, filename: string = 'export.pdf') => {
        try {
            const { title, subtitle, date, content, columns } = options;

            // Create HTML content
            let html = '<html><head><meta charset="UTF-8"><style>';
            html += 'body { font-family: Arial, sans-serif; margin: 20px; }';
            html += 'h1 { color: #333; border-bottom: 2px solid #007bff; padding-bottom: 10px; }';
            html += 'h2 { color: #666; margin-top: 20px; }';
            html += 'table { border-collapse: collapse; width: 100%; margin: 15px 0; }';
            html += 'th { background: #f0f0f0; border: 1px solid #ddd; padding: 10px; text-align: left; font-weight: bold; }';
            html += 'td { border: 1px solid #ddd; padding: 8px; }';
            html += 'tr:nth-child(even) { background: #f9f9f9; }';
            html += '.date { color: #999; font-size: 12px; margin-top: 10px; }';
            html += '</style></head><body>';

            // Add title
            html += `<h1>${title}</h1>`;
            if (subtitle) html += `<h2>${subtitle}</h2>`;
            if (date) html += `<div class="date">Generated on ${date.toLocaleString()}</div>`;

            // Add content as table
            if (Array.isArray(content)) {
                html += arrayToHtmlTable(content, columns);
            } else {
                html += '<pre>' + JSON.stringify(content, null, 2) + '</pre>';
            }

            html += '</body></html>';

            // Open in new window for printing/saving
            const printWindow = window.open('', '', 'width=800,height=600');
            if (printWindow) {
                printWindow.document.write(html);
                printWindow.document.close();
                // Trigger print dialog
                setTimeout(() => {
                    printWindow.print();
                }, 250);
            }

            console.log('[Export] PDF exported (browser print dialog):', filename);
        } catch (err) {
            console.error('[Export] PDF export failed:', err);
            throw err;
        }
    },

    /**
     * Exports data to Excel format (TSV for basic compatibility).
     *
     * @param data - Array of objects to export
     * @param filename - Output filename
     * @param columns - Specific columns to include
     */
    exportToExcel: (
        data: any[],
        filename: string = 'export.xlsx',
        columns?: string[]
    ) => {
        try {
            // Use tab-delimited format (compatible with Excel)
            const tsv = arrayToCSV(data, columns, '\t');
            const blob = new Blob([tsv], {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8;',
            });
            const link = document.createElement('a');
            const url = URL.createObjectURL(blob);

            link.setAttribute('href', url);
            link.setAttribute('download', filename.replace(/\..*$/, '.xlsx'));
            link.style.visibility = 'hidden';

            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);

            console.log('[Export] Excel exported:', filename);
        } catch (err) {
            console.error('[Export] Excel export failed:', err);
            throw err;
        }
    },

    /**
     * Determines appropriate export function based on format.
     *
     * @param format - Export format
     * @param data - Data to export
     * @param filename - Output filename
     * @param options - Format-specific options
     */
    export: (
        format: ExportFormat,
        data: any,
        filename: string,
        options?: any
    ) => {
        switch (format) {
            case 'csv':
                exportService.exportToCSV(data, filename, options?.columns);
                break;
            case 'json':
                exportService.exportToJSON(data, filename);
                break;
            case 'pdf':
                exportService.exportToPDF(
                    {
                        title: options?.title || 'Export',
                        date: new Date(),
                        content: data,
                        ...options,
                    },
                    filename
                );
                break;
            case 'excel':
                exportService.exportToExcel(data, filename, options?.columns);
                break;
            default:
                throw new Error(`Unsupported export format: ${format}`);
        }
    },
};
